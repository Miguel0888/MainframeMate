package de.bund.zrb.indexing.model;

/**
 * Tracks the indexing status of a single content item (file, mail, page).
 *
 * From these fields it must be clear WHY something is or is not in the index:
 * - state=INDEXED, indexedAt=..., contentVersion=... → it's indexed
 * - state=SKIPPED, skipReason="exceeds maxFileSize" → policy exclusion
 * - state=ERROR, errorMessage="Tika parse error: ..." → extraction failure
 * - state=DELETED, deletedAt=... → source item no longer exists
 */
public class IndexItemStatus {

    private String sourceId;
    private String itemPath;          // unique within source (file path, mail nodeId, URL)
    private IndexItemState state = IndexItemState.PENDING;

    // ── Content versioning (for delta detection) ──
    private long lastModifiedAt;      // epoch millis from source
    private long fileSize;            // bytes
    private String contentHash;       // SHA-256 or null if not computed

    // ── Index tracking ──
    private long indexedAt;           // epoch millis when last indexed
    private int indexSchemaVersion;   // schema version of the index
    private String parserVersion;     // Tika/extractor version used
    private String embeddingModel;    // embedding model name+version (or null)
    private int chunkCount;           // how many chunks were created

    // ── Error/skip info ──
    private String errorMessage;
    private String skipReason;
    private int errorCount;           // consecutive errors

    // ── Deletion ──
    private long deletedAt;           // epoch millis when tombstoned

    // ── ACL ──
    private String aclFingerprint;    // hash of ACL tags (detect permission changes)

    // ── Getters & Setters ──

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getItemPath() { return itemPath; }
    public void setItemPath(String itemPath) { this.itemPath = itemPath; }

    public IndexItemState getState() { return state; }
    public void setState(IndexItemState state) { this.state = state; }

    public long getLastModifiedAt() { return lastModifiedAt; }
    public void setLastModifiedAt(long lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public long getIndexedAt() { return indexedAt; }
    public void setIndexedAt(long indexedAt) { this.indexedAt = indexedAt; }

    public int getIndexSchemaVersion() { return indexSchemaVersion; }
    public void setIndexSchemaVersion(int indexSchemaVersion) { this.indexSchemaVersion = indexSchemaVersion; }

    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }

    public String getAclFingerprint() { return aclFingerprint; }
    public void setAclFingerprint(String aclFingerprint) { this.aclFingerprint = aclFingerprint; }

    /**
     * Returns true if this item needs (re-)indexing based on a new content version.
     */
    public boolean needsReindex(long newModifiedAt, long newSize) {
        // Always reindex if not successfully indexed (PENDING, SKIPPED, ERROR, DELETED)
        if (state != IndexItemState.INDEXED) return true;
        // Also reindex if marked as indexed but has 0 chunks (extraction failed silently)
        if (chunkCount <= 0) return true;
        return newModifiedAt != lastModifiedAt || newSize != fileSize;
    }

    /**
     * Returns true if this item needs re-indexing based on content hash.
     */
    public boolean needsReindexByHash(String newHash) {
        if (state != IndexItemState.INDEXED) return true;
        return contentHash == null || !contentHash.equals(newHash);
    }

    @Override
    public String toString() {
        return "[" + state + "] " + itemPath + " (indexed: " + indexedAt + ")";
    }
}
