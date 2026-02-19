package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.service.RagService;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * MCP Tool for searching attachments using hybrid RAG retrieval.
 * Returns top-K matching chunks from specified attachments.
 */
public class SearchAttachmentsTool implements McpTool {

    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 50;
    private static final int MAX_SNIPPET_LENGTH = 500;

    private final MainframeContext context;

    public SearchAttachmentsTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("query", new ToolSpec.Property("string", "Die Suchanfrage zum Finden relevanter Inhalte"));
        properties.put("attachmentIds", new ToolSpec.Property("array", "Liste der Attachment-IDs, in denen gesucht werden soll (Pflicht für Sicherheit)"));
        properties.put("topK", new ToolSpec.Property("integer", "Maximale Anzahl Ergebnisse (default: 10, max: 50)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Arrays.asList("query", "attachmentIds"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("query", "Hamburg Hafen");
        example.put("attachmentIds", Arrays.asList("att-123"));
        example.put("topK", 5);

        return new ToolSpec(
                "search_attachments",
                "Durchsucht angehängte Dokumente mit Hybrid-Retrieval (lexikalisch + semantisch). " +
                "Gibt die relevantesten Text-Chunks aus den angegebenen Attachments zurück. " +
                "Nutze dies, um spezifische Informationen in Dokumenten zu finden.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            // Parse required parameters
            String query = input.has("query") && !input.get("query").isJsonNull()
                    ? input.get("query").getAsString() : null;
            if (query == null || query.trim().isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "query ist erforderlich");
                return new McpToolResponse(response, resultVar, null);
            }

            if (!input.has("attachmentIds") || input.get("attachmentIds").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "attachmentIds Array ist erforderlich");
                return new McpToolResponse(response, resultVar, null);
            }

            Set<String> attachmentIds = new HashSet<>();
            for (com.google.gson.JsonElement elem : input.getAsJsonArray("attachmentIds")) {
                attachmentIds.add(elem.getAsString());
            }

            if (attachmentIds.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Mindestens eine attachmentId ist erforderlich");
                return new McpToolResponse(response, resultVar, null);
            }

            // Parse optional parameters
            int topK = DEFAULT_TOP_K;
            if (input.has("topK") && !input.get("topK").isJsonNull()) {
                topK = Math.min(input.get("topK").getAsInt(), MAX_TOP_K);
            }

            // Execute search
            RagService ragService = RagService.getInstance();
            List<ScoredChunk> results = ragService.retrieve(query, topK, attachmentIds);

            // Build result
            JsonArray chunks = new JsonArray();
            for (ScoredChunk sc : results) {
                JsonObject chunkObj = new JsonObject();
                chunkObj.addProperty("chunkId", sc.getChunkId());
                chunkObj.addProperty("attachmentId", sc.getChunk().getDocumentId());
                chunkObj.addProperty("score", sc.getScore());
                chunkObj.addProperty("source", sc.getSource().name());

                String text = sc.getChunk().getText();
                String snippet = text.length() > MAX_SNIPPET_LENGTH
                        ? text.substring(0, MAX_SNIPPET_LENGTH) + "..."
                        : text;
                chunkObj.addProperty("snippet", snippet);

                if (sc.getChunk().getHeading() != null) {
                    chunkObj.addProperty("heading", sc.getChunk().getHeading());
                }

                chunks.add(chunkObj);
            }

            response.addProperty("status", "success");
            response.addProperty("toolName", "search_attachments");
            response.addProperty("query", query);
            response.addProperty("resultCount", results.size());
            response.add("chunks", chunks);

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("toolName", "search_attachments");
            response.addProperty("message", e.getMessage());
        }

        return new McpToolResponse(response, resultVar, null);
    }
}
