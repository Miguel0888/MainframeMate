package de.bund.zrb.websearch.plugin;

import de.bund.zrb.mcpserver.tool.impl.*;
import de.bund.zrb.websearch.commands.WebSearchSettingsMenuCommand;
import de.bund.zrb.websearch.fetch.*;
import de.bund.zrb.websearch.tools.BrowserToolAdapter;
import de.bund.zrb.websearch.tools.WebFetchTool;
import de.bund.zrb.websearch.tools.WebSearchTool;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.MainframeMatePlugin;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * WebSearch plugin – provides tools for web browsing, searching, and content fetching.
 *
 * <h3>Architecture</h3>
 * <p>Two tiers of web access tools:</p>
 * <ul>
 *     <li><b>Jsoup-based (fast, no browser):</b> {@code web_fetch} and {@code web_search}
 *         – use direct HTTP for reading pages and searching. Recommended for most use cases.</li>
 *     <li><b>Browser-based (interactive):</b> {@code web_navigate}, {@code web_click}, etc.
 *         – use Firefox via WebDriver BiDi. Only needed for interacting with pages
 *         (forms, JavaScript-heavy sites, clicking, scrolling).</li>
 * </ul>
 *
 * <p>The {@code web_fetch} tool uses a {@link SmartContentFetcherStrategy} that tries
 * Jsoup first and automatically falls back to the browser when JavaScript rendering
 * is required.</p>
 */
public class WebSearchPlugin implements MainframeMatePlugin {

    private static final Logger LOG = Logger.getLogger(WebSearchPlugin.class.getName());

    private MainframeContext context;
    private WebSearchBrowserManager browserManager;
    private SmartContentFetcherStrategy contentFetcher;
    private List<McpTool> tools;

    @Override
    public String getPluginName() {
        return "Websearch";
    }

    @Override
    public void initialize(MainframeContext mainFrame) {
        this.context = mainFrame;
        this.browserManager = new WebSearchBrowserManager(mainFrame);
        this.contentFetcher = createContentFetcher();
        this.tools = createTools();
        LOG.info("[WebSearch] Plugin initialized. Fetch mode: " + contentFetcher.getMode());
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

    /**
     * Creates the smart content fetcher based on plugin settings.
     * Supports modes: SMART (default), JSOUP_ONLY, BROWSER_ONLY.
     */
    private SmartContentFetcherStrategy createContentFetcher() {
        JsoupContentFetcher jsoupFetcher = new JsoupContentFetcher();
        BrowserContentFetcher browserFetcher = new BrowserContentFetcher(browserManager);

        // Read mode from plugin settings (default: SMART)
        Map<String, String> settings = context.loadPluginSettings("webSearch");
        String modeStr = settings.getOrDefault("fetchMode", "SMART").toUpperCase();

        SmartContentFetcherStrategy.Mode mode;
        try {
            mode = SmartContentFetcherStrategy.Mode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            LOG.warning("[WebSearch] Unknown fetchMode '" + modeStr + "', using SMART");
            mode = SmartContentFetcherStrategy.Mode.SMART;
        }

        return new SmartContentFetcherStrategy(jsoupFetcher, browserFetcher, mode);
    }

    private List<McpTool> createTools() {
        List<McpTool> list = new ArrayList<>();

        // ── Jsoup-based tools (fast, no browser) ──────────────────────
        // These should be preferred by the LLM for reading content and searching.
        list.add(new WebFetchTool(contentFetcher));
        list.add(new WebSearchTool(contentFetcher));

        // ── Browser-based tools (interactive, JS-heavy pages) ─────────
        // These require Firefox and are used for page interaction.
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
