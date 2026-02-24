package de.bund.zrb.indexing.model;

/**
 * How to handle access control for indexed items.
 */
public enum SecurityMode {
    /** No ACL filtering (all results visible to all users). */
    NONE,
    /** Derive ACL tags from source path structure. */
    PATH_BASED,
    /** Read ACL from source metadata (file permissions, mail folders). */
    SOURCE_ACL
}
