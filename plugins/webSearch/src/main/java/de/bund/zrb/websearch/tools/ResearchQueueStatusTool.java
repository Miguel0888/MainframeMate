package de.bund.zrb.websearch.tools;

import com.google.gson.JsonArray;
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
 * Crawl-Queue-Status: pending/visited/skipped/errors, Rate-Limits, Budgets.
 */
public class ResearchQueueStatusTool implements McpTool {

    private static final Logger LOG = Logger.getLogger(ResearchQueueStatusTool.class.getName());

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("sourceId", new ToolSpec.Property("string",
                "Source identifier to filter by (optional, default: all)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, Collections.<String>emptyList());

        return new ToolSpec(
                "research_queue_status",
                "Check the status of the crawl queue. "
              + "Returns counts of pending/crawled/indexed/failed URLs and recent entries.",
                inputSchema, null);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String sourceId = input.has("sourceId") ? input.get("sourceId").getAsString() : null;

        try {
            ArchiveRepository repo = ArchiveRepository.getInstance();

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "ok");

            if (sourceId != null && !sourceId.isEmpty()) {
                resp.addProperty("sourceId", sourceId);
                resp.addProperty("total", repo.countBySourceId(sourceId));
                resp.addProperty("pending", repo.countByStatus(sourceId, ArchiveEntryStatus.PENDING));
                resp.addProperty("crawled", repo.countByStatus(sourceId, ArchiveEntryStatus.CRAWLED));
                resp.addProperty("indexed", repo.countByStatus(sourceId, ArchiveEntryStatus.INDEXED));
                resp.addProperty("failed", repo.countByStatus(sourceId, ArchiveEntryStatus.FAILED));

                // Recent pending URLs
                List<WebCacheEntry> pending = repo.getPendingUrls(sourceId, 10);
                JsonArray pendingArr = new JsonArray();
                for (WebCacheEntry e : pending) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("url", e.getUrl());
                    entry.addProperty("depth", e.getDepth());
                    pendingArr.add(entry);
                }
                resp.add("nextPending", pendingArr);
            } else {
                // Global status
                List<WebCacheEntry> all = repo.getWebCacheEntries(null);
                int pending = 0, crawled = 0, indexed = 0, failed = 0;
                for (WebCacheEntry e : all) {
                    switch (e.getStatus()) {
                        case PENDING: pending++; break;
                        case CRAWLED: crawled++; break;
                        case INDEXED: indexed++; break;
                        case FAILED:  failed++;  break;
                    }
                }
                resp.addProperty("total", all.size());
                resp.addProperty("pending", pending);
                resp.addProperty("crawled", crawled);
                resp.addProperty("indexed", indexed);
                resp.addProperty("failed", failed);
            }

            return new McpToolResponse(resp, resultVar, null);
        } catch (Exception e) {
            LOG.warning("[research_queue_status] Failed: " + e.getMessage());
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Queue status failed: " + e.getMessage());
            return new McpToolResponse(err, resultVar, null);
        }
    }
}
