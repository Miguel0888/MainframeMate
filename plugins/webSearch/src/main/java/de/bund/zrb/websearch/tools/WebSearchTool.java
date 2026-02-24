package de.bund.zrb.websearch.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.websearch.fetch.ContentFetcher;
import de.bund.zrb.websearch.fetch.FetchOptions;
import de.bund.zrb.websearch.fetch.FetchResult;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP Tool: web_search
 *
 * Performs a web search using DuckDuckGo HTML (no API key needed, no browser needed).
 * Returns a list of search results with title, URL, and snippet.
 * Optionally fetches the full content of top results.
 *
 * <p>This is much faster and more reliable than navigating to a search engine in the browser.</p>
 */
public class WebSearchTool implements McpTool {

    private static final Logger LOG = Logger.getLogger(WebSearchTool.class.getName());

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";

    private final ContentFetcher contentFetcher;

    /**
     * @param contentFetcher used to optionally fetch full page content of results
     */
    public WebSearchTool(ContentFetcher contentFetcher) {
        this.contentFetcher = contentFetcher;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("query", new ToolSpec.Property("string", "Search query (required)"));
        props.put("maxResults", new ToolSpec.Property("integer",
                "Maximum number of search results to return (default: 10, max: 20)"));
        props.put("fetchContent", new ToolSpec.Property("boolean",
                "If true, also fetches the full text content of the top 3 results (default: false). "
              + "This makes the call slower but gives you the actual page content."));

        List<String> required = Collections.singletonList("query");
        ToolSpec.InputSchema schema = new ToolSpec.InputSchema(props, required);

        return new ToolSpec(
                "web_search",
                "Search the web using DuckDuckGo. Returns search results with title, URL, and snippet. "
              + "No browser needed – fast and reliable. "
              + "Set fetchContent=true to also read the content of top results.",
                schema,
                null
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String query = input.has("query") ? input.get("query").getAsString() : null;
        if (query == null || query.trim().isEmpty()) {
            return errorResponse("Missing required parameter 'query'", resultVar);
        }

        int maxResults = input.has("maxResults") ? input.get("maxResults").getAsInt() : 10;
        if (maxResults > 20) maxResults = 20;
        if (maxResults < 1) maxResults = 1;

        boolean fetchContent = input.has("fetchContent") && input.get("fetchContent").getAsBoolean();

        LOG.info("[web_search] Searching: '" + query + "' (maxResults=" + maxResults
                + ", fetchContent=" + fetchContent + ")");

        try {
            List<SearchResult> results = searchDuckDuckGo(query, maxResults);

            if (results.isEmpty()) {
                // Fallback: try Google-style search
                LOG.info("[web_search] DuckDuckGo returned no results, trying Google...");
                results = searchGoogle(query, maxResults);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: \"").append(query).append("\"\n");
            sb.append("Found ").append(results.size()).append(" results\n");
            sb.append("════════════════════════════════════════\n\n");

            int fetchCount = 0;
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                sb.append(i + 1).append(". ").append(r.title).append("\n");
                sb.append("   ").append(r.url).append("\n");
                if (r.snippet != null && !r.snippet.isEmpty()) {
                    sb.append("   ").append(r.snippet).append("\n");
                }

                // Fetch full content of top results if requested
                if (fetchContent && fetchCount < 3 && r.url != null && contentFetcher != null) {
                    try {
                        LOG.info("[web_search] Fetching content for result " + (i + 1) + ": " + r.url);
                        FetchResult fetchResult = contentFetcher.fetch(r.url,
                                new FetchOptions(10000, 3000, null, false));
                        if (fetchResult.isSuccess() && fetchResult.hasSubstantialContent()) {
                            sb.append("\n   ── Content ──\n");
                            String text = fetchResult.getText();
                            if (text.length() > 3000) text = text.substring(0, 3000) + "…";
                            // Indent content
                            for (String line : text.split("\n")) {
                                sb.append("   ").append(line).append("\n");
                            }
                            sb.append("   ── End ──\n");
                            fetchCount++;
                        }
                    } catch (Exception e) {
                        LOG.fine("[web_search] Content fetch failed for " + r.url + ": " + e.getMessage());
                    }
                }

                sb.append("\n");
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "ok");
            response.addProperty("result", sb.toString());
            response.addProperty("resultCount", results.size());

            LOG.info("[web_search] Completed with " + results.size() + " results");
            return new McpToolResponse(response, resultVar, null);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[web_search] Search failed for query: " + query, e);
            return errorResponse("Search failed: " + e.getMessage(), resultVar);
        }
    }

