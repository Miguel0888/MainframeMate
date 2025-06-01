package de.zrb.bund.api;

import de.zrb.bund.newApi.mcp.McpTool;

import java.util.List;

public interface MainframeMatePlugin {

    String getPluginName();

    void initialize(Object context); // kein MainFrame

    default List<Object> getCommands(Object context) {
        return java.util.Collections.emptyList();
    }

    void initialize(MainframeContext mainFrame);

    List<Command> getCommands(MainframeContext mainFrame);

    List<McpTool> getTools();
}
