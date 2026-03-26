package de.bund.zrb.rag.usecase;

import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.port.EmbeddingClient;
import de.bund.zrb.rag.port.LexicalIndex;
import de.bund.zrb.rag.port.RerankerClient;
import de.bund.zrb.rag.port.SemanticIndex;

import java.util.*;
import java.util.logging.Logger;

/**
 * Hybrid retriever implementing a multi-stage RAG retrieval pipeline
 * modelled after Google's search architecture:
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  Stage 1+2 — Hybrid Search (BM25 + Embeddings)                     │
 * │                                                                     │
 * │  BM25 (Lucene):  Keyword matching — fast, catches exact terms.     │
 * │  Embeddings:     Semantic search — "Auto" also finds "KFZ"/"Wagen".│
 * │                  Embeddings widen the search net so that fuzzy      │
 * │                  concepts are discovered, not just exact keywords.  │
 * │  → merged candidate pool (~50 chunks)                              │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  Stage 3 — Scoring / Ranking                                       │
 * │                                                                     │
 * │  Without Reranker: BM25 hybrid score (okay, but imprecise).        │
 * │  With Reranker:    Cross-encoder re-scores each (query, passage)   │
 * │                    pair on RAW TEXT — REPLACES BM25 scoring.        │
 * │                    Much more accurate: the "Google feeling".        │
 * │  → final top 3–5 chunks                                            │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  Stage 4 — (optional) LLM generates answer from top chunks         │
 * │  Many users are already satisfied with the ranked result list       │
 * │  from Stage 3 without needing LLM generation.                      │
 * └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>Key insight:</b> Embeddings and reranking serve <em>different</em> purposes.
 * <ul>
 *   <li><b>Embeddings</b> = finding candidates (semantic widening of the search net)</li>
 *   <li><b>Reranker</b>   = scoring candidates (replaces BM25 scoring with cross-encoder)</li>
 * </ul>
 * Embeddings are a <b>prerequisite</b> for effective reranking — without them,
 * the candidate set only contains keyword matches, making reranking pointless.
 *
 * <p>Stages 1–3 run on <b>CPU only</b>. Only Stage 4 (LLM generation) needs
 * a GPU or cloud provider.
 */
public class HybridRetriever {

    private static final Logger LOG = Logger.getLogger(HybridRetriever.class.getName());

    private final LexicalIndex lexicalIndex;
    private final SemanticIndex semanticIndex;
    private final EmbeddingClient embeddingClient;
    private final RagConfig config;

    /**
     * Optional cross-encoder reranker. When set and available, the retriever
     * fetches a larger candidate pool, then reranks to get the final top-K.
     * This dramatically improves relevance for RAG queries.
     */
    private volatile RerankerClient rerankerClient;

    public HybridRetriever(LexicalIndex lexicalIndex, SemanticIndex semanticIndex,
                          EmbeddingClient embeddingClient, RagConfig config) {
        this.lexicalIndex = lexicalIndex;
        this.semanticIndex = semanticIndex;
        this.embeddingClient = embeddingClient;
        this.config = config;
    }

    /**
     * Set or replace the reranker client. Pass {@code null} to disable reranking.
     */
    public void setRerankerClient(RerankerClient rerankerClient) {
        this.rerankerClient = rerankerClient;
    }

