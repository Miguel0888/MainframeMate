package de.bund.zrb.mcp;

import de.zrb.bund.newApi.mcp.McpTool;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ToolRegistry {
    private final Map<String, McpTool> tools = new HashMap<>();

    public void register(McpTool tool) {
        tools.put(tool.getSpec().getName(), tool);
    }

    public McpTool resolve(String name) {
        return tools.get(name);
    }

    public Collection<McpTool> all() {
        return tools.values();
    }
}
