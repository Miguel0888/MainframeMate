package de.zrb.bund.api;

import de.zrb.bund.newApi.bot.Agent;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.Collections;
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
     * Returns the agents provided by this plugin.
     * Agents are registered in the AgentRegistry during plugin initialization.
     *
     * @return list of agents, or empty list if this plugin provides no agents
     */
    default List<Agent> getAgents() {
        return Collections.emptyList();
    }

    /**
     * Called when the application is shutting down.
     * Plugins should release all resources (e.g. browser processes, connections).
     */
    default void shutdown() {
        // default: nothing to clean up
    }
}
