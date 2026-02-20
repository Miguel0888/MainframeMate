package de.bund.zrb.mcpserver.tool;

import java.util.*;

/**
 * Registry of all available MCP tools.
 */
public class ToolRegistry {

    private final Map<String, McpServerTool> tools = new LinkedHashMap<String, McpServerTool>();

    public void register(McpServerTool tool) {
        tools.put(tool.name(), tool);
    }

    public McpServerTool getTool(String name) {
        return tools.get(name);
    }

    public Collection<McpServerTool> allTools() {
        return Collections.unmodifiableCollection(tools.values());
    }
}

