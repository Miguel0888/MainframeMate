package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @deprecated Use {@link ResearchBackTool}, {@link ResearchForwardTool},
 * or {@link ResearchReloadTool} instead.
 */
@Deprecated
public class ResearchBackForwardTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchBackForwardTool.class.getName());

    @Override
    public String name() {
        return "research_history";
    }

    @Override
    public String description() {
        return "[DEPRECATED – use research_back / research_forward / research_reload instead] "
             + "Navigate browser history: action='back', 'forward', or 'reload'. "
             + "Returns a fresh link list after the action.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        action.addProperty("description", "Navigation action: 'back', 'forward', or 'reload'");
        props.add("action", action);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("action");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String action = params.has("action") ? params.get("action").getAsString() : null;
        if (action == null || action.isEmpty()) {
            return ToolResult.error("Missing required parameter 'action'. Use 'back', 'forward', or 'reload'.");
        }

        LOG.info("[research_history] Action: " + action);

        try {
            // Execute history action via BiDi-native commands
            switch (action.toLowerCase()) {
                case "back":
                    session.getDriver().browsingContext().traverseHistory(session.getContextId(), -1);
                    break;
                case "forward":
                    session.getDriver().browsingContext().traverseHistory(session.getContextId(), 1);
                    break;
                case "reload":
                    session.getDriver().browsingContext().reload(session.getContextId());
                    break;
                default:
                    return ToolResult.error("Unknown action: " + action + ". Use 'back', 'forward', or 'reload'.");
            }

            // Invalidate old state
            session.getNodeRefRegistry().invalidateAll();
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);
            rs.invalidateView();

            // Dismiss cookie banners + fetch DOM snapshot
            CookieBannerDismisser.tryDismiss(session);
            String html = DomSnapshotFetcher.fetchHtml(session);
            String currentUrl = DomSnapshotFetcher.fetchCurrentUrl(session);

            if (currentUrl != null) {
                rs.setLastNavigationUrl(currentUrl);
            }

            MenuViewBuilder builder = new MenuViewBuilder(rs);
            builder.setHtmlOverride(html, currentUrl != null ? currentUrl : "");
            MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return ToolResult.text(view.toCompactText());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_history] Failed", e);
            return ToolResult.error("Navigation action '" + action + "' failed: " + e.getMessage());
        }
    }
}

