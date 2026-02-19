package de.bund.zrb.rag.port;

import java.util.List;

/**
 * Port interface for embedding generation.
 */
public interface EmbeddingClient {

    /**
     * Generate an embedding for a single text.
     *
     * @param text the text to embed
     * @return the embedding vector
     */
    float[] embed(String text);

    /**
     * Generate embeddings for multiple texts.
     *
     * @param texts the texts to embed
     * @return list of embedding vectors
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * Get the embedding dimension.
     */
    int getDimension();

    /**
     * Check if the embedding service is available.
     */
    boolean isAvailable();

    /**
     * Get the model name being used.
     */
    String getModelName();
}

