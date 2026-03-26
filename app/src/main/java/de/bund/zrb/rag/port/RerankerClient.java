package de.bund.zrb.rag.port;

import java.util.List;

/**
 * Port interface for cross-encoder reranking (Stage 3 of the RAG pipeline).
 ** <h3>Pipeline Architecture (Google-style)</h3>
 * <pre>
 *   Stage 1+2 — Hybrid Search:   BM25 keywords + Embedding vectors (semantic).
 *                                 Embeddings widen the search: "Auto" finds "KFZ".
 *                                 → ~50 candidate passages
 *   Stage 3   — Reranking:       Cross-encoder scores each (query, passage) pair
 *                                 on RAW TEXT. REPLACES BM25 scoring with much
 *                                 more accurate relevance scores.
 *                                 → top 3–5 results
 *   Stage 4   — LLM Generation:  (optional) Summarize top results into an answer.
 * </pre>
 *
 * <p><b>Prerequisite:</b> Embeddings (Stage 2) MUST be active before reranking
 * is useful. Without semantic search, the candidate set only contains keyword
 * matches, making cross-encoder reranking pointless. The reranker is NOT a
 * replacement for embeddings — it is a replacement for BM25 <em>scoring</em>.
 *
 * <p>Stages 1–3 run on <b>CPU only</b>. Only Stage 4 requires GPU/cloud.
 */
public interface RerankerClient {

    /**
     * Score each passage against the query using a cross-encoder model.
     *
     * <p>The reranker receives the raw text of each passage (not vectors!)
     * and processes each (query, passage) pair jointly through the cross-encoder.
     *
     * @param query    the user's search query
     * @param passages the raw text passages to score (loaded from the index by chunk ID)
     * @return relevance scores in the same order as {@code passages};
     *         higher values indicate more relevance. Scores are typically
     *         in [0, 1] but may exceed that range depending on the model.
     * @throws RerankerException if the reranking request fails
     */
    float[] rerank(String query, List<String> passages) throws RerankerException;

    /**
     * Check if the reranker service is available and configured.
     */
    boolean isAvailable();

    /**
     * Get a human-readable description of the reranker (model name + endpoint).
     */
    String getDescription();

    /**
     * How many candidates from Stage 1+2 should be sent to the reranker.
     * The hybrid retriever fetches this many top results from BM25+Vector merge,
     * then sends their <em>raw text</em> to the reranker for precise scoring.
     *
     * <p>Typical value: 25–50. Must be ≥ {@link #getTopN()}.
     */
    int getCandidatePoolSize();

    /**
     * How many final results to keep after reranking.
     * The reranker scores {@link #getCandidatePoolSize()} candidates,
     * then only the best {@code topN} are returned to the caller.
     *
     * <p>Typical value: 3–5.
     */
    int getTopN();

    /**
     * Minimum relevance score for a reranked result to be included.
     * Chunks scoring below this threshold are discarded even if they
     * fall within the {@link #getTopN()} limit. 0.0 disables filtering.
     */
    float getScoreThreshold();

    /**
     * Exception thrown when a reranking request fails.
     */
    class RerankerException extends Exception {
        public RerankerException(String message) {
            super(message);
        }

        public RerankerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
