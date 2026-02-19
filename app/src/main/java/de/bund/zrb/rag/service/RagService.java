package de.bund.zrb.rag.service;

import de.bund.zrb.ingestion.infrastructure.render.RendererRegistry;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.usecase.RenderDocumentUseCase;
import de.bund.zrb.rag.config.EmbeddingSettings;
import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.infrastructure.InMemorySemanticIndex;
import de.bund.zrb.rag.infrastructure.LuceneLexicalIndex;
import de.bund.zrb.rag.infrastructure.MarkdownChunker;
import de.bund.zrb.rag.infrastructure.MultiProviderEmbeddingClient;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.port.Chunker;
import de.bund.zrb.rag.port.EmbeddingClient;
import de.bund.zrb.rag.port.LexicalIndex;
import de.bund.zrb.rag.port.SemanticIndex;
import de.bund.zrb.rag.usecase.HybridRetriever;
import de.bund.zrb.rag.usecase.RagContextBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main RAG service that coordinates indexing and retrieval.
 * Manages the lifecycle of indices and provides a simple API for chat integration.
 */
public class RagService {

    private static final Logger LOG = Logger.getLogger(RagService.class.getName());

    private final RagConfig config;
    private final EmbeddingSettings embeddingSettings;

    private final Chunker chunker;
    private final LexicalIndex lexicalIndex;
    private final SemanticIndex semanticIndex;
    private EmbeddingClient embeddingClient;
    private final HybridRetriever retriever;
    private final RagContextBuilder contextBuilder;
    private final RenderDocumentUseCase renderUseCase;

    private final Map<String, IndexedDocument> indexedDocuments = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public RagService() {
        this(RagConfig.defaults(), EmbeddingSettings.defaults());
    }

