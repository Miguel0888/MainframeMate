package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.service.RagService;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.search.SearchService;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * MCP Tool for searching the global Lucene index across all sources
 * (local files, FTP, NDV, mails).
 *
 * The bot can use this tool to find documents, mails, and files
 * matching a user's query – without the user having to open the search tab.
 *
 * Supports:
 * - Full-text search (BM25) across all indexed sources
 * - Source filtering (LOCAL, FTP, NDV, MAIL)
 * - Automatic hybrid search (BM25 + embeddings) when embeddings are available
 * - Configurable result count
 */
public class SearchIndexTool implements McpTool {

    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final int MAX_RESULTS_LIMIT = 50;
    private static final int MAX_SNIPPET_LENGTH = 500;

    private final MainframeContext context;

    public SearchIndexTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("query", new ToolSpec.Property("string",
                "Suchanfrage (Volltextsuche). Unterstützt Lucene-Syntax: "
                        + "\"exakte phrase\", AND, OR, NOT, Wildcards (hamburg*), "
                        + "Fuzzy (hamburg~1), Feldsuche (subject:bericht)."));
        properties.put("sources", new ToolSpec.Property("array",
                "Optionale Liste der Quellen, in denen gesucht werden soll. "
                        + "Mögliche Werte: LOCAL, FTP, NDV, MAIL. "
                        + "Wenn nicht angegeben, werden alle Quellen durchsucht."));
        properties.put("maxResults", new ToolSpec.Property("integer",
                "Maximale Anzahl Ergebnisse (Standard: 10, Maximum: 50)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(
                properties, Collections.singletonList("query"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("query", "Hamburger Hafen Bericht");
        example.put("sources", Arrays.asList("LOCAL", "MAIL"));
        example.put("maxResults", 5);

        return new ToolSpec(
                "search_index",
                "Durchsucht den globalen Volltextindex über alle Quellen "
                        + "(lokale Dateien, FTP, NDV, E-Mails). "
                        + "Findet Dokumente, Mails und Dateien anhand von Suchbegriffen. "
                        + "Nutze dieses Tool, wenn der Nutzer nach bestimmten Dokumenten, "
                        + "Informationen oder Dateien sucht. "
                        + "Gibt Treffer mit Dateiname, Pfad, Quelle, Score und Text-Snippet zurück.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            // ── Parse query (required) ──
            String query = input.has("query") && !input.get("query").isJsonNull()
                    ? input.get("query").getAsString().trim() : null;
            if (query == null || query.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Parameter 'query' ist erforderlich.");
                return new McpToolResponse(response, resultVar, null);
            }

            // ── Parse sources (optional) ──
            Set<SearchResult.SourceType> sources = new LinkedHashSet<>();
            if (input.has("sources") && !input.get("sources").isJsonNull()) {
                for (com.google.gson.JsonElement elem : input.getAsJsonArray("sources")) {
                    String s = elem.getAsString().toUpperCase();
                    try {
                        sources.add(SearchResult.SourceType.valueOf(s));
                    } catch (IllegalArgumentException ignored) {
                        // skip unknown source types
                    }
                }
            }
            if (sources.isEmpty()) {
                // Default: all sources
                sources.add(SearchResult.SourceType.LOCAL);
                sources.add(SearchResult.SourceType.FTP);
                sources.add(SearchResult.SourceType.NDV);
                sources.add(SearchResult.SourceType.MAIL);
            }

            // ── Parse maxResults (optional) ──
            int maxResults = DEFAULT_MAX_RESULTS;
            if (input.has("maxResults") && !input.get("maxResults").isJsonNull()) {
                maxResults = Math.min(input.get("maxResults").getAsInt(), MAX_RESULTS_LIMIT);
                if (maxResults < 1) maxResults = DEFAULT_MAX_RESULTS;
            }

            // ── Execute search ──
            SearchService searchService = SearchService.getInstance();
            List<SearchResult> results = searchService.search(query, sources, maxResults, false);

            // ── Build response ──
            JsonArray hits = new JsonArray();
            for (SearchResult r : results) {
                JsonObject hit = new JsonObject();
                hit.addProperty("documentName", r.getDocumentName());
                hit.addProperty("path", r.getPath());
                hit.addProperty("source", r.getSource().name());
                hit.addProperty("score", r.getScore());
                hit.addProperty("documentId", r.getDocumentId());

                if (r.getHeading() != null && !r.getHeading().isEmpty()) {
                    hit.addProperty("heading", r.getHeading());
                }

                String snippet = r.getSnippet();
                if (snippet != null && snippet.length() > MAX_SNIPPET_LENGTH) {
                    snippet = snippet.substring(0, MAX_SNIPPET_LENGTH) + "...";
                }
                hit.addProperty("snippet", snippet != null ? snippet : "");

                if (r.getChunkId() != null) {
                    hit.addProperty("chunkId", r.getChunkId());
                }

                hits.add(hit);
            }

            // Index stats
            int indexSize = 0;
            int semanticSize = 0;
            try {
                RagService rag = RagService.getInstance();
                indexSize = rag.getLexicalIndexSize();
                semanticSize = rag.getSemanticIndexSize();
            } catch (Exception ignored) {}

            response.addProperty("status", "success");
            response.addProperty("toolName", "search_index");
            response.addProperty("query", query);
            response.addProperty("resultCount", results.size());
            response.addProperty("indexSize", indexSize);
            response.addProperty("semanticSize", semanticSize);
            response.addProperty("searchMode", semanticSize > 0 ? "hybrid" : "bm25");
            response.add("results", hits);

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("toolName", "search_index");
            response.addProperty("message", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }

        return new McpToolResponse(response, resultVar, null);
    }
}
