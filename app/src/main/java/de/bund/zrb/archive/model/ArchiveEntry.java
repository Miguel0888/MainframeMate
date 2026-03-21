package de.bund.zrb.archive.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unified cache/archive entry. Represents ANY cached or cataloged content:
 * web pages (from RechercheAgent), wiki/confluence prefetch, FTP, NDV, etc.
 * <p>
 * This is the single model for both the old {@code archive_entries} (cache)
 * and the old {@code archive_documents} (catalog) tables — now unified in
 * {@code archive_entries}.
 */
public class ArchiveEntry {

    // ── Core fields (original archive_entries) ──
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

    // ── Catalog fields (formerly ArchiveDocument) ──
    private String runId = "";                   // research run ID (empty if not from RechercheAgent)
    private String kind = "PAGE";                // document kind: PAGE, ARTICLE, LISTING, FEED_ENTRY, OTHER
    private String excerpt = "";                 // short text excerpt (max ~1200 chars)
    private String textContentPath = "";         // path to extracted text file (for Lucene)
    private String language = "";                // heuristic language detection
    private int wordCount;                       // word count of extracted text
    private String host = "";                    // hostname for filtering
    private String sourceResourceIds = "";       // comma-separated resource IDs

    // ── Getters & Setters (core) ──

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

    // ── Getters & Setters (catalog) ──

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    public String getTextContentPath() { return textContentPath; }
    public void setTextContentPath(String textContentPath) { this.textContentPath = textContentPath; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getSourceResourceIds() { return sourceResourceIds; }
    public void setSourceResourceIds(String sourceResourceIds) { this.sourceResourceIds = sourceResourceIds; }

    // ── Convenience ──

    /** True if this entry was created by the RechercheAgent (has a run_id). */
    public boolean isFromResearchRun() {
        return runId != null && !runId.isEmpty();
    }

    @Override
    public String toString() {
        return title + " [" + status + "] " + url;
    }
}
