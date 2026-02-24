package de.bund.zrb.archive.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.archive.model.WebCacheEntry;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * Tool: web_cache_status
 * Checks the archiving status of one or more URLs in the web-cache.
 */
public class WebCacheStatusTool implements McpTool {

    private final ArchiveRepository repo;

    public WebCacheStatusTool(ArchiveRepository repo) {
        this.repo = repo;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<String, ToolSpec.Property>();
        properties.put("urls", new ToolSpec.Property("array", "Liste der zu prüfenden URLs"));
        properties.put("sourceId", new ToolSpec.Property("string", "Optional: ID der IndexSource"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(
                properties, Collections.singletonList("urls"));

        return new ToolSpec(
                "web_cache_status",
                "Prüft den Archivierungsstatus einer oder mehrerer URLs im Web-Cache. "
                        + "Gibt an, ob eine URL bereits besucht, archiviert und indexiert wurde oder noch aussteht.",
                inputSchema,
                null
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonArray urls = input != null && input.has("urls") ? input.getAsJsonArray("urls") : new JsonArray();

        JsonArray results = new JsonArray();
        int indexed = 0, pending = 0, crawled = 0, failed = 0, unknown = 0;

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i).getAsString();
            WebCacheEntry entry = repo.getWebCacheEntry(url);

            JsonObject item = new JsonObject();
            item.addProperty("url", url);

            if (entry != null) {
                item.addProperty("status", entry.getStatus().name());
                item.addProperty("depth", entry.getDepth());
                item.addProperty("archiveEntryId", entry.getArchiveEntryId());
                switch (entry.getStatus()) {
                    case INDEXED: indexed++; break;
                    case PENDING: pending++; break;
                    case CRAWLED: crawled++; break;
                    case FAILED: failed++; break;
                }
            } else {
                item.addProperty("status", "UNKNOWN");
                unknown++;
            }
            results.add(item);
        }

        JsonObject response = new JsonObject();
        response.add("results", results);
        response.addProperty("summary",
                urls.size() + " URLs geprüft: " + indexed + " indexiert, "
                        + pending + " ausstehend, " + crawled + " gecrawlt, "
                        + failed + " fehlgeschlagen, " + unknown + " unbekannt");

        return new McpToolResponse(response, resultVar, null);
    }
}
