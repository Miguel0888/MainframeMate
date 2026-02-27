package de.bund.zrb.search;

import de.bund.zrb.archive.model.ArchiveDocument;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.service.RagService;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central search service that queries the Lucene index across all source types.
 *
 * Features:
 * - Parallel search across multiple source categories
 * - BM25 full-text search (Lucene)
 * - Optional RAG hybrid search (BM25 + embeddings)
 * - Streaming results via callback (results appear as found)
 * - Snippet extraction with query highlighting
 */
public class SearchService {

    private static final Logger LOG = Logger.getLogger(SearchService.class.getName());

    private static volatile SearchService instance;

    private final ExecutorService executor;

    private SearchService() {
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "SearchWorker");
            t.setDaemon(true);
            return t;
        });
    }

    public static synchronized SearchService getInstance() {
        if (instance == null) {
            instance = new SearchService();
        }
        return instance;
    }

    /**
     * Search across all enabled sources.
     *
     * @param query       the search query
     * @param sources     which source types to search
     * @param maxResults  max results per source
     * @param useRag      whether to use RAG hybrid search (BM25 + embeddings)
     * @param onResult    callback invoked for each batch of results (on calling thread context)
     * @return future that completes when all sources are searched
     */
    public Future<List<SearchResult>> searchAsync(String query, Set<SearchResult.SourceType> sources,
                                                    int maxResults, boolean useRag,
                                                    Consumer<List<SearchResult>> onResult) {
        return executor.submit(() -> {
            List<SearchResult> allResults = new ArrayList<>();

            // Always start with Lucene BM25 (finds ALL indexed documents)
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

            // Additionally run semantic search if embeddings are available
            // Semantic results boost documents that have embeddings;
            // documents without embeddings are simply not in the semantic index
            // and keep their lexical-only score.
            if (isSemanticAvailable()) {
                try {
                    List<SearchResult> semResults = searchRag(query, maxResults);
                    if (!semResults.isEmpty()) {
                        List<SearchResult> tagged = tagBySource(semResults, sources);
                        // Merge: boost scores for docs found in both
                        mergeSemanticInto(allResults, tagged);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Search] Semantic search failed", e);
                }
            }

            // Search the Archive (ArchiveDocuments) if ARCHIVE source is selected
            if (sources.contains(SearchResult.SourceType.ARCHIVE)) {
                try {
                    List<SearchResult> archiveResults = searchArchive(query, maxResults);
                    allResults.addAll(archiveResults);
                    if (onResult != null && !archiveResults.isEmpty()) {
                        onResult.accept(archiveResults);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Search] Archive search failed", e);
                }
            }

            // Sort all results by score
            Collections.sort(allResults);

            // Trim to maxResults
            if (allResults.size() > maxResults) {
                allResults = new ArrayList<>(allResults.subList(0, maxResults));
            }

            return allResults;
        });
    }

    /**
     * Synchronous search – blocks until complete.
     */
    public List<SearchResult> search(String query, Set<SearchResult.SourceType> sources,
                                      int maxResults, boolean useRag) {
        try {
            return searchAsync(query, sources, maxResults, useRag, null).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Search] Search failed", e);
            return Collections.emptyList();
        }
    }

    // ─── Internal search methods ───

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
     * Search the Archive (ArchiveDocuments from H2 database).
     * Converts matches to SearchResult with ARCHIVE source type.
     */
    private List<SearchResult> searchArchive(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            ArchiveRepository repo = ArchiveRepository.getInstance();
            List<ArchiveDocument> docs = repo.searchDocuments(query, null, maxResults);
            for (ArchiveDocument doc : docs) {
                String snippet = doc.getExcerpt() != null ? doc.getExcerpt() : "";
                if (snippet.length() > 300) snippet = snippet.substring(0, 300) + "…";
                String title = doc.getTitle() != null ? doc.getTitle() : "(kein Titel)";
                String host = doc.getHost() != null ? doc.getHost() : "";
                // Score: simple relevance heuristic (title match > excerpt match)
                float score = 1.0f;
                String lq = query.toLowerCase();
                if (title.toLowerCase().contains(lq)) score += 0.5f;
                if (snippet.toLowerCase().contains(lq)) score += 0.3f;

                results.add(new SearchResult(
                        SearchResult.SourceType.ARCHIVE,
                        doc.getDocId(),
                        title,
                        host,
                        snippet,
                        score,
                        doc.getDocId(),
                        doc.getKind()
                ));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Search] Archive search failed", e);
        }
        return results;
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


            // Determine source type from documentId path
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
     * Detect source type from document ID / path.
     */
    private SearchResult.SourceType detectSource(String docId) {
        if (docId == null) return SearchResult.SourceType.LOCAL;

        // Mail paths contain # separators (mailbox#folder#nodeId)
        if (docId.contains("#")) return SearchResult.SourceType.MAIL;

        // FTP paths (hypothetical)
        if (docId.startsWith("ftp://") || docId.startsWith("/")) return SearchResult.SourceType.FTP;

        // NDV
        if (docId.contains("NDV:") || docId.contains("ndv:")) return SearchResult.SourceType.NDV;

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
}
