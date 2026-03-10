package de.bund.zrb.betaview.ui;

/**
 * Represents a server-side document tab.
 */
public final class DocumentTab {

    private final String docId;
    private final String favId;
    private final String linkID;     // for closeSingleDocument.action?linkID=XXX
    private final String title;
    private final String timestamp;
    private final String openAction; // opendocumentlink.action?docid=XXX&favid=YYY
    private final boolean active;

    public DocumentTab(String docId, String favId, String linkID,
                       String title, String timestamp, String openAction, boolean active) {
        this.docId = docId;
        this.favId = favId;
        this.linkID = linkID;
        this.title = title;
        this.timestamp = timestamp;
        this.openAction = openAction;
        this.active = active;
    }

    public String docId()     { return docId; }
    public String favId()     { return favId; }
    public String linkID()    { return linkID; }
    public String title()     { return title; }
    public String timestamp() { return timestamp; }
    public String openAction(){ return openAction; }
    public boolean isActive() { return active; }

    /** Unique key for the tab. */
    public String key() {
        return linkID != null && !linkID.isEmpty() ? linkID : openAction;
    }

    @Override
    public String toString() {
        return timestamp + " " + title;
    }
}

