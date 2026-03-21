package de.bund.zrb.websearch.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.search.SearchService;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * MCP Tool to search the archive (unified entries) via Lucene index + H2.
 * Returns entries with title, excerpt, and metadata.
 * Use research_doc_get with docId to get full content.
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
        props.put("runId", new ToolSpec.Property("string",
                "Optional: filter results to a specific research run."));
        props.put("host", new ToolSpec.Property("string",
                "Optional: filter results to a specific host/domain."));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, Collections.singletonList("query"));

        return new ToolSpec(
                "research_search",
                "Search the catalog for documents matching a query. "
              + "Returns ranked results with document IDs, titles, excerpts, URLs, and timestamps. "
              + "Use research_doc_get with the returned docId to get full document content. "
              + "Searches curated Documents (not raw resources).",
                inputSchema, null);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String query = input.has("query") && !input.get("query").isJsonNull()
                ? input.get("query").getAsString().trim() : null;
        int maxResults = input.has("maxResults") ? input.get("maxResults").getAsInt() : 10;
        if (maxResults > 50) maxResults = 50;
        if (maxResults < 1) maxResults = 1;
        String runId = input.has("runId") && !input.get("runId").isJsonNull()
                ? input.get("runId").getAsString() : null;
        String host = input.has("host") && !input.get("host").isJsonNull()
                ? input.get("host").getAsString() : null;

        if (query == null || query.isEmpty()) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Parameter 'query' is required.");
            return new McpToolResponse(err, resultVar, null);
        }

        try {
            // First: search Lucene index
            SearchService searchService = SearchService.getInstance();
            List<SearchResult> luceneResults = searchService.search(query, null, maxResults, false);

            // Also search the H2 entries directly for title/excerpt/url matches
            CacheRepository repo = CacheRepository.getInstance();
            List<ArchiveEntry> dbEntries = repo.searchEntriesWithHost(query, host, maxResults);

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "ok");
            resp.addProperty("query", query);

            JsonArray hits = new JsonArray();

            // Add Lucene results
            Set<String> seenDocIds = new HashSet<String>();
            for (SearchResult r : luceneResults) {
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

                // Enrich with entry metadata if available
                ArchiveEntry entry = repo.findById(r.getDocumentId());
                if (entry != null) {
                    hit.addProperty("docId", entry.getEntryId());
                    hit.addProperty("title", entry.getTitle());
                    hit.addProperty("url", entry.getUrl());
                    hit.addProperty("kind", entry.getKind());
                    hit.addProperty("createdAt", entry.getCrawlTimestamp());
                    hit.addProperty("createdAtFormatted", formatTimestamp(entry.getCrawlTimestamp()));
                    hit.addProperty("host", entry.getHost());
                    hit.addProperty("wordCount", entry.getWordCount());
                    if (entry.getExcerpt() != null && !entry.getExcerpt().isEmpty()) {
                        hit.addProperty("excerpt", entry.getExcerpt());
                    }
                    seenDocIds.add(entry.getEntryId());

                    // Filter by runId if specified
                    if (runId != null && !runId.equals(entry.getRunId())) continue;
                    // Filter by host if specified
                    if (host != null && !host.equals(entry.getHost())) continue;
                }

                hits.add(hit);
            }

            // Add DB-only results (not in Lucene)
            for (ArchiveEntry entry : dbEntries) {
                if (seenDocIds.contains(entry.getEntryId())) continue;
                if (runId != null && !runId.equals(entry.getRunId())) continue;

                JsonObject hit = new JsonObject();
                hit.addProperty("docId", entry.getEntryId());
                hit.addProperty("title", entry.getTitle());
                hit.addProperty("url", entry.getUrl());
                hit.addProperty("kind", entry.getKind());
                hit.addProperty("createdAt", entry.getCrawlTimestamp());
                hit.addProperty("createdAtFormatted", formatTimestamp(entry.getCrawlTimestamp()));
                hit.addProperty("host", entry.getHost());
                hit.addProperty("wordCount", entry.getWordCount());
                hit.addProperty("source", "CATALOG");
                if (entry.getExcerpt() != null && !entry.getExcerpt().isEmpty()) {
                    hit.addProperty("excerpt", entry.getExcerpt());
                }
                hits.add(hit);
            }

            resp.addProperty("totalHits", hits.size());
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

    private String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
        return sdf.format(new Date(epochMillis));
    }
}

