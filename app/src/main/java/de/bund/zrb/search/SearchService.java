package de.bund.zrb.search;

import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.port.RerankerClient;
import de.bund.zrb.rag.service.RagService;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central search service that queries the Lucene index across all source types
 * and dispatches live backend searches in parallel.
 *
 * <p>The cache is <b>transparent</b>: every cached entry is found under its
 * real source type (WIKI, CONFLUENCE, FTP, …), not as a separate "Archive".
 * Connected backends supplement the Lucene index with live API search results
 * so that even non-indexed content can be discovered.
 *
 * <h3>Search pipeline</h3>
 * <ol>
 *   <li>Lucene BM25 full-text search (covers all indexed/cached content)</li>
 *   <li>Optional RAG hybrid search (BM25 + embeddings)</li>
 *   <li>Parallel live backend searches via registered {@link BackendSearchProvider}s</li>
 *   <li>Merge, deduplicate, sort by score</li>
 * </ol>
 */
public class SearchService {

    private static final Logger LOG = Logger.getLogger(SearchService.class.getName());

    private static volatile SearchService instance;

    private final ExecutorService executor;

    /** Registered live-search backend providers (one per SourceType). */
    private final ConcurrentHashMap<SearchResult.SourceType, BackendSearchProvider> backendProviders =
            new ConcurrentHashMap<SearchResult.SourceType, BackendSearchProvider>();

    /** Warnings from the last search (e.g. "Mail: not yet implemented"). Thread-confined to caller. */
    private volatile List<String> lastSearchWarnings = Collections.emptyList();

