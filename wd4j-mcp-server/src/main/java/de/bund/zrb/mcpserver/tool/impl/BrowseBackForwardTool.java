package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Navigate back, forward, or reload.
 */
public class BrowseBackForwardTool implements McpServerTool {

    @Override
    public String name() {
        return "web_history";
    }

    @Override
    public String description() {
        return "Navigate browser history: action='back', 'forward', or 'reload'.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        action.addProperty("description", "History action: 'back', 'forward', 'reload'");
        props.add("action", action);

        schema.add("properties", props);
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("action");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String action = params.get("action").getAsString();
        try {
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

            // Invalidate refs after navigation
            session.getNodeRefRegistry().invalidateAll();
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            String url = "";
            try {
                Object result = session.evaluate("window.location.href", true);
                if (result instanceof de.bund.zrb.type.script.WDEvaluateResult.WDEvaluateResultSuccess) {
                    url = ((de.bund.zrb.type.script.WDEvaluateResult.WDEvaluateResultSuccess) result)
                            .getResult().asString();
                }
            } catch (Exception ignored) {}

            return ToolResult.text(action + " done. Current URL: " + url
                    + "\nUse web_read_page to read content, or web_snapshot to see interactive elements.");
        } catch (Exception e) {
            return ToolResult.error("History action failed: " + e.getMessage());
        }
    }
}

