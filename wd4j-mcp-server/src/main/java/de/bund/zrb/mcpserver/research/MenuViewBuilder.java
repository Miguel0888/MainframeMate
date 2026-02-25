package de.bund.zrb.mcpserver.research;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.browser.NodeRef;
import de.bund.zrb.mcpserver.browser.NodeRefRegistry;
import de.bund.zrb.support.ScriptHelper;
import de.bund.zrb.type.browsingContext.WDLocator;
import de.bund.zrb.type.script.WDEvaluateResult;
import de.bund.zrb.type.script.WDRemoteReference;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds a {@link MenuView} from the current page state in a {@link BrowserSession}.
 *
 * <p>Uses a two-phase approach (tagging bridge):
 * <ol>
 *   <li>JS script describes interactive elements and tags them with {@code data-mm-menu-id}</li>
 *   <li>CSS {@code locateNodes} resolves the tagged elements to SharedReferences</li>
 * </ol>
 *
 * <p>Also extracts a page text excerpt for the bot's overview.
 *
 * <p>After building, the MenuView and its menuItemId→SharedRef mapping are
 * stored in the {@link ResearchSession} under a new viewToken.</p>
 */
public class MenuViewBuilder {

    private static final Logger LOG = Logger.getLogger(MenuViewBuilder.class.getName());

    // ── Pre-loaded JS scripts (loaded once from resources) ──────────
    private static final String SCRIPT_DESCRIBE       = ScriptHelper.loadScript("scripts/describePageElements.js");
    private static final String SCRIPT_CLEANUP        = ScriptHelper.loadScript("scripts/cleanupMarkers.js");
    private static final String SCRIPT_SETTLE_DOM     = ScriptHelper.loadScript("scripts/settleDomQuiet.js");
    private static final String SCRIPT_SETTLE_NETWORK = ScriptHelper.loadScript("scripts/settleNetworkQuiet.js");

    private final BrowserSession session;
    private final ResearchSession researchSession;

    public MenuViewBuilder(BrowserSession session, ResearchSession researchSession) {
        this.session = session;
        this.researchSession = researchSession;
    }

    /**
     * Build a MenuView for the current page, register it in the ResearchSession,
     * and return it with a fresh viewToken.
     *
     * @param maxItems    max number of menu items
     * @param excerptLen  max length of the text excerpt
     * @return MenuView with viewToken, excerpt, and menuItems
     */
    public MenuView build(int maxItems, int excerptLen) {
        LOG.fine("[MenuViewBuilder] Building view (maxItems=" + maxItems + ", excerptLen=" + excerptLen + ")");

        // Phase 1: JS describes + tags elements
        String jsOutput = runDescribeScript(maxItems, excerptLen);
        ParsedPage parsed = parseJsOutput(jsOutput);

        LOG.info("[MenuViewBuilder] Page: " + parsed.title + " | URL: " + parsed.url
                + " | Elements: " + parsed.elements.size());

        // Phase 2: CSS locate → SharedReferences
        Map<String, WDRemoteReference.SharedReference> itemRefs = new LinkedHashMap<>();
        List<MenuItem> menuItems = new ArrayList<>();

        if (!parsed.elements.isEmpty()) {
            try {
                // Invalidate old NodeRefs
                session.getNodeRefRegistry().invalidateAll();

                List<NodeRef> nodeRefs = session.locateAndRegister(
                        new WDLocator.CssLocator("[data-mm-menu-id]"),
                        parsed.elements.size());

                for (int i = 0; i < parsed.elements.size(); i++) {
                    ElementInfo el = parsed.elements.get(i);
                    String menuItemId = "m" + i;

                    MenuItem item = new MenuItem(
                            menuItemId,
                            MenuItem.inferType(el.tag),
                            el.label,
                            el.href,
                            el.actionHint
                    );
                    menuItems.add(item);

                    // Map menuItemId → SharedRef if we have one
                    if (i < nodeRefs.size()) {
                        NodeRefRegistry.Entry entry = session.getNodeRefRegistry().resolve(nodeRefs.get(i).getId());
                        itemRefs.put(menuItemId, entry.sharedRef);
                    }
                }

                LOG.fine("[MenuViewBuilder] Mapped " + itemRefs.size() + " menuItems to SharedRefs");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[MenuViewBuilder] CSS locate failed, creating view without action refs", e);
                // Still create menu items, just without refs
                for (int i = 0; i < parsed.elements.size(); i++) {
                    ElementInfo el = parsed.elements.get(i);
                    menuItems.add(new MenuItem("m" + i, MenuItem.inferType(el.tag), el.label, el.href, el.actionHint));
                }
            }
        }

        // Cleanup markers
        cleanupMarkers();

        // Create MenuView and register in session
        MenuView view = new MenuView(null, parsed.url, parsed.title, parsed.excerpt, menuItems);

        // Register and get the viewToken
        String viewToken = researchSession.updateView(view, itemRefs);

        MenuView finalView = new MenuView(viewToken, parsed.url, parsed.title, parsed.excerpt, menuItems);
        LOG.info("[MenuViewBuilder] View built: " + viewToken + " (" + menuItems.size() + " items, "
                + itemRefs.size() + " with refs)");

        return finalView;
    }

