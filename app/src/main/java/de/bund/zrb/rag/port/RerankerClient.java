package de.bund.zrb.rag.port;

import java.util.List;

/**
 * Port interface for cross-encoder reranking.
 *
 * <p>A reranker takes a query and a list of text passages and returns
 * a relevance score for each (query, passage) pair. Unlike bi-encoder
 * embeddings, which encode query and passage independently, a cross-encoder
 * processes both together, yielding significantly better relevance estimates
 * at the cost of higher latency.
 *
 * <p>Typical usage in the RAG pipeline:
 * <ol>
 *   <li>Retrieve a large candidate set (e.g. 50–100 chunks) via BM25 + semantic search</li>
 *   <li>Rerank the candidates with this client</li>
 *   <li>Take the top-N highest-scoring chunks for LLM context injection</li>
 * </ol>
 */
public interface RerankerClient {

    /**
     * Score each passage against the query using a cross-encoder model.
     *
     * @param query    the search query
     * @param passages the text passages to score (order matches index in result)
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
