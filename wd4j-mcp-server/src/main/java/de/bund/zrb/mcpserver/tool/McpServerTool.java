package de.bund.zrb.mcpserver.tool;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;

/**
 * Interface for an MCP tool exposed by the server.
 */
public interface McpServerTool {

    /** The unique tool name used in tools/call. */
    String name();

    /** Human-readable description shown in tools/list. */
    String description();

    /** JSON Schema describing the input parameters. */
    JsonObject inputSchema();

    /**
     * Execute the tool with the given arguments.
     *
     * @param params   arguments from the MCP client
     * @param session  the shared browser session state
     * @return tool result (text/image content)
     */
    ToolResult execute(JsonObject params, BrowserSession session);
}

