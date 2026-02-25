package de.bund.zrb.websearch.tools;

import com.google.gson.JsonObject;
import de.bund.zrb.archive.model.ArchiveResource;
import de.bund.zrb.archive.service.ArchiveService;
import de.bund.zrb.archive.service.ResourceStorageService;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * MCP Tool to retrieve raw resources from the Data Lake.
 * This is the "advanced" access path – for raw network captures,
 * API responses, and other artifacts that may not be curated documents.
 * <p>
 * Use research_doc_get for curated catalog documents instead.
 */
public class ResearchResourceGetTool implements McpTool {

    private static final Logger LOG = Logger.getLogger(ResearchResourceGetTool.class.getName());

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("resourceId", new ToolSpec.Property("string",
                "Resource ID (UUID) from the Data Lake."));
        props.put("maxTextLength", new ToolSpec.Property("integer",
                "Max characters of raw content to return (default: 5000, max: 20000)"));
        props.put("includeRaw", new ToolSpec.Property("boolean",
                "If true, include the raw body content. Default: false (metadata only)."));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, Collections.singletonList("resourceId"));

        return new ToolSpec(
                "research_resource_get",
                "Retrieve a raw resource from the Data Lake (advanced). "
              + "Returns metadata and optionally the raw body. "
              + "For curated, bot-friendly documents, use research_doc_get instead.",
                inputSchema, null);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String resourceId = input.has("resourceId") && !input.get("resourceId").isJsonNull()
                ? input.get("resourceId").getAsString() : null;
        int maxText = input.has("maxTextLength") ? input.get("maxTextLength").getAsInt() : 5000;
        if (maxText > 20000) maxText = 20000;
        boolean includeRaw = input.has("includeRaw") && input.get("includeRaw").getAsBoolean();

        if (resourceId == null || resourceId.isEmpty()) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Parameter 'resourceId' is required.");
            return new McpToolResponse(err, resultVar, null);
        }

        try {
            ArchiveRepository repo = ArchiveRepository.getInstance();
            ArchiveResource res = repo.findResourceById(resourceId);

            if (res == null) {
                JsonObject resp = new JsonObject();
                resp.addProperty("status", "not_found");
                resp.addProperty("message", "No resource found for resourceId=" + resourceId);
                return new McpToolResponse(resp, resultVar, null);
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "ok");
            resp.addProperty("resourceId", res.getResourceId());
            resp.addProperty("runId", res.getRunId());
            resp.addProperty("url", res.getUrl());
            resp.addProperty("canonicalUrl", res.getCanonicalUrl());
            resp.addProperty("mimeType", res.getMimeType());
            resp.addProperty("kind", res.getKind());
            resp.addProperty("httpStatus", res.getHttpStatus());
            resp.addProperty("sizeBytes", res.getSizeBytes());
            resp.addProperty("indexable", res.isIndexable());
            resp.addProperty("capturedAt", res.getCapturedAt());
            resp.addProperty("capturedAtFormatted", formatTimestamp(res.getCapturedAt()));
            resp.addProperty("title", res.getTitle());
            resp.addProperty("seenCount", res.getSeenCount());
            resp.addProperty("contentHash", res.getContentHash());
            resp.addProperty("source", res.getSource());

            if (res.getTopLevelUrl() != null && !res.getTopLevelUrl().isEmpty()) {
                resp.addProperty("topLevelUrl", res.getTopLevelUrl());
            }
            if (res.getParentUrl() != null && !res.getParentUrl().isEmpty()) {
                resp.addProperty("parentUrl", res.getParentUrl());
            }

            // Include raw content only if requested
            if (includeRaw && res.getStoragePath() != null && !res.getStoragePath().isEmpty()) {
                ResourceStorageService storageService = ArchiveService.getInstance().getStorageService();
                String content = storageService.readContent(res.getStoragePath(), maxText);
                if (content != null) {
                    resp.addProperty("content", content);
                }
            }

            return new McpToolResponse(resp, resultVar, null);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Resource lookup failed: " + e.getMessage());
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
