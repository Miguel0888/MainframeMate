package de.bund.zrb.mcpserver.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.tool.ToolRegistry;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.transport.JsonRpcMessage;
import de.bund.zrb.mcpserver.transport.StdioTransport;

/**
 * Routes incoming JSON-RPC methods to the appropriate MCP handlers.
 * Tool calls are serialised through a single-threaded approach (synchronized).
 */
public class McpRouter {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final StdioTransport transport;
    private final ToolRegistry toolRegistry;
    private final BrowserSession browserSession;

    private final Object toolLock = new Object();

    public McpRouter(StdioTransport transport, ToolRegistry toolRegistry, BrowserSession browserSession) {
        this.transport = transport;
        this.toolRegistry = toolRegistry;
        this.browserSession = browserSession;
    }

    /**
     * Dispatch a single incoming message.
     */
    public void handle(JsonRpcMessage msg) {
        if (msg.isNotification()) {
            handleNotification(msg);
            return;
        }

        if (!msg.isRequest()) {
            // Ignore stray responses
            return;
        }

        String method = msg.getMethod();
        JsonElement id = msg.getId();

        try {
            switch (method) {
                case "initialize":
                    handleInitialize(id, msg.getParams());
                    break;
                case "ping":
                    handlePing(id);
                    break;
                case "tools/list":
                    handleToolsList(id);
                    break;
                case "tools/call":
                    handleToolsCall(id, msg.getParams());
                    break;
                default:
                    transport.sendError(id, McpError.METHOD_NOT_FOUND,
                            "Method not found: " + method);
            }
        } catch (Exception e) {
            System.err.println("[MCP] Error handling method '" + method + "': " + e.getMessage());
            transport.sendError(id, McpError.INTERNAL_ERROR, e.getMessage());
        }
    }

    // ── Handlers ────────────────────────────────────────────────────

    private void handleInitialize(JsonElement id, JsonObject params) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "wd4j-mcp-server");
        serverInfo.addProperty("version", "1.0.0");
        result.add("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        // We support tools
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);

        transport.sendResult(id, result);
    }

    private void handlePing(JsonElement id) {
        transport.sendResult(id, new JsonObject());
    }

    private void handleToolsList(JsonElement id) {
        JsonObject result = new JsonObject();
        JsonArray tools = new JsonArray();

        for (McpServerTool tool : toolRegistry.allTools()) {
            JsonObject toolObj = new JsonObject();
            toolObj.addProperty("name", tool.name());
            toolObj.addProperty("description", tool.description());
            toolObj.add("inputSchema", tool.inputSchema());
            tools.add(toolObj);
        }
        result.add("tools", tools);
        transport.sendResult(id, result);
    }

    private void handleToolsCall(JsonElement id, JsonObject params) {
        if (params == null || !params.has("name")) {
            transport.sendError(id, McpError.INVALID_PARAMS, "Missing 'name' in tools/call params");
            return;
        }

        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        McpServerTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            transport.sendError(id, McpError.INVALID_PARAMS, "Unknown tool: " + toolName);
            return;
        }

        // Serialise tool execution
        synchronized (toolLock) {
            try {
                ToolResult toolResult = tool.execute(arguments, browserSession);
                transport.sendResult(id, toolResult.toJson());
            } catch (Exception e) {
                System.err.println("[MCP] Tool '" + toolName + "' threw: " + e.getMessage());
                e.printStackTrace(System.err);
                ToolResult errResult = ToolResult.error("Tool execution failed: " + e.getMessage());
                transport.sendResult(id, errResult.toJson());
            }
        }
    }

    private void handleNotification(JsonRpcMessage msg) {
        String method = msg.getMethod();
        if ("notifications/initialized".equals(method)) {
            System.err.println("[MCP] Client initialized.");
        } else if ("notifications/cancelled".equals(method)) {
            System.err.println("[MCP] Request cancelled by client.");
        } else {
            System.err.println("[MCP] Unknown notification: " + method);
        }
    }
}

