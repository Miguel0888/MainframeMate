package de.bund.zrb.wiki.service;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.wiki.domain.WikiCredentials;
import de.bund.zrb.wiki.domain.WikiPageView;
import de.bund.zrb.wiki.domain.WikiSiteId;
import de.bund.zrb.wiki.port.WikiContentService;
import de.bund.zrb.wiki.ui.WikiPrefetchCallback;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prefetches wiki pages into an in-memory cache (ConcurrentHashMap, O(1) lookup)
 * and persists them to the DB cache as volatile entries.
 *
 * <ul>
 *   <li><b>Prefetch pool</b> — bounded thread pool (configurable concurrency, default 4).
 *       Loads pages ahead of the cursor position, up to maxItems.</li>
 *   <li><b>Priority thread</b> — single dedicated thread for user-initiated loads
 *       (preview on click). Never blocked by the prefetch queue.</li>
 * </ul>
 */
public class WikiPrefetchService implements WikiPrefetchCallback {

    private static final Logger LOG = Logger.getLogger(WikiPrefetchService.class.getName());

    private final WikiContentService wikiService;
    private final CacheRepository cacheRepository;
    private final long maxVolatileBytes;
    private final int maxItems;

    /** Bounded pool for background prefetching. */
    private final ExecutorService prefetchPool;
    /** Dedicated single thread for user-initiated (priority) loads. */
    private final ExecutorService priorityThread;

    /** In-memory cache: "siteId/pageTitle" → WikiPageView.  O(1) lookup. */
    private final ConcurrentHashMap<String, WikiPageView> memoryCache =
            new ConcurrentHashMap<String, WikiPageView>();

    /** Track running prefetch futures so we can cancel them on new search. */
    private final CopyOnWriteArrayList<Future<?>> runningPrefetches =
            new CopyOnWriteArrayList<Future<?>>();

    /** Optional resolver for wiki credentials (used by prefetch to authenticate if needed). */
    private java.util.function.Function<WikiSiteId, WikiCredentials> credentialsResolver;

    /** Optional callback for auto-indexing loaded pages into Lucene. */
    private java.util.function.BiConsumer<WikiSiteId, WikiPageView> autoIndexCallback;

    /** Dedicated single-thread executor for auto-indexing (avoids Lucene contention with prefetch). */
    private final ExecutorService autoIndexExecutor = Executors.newSingleThreadExecutor();

    /**
     * @param wikiService     wiki content service for HTTP requests
     * @param cacheRepository persistent cache (H2)
     * @param maxVolatileMb   max DB size for volatile entries
     * @param maxItems        max number of pages to prefetch (default 100)
     * @param concurrency     number of parallel prefetch HTTP requests (default 4)
     */
    public WikiPrefetchService(WikiContentService wikiService,
                               CacheRepository cacheRepository,
                               int maxVolatileMb,
                               int maxItems,
                               int concurrency) {
        this.wikiService = wikiService;
        this.cacheRepository = cacheRepository;
        this.maxVolatileBytes = (long) maxVolatileMb * 1024L * 1024L;
        this.maxItems = Math.max(1, maxItems);
        int poolSize = Math.max(1, Math.min(concurrency, 8));
        this.prefetchPool = Executors.newFixedThreadPool(poolSize);
        this.priorityThread = Executors.newSingleThreadExecutor();
    }

    /** Set a resolver that provides credentials for a given wiki site (used during prefetch). */
    public void setCredentialsResolver(java.util.function.Function<WikiSiteId, WikiCredentials> resolver) {
        this.credentialsResolver = resolver;
    }

    /** Set a callback that indexes each loaded page into Lucene (for auto-indexing). */
    public void setAutoIndexCallback(java.util.function.BiConsumer<WikiSiteId, WikiPageView> callback) {
        this.autoIndexCallback = callback;
    }

    // ════════════════════════════════════════════════════════════
    //  Prefetch (background, from cursor position)
    // ════════════════════════════════════════════════════════════

    @Override
    public void prefetchSearchResults(WikiSiteId siteId, List<String> pageTitles, int fromIndex) {
        // Cancel still-running prefetches from the previous call
        cancelRunningPrefetches();

        int start = Math.max(0, fromIndex);
        int end = Math.min(pageTitles.size(), start + maxItems);
        int submitted = 0;

        for (int i = start; i < end; i++) {
            final String title = pageTitles.get(i);
            final String cacheKey = cacheKey(siteId, title);

            if (memoryCache.containsKey(cacheKey)) {
                continue; // already cached, skip — doesn't count
            }

            Future<?> f = prefetchPool.submit(new Runnable() {
                @Override
                public void run() {
                    if (Thread.currentThread().isInterrupted()) return;
                    try {
                        loadAndCache(siteId, title);
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            LOG.log(Level.FINE, "[WikiPrefetch] " + title, e);
                        }
                    }
                }
            });
            runningPrefetches.add(f);
            submitted++;
        }

