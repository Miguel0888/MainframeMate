package de.bund.zrb.indexing.model;

/**
 * Processing state of a single item in the index.
 */
public enum IndexItemState {
    /** Detected by scanner, not yet processed. */
    PENDING,
    /** Successfully extracted, chunked, and indexed. */
    INDEXED,
    /** Processing failed (see error message in IndexItemStatus). */
    ERROR,
    /** Skipped due to filter/size/type policy. */
    SKIPPED,
    /** Item was deleted from source → tombstone in index. */
    DELETED
}