    private SearchService() {
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "SearchWorker");
            t.setDaemon(true);
            return t;
        });
        // Auto-register singleton backend providers
        registerBackendSearchProvider(
                de.bund.zrb.search.provider.WikiSearchProvider.getInstance());
        registerBackendSearchProvider(
                de.bund.zrb.search.provider.ConfluenceSearchProvider.getInstance());
    }

    public static synchronized SearchService getInstance() {
        if (instance == null) {
            instance = new SearchService();
        }
        return instance;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Backend provider registry
    // ═══════════════════════════════════════════════════════════════

    /**
     * Register a live backend search provider for a specific source type.
     * Replaces any previously registered provider for that type.
     */
    public void registerBackendSearchProvider(BackendSearchProvider provider) {
        if (provider != null && provider.getSourceType() != null) {
            backendProviders.put(provider.getSourceType(), provider);
            LOG.info("[Search] Registered backend search provider: " + provider.getSourceType());
        }
    }

    /**
     * Unregister a live backend search provider.
     */
    public void unregisterBackendSearchProvider(SearchResult.SourceType type) {
        if (type != null) {
            backendProviders.remove(type);
        }
    }

    /**
     * Get warnings from the last search (e.g. "not yet implemented" for certain backends).
     */
    public List<String> getLastSearchWarnings() {
        return lastSearchWarnings;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Search API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Search across all enabled sources.
     *
     * @param query       the search query
     * @param sources     which source types to search
     * @param maxResults  max results per source
     * @param useRag      whether to use RAG hybrid search (BM25 + embeddings)
     * @param networkZone network zone filter: "INTERN", "EXTERN", or null for all
     * @param onResult    callback invoked for each batch of results (on calling thread context)
     * @return future that completes when all sources are searched
     */
    public Future<List<SearchResult>> searchAsync(String query, Set<SearchResult.SourceType> sources,
                                                    int maxResults, boolean useRag, String networkZone,
                                                    Consumer<List<SearchResult>> onResult) {
        return executor.submit(() -> {
            List<String> warnings = new ArrayList<String>();
            List<SearchResult> allResults = new ArrayList<>();

            // ── 1. Lucene BM25 (finds ALL indexed/cached documents) ──
            try {
                List<SearchResult> lexResults = searchLucene(query, maxResults);
                if (!lexResults.isEmpty()) {
                    List<SearchResult> tagged = tagBySource(lexResults, sources);
                    allResults.addAll(tagged);
                    if (onResult != null) onResult.accept(tagged);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Search] Lucene search failed", e);
            }

            // ── 2. Hybrid Search (BM25 + Embeddings) ──
            // Embeddings widen the search net: "Auto" also finds "KFZ", "Wagen".
            // This is OPTIONAL — the reranker works on BM25-only candidates too.
            // When active, semantic results are merged into the candidate pool.
            if (isSemanticAvailable()) {
                try {
                    List<SearchResult> semResults = searchRag(query, maxResults);
                    if (!semResults.isEmpty()) {
                        List<SearchResult> tagged = tagBySource(semResults, sources);
                        mergeSemanticInto(allResults, tagged);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Search] Semantic search failed", e);
                }
            }

            // ── 3. Parallel live backend searches ──
            List<Future<List<SearchResult>>> liveFutures = new ArrayList<Future<List<SearchResult>>>();
            final List<SearchResult.SourceType> liveTypes = new ArrayList<SearchResult.SourceType>();
            final String zone = networkZone; // effectively final for lambda

            for (final SearchResult.SourceType src : sources) {
                // Skip types that don't have live backends or are not yet implemented
                if (src == SearchResult.SourceType.RAG) continue;
                // ARCHIVE is no longer a separate search path — it's transparent
                if (src == SearchResult.SourceType.ARCHIVE) continue;

                // Check for "not yet implemented" backends
                if (src == SearchResult.SourceType.MAIL) {
                    // Mail is indexed via Lucene (MailService → MailIndexUpdater → RagService)
                    // so MAIL results come from the Lucene BM25 path above.
                    // Trigger freshness check to ensure index is up-to-date before search.
                    try {
                        de.bund.zrb.mail.service.MailService.getInstance().freshnessCheckBeforeSearch();
                    } catch (Exception ignored) {}
                    continue;
                }
                if (src == SearchResult.SourceType.SHAREPOINT) {
                    warnings.add("\uD83D\uDCCA SharePoint-Suche: noch nicht implementiert");
                    continue;
                }

                BackendSearchProvider provider = backendProviders.get(src);
                if (provider == null || !provider.isAvailable()) continue;

                liveTypes.add(src);
                final BackendSearchProvider p = provider;
                final int max = maxResults;
                liveFutures.add(executor.submit(new Callable<List<SearchResult>>() {
                    @Override
                    public List<SearchResult> call() throws Exception {
                        return p.search(query, max, zone);
                    }
                }));
            }

            // Collect live results (with timeout per provider)
            for (int i = 0; i < liveFutures.size(); i++) {
                SearchResult.SourceType src = liveTypes.get(i);
                try {
                    List<SearchResult> liveResults = liveFutures.get(i).get(20, TimeUnit.SECONDS);
                    if (liveResults != null && !liveResults.isEmpty()) {
                        // Merge: skip duplicates (same documentId)
                        Set<String> existingIds = new HashSet<String>();
                        for (SearchResult r : allResults) {
                            existingIds.add(r.getDocumentId());
                        }
                        List<SearchResult> newResults = new ArrayList<SearchResult>();
                        for (SearchResult lr : liveResults) {
                            if (!existingIds.contains(lr.getDocumentId())) {
                                newResults.add(lr);
                            }
                        }
                        allResults.addAll(newResults);
                        if (onResult != null && !newResults.isEmpty()) {
                            onResult.accept(newResults);
                        }
                        LOG.fine("[Search] Live " + src + ": " + liveResults.size()
                                + " total, " + newResults.size() + " new");
                    }
                } catch (TimeoutException te) {
                    warnings.add(src.getLabel() + ": Zeitüberschreitung bei Live-Suche");
                    LOG.warning("[Search] Live " + src + " timed out");
                } catch (Exception e) {
                    warnings.add(src.getLabel() + ": " + e.getMessage());
                    LOG.log(Level.WARNING, "[Search] Live " + src + " failed", e);
                }
            }

            lastSearchWarnings = warnings;

            // Sort all results by score
            Collections.sort(allResults);

            // Trim to maxResults
            if (allResults.size() > maxResults) {
                allResults = new ArrayList<>(allResults.subList(0, maxResults));
            }

            // ── 4. Reranking: Re-score ALL merged results (replaces BM25 scoring) ──
            // The reranker works on RAW TEXT, not on vectors! It re-scores each
            // (query, passage) pair through a cross-encoder — REPLACING the BM25
            // scoring with much more accurate relevance scores.
            //
            // Embeddings are NOT required. The reranker can rescore BM25-only
            // candidates just fine. Embeddings only widen the candidate pool.
            //
            // Pipeline: 1 (BM25) [+2 (optional Embeddings)] → 3 (Reranking) → 4 (optional LLM)
            if (isRerankerAvailable()) {
                allResults = rerankFinalResults(allResults, query, maxResults);
            }

            // ── 5. Trigger prefetch of content for live results ──
            triggerPrefetch(allResults);

            return allResults;
        });
    }

    /**
     * Synchronous search – blocks until complete.
     */
    public List<SearchResult> search(String query, Set<SearchResult.SourceType> sources,
                                      int maxResults, boolean useRag, String networkZone) {
        try {
            return searchAsync(query, sources, maxResults, useRag, networkZone, null)
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Search] Search failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * Synchronous search – blocks until complete. Searches all network zones.
     */
    public List<SearchResult> search(String query, Set<SearchResult.SourceType> sources,
                                      int maxResults, boolean useRag) {
        return search(query, sources, maxResults, useRag, null);
    }

    // ─── Internal search methods ───

    /**
     * Trigger background prefetch of content for search results.
     * Dispatches to registered providers that support content preloading.
     */
    private void triggerPrefetch(List<SearchResult> results) {
        if (results == null || results.isEmpty()) return;

        // Group results by source type
        Map<SearchResult.SourceType, List<SearchResult>> byType =
                new HashMap<SearchResult.SourceType, List<SearchResult>>();
        for (SearchResult r : results) {
            if (!byType.containsKey(r.getSource())) {
                byType.put(r.getSource(), new ArrayList<SearchResult>());
            }
            byType.get(r.getSource()).add(r);
        }

        // Dispatch to each provider
        for (Map.Entry<SearchResult.SourceType, List<SearchResult>> entry : byType.entrySet()) {
            BackendSearchProvider provider = backendProviders.get(entry.getKey());
            if (provider != null) {
                try {
                    provider.preloadResults(entry.getValue());
                } catch (Exception e) {
                    LOG.log(Level.FINE, "[Search] Prefetch trigger failed for " + entry.getKey(), e);
                }
            }
        }
    }

    /**
     * Load the full HTML content for a document via its registered backend provider.
     * Used by the SearchTab preview pane to display wiki/confluence content.
     * <p>
     * The provider may return cached (prefetched) content or perform a synchronous
     * priority load on a dedicated thread.
     *
     * @param docId the document ID (e.g. "wiki://siteId/page" or "confluence://pageId")
     * @return HTML content string, or {@code null} if no provider can load it
     */
    public String loadContentHtml(String docId) {
        if (docId == null) return null;
        for (BackendSearchProvider provider : backendProviders.values()) {
            try {
                String html = provider.loadContentHtml(docId);
                if (html != null) return html;
            } catch (Exception e) {
                LOG.log(Level.FINE, "[Search] loadContentHtml failed for provider "
                        + provider.getSourceType(), e);
            }
        }
        return null;
    }

    /**
     * Shutdown all backend providers' prefetch threads.
     * Called on application exit.
     */
    public void shutdownProviders() {
        for (BackendSearchProvider provider : backendProviders.values()) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                LOG.log(Level.FINE, "[Search] Provider shutdown failed: " + provider.getSourceType(), e);
            }
        }
    }

    private List<SearchResult> searchLucene(String query, int maxResults) {
        RagService rag = RagService.getInstance();
        // Direct Lucene search – no semantic/embedding involvement
        List<ScoredChunk> chunks = rag.searchLexicalOnly(query, maxResults * 3);
        return convertChunks(chunks, maxResults, query);
    }

    private List<SearchResult> searchRag(String query, int maxResults) {
        RagService rag = RagService.getInstance();
        // Hybrid search via HybridRetriever (BM25 + Embeddings)
        List<ScoredChunk> chunks = rag.retrieve(query, maxResults * 3);
        return convertChunks(chunks, maxResults, query);
    }

    /**
     * Check if the semantic index has any embeddings worth searching.
     */
    private boolean isSemanticAvailable() {
        try {
            RagService rag = RagService.getInstance();
            // The HybridRetriever checks embeddingClient.isAvailable() internally,
            // but we also need actual data in the semantic index
            return rag.getSemanticIndexSize() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a cross-encoder reranker is configured and available.
     * When true, the search pipeline uses the HybridRetriever (which applies
     * reranking) even when no embeddings are available.
     */
    private boolean isRerankerAvailable() {
        try {
            RagService rag = RagService.getInstance();
            RerankerClient reranker = rag.getRerankerClient();
            return reranker != null && reranker.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Apply cross-encoder reranking to the final merged search results.
     * <p>
     * This re-scores all results (from Lucene, RAG, and live backends) by
     * sending their text snippets to the reranker. This ensures that live
     * backend results (Wiki, Confluence) are also reranked, not just the
     * Lucene-indexed content.
     * <p>
     * Best-effort: if reranking fails, the original results are returned unchanged.
     */
    private List<SearchResult> rerankFinalResults(List<SearchResult> results, String query, int maxResults) {
        if (results == null || results.size() <= 1) return results;

        try {
            RagService rag = RagService.getInstance();
            RerankerClient reranker = rag.getRerankerClient();
            if (reranker == null || !reranker.isAvailable()) return results;

            // Limit candidates to reranker pool size
            int poolSize = Math.min(reranker.getCandidatePoolSize(), results.size());
            List<SearchResult> candidates = results.subList(0, poolSize);

            // Collect text for each candidate
            List<String> passages = new ArrayList<>(candidates.size());
            for (SearchResult r : candidates) {
                // Prefer full text from index, fall back to snippet
                String text = null;
                if (r.getDocumentId() != null) {
                    text = getDocumentText(r.getDocumentId(), 2000);
                }
                if (text == null || text.isEmpty()) {
                    text = r.getSnippet() != null ? r.getSnippet() : "";
                }
                passages.add(text);
            }

            long start = System.currentTimeMillis();
            float[] scores = reranker.rerank(query, passages);
            float threshold = reranker.getScoreThreshold();

            // Rebuild results with reranker scores
            List<SearchResult> reranked = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                float score = (i < scores.length) ? scores[i] : 0f;
                if (threshold > 0f && score < threshold) continue;

                SearchResult original = candidates.get(i);
                reranked.add(new SearchResult(
                        original.getSource(), original.getDocumentId(),
                        original.getDocumentName(), original.getPath(),
                        original.getSnippet(), score,
                        original.getChunkId(), original.getHeading()));
            }

            // Sort by reranker score descending
            Collections.sort(reranked);

            // Take final topN
            int topN = Math.min(reranker.getTopN(), maxResults);
            if (reranked.size() > topN) {
                reranked = new ArrayList<>(reranked.subList(0, topN));
            }

            // Append non-reranked results (beyond pool) for completeness
            if (results.size() > poolSize) {
                reranked.addAll(results.subList(poolSize, results.size()));
            }

            long duration = System.currentTimeMillis() - start;
            LOG.info(String.format("[Search] Final reranking: %d candidates → %d results in %dms",
                    candidates.size(), reranked.size(), duration));
            return reranked;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Search] Final reranking failed, using original order", e);
            return results;
        }
    }

    /**
     * Merge semantic results into the existing result list.
     * If a document appears in both, boost its score.
     * If it only appears in semantic, add it.
     */
    private void mergeSemanticInto(List<SearchResult> existing, List<SearchResult> semantic) {
        // Build lookup by chunkId for existing results
        Map<String, SearchResult> existingByChunk = new LinkedHashMap<>();
        for (SearchResult r : existing) {
            existingByChunk.put(r.getChunkId(), r);
        }

        for (SearchResult sem : semantic) {
            SearchResult lex = existingByChunk.get(sem.getChunkId());
            if (lex != null) {
                // Found in both – boost: replace with higher score
                float boosted = Math.max(lex.getScore(), sem.getScore()) * 1.2f;
                int idx = existing.indexOf(lex);
                if (idx >= 0) {
                    existing.set(idx, new SearchResult(
                            lex.getSource(), lex.getDocumentId(), lex.getDocumentName(),
                            lex.getPath(), lex.getSnippet(), boosted,
                            lex.getChunkId(), lex.getHeading()
                    ));
                }
            } else {
                // Only in semantic – add it
                existing.add(sem);
            }
        }
    }

    private List<SearchResult> convertChunks(List<ScoredChunk> chunks, int maxResults, String query) {
        List<SearchResult> results = new ArrayList<>();

        for (ScoredChunk sc : chunks) {
            if (results.size() >= maxResults) break;

            Chunk chunk = sc.getChunk();
            String docId = chunk.getDocumentId();


            // Determine source typSchluessel from documentId path
            SearchResult.SourceType sourceType = detectSource(docId);

            // Extract snippet with context around query match
            String snippet = extractSnippet(chunk.getText(), query, 200);

            // Extract readable path
            String path = extractPath(docId);
            String name = chunk.getSourceName() != null ? chunk.getSourceName() : extractFilename(docId);

            results.add(new SearchResult(
                    sourceType, docId, name, path,
                    snippet, sc.getScore(),
                    chunk.getChunkId(),
                    chunk.getHeading()
            ));
        }

        return results;
    }

    /**
     * Filter results to only include requested source types.
     */
    private List<SearchResult> tagBySource(List<SearchResult> results, Set<SearchResult.SourceType> allowed) {
        if (allowed == null || allowed.isEmpty()) return results;
        List<SearchResult> filtered = new ArrayList<>();
        for (SearchResult r : results) {
            if (allowed.contains(r.getSource())) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    /**
     * Detect source typSchluessel from document ID / path.
     */
    private SearchResult.SourceType detectSource(String docId) {
        if (docId == null) return SearchResult.SourceType.LOCAL;

        // Mail paths contain # separators (mailbox#folder#nodeId)
        if (docId.contains("#")) return SearchResult.SourceType.MAIL;

        // FTP paths
        if (docId.startsWith("FTP:") || docId.startsWith("ftp://") || docId.startsWith("/")) return SearchResult.SourceType.FTP;

        // NDV
        if (docId.contains("NDV:") || docId.contains("ndv:")) return SearchResult.SourceType.NDV;

        // SharePoint
        if (docId.startsWith("SP:") || docId.startsWith("sp://")) return SearchResult.SourceType.SHAREPOINT;

        // Wiki
        if (docId.startsWith("wiki://")) return SearchResult.SourceType.WIKI;

        // Confluence
        if (docId.startsWith("confluence://")) return SearchResult.SourceType.CONFLUENCE;

        // Default: local
        return SearchResult.SourceType.LOCAL;
    }

    /**
     * Extract a snippet from text around the first occurrence of the query.
     * Shows context before and after the match.
     */
    static String extractSnippet(String text, String query, int maxLen) {
        if (text == null || text.isEmpty()) return "";
        if (query == null || query.isEmpty()) {
            return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
        }

        // Find the query (case-insensitive)
        String lower = text.toLowerCase();
        String queryLower = query.toLowerCase().trim();

        // Try each query word
        String[] words = queryLower.split("\\s+");
        int bestPos = -1;
        for (String word : words) {
            int pos = lower.indexOf(word);
            if (pos >= 0 && (bestPos < 0 || pos < bestPos)) {
                bestPos = pos;
            }
        }

        if (bestPos < 0) {
            // Query not found literally – show start of text
            return text.length() <= maxLen ? cleanSnippet(text) : cleanSnippet(text.substring(0, maxLen)) + "…";
        }

        // Center the snippet around the match
        int start = Math.max(0, bestPos - maxLen / 3);
        int end = Math.min(text.length(), start + maxLen);
        if (start > 0) start = text.indexOf(' ', start); // start at word boundary
        if (start < 0) start = 0;

        String snippet = text.substring(start, end);
        String result = cleanSnippet(snippet);

        if (start > 0) result = "…" + result;
        if (end < text.length()) result = result + "…";

        return result;
    }

    private static String cleanSnippet(String s) {
        // Remove excessive whitespace / newlines for display
        return s.replaceAll("\\s+", " ").trim();
    }

    private String extractPath(String docId) {
        if (docId == null) return "";
        // For mail: show mailbox and folder
        if (docId.contains("#")) {
            String[] parts = docId.split("#", 3);
            String mailbox = parts[0];
            String folder = parts.length > 1 ? parts[1] : "";
            // Shorten mailbox path to filename
            String mbName = mailbox.contains("\\") ? mailbox.substring(mailbox.lastIndexOf('\\') + 1) : mailbox;
            return mbName + " › " + folder;
        }
        return docId;
    }

    private String extractFilename(String path) {
        if (path == null) return "?";
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    /**
     * Get the full text of an indexed document by concatenating all its chunks
     * from the persistent Lucene index. Works for any backend (LOCAL, FTP, NDV, etc.)
     * because the text was extracted during indexing.
     *
     * @param documentId the document ID as stored in the index
     * @param maxLength  max characters to return (0 = unlimited)
     * @return the full document text, or null if not found
     */
    public String getDocumentText(String documentId, int maxLength) {
        if (documentId == null) return null;
        try {
            RagService rag = RagService.getInstance();
            de.bund.zrb.rag.infrastructure.LuceneLexicalIndex index = rag.getLexicalIndex();
            if (index == null) return null;

            List<Chunk> chunks = index.getChunksByDocumentId(documentId);
            if (chunks.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            for (Chunk chunk : chunks) {
                if (chunk.getText() != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(chunk.getText());
                }
                if (maxLength > 0 && sb.length() >= maxLength) {
                    sb.setLength(maxLength);
                    sb.append("\n[... gek\u00fcrzt]");
                    break;
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Search] getDocumentText failed for: " + documentId, e);
            return null;
        }
    }
}
