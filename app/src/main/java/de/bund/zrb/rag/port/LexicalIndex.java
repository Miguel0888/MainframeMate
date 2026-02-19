package de.bund.zrb.rag.port;

import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;

import java.util.List;

/**
 * Port interface for lexical (BM25) search index.
 */
public interface LexicalIndex {

    /**
     * Index a chunk.
     */
    void indexChunk(Chunk chunk);

    /**
     * Index multiple chunks.
     */
    void indexChunks(List<Chunk> chunks);

    /**
     * Search for chunks matching the query.
     *
     * @param query the search query
     * @param topN maximum number of results
     * @return scored chunks
     */
    List<ScoredChunk> search(String query, int topN);

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
}

