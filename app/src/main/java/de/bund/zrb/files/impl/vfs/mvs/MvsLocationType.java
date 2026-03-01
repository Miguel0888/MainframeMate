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
     * Legacy HLQ typSchluessel for top-level qualifier context (e.g. BENUTZERKENNUNG).
     */
    HLQ,

    /**
     * Qualifier browse context (HLQ or nested qualifier prefixes).
     * Listing requires querying '<logicalPath>.*'.
     */
    QUALIFIER_CONTEXT,

    /**
     * A concrete dataset (e.g., 'BENUTZERKENNUNG.DATA.SET').
     */
    DATASET,

    /**
     * A member of a PDS (e.g., 'BENUTZERKENNUNG.PDS(MEMBER)').
     */
    MEMBER
}
