package de.bund.zrb.ui.preview;

/**
 * View modes for the Document Preview/Editor Tab.
 */
public enum ViewMode {
    /**
     * Split view: Raw (left) + Rendered (right)
     */
    SPLIT("Split"),

    /**
     * Only rendered/formatted view
     */
    RENDERED_ONLY("Gerendert"),

    /**
     * Only raw/unformatted view
     */
    RAW_ONLY("Raw");

    private final String displayName;

    ViewMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

