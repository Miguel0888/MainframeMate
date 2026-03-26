package de.bund.zrb.rag.service;

import de.bund.zrb.ingestion.infrastructure.render.RendererRegistry;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.usecase.RenderDocumentUseCase;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.rag.config.EmbeddingSettings;
import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.config.RerankerSettings;
import de.bund.zrb.rag.infrastructure.InMemorySemanticIndex;
import de.bund.zrb.rag.infrastructure.LuceneLexicalIndex;
import de.bund.zrb.rag.infrastructure.MarkdownChunker;
import de.bund.zrb.rag.infrastructure.MultiProviderEmbeddingClient;
import de.bund.zrb.rag.infrastructure.HttpRerankerClient;
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
        this(RagConfig.defaults(), EmbeddingSettings.fromStoredConfig(), RerankerSettings.fromStoredConfig());
    }

    public RagService(RagConfig config, EmbeddingSettings embeddingSettings) {
        this(config, embeddingSettings, RerankerSettings.fromStoredConfig());
    }

    public RagService(RagConfig config, EmbeddingSettings embeddingSettings, RerankerSettings rerankerSettings) {
        this.config = config;
        this.embeddingSettings = embeddingSettings;

        this.chunker = new MarkdownChunker(config);
        this.lexicalIndex = createPersistentLexicalIndex();
        this.semanticIndex = new InMemorySemanticIndex();
        this.embeddingClient = new MultiProviderEmbeddingClient(embeddingSettings);
        this.retriever = new HybridRetriever(lexicalIndex, semanticIndex, embeddingClient, config);
        this.contextBuilder = new RagContextBuilder(config);
        this.renderUseCase = new RenderDocumentUseCase(RendererRegistry.createDefault());

        // Wire up optional cross-encoder reranker
        if (rerankerSettings != null && rerankerSettings.isEnabled()) {
            HttpRerankerClient rerankerClient = new HttpRerankerClient(rerankerSettings);
            retriever.setRerankerClient(rerankerClient);
            LOG.info("Reranker enabled: " + rerankerClient.getDescription());
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RagIndexer");
            t.setDaemon(true);
            return t;
        });

        // Restore in-memory maps from persistent Lucene index (survives restarts)
        rebuildInMemoryMapsFromIndex();

        LOG.info("RAG service initialized");
    }

    /**
     * Rebuild the in-memory chunkStore, documentChunkIds and indexedDocuments
     * maps from the persisted Lucene index.  The LuceneLexicalIndex already
     * rebuilds its own chunk cache from disk on startup – we piggy-back on
     * that to restore the RagService maps so that getDocumentChunks(), getChunk()
     * and friends work correctly after an application restart.
     */
    private void rebuildInMemoryMapsFromIndex() {
        if (!(lexicalIndex instanceof LuceneLexicalIndex)) return;

        Collection<Chunk> cachedChunks = ((LuceneLexicalIndex) lexicalIndex).getAllCachedChunks();
        if (cachedChunks == null || cachedChunks.isEmpty()) return;

        int count = 0;
        for (Chunk chunk : cachedChunks) {
            chunkStore.put(chunk.getChunkId(), chunk);
            List<String> ids = documentChunkIds.get(chunk.getDocumentId());
            if (ids == null) {
                ids = new ArrayList<>();
                documentChunkIds.put(chunk.getDocumentId(), ids);
            }
            ids.add(chunk.getChunkId());
            count++;
        }

        // Rebuild indexedDocuments metadata (document name + chunk count)
        for (Map.Entry<String, List<String>> entry : documentChunkIds.entrySet()) {
            String docId = entry.getKey();
            List<String> chunkIds = entry.getValue();
            if (!indexedDocuments.containsKey(docId) && !chunkIds.isEmpty()) {
                Chunk firstChunk = chunkStore.get(chunkIds.get(0));
                String docName = (firstChunk != null && firstChunk.getSourceName() != null)
                        ? firstChunk.getSourceName() : docId;
                indexedDocuments.put(docId, new IndexedDocument(docId, docName, chunkIds.size()));
            }
        }

        LOG.info("Restored " + count + " chunks for " + documentChunkIds.size()
                + " documents from persistent Lucene index");
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
     * Index a document synchronously (with embeddings if client is available).
     */
    public void indexDocument(String documentId, String documentName, Document document) {
        indexDocument(documentId, documentName, document, true);
    }

    /**
     * Index a document synchronously, with explicit control over embedding generation.
     *
     * @param generateEmbeddings if false, skip embedding generation even if the client is available.
     *                           This respects the "embedding enabled" flag in indexing rules.
     */
    public void indexDocument(String documentId, String documentName, Document document, boolean generateEmbeddings) {
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

        // Generate embeddings and index in semantic index (only if enabled by indexing rule)
        if (generateEmbeddings && embeddingClient.isAvailable()) {
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
     * Search ONLY the Lucene lexical index (BM25).
     * Does NOT use semantic/embedding search.
     * Use this for the global search when RAG checkbox is off.
     */
    public List<ScoredChunk> searchLexicalOnly(String query, int topN) {
        if (lexicalIndex == null || !lexicalIndex.isAvailable()) {
            return Collections.emptyList();
        }
        return lexicalIndex.search(query, topN);
    }

    /**
     * Get the number of chunks in the semantic (embedding) index.
     * Returns 0 if no embeddings have been generated.
     */
    public int getSemanticIndexSize() {
        return semanticIndex != null ? semanticIndex.size() : 0;
    }

    /**
     * Get the number of chunks in the lexical (Lucene) index.
     */
    public int getLexicalIndexSize() {
        return lexicalIndex != null ? lexicalIndex.size() : 0;
    }

    /**
     * Get the underlying Lucene lexical index.
     * Used by SearchService for direct document-level queries (e.g. preview).
     *
     * @return the LuceneLexicalIndex, or null if not a Lucene-backed index
     */
    public LuceneLexicalIndex getLexicalIndex() {
        return (lexicalIndex instanceof LuceneLexicalIndex)
                ? (LuceneLexicalIndex) lexicalIndex : null;
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
     * Flush/commit the Lucene index writer so all segments are persisted to disk.
     * This does NOT close the writer – it just ensures a consistent on-disk state
     * suitable for file-level backup/export.
     */
    public void flushIndex() {
        if (lexicalIndex instanceof LuceneLexicalIndex) {
            ((LuceneLexicalIndex) lexicalIndex).flush();
        }
    }

    /**
     * Export all chunks stored in the Lucene index.
     * Used by the export service to serialise the index without touching files.
     *
     * @return list of all chunks (may be large), or empty list
     */
    public List<Chunk> exportAllChunks() {
        if (lexicalIndex instanceof LuceneLexicalIndex) {
            return ((LuceneLexicalIndex) lexicalIndex).exportAllChunks();
        }
        return Collections.emptyList();
    }

    /**
     * Bulk-import chunks into the Lucene index.
     * Used by the import service to restore a previously exported index.
     *
     * @param chunks list of chunks to index
     */
    public void importChunks(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        lexicalIndex.indexChunks(chunks);
        // Also update in-memory tracking
        for (Chunk c : chunks) {
            chunkStore.put(c.getChunkId(), c);
            List<String> ids = documentChunkIds.computeIfAbsent(
                    c.getDocumentId(), k -> new java.util.ArrayList<>());
            ids.add(c.getChunkId());
            indexedDocuments.putIfAbsent(c.getDocumentId(),
                    new IndexedDocument(c.getDocumentId(),
                            c.getSourceName() != null ? c.getSourceName() : c.getDocumentId(), 1));
        }
        LOG.info("[RagService] Imported " + chunks.size() + " chunks");
    }

    /**
     * Refresh the Lucene index after external modification (e.g. import).
     * Closes the current index and re-opens from disk.
     */
    public void refreshIndex() {
        if (lexicalIndex instanceof LuceneLexicalIndex) {
            LuceneLexicalIndex lucene = (LuceneLexicalIndex) lexicalIndex;
            try {
                lucene.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to close index for refresh", e);
            }
        }
        // Re-create the singleton so a fresh index is opened on next access
        synchronized (RagService.class) {
            INSTANCE = new RagService();
        }
        LOG.info("[RagService] Index refreshed after import");
    }

    /**
     * List all documents stored in the persistent Lucene index.
     * Unlike getIndexedDocumentIds() this survives restarts.
     *
     * @return map of documentId → sourceName/fileName
     */
    public java.util.Map<String, String> listAllIndexedDocuments() {
        if (lexicalIndex instanceof LuceneLexicalIndex) {
            return ((LuceneLexicalIndex) lexicalIndex).listAllDocuments();
        }
        // Fallback: in-memory map
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, IndexedDocument> e : indexedDocuments.entrySet()) {
            result.put(e.getKey(), e.getValue().documentName);
        }
        return result;
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
     * Update reranker settings and recreate the client.
     * Pass settings with {@code enabled=false} to disable reranking.
     */
    public void updateRerankerSettings(RerankerSettings newSettings) {
        if (newSettings != null && newSettings.isEnabled()) {
            HttpRerankerClient client = new HttpRerankerClient(newSettings);
            retriever.setRerankerClient(client);
            LOG.info("Reranker updated: " + client.getDescription());
        } else {
            retriever.setRerankerClient(null);
            LOG.info("Reranker disabled");
        }
    }

    /**
     * Get the current reranker client, or {@code null} if none is configured.
     * Used by SearchService to check availability and apply final reranking.
     */
    public de.bund.zrb.rag.port.RerankerClient getRerankerClient() {
        return retriever.getRerankerClient();
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

