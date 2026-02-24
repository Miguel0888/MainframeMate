package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.usecase.BuildDocumentFromTextUseCase;
import de.bund.zrb.rag.service.RagService;
import de.zrb.bund.newApi.ui.FtpTab;

import java.util.List;
import java.util.logging.Logger;

/**
 * Use case for attaching a tab's content to the chat.
 * Creates a ChatAttachment from the tab's content and stores it.
 * Also indexes the document in the RAG system for retrieval.
 */
public class AttachTabToChatUseCase {

    private static final Logger LOG = Logger.getLogger(AttachTabToChatUseCase.class.getName());

    private final ChatAttachmentStore store;
    private final BuildDocumentFromTextUseCase buildDocumentUseCase;

    public AttachTabToChatUseCase() {
        this(ChatAttachmentStore.getInstance());
    }

    public AttachTabToChatUseCase(ChatAttachmentStore store) {
        this.store = store;
        this.buildDocumentUseCase = new BuildDocumentFromTextUseCase();
    }

    /**
     * Attach a tab to the chat.
     *
     * @param tab the tab to attach
     * @return the created attachment, or null if the tab cannot be attached
     */
    public ChatAttachment execute(FtpTab tab) {
        if (tab == null) {
            return null;
        }

        String content = tab.getContent();
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        String name = tab.getTitle();
        String path = null;
        Document document;
        List<String> warnings = null;

        // Try to get path from Bookmarkable (works for all tab types)
        try {
            path = tab.getPath();
        } catch (Exception ignored) {}

        // Check if this is a DocumentPreviewTab (has pre-built Document)
        if (tab instanceof DocumentPreviewTabAdapter) {
            DocumentPreviewTabAdapter previewTab = (DocumentPreviewTabAdapter) tab;
            Document previewDoc = previewTab.getDocument();
            if (previewDoc != null && !previewDoc.isEmpty()) {
                document = previewDoc;
                warnings = previewTab.getWarnings();
                if (path == null && previewTab.getMetadata() != null) {
                    path = previewTab.getMetadata().getSourceName();
                }
            } else {
                // Document not available (e.g. FileTabImpl opened as editor, not preview)
                // Fall through to build from raw content
                DocumentMetadata metadata = DocumentMetadata.builder()
                        .sourceName(name)
                        .build();
                document = buildDocumentUseCase.buildWithStructure(content, metadata);
            }
        } else {
            // Build document from raw content
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .sourceName(name)
                    .build();
            document = buildDocumentUseCase.buildWithStructure(content, metadata);
        }

        if (document == null || document.isEmpty()) {
            return null;
        }

        // Create attachment
        ChatAttachment attachment = ChatAttachment.builder()
                .name(name)
                .sourcePath(path)
                .tabId(String.valueOf(System.identityHashCode(tab)))
                .document(document)
                .warnings(warnings)
                .build();

        // Store it
        store.store(attachment);

        // Index in RAG for retrieval
        indexAttachmentAsync(attachment);

        return attachment;
    }

    /**
     * Attach content directly (for testing or programmatic use).
     */
    public ChatAttachment attachContent(String name, String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        DocumentMetadata metadata = DocumentMetadata.builder()
                .sourceName(name)
                .build();
        Document document = buildDocumentUseCase.buildWithStructure(content, metadata);

        ChatAttachment attachment = ChatAttachment.builder()
                .name(name)
                .document(document)
                .build();

        store.store(attachment);

        // Index in RAG for retrieval
        indexAttachmentAsync(attachment);

        return attachment;
    }

    /**
     * Remove an attachment from the store and RAG index.
     */
    public void detach(String attachmentId) {
        store.remove(attachmentId);

        // Remove from RAG index
        try {
            RagService.getInstance().removeDocument(attachmentId);
            LOG.fine("Removed attachment from RAG index: " + attachmentId);
        } catch (Exception e) {
            LOG.warning("Failed to remove attachment from RAG: " + e.getMessage());
        }
    }

    /**
     * Get all current attachments.
     */
    public List<ChatAttachment> getCurrentAttachments() {
        return store.getAllAttachments();
    }

    /**
     * Index an attachment in the RAG system asynchronously.
     */
    private void indexAttachmentAsync(ChatAttachment attachment) {
        if (attachment == null || attachment.getDocument() == null) {
            return;
        }

        try {
            RagService.getInstance().indexDocumentAsync(
                    attachment.getId(),
                    attachment.getName(),
                    attachment.getDocument()
            );
            LOG.fine("Queued attachment for RAG indexing: " + attachment.getName());
        } catch (Exception e) {
            LOG.warning("Failed to index attachment in RAG: " + e.getMessage());
        }
    }

    /**
     * Interface for tabs that can provide a pre-built Document.
     */
    public interface DocumentPreviewTabAdapter {
        Document getDocument();
        DocumentMetadata getMetadata();
        List<String> getWarnings();
    }
}

