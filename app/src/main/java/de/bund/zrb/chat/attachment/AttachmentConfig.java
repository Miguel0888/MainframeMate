package de.bund.zrb.chat.attachment;

/**
 * Configuration for attachment context building.
 */
public class AttachmentConfig {

    private int maxAttachmentsPerMessage = 5;
    private int maxAttachmentCharsTotal = 50000;
    private int maxAttachmentCharsPerDoc = 15000;
    private TruncateStrategy truncateStrategy = TruncateStrategy.HEAD_WITH_MARKER;

    // Phase 2 (RAG) settings
    private int chunkSize = 1000;
    private int chunkOverlap = 100;
    private int topK = 5;

    public enum TruncateStrategy {
        HEAD_WITH_MARKER,   // Keep head, add "[...truncated...]" marker
        TAIL_WITH_MARKER,   // Keep tail, add marker at start
        HEAD_TAIL_SUMMARY   // Keep head + tail + summary marker
    }

    public int getMaxAttachmentsPerMessage() {
        return maxAttachmentsPerMessage;
    }

    public AttachmentConfig setMaxAttachmentsPerMessage(int maxAttachmentsPerMessage) {
        this.maxAttachmentsPerMessage = maxAttachmentsPerMessage;
        return this;
    }

    public int getMaxAttachmentCharsTotal() {
        return maxAttachmentCharsTotal;
    }

    public AttachmentConfig setMaxAttachmentCharsTotal(int maxAttachmentCharsTotal) {
        this.maxAttachmentCharsTotal = maxAttachmentCharsTotal;
        return this;
    }

    public int getMaxAttachmentCharsPerDoc() {
        return maxAttachmentCharsPerDoc;
    }

    public AttachmentConfig setMaxAttachmentCharsPerDoc(int maxAttachmentCharsPerDoc) {
        this.maxAttachmentCharsPerDoc = maxAttachmentCharsPerDoc;
        return this;
    }

    public TruncateStrategy getTruncateStrategy() {
        return truncateStrategy;
    }

    public AttachmentConfig setTruncateStrategy(TruncateStrategy truncateStrategy) {
        this.truncateStrategy = truncateStrategy;
        return this;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public AttachmentConfig setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
        return this;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public AttachmentConfig setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
        return this;
    }

    public int getTopK() {
        return topK;
    }

    public AttachmentConfig setTopK(int topK) {
        this.topK = topK;
        return this;
    }

    /**
     * Create default configuration.
     */
    public static AttachmentConfig defaults() {
        return new AttachmentConfig();
    }
}

