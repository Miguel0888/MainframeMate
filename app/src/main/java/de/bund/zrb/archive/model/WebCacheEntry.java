package de.bund.zrb.archive.model;

/**
 * Tracks a discovered URL in the web-cache.  The Recherche-Agent uses this
 * to know which pages still need to be visited.
 */
public class WebCacheEntry {

    private String url = "";
    private String sourceId = "";
    private ArchiveEntryStatus status = ArchiveEntryStatus.PENDING;
    private int depth;                      // link depth (0 = start URL)
    private String parentUrl = "";          // page where this URL was discovered
    private long discoveredAt;              // epoch millis
    private String archiveEntryId = "";     // reference to ArchiveEntry (after crawl)

    // ── Getters & Setters ──

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public ArchiveEntryStatus getStatus() { return status; }
    public void setStatus(ArchiveEntryStatus status) { this.status = status; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public String getParentUrl() { return parentUrl; }
    public void setParentUrl(String parentUrl) { this.parentUrl = parentUrl; }

    public long getDiscoveredAt() { return discoveredAt; }
    public void setDiscoveredAt(long discoveredAt) { this.discoveredAt = discoveredAt; }

    public String getArchiveEntryId() { return archiveEntryId; }
    public void setArchiveEntryId(String archiveEntryId) { this.archiveEntryId = archiveEntryId; }

    @Override
    public String toString() {
        return "[" + status + "] " + url + " (depth=" + depth + ")";
    }
}
