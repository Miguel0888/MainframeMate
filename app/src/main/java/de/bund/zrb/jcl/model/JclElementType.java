package de.bund.zrb.jcl.model;

/**
 * Types of mainframe outline elements (JCL + COBOL + Natural) for outline view.
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
    LEVEL_88("✅", "88 Condition"),

    // ── Natural (Software AG) ───────────────────────────────────────
    NAT_DEFINE_DATA("💾", "DEFINE DATA"),
    NAT_LOCAL("📦", "LOCAL"),
    NAT_PARAMETER("🔗", "PARAMETER"),
    NAT_GLOBAL("🌐", "GLOBAL"),
    NAT_INDEPENDENT("📌", "INDEPENDENT"),
    NAT_DATA_VIEW("📊", "VIEW"),
    NAT_DATA_VAR("🔢", "Variable"),
    NAT_DATA_REDEFINE("🔄", "REDEFINE"),
    NAT_DATA_CONST("📌", "CONST"),
    NAT_SUBROUTINE("📦", "SUBROUTINE"),
    NAT_INLINE_SUBROUTINE("📦", "Inline Subroutine"),
    NAT_PERFORM("🔄", "PERFORM"),
    NAT_CALLNAT("📞", "CALLNAT"),
    NAT_CALL("📞", "CALL"),
    NAT_FETCH("📞", "FETCH"),
    NAT_SUBPROGRAM("📦", "SUBPROGRAM"),
    NAT_PROGRAM("🏷", "PROGRAM"),
    NAT_FUNCTION("⚡", "FUNCTION"),
    NAT_MAP("🖥", "MAP"),
    NAT_HELPROUTINE("❓", "HELPROUTINE"),
    NAT_COPYCODE("📎", "COPYCODE"),
    NAT_READ("📖", "READ"),
    NAT_FIND("🔍", "FIND"),
    NAT_HISTOGRAM("📊", "HISTOGRAM"),
    NAT_STORE("💾", "STORE"),
    NAT_UPDATE("✏", "UPDATE"),
    NAT_DELETE("🗑", "DELETE"),
    NAT_GET("📖", "GET"),
    NAT_DECIDE("❓", "DECIDE"),
    NAT_IF_BLOCK("❓", "IF Block"),
    NAT_FOR("🔄", "FOR"),
    NAT_REPEAT("🔄", "REPEAT"),
    NAT_INPUT("🖥", "INPUT"),
    NAT_WRITE("📝", "WRITE"),
    NAT_DISPLAY("📝", "DISPLAY"),
    NAT_PRINT("🖨", "PRINT"),
    NAT_ON_ERROR("⚠", "ON ERROR"),
    NAT_INCLUDE("📎", "INCLUDE"),
    NAT_END("🔚", "END"),

    // ── DDM (Data Definition Module) ────────────────────────────────
    DDM_HEADER("🗃", "DDM Header"),
    DDM_FIELD("🔹", "Feld"),
    DDM_GROUP("📂", "Gruppe"),
    DDM_DESCRIPTOR("🔑", "Deskriptor"),
    DDM_SUPERDESCRIPTOR("🔗", "Superdeskriptor");

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
        return ordinal() >= DIVISION.ordinal() && ordinal() <= LEVEL_88.ordinal();
    }

    public boolean isNatural() {
        return ordinal() >= NAT_DEFINE_DATA.ordinal() && ordinal() <= NAT_END.ordinal();
    }

    public boolean isDdm() {
        return ordinal() >= DDM_HEADER.ordinal();
    }

    public boolean isJcl() {
        return ordinal() < DIVISION.ordinal();
    }
}

