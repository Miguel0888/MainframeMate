package de.zrb.bund.newApi;

import de.zrb.bund.newApi.mcp.McpTool;

import java.util.List;

public interface ToolRegistry {

    void registerTool(McpTool tool);

    List<McpTool> getAllTools();

    McpTool getToolByName(String toolName);
}
