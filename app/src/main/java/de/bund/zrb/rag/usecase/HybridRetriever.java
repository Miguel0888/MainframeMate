package de.bund.zrb.rag.usecase;

import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.port.EmbeddingClient;
import de.bund.zrb.rag.port.LexicalIndex;
import de.bund.zrb.rag.port.SemanticIndex;

import java.util.*;
import java.util.logging.Logger;

/**
 * Hybrid retriever that combines lexical (BM25) and semantic (embedding) search.
 */
public class HybridRetriever {

    private static final Logger LOG = Logger.getLogger(HybridRetriever.class.getName());

    private final LexicalIndex lexicalIndex;
    private final SemanticIndex semanticIndex;
    private final EmbeddingClient embeddingClient;
    private final RagConfig config;

    public HybridRetriever(LexicalIndex lexicalIndex, SemanticIndex semanticIndex,
                          EmbeddingClient embeddingClient, RagConfig config) {
        this.lexicalIndex = lexicalIndex;
        this.semanticIndex = semanticIndex;
        this.embeddingClient = embeddingClient;
        this.config = config;
    }

    /**
     * Retrieve top-K chunks for a query.
     */
    public List<ScoredChunk> retrieve(String query) {
        return retrieve(query, config.getFinalTopK(), null);
    }

    /**
     * Retrieve top-K chunks for a query.
     */
    public List<ScoredChunk> retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    /**
     * Retrieve top-K chunks for a query, filtered by allowed document IDs.
     * This is the core method that supports attachment-based filtering.
     *
     * @param query the search query
     * @param topK maximum number of results
     * @param allowedDocumentIds if non-null/non-empty, only return chunks from these documents
     * @return scored chunks
     */
    public List<ScoredChunk> retrieve(String query, int topK, Set<String> allowedDocumentIds) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();

        List<ScoredChunk> lexicalResults = new ArrayList<>();
        List<ScoredChunk> semanticResults = new ArrayList<>();

        boolean useLexical = lexicalIndex != null && lexicalIndex.isAvailable() && lexicalIndex.size() > 0;
        boolean useSemantic = semanticIndex != null && semanticIndex.isAvailable() && semanticIndex.size() > 0
                && embeddingClient != null && embeddingClient.isAvailable();

        // Fallback handling
        RagConfig.FallbackMode mode = config.getFallbackMode();
        if (mode == RagConfig.FallbackMode.LEXICAL_ONLY) {
            useSemantic = false;
        } else if (mode == RagConfig.FallbackMode.SEMANTIC_ONLY) {
            useLexical = false;
        }

        // When filtering, fetch more results to compensate for filtering loss
        int fetchMultiplier = (allowedDocumentIds != null && !allowedDocumentIds.isEmpty()) ? 3 : 1;

        // Lexical search
        if (useLexical) {
            try {
                lexicalResults = lexicalIndex.search(query, config.getLuceneTopN() * fetchMultiplier);
                // Apply document filter
                if (allowedDocumentIds != null && !allowedDocumentIds.isEmpty()) {
                    lexicalResults = filterByDocumentIds(lexicalResults, allowedDocumentIds);
                }
                LOG.fine("Lexical search returned " + lexicalResults.size() + " results (after filter)");
            } catch (Exception e) {
                LOG.warning("Lexical search failed: " + e.getMessage());
            }
        }

        // Semantic search
        if (useSemantic) {
            try {
                float[] queryEmbedding = embeddingClient.embed(query);
                if (queryEmbedding != null && queryEmbedding.length > 0) {
                    semanticResults = semanticIndex.search(queryEmbedding, config.getHnswTopM() * fetchMultiplier);
                    // Apply document filter
                    if (allowedDocumentIds != null && !allowedDocumentIds.isEmpty()) {
                        semanticResults = filterByDocumentIds(semanticResults, allowedDocumentIds);
                    }
                    LOG.fine("Semantic search returned " + semanticResults.size() + " results (after filter)");
                }
            } catch (Exception e) {
                LOG.warning("Semantic search failed: " + e.getMessage());
            }
        }

