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
}
