package de.bund.zrb.websearch.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.search.SearchService;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;
import java.util.logging.Logger;

/**
 * MCP Tool to search the Lucene index for archived/indexed documents.
 * Returns ranked results with snippets.
 */
public class ResearchSearchTool implements McpTool {

    private static final Logger LOG = Logger.getLogger(ResearchSearchTool.class.getName());

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("query", new ToolSpec.Property("string",
                "Search query (supports Lucene syntax: \"exact phrase\", AND, OR, NOT, wildcards *, fuzzy ~1)"));
        props.put("maxResults", new ToolSpec.Property("integer",
                "Max results to return (default: 10, max: 50)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, Collections.singletonList("query"));

        return new ToolSpec(
                "research_search",
                "Search the local archive/index (Lucene) for documents matching a query. "
              + "Returns ranked results with document IDs, URLs, snippets, and scores. "
              + "Use research_doc_get with the returned entryId to get full document content.",
                inputSchema, null);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String query = input.has("query") && !input.get("query").isJsonNull()
                ? input.get("query").getAsString().trim() : null;
        int maxResults = input.has("maxResults") ? input.get("maxResults").getAsInt() : 10;
        if (maxResults > 50) maxResults = 50;
        if (maxResults < 1) maxResults = 1;

        if (query == null || query.isEmpty()) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Parameter 'query' is required.");
            return new McpToolResponse(err, resultVar, null);
        }

        try {
            SearchService searchService = SearchService.getInstance();
            // Search across all sources
            List<SearchResult> results = searchService.search(query, null, maxResults, false);

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "ok");
            resp.addProperty("query", query);
            resp.addProperty("totalHits", results.size());

            JsonArray hits = new JsonArray();
            for (SearchResult r : results) {
                JsonObject hit = new JsonObject();
                hit.addProperty("documentId", r.getDocumentId());
                hit.addProperty("documentName", r.getDocumentName());
                hit.addProperty("source", r.getSource().name());
                hit.addProperty("score", r.getScore());

                if (r.getHeading() != null && !r.getHeading().isEmpty()) {
                    hit.addProperty("heading", r.getHeading());
                }
                if (r.getSnippet() != null && !r.getSnippet().isEmpty()) {
                    hit.addProperty("snippet", r.getSnippet());
                }

                hits.add(hit);
            }
            resp.add("results", hits);

            return new McpToolResponse(resp, resultVar, null);
        } catch (Exception e) {
            LOG.warning("[research_search] Search failed: " + e.getMessage());
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Search failed: " + e.getMessage());
            return new McpToolResponse(err, resultVar, null);
        }
    }
}

