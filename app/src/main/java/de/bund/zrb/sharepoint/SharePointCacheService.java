package de.bund.zrb.sharepoint;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.rag.service.RagService;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caches SharePoint page content for:
 * <ol>
 *   <li><b>H2 persistent cache</b> — via {@link CacheRepository}</li>
 *   <li><b>Lucene full-text index</b> — via {@link RagService} (for SearchEverywhere + RAG)</li>
 *   <li><b>In-memory cache</b> — for instant re-access within a session</li>
 * </ol>
 *
 * <p>Document ID convention: {@code SP:siteUrl/pagePath} —
 * detected as {@code SourceType.SHAREPOINT} by SearchService.</p>
 */
public class SharePointCacheService {

    private static final Logger LOG = Logger.getLogger(SharePointCacheService.class.getName());

    /** Prefix for SharePoint document IDs in Lucene / CacheRepository. */
    public static final String SP_PREFIX = "SP:";

    /** Bounded pool for background page fetching + indexing. */
    private final ExecutorService fetchPool;

    /** Dedicated single-thread executor for Lucene indexing (avoids contention). */
    private final ExecutorService indexExecutor;

    /** In-memory content cache: "siteUrl/pagePath" → content text. O(1) lookup. */
    private final ConcurrentHashMap<String, String> memoryCache =
            new ConcurrentHashMap<String, String>();

    /** Track which sites are currently being cached (prevent duplicate runs). */
    private final ConcurrentHashMap<String, Future<?>> activeCaches =
            new ConcurrentHashMap<String, Future<?>>();

    private static volatile SharePointCacheService instance;

    public static synchronized SharePointCacheService getInstance() {
        if (instance == null) {
            instance = new SharePointCacheService(2);
        }
        return instance;
    }

    /**
     * @param concurrency number of parallel download threads for page caching
     */
    public SharePointCacheService(int concurrency) {
        fetchPool = Executors.newFixedThreadPool(concurrency,
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "SP-Cache-" + counter++);
                        t.setDaemon(true);
                        return t;
                    }
                });
        indexExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "SP-Index");
                t.setDaemon(true);
                return t;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Cache key / document ID helpers
    // ═══════════════════════════════════════════════════════════

    /** Memory cache key: "siteUrl/pagePath" (lowercase). */
    private static String cacheKey(String siteUrl, String pagePath) {
        return (siteUrl + "/" + pagePath).toLowerCase();
    }

    /** Lucene/RAG document ID: "SP:siteUrl/pagePath". */
    public static String documentId(String siteUrl, String pagePath) {
        return SP_PREFIX + siteUrl + "/" + pagePath;
    }

    /** CacheRepository URL: "sp://siteUrl/pagePath". */
    private static String cacheUrl(String siteUrl, String pagePath) {
        return "sp://" + siteUrl + "/" + pagePath;
    }

    // ═══════════════════════════════════════════════════════════
    //  Single-page cache operations
    // ═══════════════════════════════════════════════════════════

    /**
     * Get cached content from memory (instant, O(1)).
     * Returns null if not cached.
     */
    public String getCachedContent(String siteUrl, String pagePath) {
        return memoryCache.get(cacheKey(siteUrl, pagePath));
    }

    /**
     * Check if a page is already cached (in memory).
     */
    public boolean isCached(String siteUrl, String pagePath) {
        return memoryCache.containsKey(cacheKey(siteUrl, pagePath));
    }

    /**
     * Put page content into the cache (memory + H2 + Lucene index).
     *
     * @param siteUrl   SharePoint site URL
     * @param pagePath  path within the site
     * @param title     page title
     * @param content   plain-text page content
     */
    public void cacheContent(String siteUrl, String pagePath, String title, String content) {
        if (content == null || content.isEmpty()) return;

        final String key = cacheKey(siteUrl, pagePath);
        memoryCache.put(key, content);

        final String docId = documentId(siteUrl, pagePath);
        final String url = cacheUrl(siteUrl, pagePath);
        final String fTitle = (title != null && !title.isEmpty()) ? title : pagePath;

        indexExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    persistToH2(url, fTitle, content);
                    indexInLucene(docId, fTitle, content, siteUrl);
                    LOG.fine("[SPCache] Cached + indexed: " + key);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[SPCache] Failed to cache/index: " + key, e);
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal: H2 persistence
    // ═══════════════════════════════════════════════════════════

    private void persistToH2(String url, String title, String content) {
        try {
            CacheRepository repo = CacheRepository.getInstance();
            ArchiveEntry entry = new ArchiveEntry();
            entry.setUrl(url);
            entry.setTitle(title);
            entry.setMimeType("text/html");
            entry.setContentLength(content.length());
            entry.setFileSizeBytes(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            entry.setCrawlTimestamp(System.currentTimeMillis());
            entry.setStatus(ArchiveEntryStatus.INDEXED);
            repo.save(entry);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SPCache] H2 persist failed for: " + url, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal: Lucene RAG indexing
    // ═══════════════════════════════════════════════════════════

    private void indexInLucene(String docId, String title, String content, String siteUrl) {
        try {
            RagService rag = RagService.getInstance();

            if (rag.isIndexed(docId)) {
                rag.removeDocument(docId);
            }

            de.bund.zrb.ingestion.model.document.DocumentMetadata metadata =
                    de.bund.zrb.ingestion.model.document.DocumentMetadata.builder()
                            .sourceName(title)
                            .mimeType("text/html")
                            .attribute("source_type", "SHAREPOINT")
                            .attribute("site", siteUrl)
                            .build();

            de.bund.zrb.ingestion.model.document.Document document =
                    de.bund.zrb.ingestion.model.document.Document.builder()
                            .metadata(metadata)
                            .heading(2, title)
                            .paragraph(content)
                            .build();

            rag.indexDocument(docId, title, document, false);

            LOG.fine("[SPCache] Indexed in Lucene: " + docId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SPCache] Lucene indexing failed for: " + docId, e);
        }
    }

    /**
     * Shut down thread pools.
     */
    public void shutdown() {
        fetchPool.shutdownNow();
        indexExecutor.shutdownNow();
    }
}

