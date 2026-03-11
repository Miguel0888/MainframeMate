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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prefetches wiki pages into the local cache in the background when search results arrive.
 * Entries are marked as volatile (ephemeral) and have a separate size limit.
 * Oldest volatile entries are evicted when the limit is reached.
 */
public class WikiPrefetchService implements WikiPrefetchCallback {

    private static final Logger LOG = Logger.getLogger(WikiPrefetchService.class.getName());
    private static final int THREAD_POOL_SIZE = 3;

    private final WikiContentService wikiService;
    private final CacheRepository cacheRepository;
    private final long maxVolatileBytes;
    private final ExecutorService executor;

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
        for (final String title : pageTitles) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        prefetchSingle(siteId, title);
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[WikiPrefetch] Failed to prefetch: " + title, e);
                    }
                }
            });
        }
    }

    private void prefetchSingle(WikiSiteId siteId, String pageTitle) {
        String cacheUrl = "wiki://" + siteId.value() + "/" + pageTitle;

        // Skip if already cached
        if (cacheRepository.existsByUrl(cacheUrl)) {
            return;
        }

        try {
            WikiPageView view = wikiService.loadPage(siteId, pageTitle, WikiCredentials.anonymous());
            String html = view.cleanedHtml();
            if (html == null || html.isEmpty()) return;

            long sizeBytes = html.getBytes("UTF-8").length;

            // Evict if necessary
            long currentVolatileSize = cacheRepository.getVolatileCacheSize();
            if (currentVolatileSize + sizeBytes > maxVolatileBytes) {
                cacheRepository.evictOldestVolatile(maxVolatileBytes - sizeBytes);
            }

            // Create volatile cache entry
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
            LOG.fine("[WikiPrefetch] Cached: " + pageTitle + " (" + sizeBytes + " bytes)");
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WikiPrefetch] Error prefetching " + pageTitle, e);
        }
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }
}

