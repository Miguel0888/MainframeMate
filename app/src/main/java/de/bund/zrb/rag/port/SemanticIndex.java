package de.bund.zrb.rag.port;

import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;

import java.util.List;

/**
 * Port interface for semantic (embedding) search index.
 */
public interface SemanticIndex {

    /**
     * Index a chunk with its embedding.
     *
     * @param chunk the chunk
     * @param embedding the embedding vector
     */
    void indexChunk(Chunk chunk, float[] embedding);

    /**
     * Search for chunks similar to the query embedding.
     *
     * @param queryEmbedding the query embedding vector
     * @param topM maximum number of results
     * @return scored chunks
     */
    List<ScoredChunk> search(float[] queryEmbedding, int topM);

    /**
     * Get a chunk by ID.
     */
    Chunk getChunk(String chunkId);

    /**
     * Remove all chunks for a document.
     */
    void removeDocument(String documentId);

    /**
     * Clear the entire index.
     */
    void clear();

    /**
     * Get the number of indexed chunks.
     */
    int size();

    /**
     * Check if the index is available.
     */
    boolean isAvailable();

    /**
     * Get the embedding dimension.
     */
    int getDimension();
}

