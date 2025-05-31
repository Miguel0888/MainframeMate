package de.zrb.bund.newApi;

import de.zrb.bund.newApi.mcp.McpTool;

public interface PluginRegistry {
    McpTool get(String toolName);
}
