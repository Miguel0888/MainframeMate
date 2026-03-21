package de.bund.zrb.websearch.tools;

import com.google.gson.JsonObject;
import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveResource;
import de.bund.zrb.archive.service.ArchiveService;
import de.bund.zrb.archive.service.ResourceStorageService;
import de.bund.zrb.archive.service.UrlNormalizer;
import de.bund.zrb.archive.store.CacheRepository;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * MCP Tool to retrieve cached/cataloged entries or resources from the archive.
 * Entry-first: looks up entries by entryId or URL first, falls back to Resources.
 */
public class ResearchDocGetTool implements McpTool {

    private static final Logger LOG = Logger.getLogger(ResearchDocGetTool.class.getName());

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("docId", new ToolSpec.Property("string",
                "Archive entry ID (UUID). Use this to get a cached entry with title, excerpt, and extracted text."));
        props.put("url", new ToolSpec.Property("string",
                "URL of the archived page. Looks up the most recent entry for this URL."));
        props.put("maxTextLength", new ToolSpec.Property("integer",
                "Max characters of extracted text to return (default: 5000, max: 20000)"));
        props.put("format", new ToolSpec.Property("string",
                "Output format: 'TEXT' (default) or 'MARKDOWN'"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, Collections.<String>emptyList());

        return new ToolSpec(
                "research_doc_get",
                "Retrieve a cached entry from the archive. Entries are indexed content "
              + "with clean title, excerpt, and extracted text. Look up by docId or URL. "
              + "For raw resource access, use research_resource_get instead.",
                inputSchema, null);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String docId = input.has("docId") && !input.get("docId").isJsonNull()
                ? input.get("docId").getAsString() : null;
        // Legacy support: also accept "entryId"
        if (docId == null && input.has("entryId") && !input.get("entryId").isJsonNull()) {
            docId = input.get("entryId").getAsString();
        }
        String url = input.has("url") && !input.get("url").isJsonNull()
                ? input.get("url").getAsString() : null;
        int maxText = input.has("maxTextLength") ? input.get("maxTextLength").getAsInt() : 5000;
        if (maxText > 20000) maxText = 20000;

        if ((docId == null || docId.isEmpty()) && (url == null || url.isEmpty())) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Provide either 'docId' or 'url' to look up an entry.");
            return new McpToolResponse(err, resultVar, null);
        }

        try {
            CacheRepository repo = CacheRepository.getInstance();
            ArchiveService archiveService = ArchiveService.getInstance();
            ResourceStorageService storageService = archiveService.getStorageService();

            ArchiveEntry entry = null;

            // 1. Try entry lookup by ID
            if (docId != null && !docId.isEmpty()) {
                entry = repo.findById(docId);
            }
            if (entry == null && url != null && !url.isEmpty()) {
                String canonicalUrl = UrlNormalizer.canonicalize(url);
                entry = repo.findByUrl(canonicalUrl);
            }

            if (entry != null) {
                return buildEntryResponse(entry, storageService, maxText, resultVar);
            }

            // 2. Fallback: try resource lookup (legacy docId might be a resourceId)
            if (docId != null && !docId.isEmpty()) {
                ArchiveResource res = repo.findResourceById(docId);
                if (res != null) {
                    return buildResourceFallbackResponse(res, storageService, maxText, resultVar);
                }
            }

            // 3. Not found
            JsonObject resp = new JsonObject();
            resp.addProperty("status", "not_found");
            resp.addProperty("message", "No entry found for "
                    + (docId != null ? "docId=" + docId : "url=" + url)
                    + ". Try research_search to find entries by keyword.");
            return new McpToolResponse(resp, resultVar, null);

        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Archive lookup failed: " + e.getMessage());
            return new McpToolResponse(err, resultVar, null);
        }
    }

    private McpToolResponse buildEntryResponse(ArchiveEntry entry, ResourceStorageService storageService,
                                                   int maxText, String resultVar) {
        JsonObject resp = new JsonObject();
        resp.addProperty("status", "ok");
        resp.addProperty("docId", entry.getEntryId());
        resp.addProperty("title", entry.getTitle());
        resp.addProperty("url", entry.getUrl());
        resp.addProperty("kind", entry.getKind());
        resp.addProperty("createdAt", entry.getCrawlTimestamp());
        resp.addProperty("createdAtFormatted", formatTimestamp(entry.getCrawlTimestamp()));
        resp.addProperty("language", entry.getLanguage());
        resp.addProperty("wordCount", entry.getWordCount());
        resp.addProperty("host", entry.getHost());
        resp.addProperty("runId", entry.getRunId());
        resp.addProperty("sourceResourceIds", entry.getSourceResourceIds());

        if (entry.getExcerpt() != null && !entry.getExcerpt().isEmpty()) {
            resp.addProperty("excerpt", entry.getExcerpt());
        }

        // Read extracted text from storage
        if (entry.getTextContentPath() != null && !entry.getTextContentPath().isEmpty()) {
            String text = storageService.readContent(entry.getTextContentPath(), maxText);
            if (text != null) {
                resp.addProperty("extractedText", text);
            }
        }

        return new McpToolResponse(resp, resultVar, null);
    }

    private McpToolResponse buildResourceFallbackResponse(ArchiveResource res, ResourceStorageService storageService,
                                                           int maxText, String resultVar) {
        JsonObject resp = new JsonObject();
        resp.addProperty("status", "ok");
        resp.addProperty("note", "This is a raw resource, not a curated document. Use research_search for catalog entries.");
        resp.addProperty("resourceId", res.getResourceId());
        resp.addProperty("url", res.getUrl());
        resp.addProperty("title", res.getTitle());
        resp.addProperty("mimeType", res.getMimeType());
        resp.addProperty("kind", res.getKind());
        resp.addProperty("capturedAt", res.getCapturedAt());
        resp.addProperty("capturedAtFormatted", formatTimestamp(res.getCapturedAt()));
        resp.addProperty("indexable", res.isIndexable());
        resp.addProperty("runId", res.getRunId());

        if (res.getStoragePath() != null && !res.getStoragePath().isEmpty()) {
            String text = storageService.readContent(res.getStoragePath(), maxText);
            if (text != null) {
                resp.addProperty("extractedText", text);
            }
        }

        return new McpToolResponse(resp, resultVar, null);
    }

    private String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
        return sdf.format(new Date(epochMillis));
    }
}
