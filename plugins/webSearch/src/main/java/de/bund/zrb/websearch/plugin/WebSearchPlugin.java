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

        // Register global NetworkIngestionPipeline callback:
        // captured HTTP response bodies → H2 archive via WebSnapshotPipeline
        registerNetworkIngestionCallback();
    }

    /**
     * Connects the NetworkIngestionPipeline (wd4j-mcp-server) to the
     * ArchiveService/WebSnapshotPipeline (app) for persistent storage.
     */
    private void registerNetworkIngestionCallback() {
        try {
            de.bund.zrb.archive.service.ArchiveService archiveService =
                    de.bund.zrb.archive.service.ArchiveService.getInstance();
            de.bund.zrb.archive.service.WebSnapshotPipeline snapshotPipeline =
                    archiveService.getSnapshotPipeline();

            de.bund.zrb.mcpserver.research.NetworkIngestionPipeline.setGlobalDefaultCallback(
                    new de.bund.zrb.mcpserver.research.NetworkIngestionPipeline.IngestionCallback() {
                        @Override
                        public String onBodyCaptured(String url, String mimeType, long status,
                                                     String bodyText, java.util.Map<String, String> headers,
                                                     long capturedAt) {
                            // Extract title from HTML if available
                            String title = extractTitleFromHtml(bodyText, url);

                            // Persist via WebSnapshotPipeline → H2 + filesystem
                            de.bund.zrb.archive.model.ArchiveEntry entry =
                                    snapshotPipeline.processSnapshot(url, bodyText, title);

                            if (entry != null) {
                                return entry.getEntryId();
                            }
                            return null;
                        }
                    });
        } catch (Exception e) {
            System.err.println("[WebSearchPlugin] Could not register network ingestion callback: " + e.getMessage());
        }
    }

    /**
     * Quick title extraction from HTML body text.
     */
    private static String extractTitleFromHtml(String body, String fallbackUrl) {
        if (body == null) return truncateTitle(fallbackUrl);
        int titleStart = body.indexOf("<title>");
        if (titleStart < 0) titleStart = body.indexOf("<TITLE>");
        if (titleStart >= 0) {
            titleStart += 7; // length of "<title>"
            int titleEnd = body.indexOf("</title>", titleStart);
            if (titleEnd < 0) titleEnd = body.indexOf("</TITLE>", titleStart);
            if (titleEnd > titleStart && (titleEnd - titleStart) < 500) {
                return body.substring(titleStart, titleEnd).trim();
            }
        }
        return truncateTitle(fallbackUrl);
    }

    /** Truncate title to fit H2 VARCHAR(512) column. */
    private static String truncateTitle(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500) + "…";
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
        // These replace the old Browse* tools with a menu/viewToken-based API
        list.add(new BrowserToolAdapter(new ResearchSessionStartTool(), browserManager));
        list.add(new BrowserToolAdapter(new ResearchOpenTool(), browserManager));       // replaces BrowseNavigateTool
        list.add(new BrowserToolAdapter(new ResearchMenuTool(), browserManager));       // replaces BrowseReadPageTool + BrowseSnapshotTool
        list.add(new BrowserToolAdapter(new ResearchChooseTool(), browserManager));     // replaces BrowseClickTool + BrowseLocateTool
        list.add(new BrowserToolAdapter(new ResearchBackForwardTool(), browserManager));// replaces BrowseBackForwardTool
        list.add(new BrowserToolAdapter(new ResearchConfigUpdateTool(), browserManager));

        // ── Archive/Search/Queue tools (direct McpTool, no browser needed) ──
        list.add(new ResearchDocGetTool());
        list.add(new ResearchSearchTool());
        list.add(new ResearchQueueAddTool());
        list.add(new ResearchQueueStatusTool());

        // ── Utility tools (no research equivalent, kept as-is) ──
        list.add(new BrowserToolAdapter(new BrowseTypeTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseSelectTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseScrollTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserEvalTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserScreenshotTool(), browserManager));
        return list;
    }
}
