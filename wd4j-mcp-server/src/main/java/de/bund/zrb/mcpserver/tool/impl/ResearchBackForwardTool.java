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
 * Browser history navigation (back/forward/reload) with menu view result.
 * Returns a fresh menu view after the navigation action.
 */
public class ResearchBackForwardTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchBackForwardTool.class.getName());

    @Override
    public String name() {
        return "research_history";
    }

    @Override
    public String description() {
        return "[DEPRECATED – use research_navigate with target='back'/'forward'/'reload' instead] "
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

        SettlePolicy policy = SettlePolicy.fromString(
                params.has("settlePolicy") ? params.get("settlePolicy").getAsString() : null);

        LOG.info("[research_navigate] Action: " + action + " (settle=" + policy + ")");

        try {
            // Execute history action via BiDi-native commands (NO evaluate!)
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

            // Build fresh menu view
            NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
            MenuViewBuilder builder = new MenuViewBuilder(rs, pipeline);
            MenuView view = builder.buildWithSettle(policy, rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return ToolResult.text(view.toCompactText());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_navigate] Failed", e);
            return ToolResult.error("Navigation action '" + action + "' failed: " + e.getMessage());
        }
    }
}