    /**
     * Search DuckDuckGo HTML version (no JavaScript, no API key).
     */
    private List<SearchResult> searchDuckDuckGo(String query, int maxResults) throws Exception {
        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

        LOG.fine("[web_search] DuckDuckGo URL: " + searchUrl);

        Document doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .followRedirects(true)
                .referrer("https://duckduckgo.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "de,en;q=0.9")
                .get();

        List<SearchResult> results = new ArrayList<>();

        // DuckDuckGo HTML result structure
        Elements resultElements = doc.select(".result");
        if (resultElements.isEmpty()) {
            // Alternative selector
            resultElements = doc.select(".web-result");
        }
        if (resultElements.isEmpty()) {
            // Try generic link-based extraction
            resultElements = doc.select(".results_links");
        }

        for (Element resultEl : resultElements) {
            if (results.size() >= maxResults) break;

            // Extract title and URL
            Element titleLink = resultEl.selectFirst(".result__a");
            if (titleLink == null) titleLink = resultEl.selectFirst("a.result__url");
            if (titleLink == null) titleLink = resultEl.selectFirst("a[href]");
            if (titleLink == null) continue;

            String title = titleLink.text().trim();
            String href = titleLink.attr("href");

            // DuckDuckGo wraps URLs in a redirect - extract the actual URL
            String url = extractDdgUrl(href);
            if (url == null || url.isEmpty()) continue;
            if (!url.startsWith("http")) continue;

            // Extract snippet
            Element snippetEl = resultEl.selectFirst(".result__snippet");
            String snippet = snippetEl != null ? snippetEl.text().trim() : "";

            results.add(new SearchResult(title, url, snippet));
        }

        return results;
    }

    /**
     * Fallback: Search Google and scrape results.
     */
    private List<SearchResult> searchGoogle(String query, int maxResults) throws Exception {
        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String searchUrl = "https://www.google.com/search?q=" + encodedQuery + "&hl=de&num=" + maxResults;

        Document doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "de,en;q=0.9")
                .get();

        List<SearchResult> results = new ArrayList<>();

        // Google search result structure (may change)
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            if (results.size() >= maxResults) break;

            String href = link.attr("href");
            // Google wraps results in /url?q=...
            if (href.startsWith("/url?q=")) {
                String url = href.substring(7);
                int ampIdx = url.indexOf('&');
                if (ampIdx > 0) url = url.substring(0, ampIdx);
                try {
                    url = java.net.URLDecoder.decode(url, "UTF-8");
                } catch (Exception ignored) {}

                if (!url.startsWith("http")) continue;
                if (url.contains("google.com")) continue;

                String title = link.text().trim();
                if (title.isEmpty()) continue;

                // Try to get snippet from parent
                String snippet = "";
                Element parent = link.parent();
                if (parent != null) {
                    Element snippetEl = parent.parent();
                    if (snippetEl != null) {
                        Elements spans = snippetEl.select("span");
                        for (Element span : spans) {
                            String text = span.text().trim();
                            if (text.length() > 40 && !text.equals(title)) {
                                snippet = text;
                                break;
                            }
                        }
                    }
                }

                results.add(new SearchResult(title, url, snippet));
            }
        }

        return results;
    }

    /**
     * Extract the actual URL from a DuckDuckGo redirect URL.
     * DDG format: //duckduckgo.com/l/?uddg=https%3A%2F%2F...&rut=...
     */
    private String extractDdgUrl(String href) {
        if (href == null) return null;

        // Direct URL
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }

        // DDG redirect
        if (href.contains("uddg=")) {
            int start = href.indexOf("uddg=") + 5;
            int end = href.indexOf('&', start);
            String encoded = end > 0 ? href.substring(start, end) : href.substring(start);
            try {
                return java.net.URLDecoder.decode(encoded, "UTF-8");
            } catch (Exception e) {
                return encoded;
            }
        }

        return href;
    }

    private McpToolResponse errorResponse(String message, String resultVar) {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        return new McpToolResponse(error, resultVar, null);
    }

    private static class SearchResult {
        final String title;
        final String url;
        final String snippet;

        SearchResult(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }
}