    public RagService(RagConfig config, EmbeddingSettings embeddingSettings) {
        this.config = config;
        this.embeddingSettings = embeddingSettings;

        this.chunker = new MarkdownChunker(config);
        this.lexicalIndex = new LuceneLexicalIndex();
        this.semanticIndex = new InMemorySemanticIndex();
        this.embeddingClient = new MultiProviderEmbeddingClient(embeddingSettings);
        this.retriever = new HybridRetriever(lexicalIndex, semanticIndex, embeddingClient, config);
        this.contextBuilder = new RagContextBuilder(config);
        this.renderUseCase = new RenderDocumentUseCase(RendererRegistry.createDefault());

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RagIndexer");
            t.setDaemon(true);
            return t;
        });

        LOG.info("RAG service initialized");
    }

    /**
     * Index a document asynchronously.
     */
    public void indexDocumentAsync(String documentId, String documentName, Document document) {
        executor.submit(() -> {
            try {
                indexDocument(documentId, documentName, document);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to index document: " + documentId, e);
            }
        });
    }

    /**
     * Index a document synchronously.
     */
    public void indexDocument(String documentId, String documentName, Document document) {
        if (document == null || document.isEmpty()) {
            LOG.warning("Cannot index empty document: " + documentId);
            return;
        }

        long startTime = System.currentTimeMillis();

        // Render to Markdown
        String markdown = renderUseCase.renderToMarkdown(document);
        String mimeType = document.getMetadata() != null ? document.getMetadata().getMimeType() : null;

        // Chunk the document
        List<Chunk> chunks = chunker.chunkMarkdown(markdown, documentId, documentName, mimeType);

        if (chunks.isEmpty()) {
            LOG.warning("No chunks generated for document: " + documentId);
            return;
        }

        // Index in Lucene
        lexicalIndex.indexChunks(chunks);

        // Generate embeddings and index in semantic index
        if (embeddingClient.isAvailable()) {
            for (Chunk chunk : chunks) {
                try {
                    float[] embedding = embeddingClient.embed(chunk.getText());
                    if (embedding.length > 0) {
                        semanticIndex.indexChunk(chunk, embedding);
                    }
                } catch (Exception e) {
                    LOG.warning("Failed to generate embedding for chunk: " + chunk.getChunkId());
                }
            }
        } else {
            LOG.warning("Embedding client not available, using lexical-only indexing");
        }

        // Track indexed document
        indexedDocuments.put(documentId, new IndexedDocument(documentId, documentName, chunks.size()));

        long duration = System.currentTimeMillis() - startTime;
        LOG.info(String.format("Indexed document %s: %d chunks in %dms", documentName, chunks.size(), duration));
    }

    /**
     * Remove a document from the indices.
     */
    public void removeDocument(String documentId) {
        lexicalIndex.removeDocument(documentId);
        semanticIndex.removeDocument(documentId);
        indexedDocuments.remove(documentId);
        LOG.info("Removed document from RAG: " + documentId);
    }

    /**
     * Retrieve relevant chunks for a query.
     */
    public List<ScoredChunk> retrieve(String query) {
        return retriever.retrieve(query);
    }

    /**
     * Retrieve relevant chunks for a query with custom top-K.
     */
    public List<ScoredChunk> retrieve(String query, int topK) {
        return retriever.retrieve(query, topK);
    }

    /**
     * Build hidden context from a query.
     */
    public RagContextBuilder.BuildResult buildContext(String query) {
        List<ScoredChunk> chunks = retrieve(query);
        return contextBuilder.build(chunks, "RAG Context");
    }

    /**
     * Build hidden context with custom document name.
     */
    public RagContextBuilder.BuildResult buildContext(String query, String documentName) {
        List<ScoredChunk> chunks = retrieve(query);
        return contextBuilder.build(chunks, documentName);
    }

    /**
     * Get statistics about the indexed content.
     */
    public RagStats getStats() {
        HybridRetriever.RetrievalStats retrievalStats = retriever.getStats();
        return new RagStats(
                indexedDocuments.size(),
                retrievalStats.lexicalSize,
                retrievalStats.semanticSize,
                retrievalStats.lexicalAvailable,
                retrievalStats.semanticAvailable,
                embeddingClient.isAvailable()
        );
    }

    /**
     * Check if a document is indexed.
     */
    public boolean isIndexed(String documentId) {
        return indexedDocuments.containsKey(documentId);
    }

    /**
     * Get all indexed document IDs.
     */
    public Set<String> getIndexedDocumentIds() {
        return new HashSet<>(indexedDocuments.keySet());
    }

    /**
     * Clear all indices.
     */
    public void clear() {
        lexicalIndex.clear();
        semanticIndex.clear();
        indexedDocuments.clear();
        LOG.info("RAG indices cleared");
    }

    /**
     * Update embedding settings and recreate the client.
     */
    public void updateEmbeddingSettings(EmbeddingSettings newSettings) {
        this.embeddingClient = new MultiProviderEmbeddingClient(newSettings);
        LOG.info("Embedding client updated: " + newSettings.getProvider() + " " + newSettings.getModel());
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        executor.shutdownNow();
        if (lexicalIndex instanceof LuceneLexicalIndex) {
            ((LuceneLexicalIndex) lexicalIndex).close();
        }
    }

    /**
     * Get the configuration.
     */
    public RagConfig getConfig() {
        return config;
    }

    /**
     * Information about an indexed document.
     */
    public static class IndexedDocument {
        public final String documentId;
        public final String documentName;
        public final int chunkCount;
        public final long indexedAt;

        public IndexedDocument(String documentId, String documentName, int chunkCount) {
            this.documentId = documentId;
            this.documentName = documentName;
            this.chunkCount = chunkCount;
            this.indexedAt = System.currentTimeMillis();
        }
    }

    /**
     * RAG statistics.
     */
    public static class RagStats {
        public final int documentCount;
        public final int lexicalChunkCount;
        public final int semanticChunkCount;
        public final boolean lexicalAvailable;
        public final boolean semanticAvailable;
        public final boolean embeddingsAvailable;

        public RagStats(int documentCount, int lexicalChunkCount, int semanticChunkCount,
                       boolean lexicalAvailable, boolean semanticAvailable, boolean embeddingsAvailable) {
            this.documentCount = documentCount;
            this.lexicalChunkCount = lexicalChunkCount;
            this.semanticChunkCount = semanticChunkCount;
            this.lexicalAvailable = lexicalAvailable;
            this.semanticAvailable = semanticAvailable;
            this.embeddingsAvailable = embeddingsAvailable;
        }

        @Override
        public String toString() {
            return "RagStats{" +
                    "documents=" + documentCount +
                    ", lexicalChunks=" + lexicalChunkCount +
                    ", semanticChunks=" + semanticChunkCount +
                    ", embeddingsAvailable=" + embeddingsAvailable +
                    '}';
        }
    }
}

