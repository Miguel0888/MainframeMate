package de.bund.zrb.betaview.domain;

/**
 * Stable reference to a single BetaView document (search result row).
 * Carries enough information to reload the document content later.
 */
public final class BetaViewDocumentRef {

    private final String displayName;
    private final String actionPath;
    private final String category;

    public BetaViewDocumentRef(String displayName, String actionPath, String category) {
        this.displayName = displayName != null ? displayName : "";
        this.actionPath = actionPath != null ? actionPath : "";
        this.category = category != null ? category : "";
    }

    /** Human-readable title for the tab / result list. */
    public String displayName() { return displayName; }

    /** Relative action path (e.g. "show.action?docId=123") used to load content. */
    public String actionPath() { return actionPath; }

    /** Optional category (e.g. folder, form type). */
    public String category() { return category; }

    @Override
    public String toString() {
        return "BetaViewDocumentRef{" + displayName + ", path=" + actionPath + "}";
    }
}

