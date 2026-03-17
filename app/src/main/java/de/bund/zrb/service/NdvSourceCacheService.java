package de.bund.zrb.service;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.rag.service.RagService;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caches NDV (Natural Development Server) source code for:
 * <ol>
 *   <li><b>H2 persistent cache</b> — via {@link CacheRepository} (like wiki pages)</li>
 *   <li><b>Lucene full-text index</b> — via {@link RagService} (for SearchEverywhere + RAG)</li>
 *   <li><b>Dependency graph</b> — via {@link LuceneDependencyIndex} (for AI queries)</li>
 *   <li><b>In-memory cache</b> — for instant re-access within a session</li>
 * </ol>
 *
 * <h3>Cache invalidation strategy:</h3>
 * <ul>
 *   <li><b>On open</b>: Always fetch fresh from server (another user may have changed it).
 *       After fetch, update cache + index.</li>
 *   <li><b>On save</b>: Update cache with the saved version, re-index, update dependency graph.</li>
 *   <li><b>Manual</b>: "Cache leeren" button removes all cached data for a library.</li>
 *   <li><b>Background prefetch</b>: When a library is opened, prefetch all sources in a thread pool
 *       (like WikiPrefetchService). This populates the Lucene index for SearchEverywhere.</li>
 * </ul>
 *
 * <h3>Document ID convention:</h3>
 * {@code NDV:LIBRARY/OBJECTNAME.EXT} — detected as {@code SourceType.NDV} by SearchService.
 */
public class NdvSourceCacheService {

    private static final Logger LOG = Logger.getLogger(NdvSourceCacheService.class.getName());

    /** Prefix for NDV document IDs in Lucene / CacheRepository. */
    private static final String NDV_PREFIX = "NDV:";

    /** Bounded pool for background prefetching (source download + index). */
    private final ExecutorService prefetchPool;

    /** Dedicated single-thread executor for Lucene indexing (avoids contention). */
    private final ExecutorService indexExecutor;

    /** In-memory source cache: "LIBRARY/OBJNAME" → source text. O(1) lookup. */
    private final ConcurrentHashMap<String, String> memoryCache =
            new ConcurrentHashMap<String, String>();

    /** Track which libraries are currently being prefetched (prevent duplicate runs). */
    private final ConcurrentHashMap<String, Future<?>> activePrefetches =
            new ConcurrentHashMap<String, Future<?>>();


    private volatile NdvService ndvService;

    private static volatile NdvSourceCacheService instance;

    public static synchronized NdvSourceCacheService getInstance() {
        if (instance == null) {
            instance = new NdvSourceCacheService(2);
        }
        return instance;
    }

