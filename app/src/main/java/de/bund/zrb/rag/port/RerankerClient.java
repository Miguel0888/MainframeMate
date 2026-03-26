package de.bund.zrb.rag.port;

import java.util.List;

/**
 * Port interface for cross-encoder reranking (Stage 3 of the RAG pipeline).
 *
 * <h3>3-Stage RAG Architecture</h3>
 * <pre>
 *   Stage 1 — BM25 (Lucene):   Keyword matching, very fast, catches exact terms
 *   Stage 2 — Vectors (HNSW):  Semantic similarity via bi-encoder embeddings,
 *                               acts as a "magnet" pulling the ~50 most promising
 *                               candidates from millions of chunks in milliseconds
 *   Stage 3 — Reranker:        Cross-encoder "magnifying glass" that reads the raw
 *                               text of each candidate jointly with the query,
 *                               producing highly accurate relevance scores
 * </pre>
 *
 * <p>The key insight: <b>vectors are only for pre-selection</b> (speed).
 * The reranker does <b>not</b> work with vectors — it works with the raw text
 * of the candidate passages. It scores each (query, passage) pair independently
 * using a cross-encoder model, yielding dramatically better relevance than
 * bi-encoder cosine similarity, but at higher per-item cost.
 *
 * <p>This is why you need both:
 * <ul>
 *   <li><b>Vectors</b> make the system <em>scalable</em> (speed — milliseconds over millions)</li>
 *   <li><b>Reranking</b> makes the system <em>intelligent</em> (precision — the right 3 out of 50)</li>
 * </ul>
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
