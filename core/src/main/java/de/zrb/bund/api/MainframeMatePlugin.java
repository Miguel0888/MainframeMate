package de.zrb.bund.api;

import de.zrb.bund.newApi.mcp.McpTool;

import java.util.List;

public interface MainframeMatePlugin {

    String getPluginName();

    default List<Object> getCommands(Object context) {
        return java.util.Collections.emptyList();
    }

    void initialize(MainframeContext mainFrame);

    List<MenuCommand> getCommands(MainframeContext mainFrame);

    List<McpTool> getTools();

    /**
     * Whether this plugin provides an in-process MCP server.
     * If {@code true}, the application will register the server in the
     * MCP server manager using {@link #getMcpServerDisplayName()} and
     * start it when the user enables it.
     *
     * @return {@code true} if this plugin is an MCP server plugin
     */
    default boolean isMcpServerPlugin() {
        return false;
    }

    /**
     * Display name for the MCP server in the registry
     * (defaults to {@link #getPluginName()}).
     */
    default String getMcpServerDisplayName() {
        return getPluginName();
    }

    /**
     * Whether the MCP server should be enabled by default when first registered.
     * Defaults to {@code false} (user must opt-in).
     */
    default boolean isMcpServerEnabledByDefault() {
        return false;
    }
}
