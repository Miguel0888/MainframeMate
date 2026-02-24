package de.bund.zrb.indexing.model;

/**
 * Direction/order in which items are indexed.
 */
public enum IndexDirection {
    /** Index newest items first, then work backwards in time. */
    NEWEST_FIRST,
    /** Index oldest items first (chronological order). */
    OLDEST_FIRST,
    /** No specific order (as discovered by scanner). */
    UNORDERED
}
