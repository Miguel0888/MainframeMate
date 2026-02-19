package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;

import java.util.List;
import java.util.UUID;

/**
 * Represents an attachment to a chat message.
 * Contains a reference to a Document (from ingestion) along with metadata.
 * The Document content is NOT stored in the chat transcript - only the attachment ID.
 */
public class ChatAttachment {

    private final String id;
    private final String name;
    private final String sourcePath;
    private final String tabId;
    private final Document document;
    private final long createdAt;
    private final List<String> warnings;

    private ChatAttachment(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.name = builder.name;
        this.sourcePath = builder.sourcePath;
        this.tabId = builder.tabId;
        this.document = builder.document;
        this.createdAt = builder.createdAt > 0 ? builder.createdAt : System.currentTimeMillis();
        this.warnings = builder.warnings;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getTabId() {
        return tabId;
    }

    public Document getDocument() {
        return document;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    public int getWarningsCount() {
        return warnings != null ? warnings.size() : 0;
    }

    /**
     * Get the MIME type from document metadata, if available.
     */
    public String getMimeType() {
        if (document != null && document.getMetadata() != null) {
            return document.getMetadata().getMimeType();
        }
        return null;
    }

    /**
     * Get a display label for this attachment.
     */
    public String getDisplayLabel() {
        StringBuilder label = new StringBuilder();
        label.append("📄 ").append(name != null ? name : "Dokument");
        String mime = getMimeType();
        if (mime != null) {
            // Shorten MIME type for display
            if (mime.contains("/")) {
                mime = mime.substring(mime.indexOf('/') + 1);
            }
            label.append(" (").append(mime).append(")");
        }
        return label.toString();
    }

    @Override
    public String toString() {
        return "ChatAttachment{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", warnings=" + getWarningsCount() +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String sourcePath;
        private String tabId;
        private Document document;
        private long createdAt;
        private List<String> warnings;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder sourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder tabId(String tabId) {
            this.tabId = tabId;
            return this;
        }

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public ChatAttachment build() {
            return new ChatAttachment(this);
        }
    }
}

