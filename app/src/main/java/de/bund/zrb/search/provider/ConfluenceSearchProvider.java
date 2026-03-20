package de.bund.zrb.search.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.confluence.ConfluenceRestClient;
import de.bund.zrb.search.BackendSearchProvider;
import de.bund.zrb.search.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live backend search provider for Confluence Data Center.
 * <p>
 * Uses CQL ({@code text~"query"}) via the Confluence REST API to search
 * across all accessible spaces.  Results are returned as
 * {@link SearchResult}s with source type {@link SearchResult.SourceType#CONFLUENCE}.
 * <p>
 * The ConfluenceRestClient is expected to be pre-configured with
 * mTLS certificate and Basic Auth credentials.
 */
public class ConfluenceSearchProvider implements BackendSearchProvider {

    private static final Logger LOG = Logger.getLogger(ConfluenceSearchProvider.class.getName());

    private final ConfluenceRestClient client;
    private final String baseUrl;

    /**
     * @param client  pre-authenticated Confluence REST client
     * @param baseUrl the Confluence base URL (for display in search results)
     */
    public ConfluenceSearchProvider(ConfluenceRestClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl != null ? baseUrl : "Confluence";
    }

    @Override
    public SearchResult.SourceType getSourceType() {
        return SearchResult.SourceType.CONFLUENCE;
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws Exception {
        List<SearchResult> results = new ArrayList<SearchResult>();

        // Build CQL query
        String cql = "type=page AND text~\"" + query.replace("\"", "\\\"") + "\"";
        int limit = Math.min(maxResults, 50);

        try {
            String json = client.searchJson(cql, 0, limit);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("results");
            if (arr == null) return results;

            for (JsonElement el : arr) {
                if (results.size() >= maxResults) break;
                JsonObject pg = el.getAsJsonObject();
                // Search results may wrap content in a "content" object
                JsonObject content = pg.has("content") ? pg.getAsJsonObject("content") : pg;

                String id = content.has("id") ? content.get("id").getAsString() : "";
                String title = content.has("title") ? content.get("title").getAsString() : "";
                String spaceKey = "";
                if (content.has("space") && content.getAsJsonObject("space").has("key")) {
                    spaceKey = content.getAsJsonObject("space").get("key").getAsString();
                }

                // Extract excerpt if available
                String excerpt = "";
                if (pg.has("excerpt")) {
                    excerpt = pg.get("excerpt").getAsString()
                            .replaceAll("<[^>]+>", "")  // strip HTML tags
                            .replaceAll("@@@hl@@@|@@@endhl@@@", ""); // strip highlight markers
                    if (excerpt.length() > 300) excerpt = excerpt.substring(0, 300) + "…";
                }

                String docId = "confluence://" + id;
                String path = spaceKey.isEmpty() ? baseUrl : spaceKey + " @ " + baseUrl;

                results.add(new SearchResult(
                        SearchResult.SourceType.CONFLUENCE,
                        docId,
                        title,
                        path,
                        excerpt,
                        0.8f,  // slightly lower than Lucene-indexed results
                        docId,
                        spaceKey
                ));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[ConfluenceSearch] CQL search failed", e);
            throw e;
        }
        return results;
    }
}

