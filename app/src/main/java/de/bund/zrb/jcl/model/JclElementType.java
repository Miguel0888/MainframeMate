package de.bund.zrb.jcl.model;

/**
 * Types of JCL elements for outline view.
 */
public enum JclElementType {
    JOB("📋", "JOB"),
    EXEC("▶", "EXEC"),
    DD("📄", "DD"),
    PROC("📦", "PROC"),
    PEND("📦", "PEND"),
    SET("⚙", "SET"),
    INCLUDE("📎", "INCLUDE"),
    JCLLIB("📚", "JCLLIB"),
    IF("❓", "IF"),
    ELSE("❓", "ELSE"),
    ENDIF("❓", "ENDIF"),
    OUTPUT("📤", "OUTPUT"),
    COMMENT("💬", "Comment");

    private final String icon;
    private final String displayName;

    JclElementType(String icon, String displayName) {
        this.icon = icon;
        this.displayName = displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }
}

