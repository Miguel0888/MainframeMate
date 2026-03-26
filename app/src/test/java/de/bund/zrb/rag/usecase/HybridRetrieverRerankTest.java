package de.bund.zrb.rag.usecase;

import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.port.EmbeddingClient;
import de.bund.zrb.rag.port.LexicalIndex;
import de.bund.zrb.rag.port.RerankerClient;
import de.bund.zrb.rag.port.SemanticIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the 3-stage RAG pipeline in {@link HybridRetriever}:
 * <ol>
 *   <li>Stage 1 — BM25 (lexical)</li>
 *   <li>Stage 2 — Vectors (semantic)</li>
 *   <li>Stage 3 — Cross-encoder reranker (re-scores raw text, not vectors)</li>
 * </ol>
 */
class HybridRetrieverRerankTest {

    private StubLexicalIndex lexicalIndex;
    private StubSemanticIndex semanticIndex;
    private StubEmbeddingClient embeddingClient;
    private RagConfig config;
    private HybridRetriever retriever;

    @BeforeEach
    void setUp() {
        lexicalIndex = new StubLexicalIndex();
        semanticIndex = new StubSemanticIndex();
        embeddingClient = new StubEmbeddingClient();
        config = new RagConfig()
                .setLuceneTopN(20)
                .setHnswTopM(20)
                .setFinalTopK(5)
                .setWeightLexical(0.5f)
                .setWeightSemantic(0.5f);
        retriever = new HybridRetriever(lexicalIndex, semanticIndex, embeddingClient, config);
    }

    /**
     * Stage 3 reranker uses candidatePoolSize from the client, not all merged results.
     */
    @Test
    void reranker_usesCandidatePoolSizeFromClient() {
        // Create 20 chunks returned by lexical search
        List<ScoredChunk> lexicalResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Chunk chunk = Chunk.builder()
                    .chunkId("c" + i)
                    .documentId("doc1")
                    .text("Content of chunk " + i)
                    .build();
            lexicalResults.add(new ScoredChunk(chunk, 20 - i, ScoredChunk.ScoreSource.LEXICAL));
        }
        lexicalIndex.setResults(lexicalResults);

        // Reranker with candidatePoolSize=10, topN=3
        CountingReranker reranker = new CountingReranker(10, 3, 0.0f);
        retriever.setRerankerClient(reranker);

        List<ScoredChunk> results = retriever.retrieve("test query", 5);

