package de.bund.zrb.service;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.rag.service.RagService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caches local file content for:
 * <ol>
 *   <li><b>H2 persistent cache</b> — via {@link CacheRepository}</li>
 *   <li><b>Lucene full-text index</b> — via {@link RagService} (for SearchEverywhere + RAG)</li>
 *   <li><b>In-memory cache</b> — for instant re-access within a session</li>
 * </ol>
 *
 * <p>Analogous to {@link FtpSourceCacheService} but for local file systems.</p>
 *
 * <h3>Document ID convention:</h3>
 * {@code LOCAL:C:/path/to/file} — detected as {@code SourceType.LOCAL} by SearchService.
 */
public class LocalSourceCacheService {

    private static final Logger LOG = Logger.getLogger(LocalSourceCacheService.class.getName());

    /** Prefix for LOCAL document IDs in Lucene / CacheRepository. */
    private static final String LOCAL_PREFIX = "LOCAL:";

    /** Bounded pool for background prefetching (file read + index). */
    private final ExecutorService prefetchPool;

    /** Dedicated single-thread executor for Lucene indexing (avoids contention). */
    private final ExecutorService indexExecutor;

    /** In-memory content cache: normalized path → content text. O(1) lookup. */
    private final ConcurrentHashMap<String, String> memoryCache =
            new ConcurrentHashMap<String, String>();

    /** Track which directories are currently being prefetched (prevent duplicate runs). */
    private final ConcurrentHashMap<String, Future<?>> activePrefetches =
            new ConcurrentHashMap<String, Future<?>>();

    private static volatile LocalSourceCacheService instance;

    public static synchronized LocalSourceCacheService getInstance() {
        if (instance == null) {
            instance = new LocalSourceCacheService(2);
        }
        return instance;
    }

