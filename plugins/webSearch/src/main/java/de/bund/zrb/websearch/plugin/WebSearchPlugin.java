package de.bund.zrb.websearch.plugin;

import de.bund.zrb.mcpserver.tool.impl.*;
import de.bund.zrb.websearch.commands.WebSearchSettingsMenuCommand;
import de.bund.zrb.websearch.tools.BrowserToolAdapter;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.MainframeMatePlugin;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WebSearch plugin – provides browser tools for web browsing and searching.
 *
 * <p>Wraps the wd4j-mcp-server tools (BrowserNavigate, BrowserClick, etc.)
 * as regular McpTools that are registered in the ToolRegistry, just like
 * the ExcelImport plugin registers its ImportExcelTool.</p>
 */
public class WebSearchPlugin implements MainframeMatePlugin {

    private MainframeContext context;
    private WebSearchBrowserManager browserManager;
    private List<McpTool> tools;

    @Override
    public String getPluginName() {
        return "Websearch";
    }

    @Override
    public void initialize(MainframeContext mainFrame) {
        this.context = mainFrame;
        this.browserManager = new WebSearchBrowserManager(mainFrame);
        this.tools = createTools();
    }

    @Override
    public List<MenuCommand> getCommands(MainframeContext mainFrame) {
        return Arrays.<MenuCommand>asList(
                new WebSearchSettingsMenuCommand(mainFrame)
        );
    }

    @Override
    public List<McpTool> getTools() {
        return tools != null ? tools : Collections.<McpTool>emptyList();
    }

    private List<McpTool> createTools() {
        List<McpTool> list = new ArrayList<>();
        // browser_launch / browser_open are NOT exposed as tools –
        // the browser is started automatically by WebSearchBrowserManager on first use.
        list.add(new BrowserToolAdapter(new BrowserNavigateTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserClickCssTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserTypeCssTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserEvalTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserScreenshotTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserCloseTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserWaitForTool(), browserManager));
        list.add(new BrowserToolAdapter(new PageDomSnapshotTool(), browserManager));
        list.add(new BrowserToolAdapter(new PageExtractTool(), browserManager));
        return list;
    }
}