        // Merge and score
        List<ScoredChunk> merged;
        if (lexicalResults.isEmpty() && semanticResults.isEmpty()) {
            return Collections.emptyList();
        } else if (lexicalResults.isEmpty()) {
            merged = semanticResults;
        } else if (semanticResults.isEmpty()) {
            merged = lexicalResults;
        } else {
            merged = mergeAndScore(lexicalResults, semanticResults);
        }

        // Sort by score and take top-K
        Collections.sort(merged);
        List<ScoredChunk> topResults = merged.size() > topK ? merged.subList(0, topK) : merged;

        long duration = System.currentTimeMillis() - startTime;
        LOG.info(String.format("Hybrid retrieval: %d results in %dms (lexical=%d, semantic=%d)",
                topResults.size(), duration, lexicalResults.size(), semanticResults.size()));

        return new ArrayList<>(topResults);
    }

    /**
     * Filter scored chunks to only include those from allowed document IDs.
     */
    private List<ScoredChunk> filterByDocumentIds(List<ScoredChunk> chunks, Set<String> allowedDocumentIds) {
        List<ScoredChunk> filtered = new ArrayList<>();
        for (ScoredChunk sc : chunks) {
            if (sc.getChunk() != null && allowedDocumentIds.contains(sc.getChunk().getDocumentId())) {
                filtered.add(sc);
            }
        }
        return filtered;
    }

    private List<ScoredChunk> mergeAndScore(List<ScoredChunk> lexical, List<ScoredChunk> semantic) {
        Map<String, MergedScore> scoreMap = new HashMap<>();

        // Normalize lexical scores
        float maxLexical = 0;
        for (ScoredChunk sc : lexical) {
            if (sc.getScore() > maxLexical) maxLexical = sc.getScore();
        }

        // Normalize semantic scores (cosine similarity is already 0-1)
        float maxSemantic = 0;
        for (ScoredChunk sc : semantic) {
            if (sc.getScore() > maxSemantic) maxSemantic = sc.getScore();
        }

        // Add lexical scores
        for (ScoredChunk sc : lexical) {
            float normalized = maxLexical > 0 ? sc.getScore() / maxLexical : 0;
            scoreMap.put(sc.getChunkId(), new MergedScore(sc.getChunk(), normalized, 0));
        }

        // Add/merge semantic scores
        for (ScoredChunk sc : semantic) {
            float normalized = maxSemantic > 0 ? sc.getScore() / maxSemantic : 0;
            MergedScore existing = scoreMap.get(sc.getChunkId());
            if (existing != null) {
                existing.semanticScore = normalized;
            } else {
                scoreMap.put(sc.getChunkId(), new MergedScore(sc.getChunk(), 0, normalized));
            }
        }

        // Calculate final scores
        List<ScoredChunk> result = new ArrayList<>();
        float wLex = config.getWeightLexical();
        float wSem = config.getWeightSemantic();

        for (MergedScore ms : scoreMap.values()) {
            float finalScore = wLex * ms.lexicalScore + wSem * ms.semanticScore;
            result.add(new ScoredChunk(ms.chunk, finalScore, ScoredChunk.ScoreSource.HYBRID));
        }

        return result;
    }

    /**
     * Get retrieval statistics.
     */
    public RetrievalStats getStats() {
        return new RetrievalStats(
                lexicalIndex != null ? lexicalIndex.size() : 0,
                semanticIndex != null ? semanticIndex.size() : 0,
                lexicalIndex != null && lexicalIndex.isAvailable(),
                semanticIndex != null && semanticIndex.isAvailable()
        );
    }

    private static class MergedScore {
        final Chunk chunk;
        float lexicalScore;
        float semanticScore;

        MergedScore(Chunk chunk, float lexicalScore, float semanticScore) {
            this.chunk = chunk;
            this.lexicalScore = lexicalScore;
            this.semanticScore = semanticScore;
        }
    }

    public static class RetrievalStats {
        public final int lexicalSize;
        public final int semanticSize;
        public final boolean lexicalAvailable;
        public final boolean semanticAvailable;

        public RetrievalStats(int lexicalSize, int semanticSize, boolean lexicalAvailable, boolean semanticAvailable) {
            this.lexicalSize = lexicalSize;
            this.semanticSize = semanticSize;
            this.lexicalAvailable = lexicalAvailable;
            this.semanticAvailable = semanticAvailable;
        }
    }
}

