package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.service.RagService;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * MCP Tool for reading a window of chunks around an anchor chunk.
 * Useful for getting context before/after a specific match.
 */
public class ReadDocumentWindowTool implements McpTool {

    private static final int MAX_WINDOW_SIZE = 5;
    private static final int DEFAULT_MAX_CHARS_PER_CHUNK = 2000;
    private static final int MAX_TOTAL_CHARS = 20000;

    private final MainframeContext context;

    public ReadDocumentWindowTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("attachmentId", new ToolSpec.Property("string", "Die Attachment-ID, die den Anker-Chunk enthält"));
        properties.put("anchorChunkId", new ToolSpec.Property("string", "Die Chunk-ID, um die das Fenster zentriert wird"));
        properties.put("before", new ToolSpec.Property("integer", "Anzahl Chunks vor dem Anker (default: 2, max: " + MAX_WINDOW_SIZE + ")"));
        properties.put("after", new ToolSpec.Property("integer", "Anzahl Chunks nach dem Anker (default: 2, max: " + MAX_WINDOW_SIZE + ")"));
        properties.put("maxCharsPerChunk", new ToolSpec.Property("integer", "Maximale Zeichen pro Chunk (default: " + DEFAULT_MAX_CHARS_PER_CHUNK + ")"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Arrays.asList("attachmentId", "anchorChunkId"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("attachmentId", "att-123");
        example.put("anchorChunkId", "chunk-abc-5");
        example.put("before", 2);
        example.put("after", 2);

        return new ToolSpec(
                "read_document_window",
                "Liest ein Fenster von Chunks um einen Anker-Chunk herum. " +
                "Nützlich, um Kontext vor und nach einem bestimmten Treffer zu erhalten. " +
                "Die Chunks werden in Dokumentreihenfolge zurückgegeben.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            // Parse required parameters
            String attachmentId = input.has("attachmentId") && !input.get("attachmentId").isJsonNull()
                    ? input.get("attachmentId").getAsString() : null;
            String anchorChunkId = input.has("anchorChunkId") && !input.get("anchorChunkId").isJsonNull()
                    ? input.get("anchorChunkId").getAsString() : null;

            if (attachmentId == null || attachmentId.trim().isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "attachmentId ist erforderlich");
                return new McpToolResponse(response, resultVar, null);
            }
            if (anchorChunkId == null || anchorChunkId.trim().isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "anchorChunkId ist erforderlich");
                return new McpToolResponse(response, resultVar, null);
            }

            // Parse optional parameters
            int before = 2;
            int after = 2;
            int maxCharsPerChunk = DEFAULT_MAX_CHARS_PER_CHUNK;

            if (input.has("before") && !input.get("before").isJsonNull()) {
                before = Math.min(input.get("before").getAsInt(), MAX_WINDOW_SIZE);
            }
            if (input.has("after") && !input.get("after").isJsonNull()) {
                after = Math.min(input.get("after").getAsInt(), MAX_WINDOW_SIZE);
            }
            if (input.has("maxCharsPerChunk") && !input.get("maxCharsPerChunk").isJsonNull()) {
                maxCharsPerChunk = input.get("maxCharsPerChunk").getAsInt();
            }

            // Get chunk window
            RagService ragService = RagService.getInstance();
            List<Chunk> window = ragService.getChunkWindow(anchorChunkId, before, after);

            if (window.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Chunk nicht gefunden: " + anchorChunkId);
                return new McpToolResponse(response, resultVar, null);
            }

            // Verify attachment ID matches
            boolean attachmentMatch = false;
            for (Chunk chunk : window) {
                if (attachmentId.equals(chunk.getDocumentId())) {
                    attachmentMatch = true;
                    break;
                }
            }

            if (!attachmentMatch) {
                response.addProperty("status", "error");
                response.addProperty("message", "Chunk gehört nicht zum angegebenen Attachment");
                return new McpToolResponse(response, resultVar, null);
            }

            // Build result
            JsonArray chunkResults = new JsonArray();
            int totalChars = 0;
            int anchorIndex = -1;

            for (int i = 0; i < window.size(); i++) {
                if (totalChars >= MAX_TOTAL_CHARS) {
                    break;
                }

                Chunk chunk = window.get(i);
                JsonObject chunkObj = new JsonObject();
                chunkObj.addProperty("chunkId", chunk.getChunkId());
                chunkObj.addProperty("position", chunk.getPosition());
                chunkObj.addProperty("isAnchor", chunk.getChunkId().equals(anchorChunkId));

                if (chunk.getChunkId().equals(anchorChunkId)) {
                    anchorIndex = i;
                }

                if (chunk.getHeading() != null) {
                    chunkObj.addProperty("heading", chunk.getHeading());
                }

                String text = chunk.getText();
                if (text.length() > maxCharsPerChunk) {
                    text = text.substring(0, maxCharsPerChunk) + "\n... [gekürzt]";
                }

                chunkObj.addProperty("markdown", text);
                totalChars += text.length();

                chunkResults.add(chunkObj);
            }

            response.addProperty("status", "success");
            response.addProperty("toolName", "read_document_window");
            response.addProperty("attachmentId", attachmentId);
            response.addProperty("anchorChunkId", anchorChunkId);
            response.addProperty("anchorIndex", anchorIndex);
            response.addProperty("chunkCount", chunkResults.size());
            response.addProperty("totalChars", totalChars);
            response.add("chunks", chunkResults);

            if (totalChars >= MAX_TOTAL_CHARS) {
                response.addProperty("warning", "Antwort wegen Größenlimit gekürzt");
            }

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("toolName", "read_document_window");
            response.addProperty("message", e.getMessage());
        }

        return new McpToolResponse(response, resultVar, null);
    }
}

