package de.bund.zrb.confluence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.store.CacheRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prefetches Confluence pages into an in-memory cache (ConcurrentHashMap, O(1) lookup)
 * and optionally persists them to the DB cache as volatile entries.
 *
 * <ul>
 *   <li><b>Prefetch pool</b> — bounded thread pool (configurable concurrency, default 4).
 *       Loads pages ahead of the cursor position, up to maxItems.</li>
 *   <li><b>Priority thread</b> — single dedicated thread for user-initiated loads
 *       (preview on click). Never blocked by the prefetch queue.</li>
 * </ul>
 *
 * Each cached entry is a {@link CachedPage} containing the page id, title, and HTML body.
 */
public class ConfluencePrefetchService {

    private static final Logger LOG = Logger.getLogger(ConfluencePrefetchService.class.getName());

    private final ConfluenceRestClient client;
    private final CacheRepository cacheRepository;
    private final long maxVolatileBytes;
    private final int maxItems;

    /** Bounded pool for background prefetching. */
    private final ExecutorService prefetchPool;
    /** Dedicated single thread for user-initiated (priority) loads. */
    private final ExecutorService priorityThread;

    /** In-memory cache: pageId → CachedPage.  O(1) lookup. */
    private final ConcurrentHashMap<String, CachedPage> memoryCache =
            new ConcurrentHashMap<String, CachedPage>();

    /** Track running prefetch futures so we can cancel them on new search. */
    private final CopyOnWriteArrayList<Future<?>> runningPrefetches =
            new CopyOnWriteArrayList<Future<?>>();

    /**
     * @param client          Confluence REST client (thread-safe for GET requests)
     * @param cacheRepository persistent cache (H2), may be {@code null}
     * @param maxVolatileMb   max DB size for volatile entries
     * @param maxItems        max number of pages to prefetch (default 100)
     * @param concurrency     number of parallel prefetch HTTP requests (default 4)
     */
    public ConfluencePrefetchService(ConfluenceRestClient client,
                                     CacheRepository cacheRepository,
                                     int maxVolatileMb,
                                     int maxItems,
                                     int concurrency) {
        this.client = client;
        this.cacheRepository = cacheRepository;
        this.maxVolatileBytes = (long) maxVolatileMb * 1024L * 1024L;
        this.maxItems = Math.max(1, maxItems);
        int poolSize = Math.max(1, Math.min(concurrency, 8));
        this.prefetchPool = Executors.newFixedThreadPool(poolSize);
        this.priorityThread = Executors.newSingleThreadExecutor();
    }

    // ════════════════════════════════════════════════════════════
    //  Prefetch (background, from search results)
    // ════════════════════════════════════════════════════════════

    /**
     * Prefetch the given page IDs in the background, starting from {@code fromIndex}.
     * Cancels still-running prefetches from a previous call.
     *
     * @param pageIds   list of Confluence page IDs from search results
     * @param fromIndex start index in the list (typically the cursor position)
     */
    public void prefetchPages(List<String> pageIds, int fromIndex) {
        cancelRunningPrefetches();

        int start = Math.max(0, fromIndex);
        int end = Math.min(pageIds.size(), start + maxItems);
        int submitted = 0;

        for (int i = start; i < end; i++) {
            final String pageId = pageIds.get(i);
            if (memoryCache.containsKey(pageId)) {
                continue; // already cached
            }

            Future<?> f = prefetchPool.submit(new Runnable() {
                @Override
                public void run() {
                    if (Thread.currentThread().isInterrupted()) return;
                    try {
                        loadAndCache(pageId);
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            LOG.log(Level.FINE, "[ConfluencePrefetch] " + pageId, e);
                        }
                    }
                }
            });
            runningPrefetches.add(f);
            submitted++;
        }

