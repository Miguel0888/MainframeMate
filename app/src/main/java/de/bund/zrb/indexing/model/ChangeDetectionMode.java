package de.bund.zrb.indexing.model;

/**
 * How to detect whether a source item has changed since last indexing.
 */
public enum ChangeDetectionMode {
    /** Compare last-modified timestamp + file size (fast, default). */
    MTIME_SIZE,
    /** Compute content hash (SHA-256) – slower but reliable. */
    CONTENT_HASH,
    /** Use both: mtime+size as fast-path, hash on conflict. */
    MTIME_THEN_HASH
}
