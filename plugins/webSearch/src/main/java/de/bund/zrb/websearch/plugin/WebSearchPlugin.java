package de.bund.zrb.websearch.plugin;

import de.bund.zrb.websearch.commands.WebSearchSettingsMenuCommand;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.MainframeMatePlugin;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WebSearch plugin – provides a headless browser MCP server
 * (wd4j-mcp-server) for web browsing and searching from the chat.
 *
 * <p>This plugin does <b>not</b> ship its own McpTools directly;
 * instead it declares itself as an MCP server plugin. The application
 * runs the browser tools in-process via the wd4j-mcp-server library
 * that is part of the core.</p>
 */
public class WebSearchPlugin implements MainframeMatePlugin {

    private MainframeContext context;

    @Override
    public String getPluginName() {
        return "Websearch";
    }

    @Override
    public void initialize(MainframeContext mainFrame) {
        this.context = mainFrame;
    }

    @Override
    public List<MenuCommand> getCommands(MainframeContext mainFrame) {
        return Arrays.<MenuCommand>asList(
                new WebSearchSettingsMenuCommand(mainFrame)
        );
    }

    @Override
    public List<McpTool> getTools() {
        // Tools come from the in-process MCP server, not from this plugin directly
        return Collections.emptyList();
    }

    // ── MCP Server Plugin API ───────────────────────────────────────

    @Override
    public boolean isMcpServerPlugin() {
        return true;
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
