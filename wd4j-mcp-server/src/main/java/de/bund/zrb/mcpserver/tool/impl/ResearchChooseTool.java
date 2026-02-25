package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.browsingContext.WDBrowsingContext;
import de.bund.zrb.type.script.WDLocalValue;
import de.bund.zrb.type.script.WDRemoteReference;
import de.bund.zrb.type.script.WDTarget;
import de.bund.zrb.command.request.parameters.input.sourceActions.PointerSourceAction;
import de.bund.zrb.command.request.parameters.input.sourceActions.SourceActions;
import de.bund.zrb.type.input.WDElementOrigin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Choose a menu item from the current view.
 * <p>
 * Eingaben (MUSS): menuItemId, viewToken, sessionId (opt.), contextId (opt.),
 * wait (opt.), settlePolicy (opt.).
 * <p>
 * Aktionen passieren AUSSEN mittels locateNodes+input.performActions (nicht per JS click).
 * Bei Fallback (performActions nicht unterstützt): scrollIntoView + el.click() per callFunction.
 * <p>
 * Settle-Policy (MUSS):
 * - NAVIGATION: warten auf Navigation Events
 * - DOM_QUIET: MutationObserver-basiert
 * - NETWORK_QUIET: PerformanceObserver-basiert
 */
public class ResearchChooseTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchChooseTool.class.getName());

    @Override
    public String name() {
        return "research_choose";
    }

    @Override
    public String description() {
        return "Choose a menu item by its ID (e.g. 'm3') from the current view. "
             + "Requires viewToken from the last research_menu/research_open response. "
             + "Stale viewToken → call research_menu first. "
             + "Click is performed via WebDriver Actions (not JS). "
             + "settlePolicy: NAVIGATION (default), DOM_QUIET (SPA), NETWORK_QUIET (AJAX).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject menuItem = new JsonObject();
        menuItem.addProperty("type", "string");
        menuItem.addProperty("description", "Menu item ID to choose (e.g. 'm0', 'm3')");
        props.add("menuItemId", menuItem);

        JsonObject viewToken = new JsonObject();
        viewToken.addProperty("type", "string");
        viewToken.addProperty("description", "viewToken from the last research_menu/research_open response");
        props.add("viewToken", viewToken);

        JsonObject settle = new JsonObject();
        settle.addProperty("type", "string");
        settle.addProperty("description", "Settle strategy: NAVIGATION (default), DOM_QUIET, NETWORK_QUIET");
        props.add("settlePolicy", settle);

        JsonObject wait = new JsonObject();
        wait.addProperty("type", "string");
        wait.addProperty("description", "ReadinessState hint: 'none', 'interactive', 'complete'");
        props.add("wait", wait);

        JsonObject sessionId = new JsonObject();
        sessionId.addProperty("type", "string");
        sessionId.addProperty("description", "Session ID (optional)");
        props.add("sessionId", sessionId);

        JsonObject contextId = new JsonObject();
        contextId.addProperty("type", "string");
        contextId.addProperty("description", "BrowsingContext ID (optional)");
        props.add("contextId", contextId);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("menuItemId");
        required.add("viewToken");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String menuItemId = params.has("menuItemId") ? params.get("menuItemId").getAsString() : null;
        String viewToken = params.has("viewToken") ? params.get("viewToken").getAsString() : null;

        if (menuItemId == null || menuItemId.isEmpty()) {
            return ToolResult.error("Missing required parameter 'menuItemId'.");
        }
        if (viewToken == null || viewToken.isEmpty()) {
            return ToolResult.error("Missing required parameter 'viewToken'. "
                    + "Call research_menu first to get the current viewToken.");
        }

        SettlePolicy policy = SettlePolicy.fromString(
                params.has("settlePolicy") ? params.get("settlePolicy").getAsString() : null);

        LOG.info("[research_choose] Choose " + menuItemId
                + " (viewToken=" + viewToken + ", settle=" + policy + ")");

        long timeoutSeconds = Long.getLong("websearch.navigate.timeout.seconds", 60);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ToolResult> future = executor.submit(new Callable<ToolResult>() {
            @Override
            public ToolResult call() {
                return doChoose(menuItemId, viewToken, policy, session);
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            LOG.severe("[research_choose] Timeout choosing " + menuItemId);
            session.killBrowserProcess();
            return ToolResult.error(
                    "Action timeout. Browser terminated and will restart. Try again.");
        } catch (ExecutionException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            LOG.warning("[research_choose] Failed: " + msg);
            return ToolResult.error("Choose failed: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Action interrupted.");
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolResult doChoose(String menuItemId, String viewToken,
                                SettlePolicy policy, BrowserSession session) {
        ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);

        // Validate viewToken
        if (!rs.isViewTokenValid(viewToken)) {
            LOG.warning("[research_choose] Stale viewToken: " + viewToken
                    + " (current: " + rs.getCurrentViewToken() + ")");
            return ToolResult.error(
                    "Stale viewToken '" + viewToken + "'. "
                  + "The page has changed since your last view. "
                  + "Call research_menu to get a fresh viewToken and menu items, "
                  + "then retry research_choose with the new viewToken.");
        }

        // Resolve menuItemId → SharedReference
        WDRemoteReference.SharedReference sharedRef;
        try {
            sharedRef = rs.resolveMenuItem(menuItemId, viewToken);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        }

        // Click via WebDriver input.performActions (outside, not JS click)
        String ctx = session.getContextId();
        try {
            clickViaActions(session, ctx, sharedRef);
            LOG.info("[research_choose] Clicked " + menuItemId + " via WebDriver Actions");
        } catch (Exception actionsEx) {
            // Fallback: JS click (some browsers/elements don't support pointer actions)
            LOG.warning("[research_choose] performActions failed, falling back to JS click: "
                    + actionsEx.getMessage());
            try {
                clickViaJs(session, ctx, sharedRef);
                LOG.info("[research_choose] Clicked " + menuItemId + " via JS fallback");
            } catch (Exception jsEx) {
                LOG.log(Level.WARNING, "[research_choose] Click failed for " + menuItemId, jsEx);
                return ToolResult.error("Click failed for " + menuItemId + ": " + jsEx.getMessage()
                        + ". Element may be stale. Call research_menu for a fresh view.");
            }
        }

        // Invalidate old refs and build new menu view
        try {
            session.getNodeRefRegistry().invalidateAll();
            rs.invalidateView();

            MenuViewBuilder builder = new MenuViewBuilder(session, rs);
            MenuView view = builder.buildWithSettle(policy,
                    rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            // Append newly archived docs
            List<String> newDocs = rs.drainNewArchivedDocIds();
            StringBuilder sb = new StringBuilder(view.toCompactText());
            if (!newDocs.isEmpty()) {
                sb.append("\n── Newly archived (").append(newDocs.size()).append(") ──\n");
                for (String docId : newDocs) {
                    sb.append("  ").append(docId).append("\n");
                }
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_choose] Post-click view build failed", e);
            return ToolResult.error("Click succeeded but page view failed: " + e.getMessage()
                    + ". Call research_menu to get the current state.");
        }
    }

    /**
     * Click an element via WebDriver BiDi input.performActions (pointer move to element + click).
     * This is the spec-compliant approach: actions happen OUTSIDE the page JS.
     */
    private void clickViaActions(BrowserSession session, String ctx,
                                 WDRemoteReference.SharedReference sharedRef) {
        // First scroll into view
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(ctx));
        List<WDLocalValue> args = Collections.<WDLocalValue>singletonList(sharedRef);
        session.getDriver().script().callFunction(
                "function(el) { el.scrollIntoView({block:'center'}); }",
                true, target, args);

        // Build pointer action: move to element origin, then down+up
        WDElementOrigin elementOrigin = new WDElementOrigin(sharedRef);
        PointerSourceAction.PointerMoveAction move = new PointerSourceAction.PointerMoveAction(
                0, 0, elementOrigin);
        PointerSourceAction.PointerDownAction down = new PointerSourceAction.PointerDownAction(0);
        PointerSourceAction.PointerUpAction up = new PointerSourceAction.PointerUpAction(0);

        List<PointerSourceAction> actionSeq = new ArrayList<>();
        actionSeq.add(move);
        actionSeq.add(down);
        actionSeq.add(up);

        SourceActions.PointerSourceActions pointerActions = new SourceActions.PointerSourceActions(
                "pointer-click",
                new SourceActions.PointerSourceActions.PointerParameters(),
                actionSeq);

        List<SourceActions> allActions = new ArrayList<>();
        allActions.add(pointerActions);

        session.getDriver().input().performActions(ctx, allActions);
    }

    /**
     * Fallback: click via JS callFunction (scrollIntoView + el.click()).
     */
    private void clickViaJs(BrowserSession session, String ctx,
                            WDRemoteReference.SharedReference sharedRef) {
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(ctx));
        List<WDLocalValue> args = Collections.<WDLocalValue>singletonList(sharedRef);
        session.getDriver().script().callFunction(
                "function(el) { el.scrollIntoView({block:'center'}); el.click(); }",
                true, target, args);
    }
}
