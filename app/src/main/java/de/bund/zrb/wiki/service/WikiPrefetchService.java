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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prefetches wiki pages into an in-memory cache AND the persistent DB cache.
 * Only the first MAX_PREFETCH results are prefetched to avoid flooding the server.
 * The in-memory cache uses a ConcurrentHashMap for O(1) lookup.
 */
public class WikiPrefetchService implements WikiPrefetchCallback {

    private static final Logger LOG = Logger.getLogger(WikiPrefetchService.class.getName());
    private static final int THREAD_POOL_SIZE = 2;
    /** Only prefetch the first N search results — the rest load on demand. */
    private static final int MAX_PREFETCH = 5;

    private final WikiContentService wikiService;
    private final CacheRepository cacheRepository;
    private final long maxVolatileBytes;
    private final ExecutorService executor;

    /** In-memory cache: "siteId/pageTitle" → WikiPageView.  O(1) lookup via HashMap. */
    private final ConcurrentHashMap<String, WikiPageView> memoryCache =
            new ConcurrentHashMap<String, WikiPageView>();

    /** Track running prefetch futures so we can cancel them when a new search starts. */
    private final CopyOnWriteArrayList<Future<?>> runningPrefetches =
            new CopyOnWriteArrayList<Future<?>>();

    /**
     * @param wikiService     the wiki content service for loading pages
     * @param cacheRepository the cache/archive repository
     * @param maxVolatileMb   max size in MB for volatile cache entries
     */
    public WikiPrefetchService(WikiContentService wikiService, CacheRepository cacheRepository,
                               int maxVolatileMb) {
        this.wikiService = wikiService;
        this.cacheRepository = cacheRepository;
        this.maxVolatileBytes = (long) maxVolatileMb * 1024L * 1024L;
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    @Override
    public void prefetchSearchResults(WikiSiteId siteId, List<String> pageTitles) {
        // Cancel any still-running prefetches from the previous search
        cancelRunningPrefetches();

        int count = 0;
        for (final String title : pageTitles) {
            if (count >= MAX_PREFETCH) break;

            final String cacheKey = cacheKey(siteId, title);
            if (memoryCache.containsKey(cacheKey)) {
                // Already cached — doesn't count against the limit
                continue;
            }

            Future<?> f = executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        prefetchSingle(siteId, title);
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[WikiPrefetch] Failed to prefetch: " + title, e);
                    }
                }
            });
            runningPrefetches.add(f);
            count++;
        }
    }

    @Override
    public WikiPageView getCached(WikiSiteId siteId, String pageTitle) {
        return memoryCache.get(cacheKey(siteId, pageTitle));
    }

    /**
     * Store a page that was loaded on-demand (by the UI) into the memory cache,
     * so re-selecting the same result is instant.
     */
    public void putInCache(WikiSiteId siteId, String pageTitle, WikiPageView view) {
        if (view != null) {
            memoryCache.put(cacheKey(siteId, pageTitle), view);
        }
    }

    private void cancelRunningPrefetches() {
        for (Future<?> f : runningPrefetches) {
            f.cancel(true);
        }
        runningPrefetches.clear();
    }

    private void prefetchSingle(WikiSiteId siteId, String pageTitle) {
        String cacheKey = cacheKey(siteId, pageTitle);

        if (memoryCache.containsKey(cacheKey)) {
            return;
        }

        // Check if this thread was interrupted (cancelled by new search)
        if (Thread.currentThread().isInterrupted()) return;

        try {
            WikiPageView view = wikiService.loadPage(siteId, pageTitle, WikiCredentials.anonymous());
            if (view == null) return;
            if (Thread.currentThread().isInterrupted()) return;

            memoryCache.put(cacheKey, view);

            String html = view.cleanedHtml();
            if (html == null || html.isEmpty()) return;

            // Persist to DB (low priority, fire-and-forget)
            String cacheUrl = "wiki://" + siteId.value() + "/" + pageTitle;
            if (!cacheRepository.existsByUrl(cacheUrl)) {
                long sizeBytes = html.getBytes("UTF-8").length;

                long currentVolatileSize = cacheRepository.getVolatileCacheSize();
                if (currentVolatileSize + sizeBytes > maxVolatileBytes) {
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
            }

            LOG.fine("[WikiPrefetch] Cached: " + pageTitle);
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                LOG.log(Level.FINE, "[WikiPrefetch] Error prefetching " + pageTitle, e);
            }
        }
    }

    @Override
    public void shutdown() {
        cancelRunningPrefetches();
        executor.shutdownNow();
        memoryCache.clear();
    }

    private static String cacheKey(WikiSiteId siteId, String pageTitle) {
        return siteId.value() + "/" + pageTitle;
    }
}
