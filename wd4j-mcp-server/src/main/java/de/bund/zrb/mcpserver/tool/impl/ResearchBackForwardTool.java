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
        return "research_navigate";
    }

    @Override
    public String description() {
        return "Navigate browser history: action='back', 'forward', or 'reload'. "
             + "Returns a fresh menu view after the action. "
             + "settlePolicy: NAVIGATION (default), DOM_QUIET, NETWORK_QUIET.";
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

        JsonObject settle = new JsonObject();
        settle.addProperty("type", "string");
        settle.addProperty("description", "How to wait after action: NAVIGATION (default), DOM_QUIET, NETWORK_QUIET");
        props.add("settlePolicy", settle);

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
            // Execute history action
            switch (action.toLowerCase()) {
                case "back":
                    session.evaluate("window.history.back()", true);
                    break;
                case "forward":
                    session.evaluate("window.history.forward()", true);
                    break;
                case "reload":
                    session.evaluate("window.location.reload()", true);
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

