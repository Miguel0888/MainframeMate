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
 * MCP Tool for reading specific chunks by ID.
 * Returns full Markdown content of the requested chunks.
 */
public class ReadChunksTool implements McpTool {

    private static final int MAX_CHUNKS_PER_CALL = 10;
    private static final int DEFAULT_MAX_CHARS_PER_CHUNK = 2000;
    private static final int MAX_TOTAL_CHARS = 20000;

    private final MainframeContext context;

    public ReadChunksTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("chunkIds", new ToolSpec.Property("array", "Liste der Chunk-IDs zum Lesen (max: " + MAX_CHUNKS_PER_CALL + ")"));
        properties.put("maxCharsPerChunk", new ToolSpec.Property("integer", "Maximale Zeichen pro Chunk (default: " + DEFAULT_MAX_CHARS_PER_CHUNK + ")"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.singletonList("chunkIds"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("chunkIds", Arrays.asList("chunk-abc-0", "chunk-abc-1"));

        return new ToolSpec(
                "read_chunks",
                "Liest den vollständigen Inhalt bestimmter Chunks anhand ihrer IDs. " +
                "Nutze dies nach search_attachments, um den kompletten Text relevanter Chunks zu erhalten.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            // Parse chunk IDs
            if (!input.has("chunkIds") || input.get("chunkIds").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "chunkIds Array ist erforderlich");
                return new McpToolResponse(response, resultVar, null);
            }

            List<String> chunkIds = new ArrayList<>();
            for (com.google.gson.JsonElement elem : input.getAsJsonArray("chunkIds")) {
                chunkIds.add(elem.getAsString());
                if (chunkIds.size() >= MAX_CHUNKS_PER_CALL) {
                    break;
                }
            }

            if (chunkIds.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Mindestens eine chunkId ist erforderlich");
                return new McpToolResponse(response, resultVar, null);
            }

            // Parse optional max chars
            int maxCharsPerChunk = DEFAULT_MAX_CHARS_PER_CHUNK;
            if (input.has("maxCharsPerChunk") && !input.get("maxCharsPerChunk").isJsonNull()) {
                maxCharsPerChunk = input.get("maxCharsPerChunk").getAsInt();
            }

            // Read chunks
            RagService ragService = RagService.getInstance();
            List<Chunk> chunks = ragService.getChunks(chunkIds);

            // Build result
            JsonArray chunkResults = new JsonArray();
            int totalChars = 0;

            for (Chunk chunk : chunks) {
                if (totalChars >= MAX_TOTAL_CHARS) {
                    break;
                }

                JsonObject chunkObj = new JsonObject();
                chunkObj.addProperty("chunkId", chunk.getChunkId());
                chunkObj.addProperty("attachmentId", chunk.getDocumentId());
                chunkObj.addProperty("position", chunk.getPosition());

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
            response.addProperty("toolName", "read_chunks");
            response.addProperty("requestedCount", chunkIds.size());
            response.addProperty("returnedCount", chunkResults.size());
            response.addProperty("totalChars", totalChars);
            response.add("chunks", chunkResults);

            if (totalChars >= MAX_TOTAL_CHARS) {
                response.addProperty("warning", "Antwort wegen Größenlimit gekürzt");
            }

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("toolName", "read_chunks");
            response.addProperty("message", e.getMessage());
        }

        return new McpToolResponse(response, resultVar, null);
    }
}
