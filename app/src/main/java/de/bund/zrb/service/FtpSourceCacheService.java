package de.bund.zrb.service;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.rag.service.RagService;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caches FTP file content for:
 * <ol>
 *   <li><b>H2 persistent cache</b> — via {@link CacheRepository}</li>
 *   <li><b>Lucene full-text index</b> — via {@link RagService} (for SearchEverywhere + RAG)</li>
 *   <li><b>In-memory cache</b> — for instant re-access within a session</li>
 * </ol>
 *
 * <p>Analogous to {@link NdvSourceCacheService} but for FTP file systems.
 * Uses FileNode metadata (size, lastModified) for incremental change detection.</p>
 *
 * <h3>Document ID convention:</h3>
 * {@code FTP:host/path/to/file} — detected as {@code SourceType.FTP} by SearchService.
 */
public class FtpSourceCacheService {

    private static final Logger LOG = Logger.getLogger(FtpSourceCacheService.class.getName());

    /** Prefix for FTP document IDs in Lucene / CacheRepository. */
    private static final String FTP_PREFIX = "FTP:";

    /** Bounded pool for background prefetching (file download + index). */
    private final ExecutorService prefetchPool;

    /** Dedicated single-thread executor for Lucene indexing (avoids contention). */
    private final ExecutorService indexExecutor;

    /** In-memory content cache: "host/absolutePath" → content text. O(1) lookup. */
    private final ConcurrentHashMap<String, String> memoryCache =
            new ConcurrentHashMap<String, String>();

    /** Track which directories are currently being prefetched (prevent duplicate runs). */
    private final ConcurrentHashMap<String, Future<?>> activePrefetches =
            new ConcurrentHashMap<String, Future<?>>();

    private static volatile FtpSourceCacheService instance;

    public static synchronized FtpSourceCacheService getInstance() {
        if (instance == null) {
            instance = new FtpSourceCacheService(2);
        }
        return instance;
    }