    /**
     * Returns the current reranker client, or {@code null} if none is configured.
     */
    public RerankerClient getRerankerClient() {
        return rerankerClient;
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

        // ══════════════════════════════════════════════════════════════════
        // Build the candidate pool for Stage 3 (Reranker) or final output.
        //
        // When a reranker is active:  keep a larger candidate pool (e.g. 50)
        //   so the cross-encoder has enough material to find the real gems.
        //   The pool size comes from RerankerClient.getCandidatePoolSize().
        //
        // Without reranker:  use the caller's topK directly.
        // ══════════════════════════════════════════════════════════════════
        Collections.sort(merged);
        RerankerClient reranker = this.rerankerClient;
        boolean willRerank = reranker != null && reranker.isAvailable();

        int poolSize;
        if (willRerank) {
            // Use the configured candidate pool size (default 25–50).
            // This is how many text snippets we'll send to the cross-encoder.
            poolSize = Math.max(reranker.getCandidatePoolSize(), topK);
        } else {
            poolSize = topK;
        }
        poolSize = Math.min(poolSize, merged.size());

        List<ScoredChunk> topResults = merged.size() > poolSize
                ? new ArrayList<>(merged.subList(0, poolSize))
                : new ArrayList<>(merged);

        // ══════════════════════════════════════════════════════════════════
        // Stage 3 — Cross-encoder reranking (optional)
        //
        // The reranker does NOT use vectors. It receives the RAW TEXT of
        // each candidate passage together with the user's query and scores
        // each (query, passage) pair jointly through a cross-encoder model
        // (e.g. BAAI/bge-reranker-v2-m3).
        //
        // This "deep reading" produces far more accurate relevance scores
        // than the bi-encoder cosine similarity from Stage 2, because the
        // cross-encoder can attend to fine-grained interactions between
        // query tokens and passage tokens.
        //
        // Flow:
        //   1. Load raw text snippets for the ~50 candidate chunk IDs
        //   2. POST { query, documents: [text1, text2, ...] } to reranker
        //   3. Receive relevance scores per passage
        //   4. Filter by score threshold, sort, take final top-N
        // ══════════════════════════════════════════════════════════════════
        if (willRerank && !topResults.isEmpty()) {
            try {
                long rerankStart = System.currentTimeMillis();

                // Collect the raw text of each candidate — this is what the
                // reranker "reads", not the vectors.
                List<String> passages = new ArrayList<>(topResults.size());
                for (ScoredChunk sc : topResults) {
                    passages.add(sc.getText());
                }

                // Call the cross-encoder reranker API
                float[] rerankScores = reranker.rerank(query, passages);

                // Rebuild scored chunks with the reranker's relevance scores
                List<ScoredChunk> reranked = new ArrayList<>(topResults.size());
                float threshold = reranker.getScoreThreshold();

                for (int i = 0; i < topResults.size(); i++) {
                    float newScore = (i < rerankScores.length) ? rerankScores[i] : 0f;

                    // Apply score threshold: discard passages the reranker
                    // considers irrelevant, even if they were in the pool.
                    if (threshold > 0f && newScore < threshold) {
                        continue;
                    }

                    reranked.add(new ScoredChunk(
                            topResults.get(i).getChunk(),
                            newScore,
                            ScoredChunk.ScoreSource.RERANKED
                    ));
                }

                // Sort by reranker score (highest first)
                Collections.sort(reranked);

                // Take final top-N from the reranker settings.
                // This is typically much smaller than the candidate pool
                // (e.g. 3–5 vs. 50) — only the truly relevant chunks survive.
                int finalTopN = Math.min(reranker.getTopN(), topK);
                if (reranked.size() > finalTopN) {
                    reranked = new ArrayList<>(reranked.subList(0, finalTopN));
                }
                topResults = reranked;

                long rerankDuration = System.currentTimeMillis() - rerankStart;
                LOG.info(String.format(
                        "Stage 3 Reranking: %d candidates (raw text) → %d results in %dms "
                      + "(model=%s, threshold=%.2f)",
                        passages.size(), topResults.size(), rerankDuration,
                        reranker.getDescription(), threshold));
            } catch (Exception e) {
                // Reranking is best-effort — if it fails, fall through with
                // the Stage 1+2 hybrid scores (still useful, just less precise).
                LOG.warning("Stage 3 reranking failed, falling back to hybrid scores: " + e.getMessage());
                // Trim to topK since we kept a larger pool for reranking
                if (topResults.size() > topK) {
                    topResults = new ArrayList<>(topResults.subList(0, topK));
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        LOG.info(String.format(
                "RAG retrieval complete: %d final results in %dms "
              + "(Stage1-BM25=%d, Stage2-Vector=%d, merged=%d%s)",
                topResults.size(), duration,
                lexicalResults.size(), semanticResults.size(), merged.size(),
                willRerank ? ", Stage3-Reranked" : ""));

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

