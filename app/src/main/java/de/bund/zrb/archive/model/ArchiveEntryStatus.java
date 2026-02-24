package de.bund.zrb.archive.model;

/**
 * Status of an archive entry or web-cache entry.
 */
public enum ArchiveEntryStatus {
    /** URL known, but not yet visited / file not yet processed. */
    PENDING,
    /** Page visited / file imported, snapshot exists, not yet indexed. */
    CRAWLED,
    /** Fully processed: snapshot + Lucene index (+ optional embedding). */
    INDEXED,
    /** Error during crawl or processing. */
    FAILED
}