        if (submitted > 0) {
            LOG.fine("[WikiPrefetch] Submitted " + submitted + " tasks from index " + start);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Priority load (dedicated thread, never blocked by prefetch)
    // ════════════════════════════════════════════════════════════

    @Override
    public WikiPageView loadPriority(WikiSiteId siteId, String pageTitle) {
        // Check cache first (O(1))
        WikiPageView cached = getCached(siteId, pageTitle);
        if (cached != null) return cached;

        try {
            Future<WikiPageView> f = priorityThread.submit(new Callable<WikiPageView>() {
                @Override
                public WikiPageView call() throws Exception {
                    return loadAndCache(siteId, pageTitle);
                }
            });
            return f.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[WikiPrefetch] Priority load failed: " + pageTitle, e);
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Cache access
    // ════════════════════════════════════════════════════════════

    @Override
    public WikiPageView getCached(WikiSiteId siteId, String pageTitle) {
        return memoryCache.get(cacheKey(siteId, pageTitle));
    }

    @Override
    public void putInCache(WikiSiteId siteId, String pageTitle, WikiPageView view) {
        if (view != null) {
            memoryCache.put(cacheKey(siteId, pageTitle), view);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Internal
    // ════════════════════════════════════════════════════════════

    /**
     * Load a page via HTTP, store in memory cache + DB. Thread-safe, idempotent.
     */
    private WikiPageView loadAndCache(WikiSiteId siteId, String pageTitle) throws Exception {
        String cacheKey = cacheKey(siteId, pageTitle);

        // Double-check (another thread may have cached it meanwhile)
        WikiPageView existing = memoryCache.get(cacheKey);
        if (existing != null) return existing;

        WikiCredentials creds = WikiCredentials.anonymous();
        if (credentialsResolver != null) {
            WikiCredentials resolved = credentialsResolver.apply(siteId);
            if (resolved != null) creds = resolved;
        }
        WikiPageView view = wikiService.loadPage(siteId, pageTitle, creds);
        if (view == null) return null;

        memoryCache.put(cacheKey, view);

        // Persist to DB (best-effort)
        persistToDb(siteId, pageTitle, view);

        // Auto-index into Lucene if callback is set (async, to avoid Lucene contention)
        if (autoIndexCallback != null) {
            final WikiPageView viewToIndex = view;
            autoIndexExecutor.submit(() -> {
                try {
                    autoIndexCallback.accept(siteId, viewToIndex);
                    // Small pause between index operations to avoid starving search threads
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[WikiPrefetch] Auto-index failed for: " + pageTitle, e);
                }
            });
        }

        return view;
    }

    private void persistToDb(WikiSiteId siteId, String pageTitle, WikiPageView view) {
        try {
            String html = view.cleanedHtml();
            if (html == null || html.isEmpty()) return;

            String cacheUrl = "wiki://" + siteId.value() + "/" + pageTitle;
            if (cacheRepository.existsByUrl(cacheUrl)) return;

            long sizeBytes = html.getBytes("UTF-8").length;

            long currentSize = cacheRepository.getVolatileCacheSize();
            if (currentSize + sizeBytes > maxVolatileBytes) {
                cacheRepository.evictOldestVolatile(maxVolatileBytes - sizeBytes);
            }

            ArchiveEntry entry = new ArchiveEntry();
            entry.setEntryId(UUID.randomUUID().toString());
            entry.setUrl(cacheUrl);
            entry.setTitle(pageTitle);
            entry.setMimeType("text/html");
            entry.setContentLength(html.length());
            entry.setFileSizeBytes(sizeBytes);
            entry.setCrawlTimestamp(System.currentTimeMillis());
            entry.setStatus(ArchiveEntryStatus.CRAWLED);
            entry.setSourceId("wiki-prefetch");
            cacheRepository.saveVolatile(entry);
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WikiPrefetch] DB persist failed: " + pageTitle, e);
        }
    }

    private void cancelRunningPrefetches() {
        for (Future<?> f : runningPrefetches) {
            f.cancel(true);
        }
        runningPrefetches.clear();
    }

    @Override
    public void shutdown() {
        cancelRunningPrefetches();
        prefetchPool.shutdownNow();
        priorityThread.shutdownNow();
        memoryCache.clear();
    }

    private static String cacheKey(WikiSiteId siteId, String pageTitle) {
        return siteId.value() + "/" + pageTitle;
    }
}