        if (submitted > 0) {
            LOG.fine("[ConfluencePrefetch] Submitted " + submitted + " tasks from index " + start);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Priority load (dedicated thread, never blocked by prefetch)
    // ════════════════════════════════════════════════════════════

    /**
     * Load a single page with high priority on a dedicated thread,
     * bypassing the prefetch queue. For user-initiated preview/open.
     *
     * @return cached page or {@code null} on error
     */
    public CachedPage loadPriority(String pageId) {
        CachedPage cached = getCached(pageId);
        if (cached != null) return cached;

        try {
            Future<CachedPage> f = priorityThread.submit(new Callable<CachedPage>() {
                @Override
                public CachedPage call() throws Exception {
                    return loadAndCache(pageId);
                }
            });
            return f.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[ConfluencePrefetch] Priority load failed: " + pageId, e);
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Cache access
    // ════════════════════════════════════════════════════════════

    /** @return cached page or {@code null} if not in cache. */
    public CachedPage getCached(String pageId) {
        return memoryCache.get(pageId);
    }

    /** Store a page that was loaded on-demand into the in-memory cache. */
    public void putInCache(String pageId, CachedPage page) {
        if (page != null) {
            memoryCache.put(pageId, page);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════════════

    /** Shut down background threads. Called when the connection tab closes. */
    public void shutdown() {
        cancelRunningPrefetches();
        prefetchPool.shutdownNow();
        priorityThread.shutdownNow();
        memoryCache.clear();
    }

    // ════════════════════════════════════════════════════════════
    //  Internal
    // ════════════════════════════════════════════════════════════

    /**
     * Load a page via REST, store in memory cache + DB. Thread-safe, idempotent.
     */
    private CachedPage loadAndCache(String pageId) throws Exception {
        // Double-check (another thread may have cached it meanwhile)
        CachedPage existing = memoryCache.get(pageId);
        if (existing != null) return existing;

        String json = client.getContentByIdJson(pageId);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        String title = root.has("title") ? root.get("title").getAsString() : "Page " + pageId;
        String html = "";
        if (root.has("body") && root.getAsJsonObject("body").has("view")) {
            html = root.getAsJsonObject("body").getAsJsonObject("view")
                    .get("value").getAsString();
        }

        CachedPage page = new CachedPage(pageId, title, html);
        memoryCache.put(pageId, page);

        // Persist to DB (best-effort)
        persistToDb(pageId, title, html);

        return page;
    }

    private void persistToDb(String pageId, String title, String html) {
        if (cacheRepository == null || html == null || html.isEmpty()) return;
        try {
            String cacheUrl = "confluence://" + pageId;
            if (cacheRepository.existsByUrl(cacheUrl)) return;

            long sizeBytes = html.getBytes("UTF-8").length;

            long currentSize = cacheRepository.getVolatileCacheSize();
            if (currentSize + sizeBytes > maxVolatileBytes) {
                cacheRepository.evictOldestVolatile(maxVolatileBytes - sizeBytes);
            }

            ArchiveEntry entry = new ArchiveEntry();
            entry.setEntryId(UUID.randomUUID().toString());
            entry.setUrl(cacheUrl);
            entry.setTitle(title);
            entry.setMimeType("text/html");
            entry.setContentLength(html.length());
            entry.setFileSizeBytes(sizeBytes);
            entry.setCrawlTimestamp(System.currentTimeMillis());
            entry.setStatus(ArchiveEntryStatus.CRAWLED);
            entry.setSourceId("confluence-prefetch");
            cacheRepository.saveVolatile(entry);
        } catch (Exception e) {
            LOG.log(Level.FINE, "[ConfluencePrefetch] DB persist failed: " + pageId, e);
        }
    }

    private void cancelRunningPrefetches() {
        for (Future<?> f : runningPrefetches) {
            f.cancel(true);
        }
        runningPrefetches.clear();
    }

    // ════════════════════════════════════════════════════════════
    //  Cached page DTO
    // ════════════════════════════════════════════════════════════

    /** Immutable container for a prefetched Confluence page. */
    public static final class CachedPage {
        private final String pageId;
        private final String title;
        private final String html;

        public CachedPage(String pageId, String title, String html) {
            this.pageId = pageId;
            this.title = title;
            this.html = html;
        }

        public String pageId() { return pageId; }
        public String title() { return title; }
        public String html() { return html; }
    }
}

