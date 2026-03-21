package de.bund.zrb.search.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.confluence.ConfluenceConnectionConfig;
import de.bund.zrb.confluence.ConfluencePrefetchService;
import de.bund.zrb.confluence.ConfluenceRestClient;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.search.BackendSearchProvider;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.util.CredentialStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton live backend search provider for Confluence Data Center.
 * <p>
 * Reads configured Confluence entries from {@code settings.passwordEntries}
 * (category "Confluence") on each search call — no constructor wiring needed.
 * Credentials and certificates are resolved on-demand.
 * <p>
 * Uses CQL ({@code text~"query"}) via the Confluence REST API to search
 * across all accessible spaces per configured instance.
 * <p>
 * Implements prefetch: after search, all result pages are preloaded in the
 * background using a {@link ConfluencePrefetchService} per instance. When
 * preview is requested via {@link #loadContentHtml(String)}, it either returns
 * the cached content instantly or uses priority-load.
 */
public final class ConfluenceSearchProvider implements BackendSearchProvider {

    private static final Logger LOG = Logger.getLogger(ConfluenceSearchProvider.class.getName());
    private static final ConfluenceSearchProvider INSTANCE = new ConfluenceSearchProvider();

    /** Per-instance prefetch services: entryId → ConfluencePrefetchService. */
    private final ConcurrentHashMap<String, ConfluencePrefetchService> prefetchServices =
            new ConcurrentHashMap<String, ConfluencePrefetchService>();

    /** Per-instance REST clients: entryId → ConfluenceRestClient. */
    private final ConcurrentHashMap<String, ConfluenceRestClient> clientCache =
            new ConcurrentHashMap<String, ConfluenceRestClient>();

    /** Tracks which pageId belongs to which entryId (for loadContentHtml routing). */
    private final ConcurrentHashMap<String, String> pageIdToEntryId =
            new ConcurrentHashMap<String, String>();

    private ConfluenceSearchProvider() {}

    public static ConfluenceSearchProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public SearchResult.SourceType getSourceType() {
        return SearchResult.SourceType.CONFLUENCE;
    }

    @Override
    public boolean isAvailable() {
        return !loadEntries(null).isEmpty();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults, String networkZone) throws Exception {
        List<ConfEntry> entries = loadEntries(networkZone);
        if (entries.isEmpty()) return Collections.emptyList();

        List<SearchResult> results = new ArrayList<SearchResult>();

        for (ConfEntry entry : entries) {
            if (results.size() >= maxResults) break;
            try {
                ConfluenceRestClient client = getOrCreateClient(entry);
                String cql = "type=page AND text~\"" + query.replace("\"", "\\\"") + "\"";
                int limit = Math.min(maxResults - results.size(), 50);

                String json = client.searchJson(cql, 0, limit);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray arr = root.getAsJsonArray("results");
                if (arr == null) continue;

                for (JsonElement el : arr) {
                    if (results.size() >= maxResults) break;
                    JsonObject pg = el.getAsJsonObject();
                    JsonObject content = pg.has("content") ? pg.getAsJsonObject("content") : pg;

                    String id = content.has("id") ? content.get("id").getAsString() : "";
                    String title = content.has("title") ? content.get("title").getAsString() : "";
                    String spaceKey = "";
                    if (content.has("space") && content.getAsJsonObject("space").has("key")) {
                        spaceKey = content.getAsJsonObject("space").get("key").getAsString();
                    }

                    String excerpt = "";
                    if (pg.has("excerpt")) {
                        excerpt = pg.get("excerpt").getAsString()
                                .replaceAll("<[^>]+>", "")
                                .replaceAll("@@@hl@@@|@@@endhl@@@", "");
                        if (excerpt.length() > 300) excerpt = excerpt.substring(0, 300) + "…";
                    }

                    String docId = "confluence://" + id;
                    String path = spaceKey.isEmpty() ? entry.displayName
                            : spaceKey + " @ " + entry.displayName;

                    // Track pageId → entryId mapping for prefetch routing
                    pageIdToEntryId.put(id, entry.id);

                    results.add(new SearchResult(
                            SearchResult.SourceType.CONFLUENCE,
                            docId,
                            title,
                            path,
                            excerpt,
                            0.8f,
                            docId,
                            spaceKey
                    ));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[ConfluenceSearch] CQL search failed for " + entry.displayName, e);
                // Continue with other instances
            }
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    //  Prefetch / Content preloading
    // ═══════════════════════════════════════════════════════════

    @Override
    public void preloadResults(List<SearchResult> results) {
        if (results == null || results.isEmpty()) return;

        // Group results by entryId
        ConcurrentHashMap<String, List<String>> byEntry =
                new ConcurrentHashMap<String, List<String>>();
        for (SearchResult r : results) {
            if (r.getSource() != SearchResult.SourceType.CONFLUENCE) continue;
            String docId = r.getDocumentId();
            if (docId == null || !docId.startsWith("confluence://")) continue;
            String pageId = docId.substring("confluence://".length());
            String entryId = pageIdToEntryId.get(pageId);
            if (entryId == null) continue;
            if (!byEntry.containsKey(entryId)) {
                byEntry.put(entryId, new ArrayList<String>());
            }
            byEntry.get(entryId).add(pageId);
        }

        // Trigger prefetch for each instance
        for (String entryId : byEntry.keySet()) {
            ConfluencePrefetchService pfs = getOrCreatePrefetchService(entryId);
            if (pfs != null) {
                List<String> pageIds = byEntry.get(entryId);
                pfs.prefetchPages(pageIds, 0);
            }
        }
    }

    @Override
    public String loadContentHtml(String docId) {
        if (docId == null || !docId.startsWith("confluence://")) return null;
        String pageId = docId.substring("confluence://".length());
        String entryId = pageIdToEntryId.get(pageId);

        if (entryId == null) {
            // Not tracked — try all prefetch services
            for (ConfluencePrefetchService pfs : prefetchServices.values()) {
                ConfluencePrefetchService.CachedPage cached = pfs.getCached(pageId);
                if (cached != null) return cached.html();
            }
            // Try creating a prefetch service from current entries and loading directly
            List<ConfEntry> entries = loadEntries(null);
            if (entries.isEmpty()) return null;
            ConfEntry firstEntry = entries.get(0);
            entryId = firstEntry.id;
            pageIdToEntryId.put(pageId, entryId);
        }

        ConfluencePrefetchService pfs = getOrCreatePrefetchService(entryId);
        if (pfs == null) return null;

        // Try cache first (O(1))
        ConfluencePrefetchService.CachedPage cached = pfs.getCached(pageId);
        if (cached != null) return cached.html();

        // Priority load — dedicated thread, not blocked by prefetch queue
        ConfluencePrefetchService.CachedPage loaded = pfs.loadPriority(pageId);
        if (loaded != null) return loaded.html();

        return null;
    }

    @Override
    public void shutdown() {
        for (ConfluencePrefetchService pfs : prefetchServices.values()) {
            try { pfs.shutdown(); } catch (Exception ignored) {}
        }
        prefetchServices.clear();
        clientCache.clear();
        pageIdToEntryId.clear();
    }

    // ── Internal helpers ──────────────────────────────────────────

    /** Get or create a cached REST client for a Confluence entry. */
    private ConfluenceRestClient getOrCreateClient(ConfEntry entry) {
        ConfluenceRestClient existing = clientCache.get(entry.id);
        if (existing != null) return existing;

        ConfluenceRestClient client = buildClient(entry);
        clientCache.put(entry.id, client);
        return client;
    }

    /** Get or create a prefetch service for a Confluence entry. */
    private ConfluencePrefetchService getOrCreatePrefetchService(String entryId) {
        ConfluencePrefetchService existing = prefetchServices.get(entryId);
        if (existing != null) return existing;

        // Find the entry config
        List<ConfEntry> entries = loadEntries(null);
        ConfEntry entry = null;
        for (ConfEntry e : entries) {
            if (e.id.equals(entryId)) { entry = e; break; }
        }
        if (entry == null) return null;

        ConfluenceRestClient client = getOrCreateClient(entry);
        CacheRepository cacheRepo = null;
        try {
            cacheRepo = CacheRepository.getInstance();
        } catch (Exception e) {
            LOG.log(Level.FINE, "[ConfluenceSearch] CacheRepository not available", e);
        }

        ConfluencePrefetchService pfs = new ConfluencePrefetchService(client, cacheRepo, 50, 200, 4);
        ConfluencePrefetchService prev = prefetchServices.putIfAbsent(entryId, pfs);
        return prev != null ? prev : pfs;
    }

    /** Build a fresh ConfluenceRestClient for one entry. */
    private static ConfluenceRestClient buildClient(ConfEntry entry) {
        ConfluenceConnectionConfig config = new ConfluenceConnectionConfig(
                entry.url, entry.user, entry.password, entry.certAlias,
                15000, 30000);
        return new ConfluenceRestClient(config);
    }

    /** Load Confluence entries from settings, optionally filtered by networkZone. */
    private static List<ConfEntry> loadEntries(String networkZone) {
        Settings settings = SettingsHelper.load();
        List<ConfEntry> result = new ArrayList<ConfEntry>();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if (!"Confluence".equalsIgnoreCase(meta.category)) continue;
            if (meta.url == null || meta.url.isEmpty()) continue;
            if (networkZone != null && !networkZone.equals(meta.networkZone)) continue;

            String user = "";
            String pass = "";
            if (meta.requiresLogin) {
                try {
                    String[] cred = CredentialStore.resolveIncludingEmpty("pwd:" + meta.id);
                    if (cred != null) {
                        user = cred[0];
                        pass = cred[1];
                    }
                } catch (Exception ignore) {}
            }

            String name = (meta.displayName != null && !meta.displayName.isEmpty())
                    ? meta.displayName : meta.id;
            result.add(new ConfEntry(meta.id, name, meta.url,
                    meta.certAlias != null ? meta.certAlias : "",
                    user, pass));
        }
        return result;
    }

    /** Lightweight holder for a Confluence entry from settings. */
    private static final class ConfEntry {
        final String id;
        final String displayName;
        final String url;
        final String certAlias;
        final String user;
        final String password;

        ConfEntry(String id, String displayName, String url,
                  String certAlias, String user, String password) {
            this.id = id;
            this.displayName = displayName;
            this.url = url;
            this.certAlias = certAlias;
            this.user = user;
            this.password = password;
        }
    }
}
