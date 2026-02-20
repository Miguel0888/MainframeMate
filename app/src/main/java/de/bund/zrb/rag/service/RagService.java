package de.bund.zrb.rag.service;

import de.bund.zrb.ingestion.infrastructure.render.RendererRegistry;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.usecase.RenderDocumentUseCase;
import de.bund.zrb.helper.SettingsHelper;
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

import java.io.File;
import java.nio.file.Path;
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

    // Singleton instance
    private static volatile RagService INSTANCE;

    /**
     * Get the singleton instance.
     */
    public static RagService getInstance() {
        if (INSTANCE == null) {
            synchronized (RagService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RagService();
                }
            }
        }
        return INSTANCE;
    }

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
    // Chunk store for tool-calling (read_chunks, read_document_window)
    private final Map<String, Chunk> chunkStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> documentChunkIds = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public RagService() {
        this(RagConfig.defaults(), EmbeddingSettings.fromStoredConfig());
    }

    public RagService(RagConfig config, EmbeddingSettings embeddingSettings) {
        this.config = config;
        this.embeddingSettings = embeddingSettings;

        this.chunker = new MarkdownChunker(config);
        this.lexicalIndex = createPersistentLexicalIndex();
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
     * Create a persistent Lucene lexical index under ~/.mainframemate/db/rag/lexical/.
     * Falls back to in-memory index on error.
     */
    private static LuceneLexicalIndex createPersistentLexicalIndex() {
        try {
            File settingsFolder = SettingsHelper.getSettingsFolder();
            Path indexPath = new File(settingsFolder, "db/rag/lexical").toPath();
            return new LuceneLexicalIndex(indexPath);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to create persistent lexical index, falling back to in-memory", e);
            return new LuceneLexicalIndex();
        }
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

        // Store chunks for tool-calling access
        List<String> chunkIds = new ArrayList<>();
        for (Chunk chunk : chunks) {
            chunkStore.put(chunk.getChunkId(), chunk);
            chunkIds.add(chunk.getChunkId());
        }
        documentChunkIds.put(documentId, chunkIds);

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

        // Remove chunks from store
        List<String> chunkIds = documentChunkIds.remove(documentId);
        if (chunkIds != null) {
            for (String chunkId : chunkIds) {
                chunkStore.remove(chunkId);
            }
        }
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
     * Retrieve relevant chunks for a query, filtered by allowed document IDs.
     * This is used for attachment-based RAG where only specific documents should be searched.
     *
     * @param query the search query
     * @param topK maximum number of results
     * @param allowedDocumentIds if non-null/non-empty, only return chunks from these documents
     * @return scored chunks
     */
    public List<ScoredChunk> retrieve(String query, int topK, Set<String> allowedDocumentIds) {
        return retriever.retrieve(query, topK, allowedDocumentIds);
    }

    /**
     * Build hidden context from a query, filtered by allowed document IDs.
     * Used for attachment-based RAG.
     */
    public RagContextBuilder.BuildResult buildContext(String query, int topK, Set<String> allowedDocumentIds) {
        List<ScoredChunk> chunks = retrieve(query, topK, allowedDocumentIds);
        return contextBuilder.build(chunks, "Attachment Context");
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
     * Get indexed document info.
     */
    public IndexedDocument getIndexedDocument(String documentId) {
        return indexedDocuments.get(documentId);
    }

    /**
     * Get all indexed documents.
     */
    public Collection<IndexedDocument> getIndexedDocuments() {
        return new ArrayList<>(indexedDocuments.values());
    }

    // ==================== Chunk Access for Tool-Calling ====================

    /**
     * Get a single chunk by ID.
     */
    public Chunk getChunk(String chunkId) {
        return chunkStore.get(chunkId);
    }

    /**
     * Get multiple chunks by IDs.
     */
    public List<Chunk> getChunks(List<String> chunkIds) {
        List<Chunk> result = new ArrayList<>();
        for (String chunkId : chunkIds) {
            Chunk chunk = chunkStore.get(chunkId);
            if (chunk != null) {
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * Get a window of chunks around an anchor chunk.
     *
     * @param anchorChunkId the chunk to center on
     * @param before number of chunks before
     * @param after number of chunks after
     * @return list of chunks in order
     */
    public List<Chunk> getChunkWindow(String anchorChunkId, int before, int after) {
        Chunk anchor = chunkStore.get(anchorChunkId);
        if (anchor == null) {
            return Collections.emptyList();
        }

        String documentId = anchor.getDocumentId();
        List<String> docChunkIds = documentChunkIds.get(documentId);
        if (docChunkIds == null) {
            return Collections.singletonList(anchor);
        }

        int anchorIndex = -1;
        for (int i = 0; i < docChunkIds.size(); i++) {
            if (docChunkIds.get(i).equals(anchorChunkId)) {
                anchorIndex = i;
                break;
            }
        }

        if (anchorIndex < 0) {
            return Collections.singletonList(anchor);
        }

        int start = Math.max(0, anchorIndex - before);
        int end = Math.min(docChunkIds.size(), anchorIndex + after + 1);

        List<Chunk> window = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Chunk chunk = chunkStore.get(docChunkIds.get(i));
            if (chunk != null) {
                window.add(chunk);
            }
        }

        return window;
    }

    /**
     * Get all chunks for a document.
     */
    public List<Chunk> getDocumentChunks(String documentId) {
        List<String> chunkIds = documentChunkIds.get(documentId);
        if (chunkIds == null) {
            return Collections.emptyList();
        }
        return getChunks(chunkIds);
    }

    /**
     * Clear all indices.
     */
    public void clear() {
        lexicalIndex.clear();
        semanticIndex.clear();
        indexedDocuments.clear();
        chunkStore.clear();
        documentChunkIds.clear();
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

