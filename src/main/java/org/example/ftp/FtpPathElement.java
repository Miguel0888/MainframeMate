package org.example.ftp;

public class FtpPathElement {
    private final String name;
    private final boolean isPds;
    private final String member; // kann null oder leer sein

    public FtpPathElement(String name, boolean isPds, String member) {
        this.name = name;
        this.isPds = isPds;
        this.member = member;
    }

    /**
     * Return true if the element represents a PDS member.
     */
    public boolean isPdsMember() {
        return isPds && member != null && !member.isEmpty();
    }

    /**
     * Return true if the element represents a PDS listing (e.g., DATASET()).
     */
    public boolean isPdsListing() {
        return isPds && (member == null || member.isEmpty());
    }

    /**
     * Return true if this element uses PDS syntax.
     */
    public boolean isPdsSyntaxUsed() {
        return isPds;
    }

    /**
     * Return true if this element is blank (no name).
     */
    public boolean isBlank() {
        return name == null || name.trim().isEmpty();
    }

    public String getName() {
        return name;
    }

    public String getMember() {
        return member;
    }

    public String getDisplayName() {
        return isPds && member != null ? name + "(" + member + ")" : name;
    }
}
