package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.browsingContext.WDReadinessState;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Choose a menu item (link) from the current view and navigate to its URL.
 * <p>
 * Navigation is purely URL-based (address bar). No element clicking, no JS injection.
 * The link href is extracted from the MenuView and navigated to via
 * {@code browsingContext.navigate}.
 */
public class ResearchChooseTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchChooseTool.class.getName());

    @Override
    public String name() {
        return "research_choose";
    }

    @Override
    public String description() {
        return "[DEPRECATED – use research_navigate with the URL as target instead] "
             + "Follow a link from the current page. Use research_navigate instead.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject menuItem = new JsonObject();
        menuItem.addProperty("type", "string");
        menuItem.addProperty("description", "Link ID to follow (e.g. 'm0', 'm3')");
        props.add("menuItemId", menuItem);

        JsonObject viewToken = new JsonObject();
        viewToken.addProperty("type", "string");
        viewToken.addProperty("description", "viewToken from the last response");
        props.add("viewToken", viewToken);


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
            return ToolResult.error("Missing required parameter 'viewToken'.");
        }

        LOG.info("[research_choose] Follow " + menuItemId + " (viewToken=" + viewToken + ")");

        try {
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);

            // Tolerate viewToken='null' (string) – use current token instead.
            // This can happen if the bot received a stale response with null viewToken.
            if ("null".equals(viewToken)) {
                String current = rs.getCurrentViewToken();
                if (current != null) {
                    LOG.warning("[research_choose] Bot sent viewToken='null', using current: " + current);
                    viewToken = current;
                }
            }

            // Validate viewToken
            if (!rs.isViewTokenValid(viewToken)) {
                return ToolResult.error("Stale viewToken '" + viewToken + "'. "
                        + "Call research_open to get a fresh view.");
            }

            // Find the MenuItem and its URL
            MenuView currentView = rs.getCurrentMenuView();
            if (currentView == null) {
                return ToolResult.error("No current view. Call research_open first.");
            }

            MenuItem chosen = null;
            for (MenuItem item : currentView.getMenuItems()) {
                if (menuItemId.equals(item.getMenuItemId())) {
                    chosen = item;
                    break;
                }
            }

            if (chosen == null) {
                return ToolResult.error("Unknown menuItemId '" + menuItemId + "'. "
                        + "Valid IDs: m0 to m" + (currentView.getMenuItems().size() - 1));
            }

            String url = chosen.getHref();
            if (url == null || url.isEmpty()) {
                return ToolResult.error("Menu item '" + menuItemId + "' has no URL.");
            }

            LOG.info("[research_choose] Navigating to: " + url + " (label: " + chosen.getLabel() + ")");

            // Ensure pipeline is running (auto-start after browser restart)
            NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
            if (pipeline == null || !pipeline.isActive()) {
                try {
                    NetworkIngestionPipeline newPipeline = new NetworkIngestionPipeline(session.getDriver(), rs);
                    NetworkIngestionPipeline.IngestionCallback cb = NetworkIngestionPipeline.getGlobalDefaultCallback();
                    if (cb == null) {
                        cb = (runId, u, mime, st, body, headers, ts) -> "net-" + Long.toHexString(ts);
                    }
                    if (rs.getRunId() == null) rs.setRunId(java.util.UUID.randomUUID().toString());
                    newPipeline.start(cb);
                    rs.setNetworkPipeline(newPipeline);
                    pipeline = newPipeline;
                } catch (Exception ex) {
                    LOG.warning("[research_choose] Failed to auto-start pipeline: " + ex.getMessage());
                }
            }
            if (pipeline != null) {
                pipeline.clearNavigationCache();
                pipeline.setLastNavigationUrl(url);
            }

            session.getDriver().browsingContext().navigate(
                    url, session.getContextId(), WDReadinessState.INTERACTIVE);

            // Build menu from new page HTML
            MenuViewBuilder builder = new MenuViewBuilder(rs, pipeline);
            MenuView view = builder.buildWithSettle(SettlePolicy.NAVIGATION,
                    rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            // Response
            List<String> newDocs = rs.drainNewArchivedDocIds();
            StringBuilder sb = new StringBuilder(view.toCompactText());
            if (!newDocs.isEmpty()) {
                sb.append("\n── Newly archived (").append(newDocs.size()).append(") ──\n");
                for (String docId : newDocs) {
                    sb.append("  ").append(docId).append("\n");
                }
            }
            sb.append("\n── Next step ──\n");
            sb.append("Use research_navigate with a URL from the link list to continue.");

            return ToolResult.text(sb.toString());

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_choose] Failed", e);
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }
}
