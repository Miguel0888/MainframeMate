package de.bund.zrb.websearch.plugin;

import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.MainframeMatePlugin;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.Collections;
import java.util.List;

/**
 * WebSearch plugin – provides a headless browser MCP server
 * (wd4j-mcp-server) for web browsing and searching from the chat.
 *
 * <p>This plugin does <b>not</b> ship its own McpTools directly;
 * instead it declares an MCP server JAR that the application starts
 * as a separate process. The server's tools are then discovered
 * dynamically via JSON-RPC {@code tools/list}.</p>
 */
public class WebSearchPlugin implements MainframeMatePlugin {

    @Override
    public String getPluginName() {
        return "Websearch";
    }

    @Override
    public void initialize(MainframeContext mainFrame) {
        // Nothing to do – the MCP server is started by McpServerManager
    }

    @Override
    public List<MenuCommand> getCommands(MainframeContext mainFrame) {
        return Collections.emptyList();
    }

    @Override
    public List<McpTool> getTools() {
        // Tools come from the MCP server process, not from this plugin directly
        return Collections.emptyList();
    }

    // ── MCP Server Plugin API ───────────────────────────────────────

    @Override
    public String getMcpServerJarName() {
        return "wd4j-mcp-server.jar";
    }

    @Override
    public String getMcpServerDisplayName() {
        return "Websearch";
    }

    @Override
    public boolean isMcpServerEnabledByDefault() {
        return false;
    }
}

