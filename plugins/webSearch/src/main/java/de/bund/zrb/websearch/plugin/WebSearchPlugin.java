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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSearch plugin – provides browser tools for web browsing and searching.
 *
 * <p>Wraps the wd4j-mcp-server tools (BrowserNavigate, BrowserClick, etc.)
 * as regular McpTools that are registered in the ToolRegistry, just like
 * the ExcelImport plugin registers its ImportExcelTool.</p>
 */
public class WebSearchPlugin implements MainframeMatePlugin {

    private static final Logger LOG = Logger.getLogger("de.bund.zrb.plugin");

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

        // Register snapshot archiving callback:
        // DOM snapshots → Data Lake + Catalog via ArchiveService
        registerSnapshotArchivingCallback();
        // Register run lifecycle callback for Data Lake run management
        registerRunLifecycleCallback();
        LOG.info("[WebSearchPlugin] Initialized with " + tools.size() + " tools");
    }

    /**
     * Connects the DOM snapshot archiving (wd4j-mcp-server) to the
     * ArchiveService (app) for persistent storage via Data Lake + Catalog.
     * Replaces the old NetworkIngestionPipeline callback.
     */
    private void registerSnapshotArchivingCallback() {
        try {
            de.bund.zrb.archive.service.ArchiveService archiveService =
                    de.bund.zrb.archive.service.ArchiveService.getInstance();

            de.bund.zrb.mcpserver.research.ResearchSessionManager.setSnapshotArchivingCallback(
                    (runId, url, mimeType, statusCode, bodyText, capturedAt) ->
                            archiveService.ingestNetworkResponse(
                                    runId, url, mimeType, statusCode, bodyText,
                                    java.util.Collections.emptyMap(), capturedAt)
            );
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[WebSearchPlugin] Could not register snapshot archiving callback: " + e.getMessage(), e);
        }
    }

    /**
     * Connects the ResearchSessionManager run lifecycle to the ArchiveService
     * for Data Lake run creation/completion.
     */
    private void registerRunLifecycleCallback() {
        try {
            de.bund.zrb.archive.service.ArchiveService archiveService =
                    de.bund.zrb.archive.service.ArchiveService.getInstance();

            de.bund.zrb.mcpserver.research.ResearchSessionManager.setRunLifecycleCallback(
                    new de.bund.zrb.mcpserver.research.RunLifecycleCallback() {
                        @Override
                        public String startRun(String mode, String domainPolicyJson) {
                            de.bund.zrb.archive.model.ArchiveRun run =
                                    archiveService.startRun(mode, null, domainPolicyJson);
                            return run.getRunId();
                        }

                        @Override
                        public void endRun(String runId) {
                            archiveService.endRun(runId);
                        }
                    });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[WebSearchPlugin] Could not register run lifecycle callback: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MenuCommand> getCommands(MainframeContext mainFrame) {
        return Arrays.<MenuCommand>asList(
                new WebSearchSettingsMenuCommand(mainFrame, browserManager)
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
        list.add(new BrowserToolAdapter(new ResearchNavigateTool(), browserManager));

        // ── Archive/Search/Queue tools (direct McpTool, no browser needed) ──
        list.add(new ResearchDocGetTool());
        list.add(new ResearchResourceGetTool());list.add(new ResearchSearchTool());
        list.add(new ResearchQueueAddTool());
        list.add(new ResearchQueueStatusTool());

        // ── Utility tools (no research equivalent, kept as-is) ──
        list.add(new BrowserToolAdapter(new BrowseClickTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseTypeTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseSelectTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowseScrollTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserEvalTool(), browserManager));
        list.add(new BrowserToolAdapter(new BrowserScreenshotTool(), browserManager));
        return list;
    }
}
