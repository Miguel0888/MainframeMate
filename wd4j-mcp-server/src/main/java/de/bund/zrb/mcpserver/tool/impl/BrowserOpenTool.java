package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Connects to an existing BiDi WebSocket endpoint and creates a session.
 */
public class BrowserOpenTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_open";
    }

    @Override
    public String description() {
        return "Connect to an existing browser via its BiDi WebSocket URL. Creates a session and default browsing context.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject wsUrl = new JsonObject();
        wsUrl.addProperty("type", "string");
        wsUrl.addProperty("description", "The BiDi WebSocket URL of the browser (e.g. ws://localhost:9222)");
        props.add("bidiWebSocketUrl", wsUrl);

        JsonObject browserName = new JsonObject();
        browserName.addProperty("type", "string");
        browserName.addProperty("description", "Browser name for session creation (default: firefox)");
        props.add("browserName", browserName);

        JsonObject createContext = new JsonObject();
        createContext.addProperty("type", "boolean");
        createContext.addProperty("description", "Whether to create a new browsing context (tab). Default: true");
        props.add("createContext", createContext);

        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("bidiWebSocketUrl");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String wsUrl = params.get("bidiWebSocketUrl").getAsString();
        String browserName = params.has("browserName") ? params.get("browserName").getAsString() : "firefox";
        boolean createContext = !params.has("createContext") || params.get("createContext").getAsBoolean();

        try {
            session.connect(wsUrl, browserName, createContext);
            String contextId = session.getContextId();

            StringBuilder sb = new StringBuilder();
            sb.append("Connected to browser at ").append(wsUrl);
            if (contextId != null) {
                sb.append("\ncontextId: ").append(contextId);
            }
            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Failed to connect: " + e.getMessage());
        }
    }
}

