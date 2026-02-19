package de.bund.zrb.rag.infrastructure;

import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.port.SemanticIndex;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Simple in-memory semantic index using brute-force cosine similarity.
 * For production, this should be replaced with hnswlib-core for better performance.
 *
 * This implementation is suitable for small to medium document collections (< 10,000 chunks).
 */
public class InMemorySemanticIndex implements SemanticIndex {

    private static final Logger LOG = Logger.getLogger(InMemorySemanticIndex.class.getName());

    private final Map<String, ChunkWithEmbedding> index = new ConcurrentHashMap<>();
    private final Map<String, List<String>> documentChunks = new ConcurrentHashMap<>();
    private int dimension = 0;

    @Override
    public synchronized void indexChunk(Chunk chunk, float[] embedding) {
        if (chunk == null || embedding == null || embedding.length == 0) {
            return;
        }

        // Set dimension from first embedding
        if (dimension == 0) {
            dimension = embedding.length;
        }

        // Normalize the embedding
        float[] normalized = normalize(embedding);

        index.put(chunk.getChunkId(), new ChunkWithEmbedding(chunk, normalized));

        // Track chunks per document
        documentChunks.computeIfAbsent(chunk.getDocumentId(), k -> new ArrayList<>())
                .add(chunk.getChunkId());

        LOG.fine("Indexed chunk: " + chunk.getChunkId() + " (dim=" + embedding.length + ")");
    }

    @Override
    public List<ScoredChunk> search(float[] queryEmbedding, int topM) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || index.isEmpty()) {
            return Collections.emptyList();
        }

        // Normalize query embedding
        float[] normalizedQuery = normalize(queryEmbedding);

        // Calculate cosine similarity with all indexed chunks
        List<ScoredChunk> results = new ArrayList<>();

        for (ChunkWithEmbedding entry : index.values()) {
            float similarity = cosineSimilarity(normalizedQuery, entry.embedding);
            results.add(new ScoredChunk(entry.chunk, similarity, ScoredChunk.ScoreSource.SEMANTIC));
        }

        // Sort by similarity (highest first)
        Collections.sort(results);

        // Return top-M
        return results.size() > topM ? results.subList(0, topM) : results;
    }

    @Override
    public Chunk getChunk(String chunkId) {
        ChunkWithEmbedding entry = index.get(chunkId);
        return entry != null ? entry.chunk : null;
    }

    @Override
    public synchronized void removeDocument(String documentId) {
        List<String> chunkIds = documentChunks.remove(documentId);
        if (chunkIds != null) {
            for (String chunkId : chunkIds) {
                index.remove(chunkId);
            }
            LOG.info("Removed " + chunkIds.size() + " chunks for document: " + documentId);
        }
    }

    @Override
    public synchronized void clear() {
        index.clear();
        documentChunks.clear();
        dimension = 0;
        LOG.info("Semantic index cleared");
    }

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    /**
     * Normalize a vector to unit length.
     */
    private float[] normalize(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm == 0) {
            return vector;
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }

    /**
     * Calculate cosine similarity between two normalized vectors.
     */
    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }

        float dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    private static class ChunkWithEmbedding {
        final Chunk chunk;
        final float[] embedding;

        ChunkWithEmbedding(Chunk chunk, float[] embedding) {
            this.chunk = chunk;
            this.embedding = embedding;
        }
    }
}

