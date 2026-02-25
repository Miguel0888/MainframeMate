package de.bund.zrb.websearch.tools;

import com.google.gson.JsonObject;
import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * MCP Tool to retrieve archived documents from the H2 database.
 * Can look up by entryId or by URL.
 */
public class ResearchDocGetTool implements McpTool {

    private static final Logger LOG = Logger.getLogger(ResearchDocGetTool.class.getName());

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("entryId", new ToolSpec.Property("string",
                "Archive entry ID (UUID). Use this if you have a specific document ID."));
        props.put("url", new ToolSpec.Property("string",
                "URL of the archived page. Looks up the most recent archived version."));
        props.put("maxTextLength", new ToolSpec.Property("integer",
                "Max characters of extracted text to return (default: 5000, max: 20000)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, Collections.<String>emptyList());

        return new ToolSpec(
                "research_doc_get",
                "Retrieve an archived document from the local archive (H2 database). "
              + "Look up by entryId or URL. Returns metadata and extracted text content.",
                inputSchema, null);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String entryId = input.has("entryId") && !input.get("entryId").isJsonNull()
                ? input.get("entryId").getAsString() : null;
        String url = input.has("url") && !input.get("url").isJsonNull()
                ? input.get("url").getAsString() : null;
        int maxText = input.has("maxTextLength") ? input.get("maxTextLength").getAsInt() : 5000;
        if (maxText > 20000) maxText = 20000;

        if ((entryId == null || entryId.isEmpty()) && (url == null || url.isEmpty())) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Provide either 'entryId' or 'url' to look up a document.");
            return new McpToolResponse(err, resultVar, null);
        }

        try {
            ArchiveRepository repo = ArchiveRepository.getInstance();
            ArchiveEntry entry;

            if (entryId != null && !entryId.isEmpty()) {
                entry = repo.findById(entryId);
            } else {
                entry = repo.findByUrl(url);
            }

            if (entry == null) {
                JsonObject resp = new JsonObject();
                resp.addProperty("status", "not_found");
                resp.addProperty("message", "No archived document found for "
                        + (entryId != null ? "entryId=" + entryId : "url=" + url));
                return new McpToolResponse(resp, resultVar, null);
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "ok");
            resp.addProperty("entryId", entry.getEntryId());
            resp.addProperty("url", entry.getUrl());
            resp.addProperty("title", entry.getTitle());
            resp.addProperty("mimeType", entry.getMimeType());
            resp.addProperty("capturedAt", entry.getCrawlTimestamp());
            resp.addProperty("contentLength", entry.getContentLength());
            resp.addProperty("archiveStatus", entry.getStatus().name());

            Map<String, String> meta = entry.getMetadata();
            if (meta != null && !meta.isEmpty()) {
                JsonObject metaJson = new JsonObject();
                for (Map.Entry<String, String> e : meta.entrySet()) {
                    metaJson.addProperty(e.getKey(), e.getValue());
                }
                resp.add("metadata", metaJson);
            }

            // Try to read the snapshot text file
            if (entry.getSnapshotPath() != null && !entry.getSnapshotPath().isEmpty()) {
                try {
                    String home = System.getProperty("user.home");
                    File snapshotFile = new File(home, ".mainframemate" + File.separator
                            + "archive" + File.separator + entry.getSnapshotPath());
                    if (snapshotFile.exists()) {
                        String text = readFile(snapshotFile, maxText);
                        resp.addProperty("extractedText", text);
                    }
                } catch (Exception e) {
                    LOG.fine("[research_doc_get] Could not read snapshot: " + e.getMessage());
                }
            }

            return new McpToolResponse(resp, resultVar, null);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Archive lookup failed: " + e.getMessage());
            return new McpToolResponse(err, resultVar, null);
        }
    }

    private String readFile(File file, int maxLen) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1 && sb.length() < maxLen) {
                sb.append(buf, 0, read);
            }
        } finally {
            reader.close();
        }
        String text = sb.toString();
        if (text.length() > maxLen) {
            text = text.substring(0, maxLen) + "\n[... truncated at " + maxLen + " chars]";
        }
        return text;
    }
}
