package de.bund.zrb.indexing.service;

import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase;
import de.bund.zrb.rag.service.RagService;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ContentProcessor implementation that bridges the indexing pipeline with
 * the existing RAG infrastructure (Tika extraction → Chunking → Lucene).
 *
 * Flow per item:
 *   1. Receive raw bytes + MIME type from scanner
 *   2. Extract text via Tika (ExtractTextFromDocumentUseCase)
 *   3. Build a Document from extracted text
 *   4. Index via RagService (chunk → Lucene + optional embeddings)
 */
public class RagContentProcessor implements IndexingPipeline.ContentProcessor {

    private static final Logger LOG = Logger.getLogger(RagContentProcessor.class.getName());

    private final ExtractTextFromDocumentUseCase extractionUseCase;
    private final RagService ragService;

    public RagContentProcessor() {
        this.extractionUseCase = new ExtractTextFromDocumentUseCase();
        this.ragService = RagService.getInstance();
    }

    @Override
    public int process(IndexSource source, String itemPath, byte[] content, String mimeType) throws Exception {
        if (content == null || content.length == 0) {
            LOG.fine("[IndexProcessor] Skipping empty content: " + itemPath);
            return 0;
        }

        // Derive a filename hint from the item path
        String filenameHint = extractFilename(itemPath);

        // Step 1: Extract text via Tika pipeline
        DocumentSource docSource = DocumentSource.fromBytes(content, filenameHint);
        ExtractionResult extraction = extractionUseCase.executeSync(docSource);

        if (!extraction.isSuccess()) {
            LOG.fine("[IndexProcessor] Extraction failed for " + filenameHint
                    + ": " + extraction.getErrorMessage());
            return 0;
        }

        String plainText = extraction.getPlainText();
        if (plainText == null || plainText.trim().isEmpty()) {
            LOG.fine("[IndexProcessor] No text extracted from: " + filenameHint);
            return 0;
        }

        // Step 2: Build a Document for RAG indexing
        String documentId = itemPath;
        String documentName = filenameHint;

        DocumentMetadata metadata = DocumentMetadata.builder()
                .sourceName(filenameHint)
                .mimeType(mimeType != null ? mimeType : "text/plain")
                .attribute("sourcePath", itemPath)
                .build();

        Document document = Document.builder()
                .metadata(metadata)
                .paragraph(plainText)
                .build();

        // Step 3: Index via RagService (chunks + Lucene + optional embeddings)
        try {
            ragService.indexDocument(documentId, documentName, document);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[IndexProcessor] RAG indexing failed for: " + itemPath, e);
            throw e;
        }

        // Return chunk count
        RagService.IndexedDocument indexed = ragService.getIndexedDocument(documentId);
        return indexed != null ? indexed.chunkCount : 1;
    }

    @Override
    public void removeFromIndex(String documentId) throws Exception {
        ragService.removeDocument(documentId);
    }

    /**
     * Extract a human-readable filename from an item path.
     * Handles both file paths and mail paths (mailbox#folder#nodeId).
     */
    private String extractFilename(String itemPath) {
        if (itemPath == null) return "unknown";

        // Mail path format: "mailboxPath#folderPath#nodeId"
        if (itemPath.contains("#")) {
            String[] parts = itemPath.split("#");
            if (parts.length >= 2) {
                String folder = parts[1];
                String nodeId = parts.length > 2 ? parts[2] : "";
                String folderName = folder.contains("/")
                        ? folder.substring(folder.lastIndexOf('/') + 1)
                        : folder;
                return folderName + "_" + nodeId + ".eml";
            }
        }

        // File path: use last segment
        String name = itemPath.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            return name.substring(lastSlash + 1);
        }
        return name;
    }
}
