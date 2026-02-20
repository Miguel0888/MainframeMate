package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Closes the browser session and cleans up resources.
 */
public class BrowserCloseTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_close";
    }

    @Override
    public String description() {
        return "Close the browser session, disconnect the WebSocket, and terminate the browser process if it was launched by the server.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        try {
            session.close();
            return ToolResult.text("Browser session closed.");
        } catch (Exception e) {
            return ToolResult.error("Close failed: " + e.getMessage());
        }
    }
}

