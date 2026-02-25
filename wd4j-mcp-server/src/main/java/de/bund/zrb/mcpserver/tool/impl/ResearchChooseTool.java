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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Choose a menu item from the current view.
 * <p>
 * The bot passes a {@code menuItemId} (e.g. "m3") and the {@code viewToken}
 * from the last {@code research_menu} / {@code research_open} response.
 * If the viewToken is stale, the tool rejects the request and instructs
 * the bot to call {@code research_menu} first.
 * <p>
 * After clicking, the tool waits according to the {@code settlePolicy},
 * then returns a fresh menu view.
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
             + "Requires the viewToken from the last research_menu or research_open response. "
             + "If the viewToken is stale, call research_menu first to get a fresh view. "
             + "After the action, returns an updated menu view. "
             + "settlePolicy: NAVIGATION (default, for link clicks), DOM_QUIET (SPA), NETWORK_QUIET (AJAX).";
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
        settle.addProperty("description", "How to wait after click: NAVIGATION (default), DOM_QUIET, NETWORK_QUIET");
        props.add("settlePolicy", settle);

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

        LOG.info("[research_choose] Choose " + menuItemId + " (viewToken=" + viewToken + ", settle=" + policy + ")");

        long timeoutSeconds = Long.getLong("websearch.navigate.timeout.seconds", 60);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ToolResult> future = executor.submit(new Callable<ToolResult>() {
            @Override
            public ToolResult call() throws Exception {
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
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        }

        // Click the element via WebDriver actions (scroll into view + click)
        try {
            String ctx = session.getContextId();
            WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(ctx));
            List<WDLocalValue> args = Collections.<WDLocalValue>singletonList(sharedRef);

            session.getDriver().script().callFunction(
                    "function(el) { el.scrollIntoView({block:'center'}); el.click(); }",
                    true, target, args);

            LOG.info("[research_choose] Clicked " + menuItemId + " successfully");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_choose] Click failed for " + menuItemId, e);
            return ToolResult.error("Click failed for " + menuItemId + ": " + e.getMessage()
                    + ". The element may have become stale. Call research_menu to get a fresh view.");
        }

        // Invalidate old refs and build new menu view
        try {
            session.getNodeRefRegistry().invalidateAll();
            rs.invalidateView();

            MenuViewBuilder builder = new MenuViewBuilder(session, rs);
            MenuView view = builder.buildWithSettle(policy, rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return ToolResult.text(view.toCompactText());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_choose] Post-click view build failed", e);
            return ToolResult.error("Click succeeded but page view failed: " + e.getMessage()
                    + ". Call research_menu to get the current state.");
        }
    }
}

