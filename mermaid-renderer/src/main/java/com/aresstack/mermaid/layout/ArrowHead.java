package com.aresstack.mermaid.layout;

/**
 * Arrowhead (marker) type on an edge endpoint.
 *
 * <h3>UML / class-diagram mapping</h3>
 * <pre>
 *   NORMAL           →  ▸  filled triangle (association)
 *   TRIANGLE_OPEN    →  ▹  hollow triangle (inheritance / realization)
 *   DIAMOND_FILLED   →  ◆  filled diamond  (composition)
 *   DIAMOND_OPEN     →  ◇  hollow diamond  (aggregation)
 *   NONE             →  —  no arrowhead (plain line)
 *   CIRCLE           →  ○  circle (crow's-foot "one")
 *   CROSS            →  ×  cross
 * </pre>
 */
public enum ArrowHead {

    /** Standard filled arrowhead (▸). */
    NORMAL("Pfeil (ausgefüllt)"),

    /** Hollow / open triangle (▹) — used for inheritance / realization. */
    TRIANGLE_OPEN("Dreieck (offen)"),

    /** Filled diamond (◆) — composition. */
    DIAMOND_FILLED("Raute (ausgefüllt)"),

    /** Hollow diamond (◇) — aggregation. */
    DIAMOND_OPEN("Raute (offen)"),

    /** Circle marker — used in ER "one" cardinality. */
    CIRCLE("Kreis"),

    /** Cross marker (×). */
    CROSS("Kreuz"),

    /** No arrowhead at all. */
    NONE("Kein Pfeil");

    private final String displayName;

    ArrowHead(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable name for UI. */
    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}

