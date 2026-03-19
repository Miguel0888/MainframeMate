package de.bund.zrb.indexing.model;

/**
 * Types of data sources that can be indexed.
 */
public enum SourceType {
    LOCAL("Lokale Dateien"),
    FTP("FTP/Mainframe"),
    NDV("NDV/Natural"),
    MAIL("E-Mail (OST/PST)"),
    WEB("Web/URL"),
    BETAVIEW("BetaView"),
    WIKI("Wiki"),
    CONFLUENCE("Confluence"),
    SHAREPOINT("SharePoint");

    private final String displayName;

    SourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
