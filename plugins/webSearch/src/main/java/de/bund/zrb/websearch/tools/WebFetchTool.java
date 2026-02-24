package de.bund.zrb.websearch.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.websearch.fetch.ContentFetcher;
import de.bund.zrb.websearch.fetch.FetchOptions;
import de.bund.zrb.websearch.fetch.FetchResult;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;
import java.util.logging.Logger;

/**
 * MCP Tool: web_fetch
 *
 * Fetches a web page and returns its text content without needing a browser.
 * Uses Jsoup for direct HTTP access (fast, reliable, no timeouts).
 * Automatically falls back to browser for JavaScript-heavy pages when in smart mode.
 *
 * <p>This is the recommended tool for reading web pages. Use web_navigate only
 * when you need to interact with a page (click buttons, fill forms, etc.).</p>
 */
public class WebFetchTool implements McpTool {

    private static final Logger LOG = Logger.getLogger(WebFetchTool.class.getName());

    private final ContentFetcher fetcher;

    public WebFetchTool(ContentFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("url", new ToolSpec.Property("string", "URL to fetch (required)"));
        props.put("selector", new ToolSpec.Property("string",
                "Optional CSS selector to extract only a specific section (e.g. 'article', 'main', '#content')"));
        props.put("maxLength", new ToolSpec.Property("integer",
                "Maximum characters to return (default: 8000, max: 15000)"));
        props.put("includeLinks", new ToolSpec.Property("boolean",
                "Include links found on the page (default: true)"));

        List<String> required = Collections.singletonList("url");
        ToolSpec.InputSchema schema = new ToolSpec.InputSchema(props, required);

        return new ToolSpec(
                "web_fetch",
                "Fetch a web page and extract its text content. This is FAST (no browser needed) and reliable. "
              + "Use this for reading articles, news, documentation, search results, etc. "
              + "Only use web_navigate instead when you need to interact with a page (click, type, scroll). "
              + "Supports CSS selectors to focus on specific page sections.",
                schema,
                null
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String url = input.has("url") ? input.get("url").getAsString() : null;
        if (url == null || url.trim().isEmpty()) {
            return errorResponse("Missing required parameter 'url'", resultVar);
        }

        String selector = input.has("selector") ? input.get("selector").getAsString() : null;
        int maxLength = input.has("maxLength") ? input.get("maxLength").getAsInt() : 8000;
        if (maxLength > 15000) maxLength = 15000;
        if (maxLength < 100) maxLength = 100;
        boolean includeLinks = !input.has("includeLinks") || input.get("includeLinks").getAsBoolean();

        LOG.info("[web_fetch] Fetching: " + url + " (selector=" + selector + ", maxLen=" + maxLength + ")");

        FetchOptions options = new FetchOptions(15000, maxLength, selector, includeLinks);
        FetchResult result = fetcher.fetch(url, options);

        JsonObject response = new JsonObject();
        if (result.isSuccess()) {
            response.addProperty("status", "ok");
            response.addProperty("result", result.toFormattedString(maxLength));

            // Include links as structured data
            if (includeLinks && !result.getLinks().isEmpty()) {
                StringBuilder linkText = new StringBuilder("\n\n── Links ──────────────────────────────\n");
                int count = 0;
                for (FetchResult.Link link : result.getLinks()) {
                    linkText.append("• ").append(link.getText()).append("\n  ").append(link.getUrl()).append("\n");
                    count++;
                    if (count >= 20) {
                        linkText.append("(").append(result.getLinks().size() - 20).append(" more links)\n");
                        break;
                    }
                }
                response.addProperty("result", result.toFormattedString(maxLength) + linkText.toString());
            }
        } else {
            response.addProperty("status", "error");
            response.addProperty("message", result.getErrorMessage());
            response.addProperty("result", "Fetch failed: " + result.getErrorMessage()
                    + "\nURL: " + url
                    + "\nTry using web_navigate as an alternative.");
        }

        response.addProperty("source", result.getSource().name());
        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse errorResponse(String message, String resultVar) {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        return new McpToolResponse(error, resultVar, null);
    }
}
