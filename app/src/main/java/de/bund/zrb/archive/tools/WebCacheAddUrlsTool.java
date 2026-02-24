package de.bund.zrb.archive.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.model.WebCacheEntry;
import de.bund.zrb.archive.service.DomainFilter;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.store.IndexSourceRepository;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * Tool: web_cache_add_urls
 * Adds new URLs to the web-cache for later crawling.
 */
public class WebCacheAddUrlsTool implements McpTool {

    private final ArchiveRepository repo;

    public WebCacheAddUrlsTool(ArchiveRepository repo) {
        this.repo = repo;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<String, ToolSpec.Property>();
        properties.put("urls", new ToolSpec.Property("array", "Liste der hinzuzufügenden URLs"));
        properties.put("sourceId", new ToolSpec.Property("string", "ID der IndexSource, zu der die URLs gehören"));
        properties.put("parentUrl", new ToolSpec.Property("string", "URL der Seite, von der die Links stammen"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(
                properties, Collections.singletonList("urls"));

        return new ToolSpec(
                "web_cache_add_urls",
                "Fügt neue URLs zum Web-Cache hinzu, damit sie beim nächsten Crawl-Durchlauf besucht werden. "
                        + "URLs, die nicht zum Domain-Filter passen, werden abgelehnt.",
                inputSchema,
                null
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonArray urls = input != null && input.has("urls") ? input.getAsJsonArray("urls") : new JsonArray();
        String sourceId = input != null && input.has("sourceId") && !input.get("sourceId").isJsonNull()
                ? input.get("sourceId").getAsString() : "";
        String parentUrl = input != null && input.has("parentUrl") && !input.get("parentUrl").isJsonNull()
                ? input.get("parentUrl").getAsString() : "";

        // Determine depth from parent
        int parentDepth = 0;
        if (!parentUrl.isEmpty()) {
            WebCacheEntry parentEntry = repo.getWebCacheEntry(parentUrl);
            if (parentEntry != null) {
                parentDepth = parentEntry.getDepth();
            }
        }
        int childDepth = parentDepth + 1;

        // Build domain filter from source config (if available)
        DomainFilter filter = buildFilter(sourceId);

        int added = 0;
        JsonArray rejected = new JsonArray();

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i).getAsString().trim();
            if (url.isEmpty()) continue;

            // Skip already known URLs
            if (repo.urlExists(url)) continue;

            // Apply domain filter
            if (filter != null && !filter.accepts(url)) {
                JsonObject r = new JsonObject();
                r.addProperty("url", url);
                r.addProperty("reason", "Domain-Filter abgelehnt");
                rejected.add(r);
                continue;
            }

            // Skip non-http URLs
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                JsonObject r = new JsonObject();
                r.addProperty("url", url);
                r.addProperty("reason", "Kein HTTP(S)-URL");
                rejected.add(r);
                continue;
            }

            WebCacheEntry entry = new WebCacheEntry();
            entry.setUrl(url);
            entry.setSourceId(sourceId);
            entry.setStatus(ArchiveEntryStatus.PENDING);
            entry.setDepth(childDepth);
            entry.setParentUrl(parentUrl);
            entry.setDiscoveredAt(System.currentTimeMillis());
            repo.addWebCacheEntry(entry);
            added++;
        }

        int totalPending = sourceId.isEmpty() ? 0 : repo.countByStatus(sourceId, ArchiveEntryStatus.PENDING);

        JsonObject response = new JsonObject();
        response.addProperty("added", added);
        response.addProperty("rejected", rejected.size());
        response.add("rejectedUrls", rejected);
        response.addProperty("totalPending", totalPending);

        return new McpToolResponse(response, resultVar, null);
    }

    private DomainFilter buildFilter(String sourceId) {
        if (sourceId == null || sourceId.isEmpty()) return null;

        try {
            IndexSourceRepository sourceRepo = new IndexSourceRepository();
            List<IndexSource> sources = sourceRepo.loadAll();
            for (IndexSource src : sources) {
                if (sourceId.equals(src.getSourceId())) {
                    return new DomainFilter(src.getIncludePatterns(), src.getExcludePatterns());
                }
            }
        } catch (Exception e) {
            // Ignore – no filter
        }
        return null;
    }
}
