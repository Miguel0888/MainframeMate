package de.bund.zrb.jcl.model;

/**
 * Types of mainframe outline elements (JCL + COBOL) for outline view.
 */
public enum JclElementType {
    // ── JCL ─────────────────────────────────────────────────────────
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
    COMMENT("💬", "Comment"),

    // ── COBOL ───────────────────────────────────────────────────────
    DIVISION("📂", "Division"),
    SECTION("📁", "Section"),
    PARAGRAPH("📝", "Paragraph"),
    DATA_ITEM("🔢", "Data Item"),
    COPY_STMT("📎", "COPY"),
    PERFORM_STMT("🔄", "PERFORM"),
    CALL_STMT("📞", "CALL"),
    FILE_DESCRIPTOR("📄", "FD"),
    PROGRAM_ID("🏷", "PROGRAM-ID"),
    WORKING_STORAGE("💾", "Working-Storage"),
    LINKAGE_SECTION("🔗", "Linkage"),
    FILE_SECTION("📂", "File Section"),
    SCREEN_SECTION("🖥", "Screen Section"),
    PROCEDURE_DIVISION("▶", "Procedure Division"),
    LEVEL_01("📦", "01 Level"),
    LEVEL_77("📦", "77 Level"),
    LEVEL_88("✅", "88 Condition");

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

    public boolean isCobol() {
        return ordinal() >= DIVISION.ordinal();
    }

    public boolean isJcl() {
        return ordinal() < DIVISION.ordinal();
    }
}

