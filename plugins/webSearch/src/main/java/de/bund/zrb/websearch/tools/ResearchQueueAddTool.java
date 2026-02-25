package de.bund.zrb.websearch.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.model.WebCacheEntry;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;
import java.util.logging.Logger;

/**
 * Auto-Crawl-Steuerung: Seed-URLs zur Crawl-Queue hinzufügen.
 */
public class ResearchQueueAddTool implements McpTool {

    private static final Logger LOG = Logger.getLogger(ResearchQueueAddTool.class.getName());

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("urls", new ToolSpec.Property("array",
                "URLs to add to the crawl queue"));
        props.put("sourceId", new ToolSpec.Property("string",
                "Source identifier for grouping (e.g. session ID)"));
        props.put("depth", new ToolSpec.Property("integer",
                "Link depth for these URLs (default: 0 = seed)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, Collections.singletonList("urls"));

        return new ToolSpec(
                "research_queue_add",
                "Add URLs to the crawl queue for automatic archiving. "
              + "URLs will be visited and archived in the background. "
              + "Use research_queue_status to check progress.",
                inputSchema, null);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject resp = new JsonObject();

        if (!input.has("urls") || !input.get("urls").isJsonArray()) {
            resp.addProperty("status", "error");
            resp.addProperty("message", "Parameter 'urls' (array) is required.");
            return new McpToolResponse(resp, resultVar, null);
        }

        JsonArray urlsArr = input.getAsJsonArray("urls");
        String sourceId = input.has("sourceId") ? input.get("sourceId").getAsString() : "research";
        int depth = input.has("depth") ? input.get("depth").getAsInt() : 0;

        try {
            ArchiveRepository repo = ArchiveRepository.getInstance();
            int added = 0;
            int skipped = 0;

            for (JsonElement el : urlsArr) {
                String url = el.getAsString().trim();
                if (url.isEmpty()) continue;

                if (repo.urlExists(url)) {
                    skipped++;
                    continue;
                }

                WebCacheEntry entry = new WebCacheEntry();
                entry.setUrl(url);
                entry.setSourceId(sourceId);
                entry.setDepth(depth);
                entry.setStatus(ArchiveEntryStatus.PENDING);
                entry.setDiscoveredAt(System.currentTimeMillis());
                repo.addWebCacheEntry(entry);
                added++;
            }

            resp.addProperty("status", "ok");
            resp.addProperty("added", added);
            resp.addProperty("skipped", skipped);
            resp.addProperty("message", added + " URLs added to queue, " + skipped + " already existed.");

            return new McpToolResponse(resp, resultVar, null);
        } catch (Exception e) {
            LOG.warning("[research_queue_add] Failed: " + e.getMessage());
            resp.addProperty("status", "error");
            resp.addProperty("message", "Queue add failed: " + e.getMessage());
            return new McpToolResponse(resp, resultVar, null);
        }
    }
}