        // The reranker should have received at most 10 passages (candidatePoolSize)
        assertTrue(reranker.lastPassageCount <= 10,
                "Reranker should receive at most candidatePoolSize passages, got: " + reranker.lastPassageCount);
        // Final results should be at most topN=3
        assertTrue(results.size() <= 3,
                "Should return at most topN results, got: " + results.size());
    }

    /**
     * Stage 3 reranker applies scoreThreshold — low-scoring chunks are discarded.
     */
    @Test
    void reranker_appliesScoreThreshold() {
        List<ScoredChunk> lexicalResults = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Chunk chunk = Chunk.builder()
                    .chunkId("c" + i)
                    .documentId("doc1")
                    .text("Chunk " + i)
                    .build();
            lexicalResults.add(new ScoredChunk(chunk, 5 - i, ScoredChunk.ScoreSource.LEXICAL));
        }
        lexicalIndex.setResults(lexicalResults);

        // Reranker returns scores: [0.9, 0.1, 0.8, 0.05, 0.7]
        // With threshold=0.5, only chunks with score >= 0.5 survive (indices 0, 2, 4)
        float[] rerankScores = {0.9f, 0.1f, 0.8f, 0.05f, 0.7f};
        FixedScoreReranker reranker = new FixedScoreReranker(rerankScores, 50, 10, 0.5f);
        retriever.setRerankerClient(reranker);

        List<ScoredChunk> results = retriever.retrieve("test query", 10);

        // Only scores >= 0.5 should survive
        for (ScoredChunk sc : results) {
            assertTrue(sc.getScore() >= 0.5f,
                    "Score should be >= threshold 0.5, got: " + sc.getScore());
        }
        assertEquals(3, results.size(), "3 of 5 chunks should pass the 0.5 threshold");
    }

    /**
     * When no reranker is configured, the hybrid merge produces final results directly.
     */
    @Test
    void noReranker_returnsHybridMergedResults() {
        List<ScoredChunk> lexicalResults = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Chunk chunk = Chunk.builder()
                    .chunkId("c" + i)
                    .documentId("doc1")
                    .text("Chunk " + i)
                    .build();
            lexicalResults.add(new ScoredChunk(chunk, 10 - i, ScoredChunk.ScoreSource.LEXICAL));
        }
        lexicalIndex.setResults(lexicalResults);

        // No reranker set
        List<ScoredChunk> results = retriever.retrieve("test query", 3);

        assertEquals(3, results.size(), "Should return topK=3 results without reranker");
        // All should have HYBRID or LEXICAL source (not RERANKED)
        for (ScoredChunk sc : results) {
            assertNotEquals(ScoredChunk.ScoreSource.RERANKED, sc.getSource());
        }
    }

    /**
     * Reranker failure is graceful — falls back to Stage 1+2 results.
     */
    @Test
    void reranker_failureGracefulFallback() {
        List<ScoredChunk> lexicalResults = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Chunk chunk = Chunk.builder()
                    .chunkId("c" + i)
                    .documentId("doc1")
                    .text("Chunk " + i)
                    .build();
            lexicalResults.add(new ScoredChunk(chunk, 5 - i, ScoredChunk.ScoreSource.LEXICAL));
        }
        lexicalIndex.setResults(lexicalResults);

        // Reranker that always throws
        FailingReranker reranker = new FailingReranker();
        retriever.setRerankerClient(reranker);

        // Should not throw, should fall back to hybrid results
        List<ScoredChunk> results = retriever.retrieve("test query", 3);

        assertFalse(results.isEmpty(), "Should return fallback results when reranker fails");
        assertTrue(results.size() <= 3, "Should respect topK even on fallback");
    }

    /**
     * Reranker results are sorted by cross-encoder score (highest first).
     */
    @Test
    void reranker_sortsByRerankerScore() {
        List<ScoredChunk> lexicalResults = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Chunk chunk = Chunk.builder()
                    .chunkId("c" + i)
                    .documentId("doc1")
                    .text("Chunk " + i)
                    .build();
            // Lexical order: c0 > c1 > c2
            lexicalResults.add(new ScoredChunk(chunk, 3 - i, ScoredChunk.ScoreSource.LEXICAL));
        }
        lexicalIndex.setResults(lexicalResults);

        // Reranker reverses the order: c2 is the most relevant
        float[] rerankScores = {0.1f, 0.5f, 0.9f};
        FixedScoreReranker reranker = new FixedScoreReranker(rerankScores, 50, 10, 0.0f);
        retriever.setRerankerClient(reranker);

        List<ScoredChunk> results = retriever.retrieve("test query", 3);

        assertEquals(3, results.size());
        // Highest reranker score first
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        assertTrue(results.get(1).getScore() >= results.get(2).getScore());
        assertEquals(ScoredChunk.ScoreSource.RERANKED, results.get(0).getSource());
    }

    // ══════════════════════════════════════════════════════════════════
    // Stub implementations
    // ══════════════════════════════════════════════════════════════════

    private static class StubLexicalIndex implements LexicalIndex {
        private List<ScoredChunk> results = Collections.emptyList();

        void setResults(List<ScoredChunk> results) {
            this.results = results;
        }

        @Override
        public List<ScoredChunk> search(String query, int topN) {
            return new ArrayList<>(results.subList(0, Math.min(topN, results.size())));
        }

        @Override public void indexChunk(Chunk chunk) {}
        @Override public void indexChunks(List<Chunk> chunks) {}
        @Override public void removeDocument(String documentId) {}
        @Override public void clear() {}
        @Override public int size() { return results.size(); }
        @Override public boolean isAvailable() { return true; }
    }

    private static class StubSemanticIndex implements SemanticIndex {
        @Override public List<ScoredChunk> search(float[] queryEmbedding, int topN) {
            return Collections.emptyList();
        }
        @Override public void indexChunk(Chunk chunk, float[] embedding) {}
        @Override public Chunk getChunk(String chunkId) { return null; }
        @Override public void removeDocument(String documentId) {}
        @Override public void clear() {}
        @Override public int size() { return 0; }
        @Override public boolean isAvailable() { return false; }
        @Override public int getDimension() { return 0; }
    }

    private static class StubEmbeddingClient implements EmbeddingClient {
        @Override public float[] embed(String text) { return new float[0]; }
        @Override public List<float[]> embedBatch(List<String> texts) { return Collections.emptyList(); }
        @Override public int getDimension() { return 0; }
        @Override public boolean isAvailable() { return false; }
        @Override public String getModelName() { return "stub"; }
    }

    /**
     * Reranker that counts how many passages it receives and returns uniform scores.
     */
    private static class CountingReranker implements RerankerClient {
        private final int candidatePoolSize;
        private final int topN;
        private final float scoreThreshold;
        int lastPassageCount = 0;

        CountingReranker(int candidatePoolSize, int topN, float scoreThreshold) {
            this.candidatePoolSize = candidatePoolSize;
            this.topN = topN;
            this.scoreThreshold = scoreThreshold;
        }

        @Override
        public float[] rerank(String query, List<String> passages) {
            lastPassageCount = passages.size();
            float[] scores = new float[passages.size()];
            for (int i = 0; i < scores.length; i++) {
                scores[i] = 1.0f - (i * 0.01f); // Decreasing scores
            }
            return scores;
        }

        @Override public boolean isAvailable() { return true; }
        @Override public String getDescription() { return "counting-reranker"; }
        @Override public int getCandidatePoolSize() { return candidatePoolSize; }
        @Override public int getTopN() { return topN; }
        @Override public float getScoreThreshold() { return scoreThreshold; }
    }

    /**
     * Reranker that returns fixed scores for testing score threshold and sorting.
     */
    private static class FixedScoreReranker implements RerankerClient {
        private final float[] scores;
        private final int candidatePoolSize;
        private final int topN;
        private final float scoreThreshold;

        FixedScoreReranker(float[] scores, int candidatePoolSize, int topN, float scoreThreshold) {
            this.scores = scores;
            this.candidatePoolSize = candidatePoolSize;
            this.topN = topN;
            this.scoreThreshold = scoreThreshold;
        }

        @Override
        public float[] rerank(String query, List<String> passages) {
            return Arrays.copyOf(scores, passages.size());
        }

        @Override public boolean isAvailable() { return true; }
        @Override public String getDescription() { return "fixed-score-reranker"; }
        @Override public int getCandidatePoolSize() { return candidatePoolSize; }
        @Override public int getTopN() { return topN; }
        @Override public float getScoreThreshold() { return scoreThreshold; }
    }

    /**
     * Reranker that always throws — tests graceful degradation.
     */
    private static class FailingReranker implements RerankerClient {
        @Override
        public float[] rerank(String query, List<String> passages) throws RerankerException {
            throw new RerankerException("Simulated reranker failure");
        }

        @Override public boolean isAvailable() { return true; }
        @Override public String getDescription() { return "failing-reranker"; }
        @Override public int getCandidatePoolSize() { return 50; }
        @Override public int getTopN() { return 5; }
        @Override public float getScoreThreshold() { return 0.0f; }
    }
}