    /**
     * @param concurrency number of parallel read threads for prefetch
     */
    public LocalSourceCacheService(int concurrency) {
        int poolSize = Math.max(1, Math.min(concurrency, 4));
        this.prefetchPool = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "LocalPrefetch-" + (count++));
                t.setDaemon(true);
                return t;
            }
        });
        this.indexExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "LocalIndexer");
                t.setDaemon(true);
                return t;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Cache key / document ID helpers
    // ═══════════════════════════════════════════════════════════

    /** Memory cache key: normalized path. */
    private static String cacheKey(String absolutePath) {
        return absolutePath.replace('\\', '/').toLowerCase();
    }

    /** Lucene/RAG document ID: "LOCAL:path" */
    public static String documentId(String absolutePath) {
        return LOCAL_PREFIX + absolutePath.replace('\\', '/');
    }

    /** CacheRepository URL: "local://path" */
    private static String cacheUrl(String absolutePath) {
        return "local://" + absolutePath.replace('\\', '/');
    }

    /** Directory-level cache URL prefix for queries. */
    private static String dirUrlPrefix(String dirPath) {
        String base = "local://" + dirPath.replace('\\', '/');
        if (!base.endsWith("/")) base += "/";
        return base;
    }

    // ═══════════════════════════════════════════════════════════
    //  Single-file cache operations
    // ═══════════════════════════════════════════════════════════

    /**
     * Get cached content from memory (instant, O(1)).
     * Returns null if not cached.
     */
    public String getCachedContent(String absolutePath) {
        return memoryCache.get(cacheKey(absolutePath));
    }

    /**
     * Put file content into the cache (memory + H2 + Lucene index).
     */
    public void cacheContent(String absolutePath, String fileName,
                             String content, final long fileSize, final long lastModified) {
        if (content == null) return;

        String key = cacheKey(absolutePath);
        memoryCache.put(key, content);

        final String url = cacheUrl(absolutePath);
        final String docId = documentId(absolutePath);
        String extension = extractExtension(fileName);
        final String title = fileName;
        final String ext = extension;
        final String fContent = content;

        indexExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    persistToH2(url, title, ext, fContent, fileSize, lastModified);
                    indexInLucene(docId, title, fContent, ext);
                    LOG.fine("[LocalCache] Cached + indexed: " + key);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[LocalCache] Failed to cache/index: " + key, e);
                }
            }
        });
    }

    /**
     * Invalidate cache for an entire directory (memory + H2 + Lucene).
     * Used by the "Cache leeren" button.
     */
    public void invalidateDirectory(String dirPath) {
        if (dirPath == null) return;

        LOG.info("[LocalCache] Invalidating all caches for: " + dirPath);

        // Cancel running prefetch for this path
        String prefetchKey = cacheKey(dirPath);
        Future<?> prefetch = activePrefetches.remove(prefetchKey);
        if (prefetch != null) {
            prefetch.cancel(true);
        }

        // Remove all in-memory entries for this directory
        String prefix = cacheKey(dirPath);
        if (!prefix.endsWith("/")) prefix += "/";
        final String memPrefix = prefix;
        Iterator<String> it = memoryCache.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(memPrefix)) {
                it.remove();
            }
        }

        final String urlPfx = dirUrlPrefix(dirPath);
        indexExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    CacheRepository repo = CacheRepository.getInstance();
                    List<ArchiveEntry> all = repo.findAll();
                    int removed = 0;
                    for (ArchiveEntry entry : all) {
                        if (entry.getUrl() != null && entry.getUrl().startsWith(urlPfx)) {
                            repo.delete(entry.getEntryId());
                            removed++;
                        }
                    }
                    LOG.info("[LocalCache] Removed " + removed + " H2 entries for " + urlPfx);

                    RagService rag = RagService.getInstance();
                    Map<String, String> allDocs = rag.listAllIndexedDocuments();
                    String ragPrefix = LOCAL_PREFIX + urlPfx.substring("local://".length());
                    for (String docId : allDocs.keySet()) {
                        if (docId.startsWith(ragPrefix)) {
                            rag.removeDocument(docId);
                        }
                    }
                    LOG.info("[LocalCache] Directory cache fully invalidated: " + urlPfx);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[LocalCache] Failed to invalidate directory", e);
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Background prefetch
    // ═══════════════════════════════════════════════════════════

    /**
     * Callback for prefetch progress reporting.
     */
    public interface PrefetchCallback {
        void onProgress(int current, int total, String fileName);
        void onComplete(int total, int indexed);
        void onError(String message);
    }

    /**
     * Start background prefetching of all text files in a directory.
     * Reads each file, caches in H2, indexes in Lucene.
     * <p>
     * <b>Incremental:</b> Compares file size and lastModified with cached metadata.
     * Files whose size and date haven't changed are skipped.
     *
     * @param dirPath     directory path
     * @param fileNodes   list of FileNode in the directory
     * @param callback    optional progress callback (called on background thread)
     */
    public void prefetchDirectory(final String dirPath,
                                  final List<FileNode> fileNodes,
                                  final PrefetchCallback callback) {
        final String prefetchKey = cacheKey(dirPath);

        // Skip if already running
        Future<?> existing = activePrefetches.get(prefetchKey);
        if (existing != null && !existing.isDone()) {
            LOG.fine("[LocalCache] Prefetch already running for: " + prefetchKey);
            return;
        }

        Future<?> future = prefetchPool.submit(new Runnable() {
            @Override
            public void run() {
                Map<String, String[]> cachedMeta = loadCacheMetadata(dirPath);
                LOG.info("[LocalCache] Prefetch started for " + prefetchKey
                        + ": " + fileNodes.size() + " files, " + cachedMeta.size() + " cached entries found");

                int total = fileNodes.size();
                int indexed = 0;
                int skipped = 0;
                int unchanged = 0;

                for (int i = 0; i < total; i++) {
                    if (Thread.currentThread().isInterrupted()) break;

                    FileNode node = fileNodes.get(i);

                    // Skip directories
                    if (node.isDirectory()) {
                        skipped++;
                        continue;
                    }

                    // Skip non-text files
                    if (!FtpSourceCacheService.isTextFile(node.getName())) {
                        skipped++;
                        continue;
                    }

                    String absolutePath = node.getPath();
                    if (absolutePath == null || absolutePath.isEmpty()) {
                        absolutePath = dirPath + File.separator + node.getName();
                    }
                    String key = cacheKey(absolutePath);

                    // Skip if already in memory cache
                    if (memoryCache.containsKey(key)) {
                        skipped++;
                        continue;
                    }

                    // Check persistent cache: compare file size + lastModified
                    String[] cached = cachedMeta.get(key);
                    if (cached != null) {
                        String cachedSize = cached[0];
                        String cachedMtime = cached[1];

                        long serverSize = node.getSize();
                        boolean sizeMatch;
                        if (serverSize <= 0) {
                            sizeMatch = true;
                        } else {
                            sizeMatch = String.valueOf(serverSize).equals(cachedSize);
                        }

                        long serverMtime = node.getLastModifiedMillis();
                        boolean mtimeMatch;
                        if (serverMtime <= 0 && (cachedMtime == null || cachedMtime.isEmpty() || "0".equals(cachedMtime))) {
                            mtimeMatch = true;
                        } else if (serverMtime <= 0 || cachedMtime == null || cachedMtime.isEmpty()) {
                            mtimeMatch = false;
                        } else {
                            mtimeMatch = String.valueOf(serverMtime).equals(cachedMtime);
                        }

                        if (sizeMatch && mtimeMatch) {
                            unchanged++;
                            continue;
                        }
                    }

                    try {
                        if (callback != null) {
                            callback.onProgress(i + 1, total, node.getName());
                        }

                        File file = new File(absolutePath);
                        if (!file.exists() || !file.canRead() || file.length() > 10 * 1024 * 1024) {
                            skipped++;
                            continue; // skip non-existent, unreadable, or files > 10 MB
                        }

                        byte[] bytes = Files.readAllBytes(file.toPath());
                        String content = new String(bytes, StandardCharsets.UTF_8);
                        if (content != null && !content.isEmpty()) {
                            cacheContent(absolutePath, node.getName(),
                                    content, node.getSize(), node.getLastModifiedMillis());
                            indexed++;
                        }

                        Thread.sleep(20); // small pause
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[LocalCache] Prefetch failed for " + node.getName(), e);
                    }
                }

                activePrefetches.remove(prefetchKey);

                if (callback != null) {
                    callback.onComplete(total, indexed);
                }

                LOG.info("[LocalCache] Prefetch complete for " + prefetchKey
                        + ": indexed=" + indexed + ", unchanged=" + unchanged
                        + ", skipped=" + skipped + ", total=" + total);
            }
        });

        activePrefetches.put(prefetchKey, future);
    }

    /** Cancel a running prefetch. */
    public void cancelPrefetch(String dirPath) {
        if (dirPath == null) return;
        String key = cacheKey(dirPath);
        Future<?> future = activePrefetches.remove(key);
        if (future != null) {
            future.cancel(true);
        }
    }

    /** Check if a prefetch is running. */
    public boolean isPrefetching(String dirPath) {
        if (dirPath == null) return false;
        String key = cacheKey(dirPath);
        Future<?> future = activePrefetches.get(key);
        return future != null && !future.isDone();
    }

    // ═══════════════════════════════════════════════════════════
    //  Stats / queries
    // ═══════════════════════════════════════════════════════════

    /** Load existing cache metadata from H2 for incremental change detection. */
    private Map<String, String[]> loadCacheMetadata(String dirPath) {
        Map<String, String[]> result = new HashMap<String, String[]>();
        try {
            CacheRepository repo = CacheRepository.getInstance();
            String urlPrefix = dirUrlPrefix(dirPath);
            List<ArchiveEntry> entries = repo.findByUrlPrefixWithMetadata(urlPrefix);

            for (ArchiveEntry entry : entries) {
                String url = entry.getUrl();
                if (url == null || !url.startsWith(urlPrefix)) continue;

                String fileName = url.substring(urlPrefix.length());
                String key = cacheKey(dirPath + "/" + fileName);

                Map<String, String> meta = entry.getMetadata();
                String size = meta != null ? meta.get("local_file_size") : null;
                String mtime = meta != null ? meta.get("local_last_modified") : null;

                result.put(key, new String[]{size, mtime});
            }

            LOG.info("[LocalCache] Loaded " + result.size() + " cache entries for " + dirPath);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[LocalCache] Failed to load cache metadata", e);
        }
        return result;
    }

    /** Get number of cached files in memory for a directory. */
    public int getMemoryCacheSize(String dirPath) {
        if (dirPath == null) return 0;
        String prefix = cacheKey(dirPath);
        if (!prefix.endsWith("/")) prefix += "/";
        int count = 0;
        for (String key : memoryCache.keySet()) {
            if (key.startsWith(prefix)) count++;
        }
        return count;
    }

    /** Get total memory cache size. */
    public int getTotalMemoryCacheSize() {
        return memoryCache.size();
    }

    /** Get number of cached files in H2 for a directory. */
    public int getH2CacheSize(String dirPath) {
        if (dirPath == null) return 0;
        String urlPrefix = dirUrlPrefix(dirPath);
        try {
            return CacheRepository.getInstance().countByUrlPrefix(urlPrefix);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[LocalCache] Failed to count H2 entries", e);
            return 0;
        }
    }

    /** Shut down background threads. */
    public void shutdown() {
        for (Future<?> f : activePrefetches.values()) {
            f.cancel(true);
        }
        activePrefetches.clear();
        prefetchPool.shutdownNow();
        indexExecutor.shutdownNow();
        memoryCache.clear();
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal: H2 persistence
    // ═══════════════════════════════════════════════════════════

    private void persistToH2(String url, String title, String extension, String content,
                             long fileSize, long lastModified) {
        try {
            CacheRepository repo = CacheRepository.getInstance();

            ArchiveEntry existing = repo.findByUrl(url);
            ArchiveEntry entry;
            if (existing != null) {
                entry = existing;
            } else {
                entry = new ArchiveEntry();
            }

            entry.setUrl(url);
            entry.setTitle(title);
            entry.setMimeType(mimeTypeForExtension(extension));
            entry.setContentLength(content.length());
            entry.setFileSizeBytes(content.getBytes(StandardCharsets.UTF_8).length);
            entry.setCrawlTimestamp(System.currentTimeMillis());
            entry.setStatus(ArchiveEntryStatus.INDEXED);
            entry.setSourceId("LOCAL");

            Map<String, String> meta = entry.getMetadata();
            if (meta == null) {
                meta = new HashMap<String, String>();
                entry.setMetadata(meta);
            }
            meta.put("source_type", "LOCAL");
            meta.put("extension", extension != null ? extension : "");
            meta.put("local_file_size", String.valueOf(fileSize));
            meta.put("local_last_modified", String.valueOf(lastModified));

            repo.save(entry);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[LocalCache] H2 persist failed for: " + url, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal: Lucene RAG indexing
    // ═══════════════════════════════════════════════════════════

    private void indexInLucene(String docId, String title, String content, String extension) {
        try {
            RagService rag = RagService.getInstance();

            if (rag.isIndexed(docId)) {
                rag.removeDocument(docId);
            }

            String lang = languageForExtension(extension);

            de.bund.zrb.ingestion.model.document.DocumentMetadata metadata =
                    de.bund.zrb.ingestion.model.document.DocumentMetadata.builder()
                            .sourceName(title)
                            .mimeType(mimeTypeForExtension(extension))
                            .attribute("source_type", "LOCAL")
                            .build();

            de.bund.zrb.ingestion.model.document.Document.Builder docBuilder =
                    de.bund.zrb.ingestion.model.document.Document.builder()
                            .metadata(metadata)
                            .heading(2, title);

            if (lang != null) {
                docBuilder.code(lang, content);
            } else {
                docBuilder.paragraph(content);
            }

            rag.indexDocument(docId, title, docBuilder.build(), false);

            LOG.fine("[LocalCache] Indexed in Lucene: " + docId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[LocalCache] Lucene indexing failed for: " + docId, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════

    private static String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    private static String mimeTypeForExtension(String ext) {
        if (ext == null || ext.isEmpty()) return "text/plain";
        String lower = ext.toLowerCase();
        if (lower.equals("jcl")) return "text/x-jcl";
        if (lower.equals("cbl") || lower.equals("cob") || lower.equals("cpy")) return "text/x-cobol";
        if (lower.equals("xml")) return "text/xml";
        if (lower.equals("json")) return "application/json";
        if (lower.equals("html") || lower.equals("htm")) return "text/html";
        if (lower.equals("csv")) return "text/csv";
        if (lower.equals("sql") || lower.equals("ddl") || lower.equals("dml")) return "text/x-sql";
        if (lower.equals("java")) return "text/x-java";
        if (lower.equals("py")) return "text/x-python";
        if (lower.equals("js") || lower.equals("ts")) return "text/javascript";
        return "text/plain";
    }

    private static String languageForExtension(String ext) {
        if (ext == null || ext.isEmpty()) return null;
        String lower = ext.toLowerCase();
        if (lower.equals("jcl")) return "jcl";
        if (lower.equals("cbl") || lower.equals("cob") || lower.equals("cpy")) return "cobol";
        if (lower.equals("xml")) return "xml";
        if (lower.equals("json")) return "json";
        if (lower.equals("sql") || lower.equals("ddl") || lower.equals("dml")) return "sql";
        if (lower.equals("py")) return "python";
        if (lower.equals("java")) return "java";
        if (lower.equals("js") || lower.equals("ts")) return "javascript";
        if (lower.equals("md")) return "markdown";
        return null;
    }
}

