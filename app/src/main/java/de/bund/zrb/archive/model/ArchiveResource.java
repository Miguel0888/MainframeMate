package de.bund.zrb.archive.model;

import java.util.UUID;

/**
 * A raw artifact in the Data Lake. Every captured HTTP response, DOM snapshot,
 * or manually imported file becomes an ArchiveResource.
 */
public class ArchiveResource {

    private String resourceId = UUID.randomUUID().toString();
    private String runId = "";
    private long capturedAt;                    // epoch millis UTC
    private String source = "NETWORK";          // NETWORK | DOM_SNAPSHOT | MANUAL
    private String url = "";                    // original URL
    private String canonicalUrl = "";           // normalized URL
    private String urlHash = "";                // SHA-256 of canonicalUrl
    private String contentHash = "";            // SHA-256 of body bytes
    private String mimeType = "";
    private String charset = "";
    private int httpStatus;
    private String httpMethod = "GET";
    private String kind = "OTHER";              // ResourceKind.name()
    private long sizeBytes;
    private String topLevelUrl = "";            // URL of the tab/context at capture time
    private String parentUrl = "";              // initiator/referer
    private int depth;                          // crawl depth
    private boolean indexable;
    private String storagePath = "";            // relative path to blob file
    private String title = "";                  // extracted title (for display)
    private int seenCount = 1;
    private long firstSeenAt;
    private long lastSeenAt;
    private String errorMessage = "";

    // ── Getters & Setters ──

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public long getCapturedAt() { return capturedAt; }
    public void setCapturedAt(long capturedAt) { this.capturedAt = capturedAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }

    public String getUrlHash() { return urlHash; }
    public void setUrlHash(String urlHash) { this.urlHash = urlHash; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getCharset() { return charset; }
    public void setCharset(String charset) { this.charset = charset; }

    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getTopLevelUrl() { return topLevelUrl; }
    public void setTopLevelUrl(String topLevelUrl) { this.topLevelUrl = topLevelUrl; }

    public String getParentUrl() { return parentUrl; }
    public void setParentUrl(String parentUrl) { this.parentUrl = parentUrl; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public boolean isIndexable() { return indexable; }
    public void setIndexable(boolean indexable) { this.indexable = indexable; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getSeenCount() { return seenCount; }
    public void setSeenCount(int seenCount) { this.seenCount = seenCount; }

    public long getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(long firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public long getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(long lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public String toString() {
        return "Resource[" + resourceId + " " + kind + " " + url + "]";
    }
}
