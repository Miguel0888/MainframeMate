package de.bund.zrb.files.impl.vfs.mvs;

/**
 * Type of MVS location in the dataset hierarchy.
 * Used for state-based navigation instead of string guessing.
 */
public enum MvsLocationType {
    /**
     * Root of the MVS file system (no HLQ selected).
     */
    ROOT,

    /**
     * High-Level Qualifier (e.g., 'USERID').
     * Not a directory - it's a search context.
     * Listing requires querying 'HLQ.*'
     */
    HLQ,

    /**
     * A dataset (e.g., 'USERID.DATA.SET').
     * Can be either PDS (partitioned) or SEQ (sequential).
     * Needs probing to determine type.
     */
    DATASET,

    /**
     * A member of a PDS (e.g., 'USERID.PDS(MEMBER)').
     * This is always a "file" that can be opened.
     */
    MEMBER
}

