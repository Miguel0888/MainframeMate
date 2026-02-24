package de.bund.zrb.websearch.fetch;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.mcpserver.tool.impl.BrowseNavigateTool;
import de.bund.zrb.mcpserver.tool.impl.BrowseReadPageTool;
import de.bund.zrb.websearch.plugin.WebSearchBrowserManager;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Content fetcher that uses the browser (WebDriver BiDi) for page retrieval.
 * This is the fallback for JavaScript-heavy pages where Jsoup cannot extract content.
 *
 * <p>Uses the existing BrowseNavigateTool and BrowseReadPageTool internally.</p>
 */
public class BrowserContentFetcher implements ContentFetcher {

    private static final Logger LOG = Logger.getLogger(BrowserContentFetcher.class.getName());

    private final WebSearchBrowserManager browserManager;

    public BrowserContentFetcher(WebSearchBrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    @Override
    public String getName() {
        return "Browser";
    }

    @Override
    public FetchResult fetch(String url) {
        return fetch(url, FetchOptions.DEFAULT);
    }

    @Override
    public FetchResult fetch(String url, FetchOptions options) {
        LOG.info("[BrowserFetcher] Fetching via browser: " + url);
        long start = System.currentTimeMillis();

        try {
            BrowserSession session = browserManager.getSession();

            // Navigate
            BrowseNavigateTool navTool = new BrowseNavigateTool();
            JsonObject navParams = new JsonObject();
            navParams.addProperty("url", url);
            ToolResult navResult = navTool.execute(navParams, session);

            if (navResult.isError()) {
                LOG.warning("[BrowserFetcher] Navigation failed: " + navResult.getText());
                return FetchResult.error(url, "Browser navigation failed: " + navResult.getText());
            }

            // Read page content
            BrowseReadPageTool readTool = new BrowseReadPageTool();
            JsonObject readParams = new JsonObject();
            if (options.getCssSelector() != null) {
                readParams.addProperty("selector", options.getCssSelector());
            }
            readParams.addProperty("maxLength", options.getMaxTextLength());
            ToolResult readResult = readTool.execute(readParams, session);

            long elapsed = System.currentTimeMillis() - start;

            if (readResult.isError()) {
                LOG.warning("[BrowserFetcher] Read failed after " + elapsed + "ms: " + readResult.getText());
                return FetchResult.error(url, "Browser read failed: " + readResult.getText());
            }

            // Parse the read result to extract title and text
            String resultText = readResult.getText();
            String title = extractField(resultText, "Page: ");
            String finalUrl = extractField(resultText, "URL: ");
            String pageText = extractTextAfterSeparator(resultText);

            LOG.info("[BrowserFetcher] Fetched in " + elapsed + "ms: " + finalUrl
                    + " (textLen=" + (pageText != null ? pageText.length() : 0) + ")");

            return FetchResult.builder()
                    .success(true)
                    .source(FetchResult.Source.BROWSER)
                    .url(finalUrl != null ? finalUrl : url)
                    .title(title)
                    .text(pageText)
                    .statusCode(200)
                    .build();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            LOG.log(Level.WARNING, "[BrowserFetcher] Failed after " + elapsed + "ms: " + url, e);
            return FetchResult.error(url, "Browser fetch failed: " + e.getMessage());
        }
    }

    private String extractField(String text, String prefix) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private String extractTextAfterSeparator(String text) {
        if (text == null) return null;
        int sepIdx = text.indexOf("─────");
        if (sepIdx >= 0) {
            int nextLine = text.indexOf('\n', sepIdx);
            if (nextLine >= 0) {
                return text.substring(nextLine + 1).trim();
            }
        }
        return text;
    }
}
