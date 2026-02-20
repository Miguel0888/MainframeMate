package de.bund.zrb.mcp.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for an external MCP server.
 * Modelled after GitHub Copilot's MCP server registry.
 */
public class McpServerConfig {

    private String name;
    private String command;
    private List<String> args = new ArrayList<>();
    private boolean enabled = true;

    public McpServerConfig() {}

    public McpServerConfig(String name, String command, List<String> args, boolean enabled) {
        this.name = name;
        this.command = command;
        this.args = args != null ? args : new ArrayList<String>();
        this.enabled = enabled;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public List<String> getArgs() { return args; }
    public void setArgs(List<String> args) { this.args = args != null ? args : new ArrayList<String>(); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public String toString() {
        return name + " (" + command + ")";
    }
}

