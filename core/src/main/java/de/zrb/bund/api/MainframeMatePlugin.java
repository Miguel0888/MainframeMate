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
     * If this plugin bundles an MCP server that should be started as a separate
     * process, return the JAR filename (e.g. {@code "wd4j-mcp-server.jar"}).
     * The JAR is expected in the plugins directory ({@code ~/.mainframemate/plugins/}).
     *
     * <p>Return {@code null} (default) if this plugin does not provide an MCP server.</p>
     *
     * @return the JAR filename, or {@code null}
     */
    default String getMcpServerJarName() {
        return null;
    }

    /**
     * If this plugin provides an MCP server, this returns the display name
     * for the server in the MCP registry (defaults to {@link #getPluginName()}).
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
