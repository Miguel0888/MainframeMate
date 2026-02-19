package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.usecase.BuildDocumentFromTextUseCase;
import de.zrb.bund.newApi.ui.FtpTab;

import java.util.Collections;
import java.util.List;

/**
 * Use case for attaching a tab's content to the chat.
 * Creates a ChatAttachment from the tab's content and stores it.
 */
public class AttachTabToChatUseCase {

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

        // Check if this is a DocumentPreviewTab (has pre-built Document)
        if (tab instanceof DocumentPreviewTabAdapter) {
            DocumentPreviewTabAdapter previewTab = (DocumentPreviewTabAdapter) tab;
            document = previewTab.getDocument();
            warnings = previewTab.getWarnings();
            if (previewTab.getMetadata() != null) {
                path = previewTab.getMetadata().getSourceName();
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
        return attachment;
    }

    /**
     * Remove an attachment from the store.
     */
    public void detach(String attachmentId) {
        store.remove(attachmentId);
    }

    /**
     * Get all current attachments.
     */
    public List<ChatAttachment> getCurrentAttachments() {
        return store.getAllAttachments();
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

