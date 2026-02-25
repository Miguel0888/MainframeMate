package de.bund.zrb.websearch.plugin;

import de.bund.zrb.mcpserver.tool.impl.*;
import de.bund.zrb.websearch.commands.WebSearchSettingsMenuCommand;
import de.bund.zrb.websearch.tools.*;
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

    @Override
    public void shutdown() {
        if (browserManager != null) {
            browserManager.closeSession();
        }
    }

    private List<McpTool> createTools() {
        List<McpTool> list = new ArrayList<>();

        // ── Research-mode tools (bot-friendly menu-based navigation) ──
        list.add(new BrowserToolAdapter(new ResearchSessionStartTool(), browserManager));
        list.add(new BrowserToolAdapter(new ResearchOpenTool(), browserManager));
        list.add(new BrowserToolAdapter(new ResearchMenuTool(), browserManager));
        list.add(new BrowserToolAdapter(new ResearchChooseTool(), browserManager));
        list.add(new BrowserToolAdapter(new ResearchBackForwardTool(), browserManager));
        list.add(new BrowserToolAdapter(new ResearchConfigUpdateTool(), browserManager));

        // ── Archive/Search/Queue tools (direct McpTool, no browser needed) ──
        list.add(new ResearchDocGetTool());
        list.add(new ResearchSearchTool());
        list.add(new ResearchQueueAddTool());
        list.add(new ResearchQueueStatusTool());

        // ── Low-level browsing tools (NodeRef-based, for advanced use) ──
        list.add(new BrowserToolAdapter(new BrowseNavigateTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseReadPageTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseSnapshotTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseLocateTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseClickTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseTypeTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseSelectTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseScrollTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseWaitTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseBackForwardTool(), browserManager));
        // Keep eval and screenshot as utility tools
        list.add(new BrowserToolAdapter(new BrowserEvalTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserScreenshotTool(), browserManager));
        return list;
    }
}
