package de.bund.zrb.rag.model;

import java.util.Objects;

/**
 * A chunk of text from a document, ready for indexing.
 * Immutable.
 */
public final class Chunk {

    private final String chunkId;
    private final String documentId;
    private final String sourceName;
    private final String mimeType;
    private final int position;
    private final String text;
    private final String heading;
    private final int startOffset;
    private final int endOffset;

    private Chunk(Builder builder) {
        this.chunkId = builder.chunkId;
        this.documentId = builder.documentId;
        this.sourceName = builder.sourceName;
        this.mimeType = builder.mimeType;
        this.position = builder.position;
        this.text = builder.text;
        this.heading = builder.heading;
        this.startOffset = builder.startOffset;
        this.endOffset = builder.endOffset;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public String getHeading() {
        return heading;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public int getTextLength() {
        return text != null ? text.length() : 0;
    }

    /**
     * Generate a hash for caching embeddings.
     */
    public String getContentHash() {
        return Integer.toHexString(Objects.hash(text));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chunk chunk = (Chunk) o;
        return Objects.equals(chunkId, chunk.chunkId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkId);
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "chunkId='" + chunkId + '\'' +
                ", documentId='" + documentId + '\'' +
                ", position=" + position +
                ", textLength=" + getTextLength() +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String chunkId;
        private String documentId;
        private String sourceName;
        private String mimeType;
        private int position;
        private String text;
        private String heading;
        private int startOffset;
        private int endOffset;

        public Builder chunkId(String chunkId) {
            this.chunkId = chunkId;
            return this;
        }

        public Builder documentId(String documentId) {
            this.documentId = documentId;
            return this;
        }

        public Builder sourceName(String sourceName) {
            this.sourceName = sourceName;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder position(int position) {
            this.position = position;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder heading(String heading) {
            this.heading = heading;
            return this;
        }

        public Builder startOffset(int startOffset) {
            this.startOffset = startOffset;
            return this;
        }

        public Builder endOffset(int endOffset) {
            this.endOffset = endOffset;
            return this;
        }

        public Chunk build() {
            if (chunkId == null || chunkId.isEmpty()) {
                chunkId = documentId + "_" + position;
            }
            return new Chunk(this);
        }
    }
}

