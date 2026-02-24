package de.bund.zrb.archive.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single document in the archive – either a web-page snapshot
 * or a manually imported file.
 */
public class ArchiveEntry {

    private String entryId = UUID.randomUUID().toString();
    private String url = "";                     // source URL (empty for manual imports)
    private String title = "";                   // page title or filename
    private String mimeType = "text/html";
    private String snapshotPath = "";            // relative path inside snapshot dir
    private long contentLength;                  // text length in chars
    private long fileSizeBytes;                  // file size of snapshot on disk
    private long crawlTimestamp;                 // epoch millis
    private long lastIndexed;                    // epoch millis
    private ArchiveEntryStatus status = ArchiveEntryStatus.PENDING;
    private String sourceId = "";                // reference to IndexSource (optional)
    private String errorMessage = "";
    private Map<String, String> metadata = new HashMap<String, String>();

    // ── Getters & Setters ──

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getSnapshotPath() { return snapshotPath; }
    public void setSnapshotPath(String snapshotPath) { this.snapshotPath = snapshotPath; }

    public long getContentLength() { return contentLength; }
    public void setContentLength(long contentLength) { this.contentLength = contentLength; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public long getCrawlTimestamp() { return crawlTimestamp; }
    public void setCrawlTimestamp(long crawlTimestamp) { this.crawlTimestamp = crawlTimestamp; }

    public long getLastIndexed() { return lastIndexed; }
    public void setLastIndexed(long lastIndexed) { this.lastIndexed = lastIndexed; }

    public ArchiveEntryStatus getStatus() { return status; }
    public void setStatus(ArchiveEntryStatus status) { this.status = status; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return title + " [" + status + "] " + url;
    }
}
