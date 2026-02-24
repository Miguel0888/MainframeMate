package de.bund.zrb.archive.tools;

import com.google.gson.JsonObject;
import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.service.WebSnapshotPipeline;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * Tool: web_archive_snapshot
 * Manually creates a snapshot of a web page and stores it in the archive.
 */
public class WebArchiveSnapshotTool implements McpTool {

    private final WebSnapshotPipeline pipeline;

    public WebArchiveSnapshotTool(WebSnapshotPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<String, ToolSpec.Property>();
        properties.put("url", new ToolSpec.Property("string", "URL der zu archivierenden Seite"));
        properties.put("content", new ToolSpec.Property("string", "Optional: Bereits extrahierter Text"));
        properties.put("title", new ToolSpec.Property("string", "Optional: Seitentitel"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(
                properties, Collections.singletonList("url"));

        return new ToolSpec(
                "web_archive_snapshot",
                "Erstellt einen Snapshot der aktuell im Browser geladenen Seite und speichert ihn im Archiv.",
                inputSchema,
                null
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String url = input.has("url") && !input.get("url").isJsonNull() ? input.get("url").getAsString() : "";
        String content = input.has("content") && !input.get("content").isJsonNull() ? input.get("content").getAsString() : "";
        String title = input.has("title") && !input.get("title").isJsonNull() ? input.get("title").getAsString() : url;

        JsonObject response = new JsonObject();

        if (url.isEmpty()) {
            response.addProperty("status", "error");
            response.addProperty("message", "URL ist erforderlich");
            return new McpToolResponse(response, resultVar, null);
        }

        ArchiveEntry entry = pipeline.processSnapshot(url, content, title);

        if (entry != null) {
            response.addProperty("status", "ok");
            response.addProperty("entryId", entry.getEntryId());
            response.addProperty("title", entry.getTitle());
            response.addProperty("snapshotPath", entry.getSnapshotPath());
            response.addProperty("contentLength", entry.getContentLength());
            response.addProperty("archiveStatus", entry.getStatus().name());
        } else {
            response.addProperty("status", "error");
            response.addProperty("message", "Snapshot konnte nicht erstellt werden");
        }

        return new McpToolResponse(response, resultVar, null);
    }
}