    /**
     * @param concurrency number of parallel NDV download threads for prefetch
     */
    public NdvSourceCacheService(int concurrency) {
        int poolSize = Math.max(1, Math.min(concurrency, 4));
        this.prefetchPool = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "NdvPrefetch-" + (count++));
                t.setDaemon(true);
                return t;
            }
        });
        this.indexExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "NdvIndexer");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     * Set the NDV service to use for downloading sources.
     * Must be called before prefetch operations.
     */
    public void setNdvService(NdvService ndvService) {
        this.ndvService = ndvService;
    }

    // ═══════════════════════════════════════════════════════════
    //  Cache key / document ID helpers
    // ═══════════════════════════════════════════════════════════

    /** Memory cache key: "LIBRARY/OBJNAME" (uppercase). */
    private static String cacheKey(String library, String objectName) {
        return library.toUpperCase() + "/" + objectName.toUpperCase();
    }

    /** Lucene/RAG document ID: "NDV:LIBRARY/OBJNAME.EXT" — SearchService detects NDV: prefix. */
    public static String documentId(String library, String objectName, String extension) {
        String ext = (extension != null && !extension.isEmpty()) ? "." + extension : "";
        return NDV_PREFIX + library.toUpperCase() + "/" + objectName.toUpperCase() + ext;
    }

    /** CacheRepository URL: "ndv://LIBRARY/OBJNAME.EXT" */
    private static String cacheUrl(String library, String objectName, String extension) {
        String ext = (extension != null && !extension.isEmpty()) ? "." + extension : "";
        return "ndv://" + library.toUpperCase() + "/" + objectName.toUpperCase() + ext;
    }

    // ═══════════════════════════════════════════════════════════
    //  Single-object cache operations
    // ═══════════════════════════════════════════════════════════

    /**
     * Get cached source text from memory (instant, O(1)).
     * Returns null if not cached.
     */
    public String getCachedSource(String library, String objectName) {
        return memoryCache.get(cacheKey(library, objectName));
    }

    /**
     * Put a source text into the cache (memory + H2 + Lucene index).
     * Call this after a fresh download from the server or after saving.
     *
     * @param library    Natural library name
     * @param objectName object name (e.g. "MYPROG")
     * @param extension  type extension (e.g. "NSP")
     * @param sourceText the source code
     */
    public void cacheSource(String library, String objectName, String extension, String sourceText) {
        if (sourceText == null) return;

        String key = cacheKey(library, objectName);
        memoryCache.put(key, sourceText);

        // Persist to H2 (async, best-effort)
        final String url = cacheUrl(library, objectName, extension);
        final String docId = documentId(library, objectName, extension);
        final String title = objectName.toUpperCase() + " (" + library.toUpperCase() + ")";

        indexExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1) H2 persistent cache
                    persistToH2(url, title, extension, sourceText);

                    // 2) Lucene full-text index (for SearchEverywhere + RAG)
                    indexInLucene(docId, title, sourceText, library, objectName, extension);

                    LOG.fine("[NdvCache] Cached + indexed: " + key);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[NdvCache] Failed to cache/index: " + key, e);
                }
            }
        });
    }

    /**
     * Invalidate cache for a single object (memory + H2 + Lucene).
     * Call this before re-downloading from the server.
     */
    public void invalidateObject(String library, String objectName, String extension) {
        String key = cacheKey(library, objectName);
        memoryCache.remove(key);

        final String url = cacheUrl(library, objectName, extension);
        final String docId = documentId(library, objectName, extension);

        indexExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // Remove from H2
                    CacheRepository repo = CacheRepository.getInstance();
                    ArchiveEntry existing = repo.findByUrl(url);
                    if (existing != null) {
                        repo.delete(existing.getEntryId());
                    }

                    // Remove from Lucene RAG index
                    RagService.getInstance().removeDocument(docId);

                    LOG.fine("[NdvCache] Invalidated: " + key);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[NdvCache] Failed to invalidate: " + key, e);
                }
            }
        });
    }

    /**
     * Invalidate the entire cache for a library (memory + H2 + Lucene + dependency graph).
     * Used by the "Cache leeren" button.
     */
    public void invalidateLibrary(String library) {
        if (library == null) return;
        String libUpper = library.toUpperCase();

        LOG.info("[NdvCache] Invalidating all caches for library: " + libUpper);

        // Cancel running prefetch for this library
        Future<?> prefetch = activePrefetches.remove(libUpper);
        if (prefetch != null) {
            prefetch.cancel(true);
        }

        // Remove all in-memory entries for this library
        String prefix = libUpper + "/";
        Iterator<String> it = memoryCache.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }

        indexExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // Remove from H2: find all entries with ndv://LIBRARY/ URL prefix
                    CacheRepository repo = CacheRepository.getInstance();
                    String urlPrefix = "ndv://" + libUpper + "/";
                    List<ArchiveEntry> all = repo.findAll();
                    int removed = 0;
                    for (ArchiveEntry entry : all) {
                        if (entry.getUrl() != null && entry.getUrl().startsWith(urlPrefix)) {
                            repo.delete(entry.getEntryId());
                            removed++;
                        }
                    }
                    LOG.info("[NdvCache] Removed " + removed + " H2 entries for " + libUpper);

                    // Remove from Lucene RAG index: all docs with NDV:LIBRARY/ prefix
                    RagService rag = RagService.getInstance();
                    Map<String, String> allDocs = rag.listAllIndexedDocuments();
                    String ragPrefix = NDV_PREFIX + libUpper + "/";
                    for (String docId : allDocs.keySet()) {
                        if (docId.startsWith(ragPrefix)) {
                            rag.removeDocument(docId);
                        }
                    }

                    // Remove dependency graph from Lucene index
                    LuceneDependencyIndex.getInstance().removeLibrary(libUpper);

                    LOG.info("[NdvCache] Library cache fully invalidated: " + libUpper);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[NdvCache] Failed to invalidate library: " + libUpper, e);
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Save callback: update cache after writing to server
    // ═══════════════════════════════════════════════════════════

    /**
     * Called after a source has been successfully saved to the NDV server.
     * Updates cache, re-indexes, and incrementally updates the dependency graph.
     *
     * @param library    library name
     * @param objectName object name
     * @param extension  type extension (e.g. "NSP")
     * @param newSource  the saved source text
     * @param graph      the in-memory dependency graph for this library (may be null)
     */
    public void onSourceSaved(String library, String objectName, String extension,
                              String newSource, NaturalDependencyGraph graph) {
        // Update memory + H2 + Lucene
        cacheSource(library, objectName, extension, newSource);

        // Incrementally update the dependency graph
        if (graph != null) {
            final String lib = library;
            final String obj = objectName;
            final String src = newSource;
            indexExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Re-analyze this single source
                        graph.addSource(lib, obj, src);
                        graph.build(); // rebuild passive XRefs

                        // Persist updated graph to Lucene
                        LuceneDependencyIndex.getInstance().storeGraph(graph);

                        LOG.info("[NdvCache] Graph updated after save: " + obj + " in " + lib);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "[NdvCache] Graph update failed after save", e);
                    }
                }
            });
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Background prefetch (like WikiPrefetchService)
    // ═══════════════════════════════════════════════════════════

    /**
     * Callback for prefetch progress reporting.
     */
    public interface PrefetchCallback {
        void onProgress(int current, int total, String objectName);
        void onComplete(int total, int indexed);
        void onError(String message);
    }

    /**
     * Start background prefetching of all sources in a library.
     * Downloads each source, caches in H2, indexes in Lucene.
     * Skips objects that are already in the memory cache (recently downloaded).
     * <p>
     * This is idempotent — calling it again for the same library is a no-op if
     * a prefetch is already running.
     *
     * @param library  library name
     * @param objects  list of objects in the library (from listObjects)
     * @param callback optional progress callback (called on background thread)
     */
    public void prefetchLibrary(final String library, final List<NdvObjectInfo> objects,
                                final PrefetchCallback callback) {
        if (ndvService == null) {
            LOG.warning("[NdvCache] Cannot prefetch: NdvService not set");
            return;
        }

        final String libUpper = library.toUpperCase();

        // Skip if already running
        Future<?> existing = activePrefetches.get(libUpper);
        if (existing != null && !existing.isDone()) {
            LOG.fine("[NdvCache] Prefetch already running for: " + libUpper);
            return;
        }

        Future<?> future = prefetchPool.submit(new Runnable() {
            @Override
            public void run() {
                int total = objects.size();
                int indexed = 0;
                int skipped = 0;

                for (int i = 0; i < total; i++) {
                    if (Thread.currentThread().isInterrupted()) break;

                    NdvObjectInfo obj = objects.get(i);
                    if (!isNaturalSourceType(obj)) {
                        skipped++;
                        continue;
                    }

                    String key = cacheKey(libUpper, obj.getName());

                    // Skip if already in memory cache (recently opened/downloaded)
                    if (memoryCache.containsKey(key)) {
                        skipped++;
                        continue;
                    }

                    try {
                        if (callback != null) {
                            callback.onProgress(i + 1, total, obj.getName());
                        }

                        String source = ndvService.readSource(libUpper, obj);
                        if (source != null && !source.isEmpty()) {
                            cacheSource(libUpper, obj.getName(), obj.getTypeExtension(), source);
                            indexed++;
                        }

                        // Small pause to avoid starving the NDV connection for interactive use
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[NdvCache] Prefetch failed for " + obj.getName(), e);
                    }
                }

                activePrefetches.remove(libUpper);

                if (callback != null) {
                    callback.onComplete(total, indexed);
                }

                LOG.info("[NdvCache] Prefetch complete for " + libUpper
                        + ": indexed=" + indexed + ", skipped=" + skipped + ", total=" + total);
            }
        });

        activePrefetches.put(libUpper, future);
    }

    /**
     * Cancel a running prefetch for a library.
     */
    public void cancelPrefetch(String library) {
        if (library == null) return;
        Future<?> future = activePrefetches.remove(library.toUpperCase());
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * Check if a prefetch is running for a library.
     */
    public boolean isPrefetching(String library) {
        if (library == null) return false;
        Future<?> future = activePrefetches.get(library.toUpperCase());
        return future != null && !future.isDone();
    }

    // ═══════════════════════════════════════════════════════════
    //  Stats / queries
    // ═══════════════════════════════════════════════════════════

    /**
     * Get number of cached sources in memory for a library.
     */
    public int getMemoryCacheSize(String library) {
        if (library == null) return 0;
        String prefix = library.toUpperCase() + "/";
        int count = 0;
        for (String key : memoryCache.keySet()) {
            if (key.startsWith(prefix)) count++;
        }
        return count;
    }

    /**
     * Get total memory cache size.
     */
    public int getTotalMemoryCacheSize() {
        return memoryCache.size();
    }

    /**
     * Get a summary string for display in UI.
     */
    public String getSummary(String library) {
        int mem = getMemoryCacheSize(library);
        boolean prefetching = isPrefetching(library);
        LuceneDependencyIndex depIdx = LuceneDependencyIndex.getInstance();
        boolean hasGraph = depIdx.hasLibrary(library);
        return String.format("Cache: %d Quellen im Speicher%s%s",
                mem,
                prefetching ? " (Indizierung läuft…)" : "",
                hasGraph ? " | Graph ✓" : "");
    }

    // ═══════════════════════════════════════════════════════════
    //  Shutdown
    // ═══════════════════════════════════════════════════════════

    /**
     * Shut down background threads. Called when the connection tab closes.
     */
    public void shutdown() {
        // Cancel all running prefetches
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

    private void persistToH2(String url, String title, String extension, String sourceText) {
        try {
            CacheRepository repo = CacheRepository.getInstance();

            // Check if already exists → update
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
            entry.setContentLength(sourceText.length());
            entry.setFileSizeBytes(sourceText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            entry.setCrawlTimestamp(System.currentTimeMillis());
            entry.setStatus(ArchiveEntryStatus.INDEXED);
            entry.setSourceId("NDV");

            // Store metadata
            Map<String, String> meta = entry.getMetadata();
            if (meta == null) {
                meta = new HashMap<String, String>();
                entry.setMetadata(meta);
            }
            meta.put("source_type", "NDV");
            meta.put("extension", extension != null ? extension : "");

            repo.save(entry);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[NdvCache] H2 persist failed for: " + url, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal: Lucene RAG indexing
    // ═══════════════════════════════════════════════════════════

    private void indexInLucene(String docId, String title, String sourceText,
                              String library, String objectName, String extension) {
        try {
            RagService rag = RagService.getInstance();

            // Remove old version first (if re-indexing)
            if (rag.isIndexed(docId)) {
                rag.removeDocument(docId);
            }

            // Build a Document using the immutable Builder pattern
            de.bund.zrb.ingestion.model.document.DocumentMetadata metadata =
                    de.bund.zrb.ingestion.model.document.DocumentMetadata.builder()
                            .sourceName(title)
                            .mimeType(mimeTypeForExtension(extension))
                            .attribute("source_type", "NDV")
                            .attribute("library", library.toUpperCase())
                            .attribute("object", objectName.toUpperCase())
                            .build();

            de.bund.zrb.ingestion.model.document.Document document =
                    de.bund.zrb.ingestion.model.document.Document.builder()
                            .metadata(metadata)
                            .heading(2, objectName.toUpperCase() + " [" + library.toUpperCase() + "]")
                            .code("natural", sourceText)
                            .build();

            // Index without embeddings (fast, BM25-only for now)
            rag.indexDocument(docId, title, document, false);

            LOG.fine("[NdvCache] Indexed in Lucene: " + docId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[NdvCache] Lucene indexing failed for: " + docId, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════

    private static String mimeTypeForExtension(String ext) {
        if (ext == null) return "text/plain";
        String upper = ext.toUpperCase();
        if (upper.equals("NSP")) return "text/x-natural-program";
        if (upper.equals("NSS")) return "text/x-natural-subprogram";
        if (upper.equals("NSN")) return "text/x-natural-subroutine";
        if (upper.equals("NSH")) return "text/x-natural-helproutine";
        if (upper.equals("NSC")) return "text/x-natural-copycode";
        if (upper.equals("NSL")) return "text/x-natural-lda";
        if (upper.equals("NSA")) return "text/x-natural-pda";
        if (upper.equals("NSG")) return "text/x-natural-gda";
        if (upper.equals("NSM")) return "text/x-natural-map";
        if (upper.equals("NS4")) return "text/x-natural-function";
        if (upper.equals("NSD")) return "text/x-natural-dialog";
        return "text/x-natural";
    }

    private static boolean isNaturalSourceType(NdvObjectInfo objInfo) {
        String ext = objInfo.getTypeExtension();
        if (ext == null) return true;
        String upper = ext.toUpperCase();
        return upper.startsWith("NS") || upper.equals("NAT");
    }
}