    /**
     * @param concurrency number of parallel FTP download threads for prefetch
     */
    public FtpSourceCacheService(int concurrency) {
        int poolSize = Math.max(1, Math.min(concurrency, 4));
        this.prefetchPool = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FtpPrefetch-" + (count++));
                t.setDaemon(true);
                return t;
            }
        });
        this.indexExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FtpIndexer");
                t.setDaemon(true);
                return t;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Cache key / document ID helpers
    // ═══════════════════════════════════════════════════════════

    /** Memory cache key: "host/absolutePath" (lowercase). */
    private static String cacheKey(String host, String absolutePath) {
        return (host + "/" + absolutePath).toLowerCase();
    }

    /** Lucene/RAG document ID: "FTP:host/path" — SearchService detects FTP: prefix. */
    public static String documentId(String host, String absolutePath) {
        return FTP_PREFIX + host + "/" + absolutePath;
    }

    /** CacheRepository URL: "ftp://host/path" */
    private static String cacheUrl(String host, String absolutePath) {
        return "ftp://" + host + "/" + absolutePath;
    }

    /** Directory-level cache URL prefix for queries. */
    private static String dirUrlPrefix(String host, String dirPath) {
        String base = "ftp://" + host + "/" + dirPath;
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
    public String getCachedContent(String host, String absolutePath) {
        return memoryCache.get(cacheKey(host, absolutePath));
    }

    /**
     * Put file content into the cache (memory + H2 + Lucene index).
     *
     * @param host         FTP host
     * @param absolutePath absolute path on FTP server
     * @param fileName     file name (for display)
     * @param content      file content as text
     * @param fileSize     server-reported file size, or -1 if unknown
     * @param lastModified server-reported last modified millis, or -1 if unknown
     */
    public void cacheContent(String host, String absolutePath, String fileName,
                             String content, final long fileSize, final long lastModified) {
        if (content == null) return;

        String key = cacheKey(host, absolutePath);
        memoryCache.put(key, content);

        final String url = cacheUrl(host, absolutePath);
        final String docId = documentId(host, absolutePath);
        String extension = extractExtension(fileName);
        final String title = fileName;
        final String ext = extension;
        final String fContent = content;
        final String fHost = host;

        indexExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    persistToH2(url, title, ext, fContent, fileSize, lastModified);
                    indexInLucene(docId, title, fContent, fHost, ext);
                    LOG.fine("[FtpCache] Cached + indexed: " + key);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[FtpCache] Failed to cache/index: " + key, e);
                }
            }
        });
    }

    /**
     * Invalidate cache for an entire directory (memory + H2 + Lucene).
     * Used by the "Cache leeren" button.
     */
    public void invalidateDirectory(String host, String dirPath) {
        if (host == null || dirPath == null) return;

        LOG.info("[FtpCache] Invalidating all caches for: " + host + "/" + dirPath);

        // Cancel running prefetch for this path
        String prefetchKey = (host + "/" + dirPath).toLowerCase();
        Future<?> prefetch = activePrefetches.remove(prefetchKey);
        if (prefetch != null) {
            prefetch.cancel(true);
        }

        // Remove all in-memory entries for this directory
        String prefix = cacheKey(host, dirPath);
        if (!prefix.endsWith("/")) prefix += "/";
        final String memPrefix = prefix;
        Iterator<String> it = memoryCache.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(memPrefix)) {
                it.remove();
            }
        }

        final String urlPfx = dirUrlPrefix(host, dirPath);
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
                    LOG.info("[FtpCache] Removed " + removed + " H2 entries for " + urlPfx);

                    RagService rag = RagService.getInstance();
                    Map<String, String> allDocs = rag.listAllIndexedDocuments();
                    String ragPrefix = FTP_PREFIX + urlPfx.substring("ftp://".length());
                    for (String docId : allDocs.keySet()) {
                        if (docId.startsWith(ragPrefix)) {
                            rag.removeDocument(docId);
                        }
                    }
                    LOG.info("[FtpCache] Directory cache fully invalidated: " + urlPfx);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[FtpCache] Failed to invalidate directory", e);
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Background prefetch
    // ═══════════════════════════════════════════════════════════

    /**
     * Callback for prefetch progress reporting.
     * Same interface as {@link NdvSourceCacheService.PrefetchCallback}.
     */
    public interface PrefetchCallback {
        void onProgress(int current, int total, String fileName);
        void onComplete(int total, int indexed);
        void onError(String message);
    }

    /**
     * Start background prefetching of all text files in a directory.
     * Downloads each file, caches in H2, indexes in Lucene.
     * <p>
     * <b>Incremental:</b> Before downloading, compares server-reported size and
     * lastModified with metadata stored in H2 from the last prefetch.
     * Files whose size and date haven't changed are skipped.
     *
     * @param host        FTP host
     * @param dirPath     directory path
     * @param fileNodes   list of FileNode in the directory
     * @param fileService the FileService to use for reading files
     * @param callback    optional progress callback (called on background thread)
     */
    public void prefetchDirectory(final String host, final String dirPath,
                                  final List<FileNode> fileNodes,
                                  final FileService fileService,
                                  final PrefetchCallback callback) {
        final String prefetchKey = (host + "/" + dirPath).toLowerCase();

        // Skip if already running
        Future<?> existing = activePrefetches.get(prefetchKey);
        if (existing != null && !existing.isDone()) {
            LOG.fine("[FtpCache] Prefetch already running for: " + prefetchKey);
            return;
        }

        Future<?> future = prefetchPool.submit(new Runnable() {
            @Override
            public void run() {
                Map<String, String[]> cachedMeta = loadCacheMetadata(host, dirPath);
                LOG.info("[FtpCache] Prefetch started for " + prefetchKey
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

                    // Skip non-text files (binary files can't be meaningfully indexed)
                    if (!isTextFile(node.getName())) {
                        skipped++;
                        continue;
                    }

                    String absolutePath = node.getPath();
                    if (absolutePath == null || absolutePath.isEmpty()) {
                        absolutePath = dirPath + (dirPath.endsWith("/") ? "" : "/") + node.getName();
                    }
                    String key = cacheKey(host, absolutePath);

                    // Skip if already in memory cache
                    if (memoryCache.containsKey(key)) {
                        skipped++;
                        continue;
                    }

                    // Check persistent cache: compare server-reported size + lastModified
                    String[] cached = cachedMeta.get(key);
                    if (cached != null) {
                        String cachedSize = cached[0];
                        String cachedMtime = cached[1];

                        long serverSize = node.getSize();
                        boolean sizeMatch;
                        if (serverSize <= 0) {
                            sizeMatch = true; // unknown → can't compare
                        } else {
                            sizeMatch = String.valueOf(serverSize).equals(cachedSize);
                        }

                        long serverMtime = node.getLastModifiedMillis();
                        boolean mtimeMatch;
                        if (serverMtime <= 0 && (cachedMtime == null || cachedMtime.isEmpty() || "0".equals(cachedMtime))) {
                            mtimeMatch = true; // both unknown
                        } else if (serverMtime <= 0 || cachedMtime == null || cachedMtime.isEmpty()) {
                            mtimeMatch = false; // one has value, other doesn't
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

                        FilePayload payload = fileService.readFile(absolutePath);
                        String content = payload.getEditorText();
                        if (content != null && !content.isEmpty()) {
                            cacheContent(host, absolutePath, node.getName(),
                                    content, node.getSize(), node.getLastModifiedMillis());
                            indexed++;
                        }

                        Thread.sleep(50); // small pause
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[FtpCache] Prefetch failed for " + node.getName(), e);
                    }
                }

                activePrefetches.remove(prefetchKey);

                if (callback != null) {
                    callback.onComplete(total, indexed);
                }

                LOG.info("[FtpCache] Prefetch complete for " + prefetchKey
                        + ": indexed=" + indexed + ", unchanged=" + unchanged
                        + ", skipped=" + skipped + ", total=" + total);
            }
        });

        activePrefetches.put(prefetchKey, future);
    }

    /** Cancel a running prefetch. */
    public void cancelPrefetch(String host, String dirPath) {
        if (host == null || dirPath == null) return;
        String key = (host + "/" + dirPath).toLowerCase();
        Future<?> future = activePrefetches.remove(key);
        if (future != null) {
            future.cancel(true);
        }
    }

    /** Check if a prefetch is running. */
    public boolean isPrefetching(String host, String dirPath) {
        if (host == null || dirPath == null) return false;
        String key = (host + "/" + dirPath).toLowerCase();
        Future<?> future = activePrefetches.get(key);
        return future != null && !future.isDone();
    }

    // ═══════════════════════════════════════════════════════════
    //  Stats / queries
    // ═══════════════════════════════════════════════════════════

    /** Load existing cache metadata from H2 for incremental change detection. */
    private Map<String, String[]> loadCacheMetadata(String host, String dirPath) {
        Map<String, String[]> result = new HashMap<String, String[]>();
        try {
            CacheRepository repo = CacheRepository.getInstance();
            String urlPrefix = dirUrlPrefix(host, dirPath);
            List<ArchiveEntry> entries = repo.findByUrlPrefixWithMetadata(urlPrefix);

            for (ArchiveEntry entry : entries) {
                String url = entry.getUrl();
                if (url == null || !url.startsWith(urlPrefix)) continue;

                String fileName = url.substring(urlPrefix.length());
                String key = cacheKey(host, dirPath + "/" + fileName);

                Map<String, String> meta = entry.getMetadata();
                String size = meta != null ? meta.get("ftp_file_size") : null;
                String mtime = meta != null ? meta.get("ftp_last_modified") : null;

                result.put(key, new String[]{size, mtime});
            }

            LOG.info("[FtpCache] Loaded " + result.size() + " cache entries for " + host + "/" + dirPath);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[FtpCache] Failed to load cache metadata", e);
        }
        return result;
    }

    /** Get number of cached files in memory for a directory. */
    public int getMemoryCacheSize(String host, String dirPath) {
        if (host == null || dirPath == null) return 0;
        String prefix = cacheKey(host, dirPath);
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
    public int getH2CacheSize(String host, String dirPath) {
        if (host == null || dirPath == null) return 0;
        String urlPrefix = dirUrlPrefix(host, dirPath);
        try {
            return CacheRepository.getInstance().countByUrlPrefix(urlPrefix);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[FtpCache] Failed to count H2 entries", e);
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
            entry.setSourceId("FTP");

            Map<String, String> meta = entry.getMetadata();
            if (meta == null) {
                meta = new HashMap<String, String>();
                entry.setMetadata(meta);
            }
            meta.put("source_type", "FTP");
            meta.put("extension", extension != null ? extension : "");
            meta.put("ftp_file_size", String.valueOf(fileSize));
            meta.put("ftp_last_modified", String.valueOf(lastModified));

            repo.save(entry);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[FtpCache] H2 persist failed for: " + url, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal: Lucene RAG indexing
    // ═══════════════════════════════════════════════════════════

    private void indexInLucene(String docId, String title, String content,
                              String host, String extension) {
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
                            .attribute("source_type", "FTP")
                            .attribute("host", host)
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

            LOG.fine("[FtpCache] Indexed in Lucene: " + docId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[FtpCache] Lucene indexing failed for: " + docId, e);
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

    /** Determine if a file is likely text-based (cacheable/indexable). */
    public static boolean isTextFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        // Common text file extensions
        return lower.endsWith(".txt") || lower.endsWith(".jcl") || lower.endsWith(".cbl")
                || lower.endsWith(".cob") || lower.endsWith(".cpy") || lower.endsWith(".nat")
                || lower.endsWith(".nsp") || lower.endsWith(".nss") || lower.endsWith(".nsn")
                || lower.endsWith(".nsh") || lower.endsWith(".nsc") || lower.endsWith(".nsl")
                || lower.endsWith(".nsa") || lower.endsWith(".nsg") || lower.endsWith(".nsm")
                || lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".csv")
                || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".md")
                || lower.endsWith(".log") || lower.endsWith(".cfg") || lower.endsWith(".ini")
                || lower.endsWith(".properties") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".cmd")
                || lower.endsWith(".sql") || lower.endsWith(".ddl") || lower.endsWith(".dml")
                || lower.endsWith(".py") || lower.endsWith(".java") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".css") || lower.endsWith(".rexx")
                || lower.endsWith(".proc") || lower.endsWith(".prc") || lower.endsWith(".asm")
                || lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".cpp")
                || lower.endsWith(".pl1") || lower.endsWith(".pli")
                // MVS datasets often have no extension — treat as text if no dot
                || !lower.contains(".");
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
        if (lower.equals("rexx")) return "text/x-rexx";
        if (lower.equals("asm")) return "text/x-asm";
        if (lower.equals("pl1") || lower.equals("pli")) return "text/x-pl1";
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
        if (lower.equals("rexx")) return "rexx";
        if (lower.equals("asm")) return "asm";
        if (lower.equals("py")) return "python";
        if (lower.equals("java")) return "java";
        if (lower.equals("js") || lower.equals("ts")) return "javascript";
        return null;
    }
}