    /**
     * Wait for page to settle according to the given policy, then build.
     */
    public MenuView buildWithSettle(SettlePolicy policy, int maxItems, int excerptLen) {
        settle(policy);
        return build(maxItems, excerptLen);
    }

    // ══════════════════════════════════════════════════════════════════
    // Settle strategies
    // ══════════════════════════════════════════════════════════════════

    private void settle(SettlePolicy policy) {
        switch (policy) {
            case NAVIGATION:
                settleNavigation();
                break;
            case DOM_QUIET:
                settleDomQuiet();
                break;
            case NETWORK_QUIET:
                settleNetworkQuiet();
                break;
        }
    }

    private void settleNavigation() {
        sleep(300);
    }

    private void settleDomQuiet() {
        try {
            session.evaluate(SCRIPT_SETTLE_DOM, true);
        } catch (Exception e) {
            LOG.fine("[MenuViewBuilder] DOM_QUIET settle failed: " + e.getMessage());
        }
        sleep(300);
    }

    private void settleNetworkQuiet() {
        try {
            session.evaluate(SCRIPT_SETTLE_NETWORK, true);
        } catch (Exception e) {
            LOG.fine("[MenuViewBuilder] NETWORK_QUIET settle failed: " + e.getMessage());
        }
        sleep(300);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Script execution
    // ══════════════════════════════════════════════════════════════════

    /**
     * Run the describe script with a Java-side timeout guard.
     * The script itself also has an internal 3 s timeout.
     */
    private String runDescribeScript(int maxItems, int excerptLen) {
        // Inject parameters into the pre-loaded script template
        final String script = SCRIPT_DESCRIBE
                .replace("__MAX_ITEMS__", String.valueOf(maxItems))
                .replace("__EXCERPT_LEN__", String.valueOf(excerptLen));

        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = ex.submit(new Callable<String>() {
                @Override
                public String call() {
                    Object result = session.evaluate(script, true);
                    if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                        String s = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
                        if (s != null && !s.startsWith("[Object:")) return s;
                    }
                    return "";
                }
            });
            return future.get(8, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.warning("[MenuViewBuilder] Describe script timed out after 8s");
            return "";
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MenuViewBuilder] Describe script failed", e);
            return "";
        } finally {
            ex.shutdownNow();
        }
    }

    private void cleanupMarkers() {
        try {
            session.evaluate(SCRIPT_CLEANUP, true);
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════
    // Parsing
    // ══════════════════════════════════════════════════════════════════

    private ParsedPage parseJsOutput(String jsOutput) {
        ParsedPage page = new ParsedPage();
        if (jsOutput == null || jsOutput.isEmpty()) return page;

        String[] lines = jsOutput.split("\n");
        for (String line : lines) {
            if (line.startsWith("TITLE|")) {
                page.title = line.substring(6);
            } else if (line.startsWith("URL|")) {
                page.url = line.substring(4);
            } else if (line.startsWith("EXCERPT|")) {
                page.excerpt = line.substring(8);
            } else if (line.startsWith("EL|")) {
                String[] parts = line.split("\\|", 7);
                if (parts.length >= 4) {
                    ElementInfo el = new ElementInfo();
                    el.index = parts[1];
                    el.tag = parts[2];
                    el.label = parts.length > 3 ? parts[3] : "";
                    el.href = parts.length > 4 ? parts[4] : "";
                    el.actionHint = parts.length > 5 ? parts[5] : "";
                    page.elements.add(el);
                }
            }
        }
        return page;
    }

    // ── Internal data classes ───────────────────────────────────────

    private static class ParsedPage {
        String title = "";
        String url = "";
        String excerpt = "";
        List<ElementInfo> elements = new ArrayList<>();
    }

    private static class ElementInfo {
        String index;
        String tag;
        String label;
        String href;
        String actionHint;
    }
}

