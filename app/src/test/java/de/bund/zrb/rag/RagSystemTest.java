package de.bund.zrb.rag;

import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.infrastructure.MarkdownChunker;
import de.bund.zrb.rag.infrastructure.InMemorySemanticIndex;
import de.bund.zrb.rag.infrastructure.LuceneLexicalIndex;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.usecase.RagContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RAG system components.
 */
class RagSystemTest {

    private MarkdownChunker chunker;
    private LuceneLexicalIndex lexicalIndex;
    private InMemorySemanticIndex semanticIndex;
    private RagContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        RagConfig config = new RagConfig()
                .setChunkSizeChars(200)
                .setOverlapChars(20);
        chunker = new MarkdownChunker(config);
        lexicalIndex = new LuceneLexicalIndex();
        semanticIndex = new InMemorySemanticIndex();
        contextBuilder = new RagContextBuilder(config);
    }

    // ========== Chunker Tests ==========

    @Test
    void chunker_createsChunksFromText() {
        String text = "This is a test paragraph. It contains some text that should be chunked. " +
                "We need enough text to create multiple chunks for testing purposes.";

        List<Chunk> chunks = chunker.chunk(text, "doc1", "test.txt", "text/plain");

        assertFalse(chunks.isEmpty());
        assertEquals("doc1", chunks.get(0).getDocumentId());
        assertEquals("test.txt", chunks.get(0).getSourceName());
    }

    @Test
    void chunker_respectsMarkdownHeadings() {
        String markdown = "# Title\n\nFirst paragraph.\n\n## Section 1\n\nSection content here.\n\n## Section 2\n\nMore content.";

        List<Chunk> chunks = chunker.chunkMarkdown(markdown, "doc1", "test.md", "text/markdown");

        assertFalse(chunks.isEmpty());
        // Should have chunks for each section
        assertTrue(chunks.size() >= 1);
    }

    @Test
    void chunker_assignsIncrementalPositions() {
        String text = "Paragraph one. ".repeat(50) + "\n\n" + "Paragraph two. ".repeat(50);

        List<Chunk> chunks = chunker.chunk(text, "doc1", "test.txt", "text/plain");

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getPosition());
        }
    }

    @Test
    void chunker_generatesUniqueChunkIds() {
        String text = "Some text that will be chunked. ".repeat(50);

        List<Chunk> chunks = chunker.chunk(text, "doc1", "test.txt", "text/plain");

        long uniqueIds = chunks.stream().map(Chunk::getChunkId).distinct().count();
        assertEquals(chunks.size(), uniqueIds);
    }

    // ========== Lucene Index Tests ==========

    @Test
    void lexicalIndex_indexesAndSearchesChunks() {
        Chunk chunk = Chunk.builder()
                .chunkId("c1")
                .documentId("doc1")
                .text("The quick brown fox jumps over the lazy dog")
                .build();

        lexicalIndex.indexChunk(chunk);

        List<ScoredChunk> results = lexicalIndex.search("quick fox", 10);

        assertFalse(results.isEmpty());
        assertEquals("c1", results.get(0).getChunkId());
        assertEquals(ScoredChunk.ScoreSource.LEXICAL, results.get(0).getSource());
    }

    @Test
    void lexicalIndex_returnsEmptyForNoMatch() {
        Chunk chunk = Chunk.builder()
                .chunkId("c1")
                .documentId("doc1")
                .text("Hello world")
                .build();

        lexicalIndex.indexChunk(chunk);

        List<ScoredChunk> results = lexicalIndex.search("zzzznotfound", 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void lexicalIndex_removesDocumentChunks() {
        Chunk chunk1 = Chunk.builder().chunkId("c1").documentId("doc1").text("First chunk").build();
        Chunk chunk2 = Chunk.builder().chunkId("c2").documentId("doc1").text("Second chunk").build();
        Chunk chunk3 = Chunk.builder().chunkId("c3").documentId("doc2").text("Third chunk").build();

        lexicalIndex.indexChunks(Arrays.asList(chunk1, chunk2, chunk3));
        assertEquals(3, lexicalIndex.size());

        lexicalIndex.removeDocument("doc1");
        assertEquals(1, lexicalIndex.size());
    }

    // ========== Semantic Index Tests ==========

    @Test
    void semanticIndex_indexesAndSearchesByEmbedding() {
        Chunk chunk = Chunk.builder()
                .chunkId("c1")
                .documentId("doc1")
                .text("Test content")
                .build();

        float[] embedding = {0.1f, 0.2f, 0.3f, 0.4f};
        semanticIndex.indexChunk(chunk, embedding);

        // Search with same embedding should return high score
        List<ScoredChunk> results = semanticIndex.search(embedding, 10);

        assertFalse(results.isEmpty());
        assertEquals("c1", results.get(0).getChunkId());
        assertTrue(results.get(0).getScore() > 0.99f); // Should be ~1.0 for identical
    }

    @Test
    void semanticIndex_returnsSimilarChunks() {
        Chunk chunk1 = Chunk.builder().chunkId("c1").documentId("doc1").text("Similar").build();
        Chunk chunk2 = Chunk.builder().chunkId("c2").documentId("doc1").text("Different").build();

        float[] embedding1 = {1.0f, 0.0f, 0.0f, 0.0f};
        float[] embedding2 = {0.0f, 1.0f, 0.0f, 0.0f};

        semanticIndex.indexChunk(chunk1, embedding1);
        semanticIndex.indexChunk(chunk2, embedding2);

        // Query similar to embedding1
        float[] query = {0.9f, 0.1f, 0.0f, 0.0f};
        List<ScoredChunk> results = semanticIndex.search(query, 10);

        assertEquals(2, results.size());
        assertEquals("c1", results.get(0).getChunkId()); // Should be first (more similar)
    }

    @Test
    void semanticIndex_normalizesVectors() {
        Chunk chunk = Chunk.builder().chunkId("c1").documentId("doc1").text("Test").build();

        // Non-normalized vector
        float[] embedding = {10.0f, 20.0f, 30.0f, 40.0f};
        semanticIndex.indexChunk(chunk, embedding);

        // Query with different magnitude but same direction
        float[] query = {1.0f, 2.0f, 3.0f, 4.0f};
        List<ScoredChunk> results = semanticIndex.search(query, 10);

        // Should still match well because both are normalized
        assertTrue(results.get(0).getScore() > 0.99f);
    }

    // ========== Context Builder Tests ==========

    @Test
    void contextBuilder_buildsContextFromChunks() {
        Chunk chunk = Chunk.builder()
                .chunkId("c1")
                .documentId("doc1")
                .text("This is the relevant content.")
                .heading("Important Section")
                .build();

        ScoredChunk scored = new ScoredChunk(chunk, 0.85f, ScoredChunk.ScoreSource.HYBRID);

        RagContextBuilder.BuildResult result = contextBuilder.build(
                Arrays.asList(scored), "Test Document"
        );

        assertFalse(result.isEmpty());
        assertTrue(result.getContext().contains("RETRIEVED CONTEXT"));
        assertTrue(result.getContext().contains("Test Document"));
        assertTrue(result.getContext().contains("relevant content"));
        assertTrue(result.getContext().contains("0.85"));
    }

    @Test
    void contextBuilder_truncatesLongChunks() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longText.append("This is a very long chunk that needs truncation. ");
        }

        Chunk chunk = Chunk.builder()
                .chunkId("c1")
                .documentId("doc1")
                .text(longText.toString())
                .build();

        RagConfig config = new RagConfig().setMaxContextCharsPerChunk(500);
        RagContextBuilder builder = new RagContextBuilder(config);

        ScoredChunk scored = new ScoredChunk(chunk, 0.9f, ScoredChunk.ScoreSource.HYBRID);
        RagContextBuilder.BuildResult result = builder.build(Arrays.asList(scored), "Test");

        assertTrue(result.hasTruncations());
        assertTrue(result.getContext().contains("gekürzt"));
    }

    @Test
    void contextBuilder_returnsEmptyForNoChunks() {
        RagContextBuilder.BuildResult result = contextBuilder.build(
                Arrays.asList(), "Test"
        );

        assertTrue(result.isEmpty());
        assertEquals(0, result.getIncludedChunks());
    }
}

