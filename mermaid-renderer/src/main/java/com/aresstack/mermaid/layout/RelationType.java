package com.aresstack.mermaid.layout;

/**
 * UML relationship types used in class diagrams.
 *
 * <h3>Mermaid syntax mapping</h3>
 * <pre>
 *   INHERITANCE   →  A &lt;|-- B     (hollow triangle, solid line)
 *   REALIZATION   →  A &lt;|.. B     (hollow triangle, dashed line)
 *   COMPOSITION   →  A *-- B      (filled diamond, solid line)
 *   AGGREGATION   →  A o-- B      (hollow diamond, solid line)
 *   ASSOCIATION   →  A --&gt; B      (arrow, solid line)
 *   DEPENDENCY    →  A ..&gt; B      (arrow, dashed line)
 *   LINK          →  A -- B       (no arrow, solid line)
 * </pre>
 */
public enum RelationType {

    /** Solid line + filled arrowhead: {@code A --> B} */
    ASSOCIATION(LineStyle.SOLID, ArrowHead.NORMAL, ArrowHead.NONE, "-->", "Assoziation"),

    /** Solid line + hollow triangle: {@code A <|-- B} (B inherits from A) */
    INHERITANCE(LineStyle.SOLID, ArrowHead.TRIANGLE_OPEN, ArrowHead.NONE, "<|--", "Vererbung"),

    /** Dashed line + hollow triangle: {@code A <|.. B} (B realizes A) */
    REALIZATION(LineStyle.DOTTED, ArrowHead.TRIANGLE_OPEN, ArrowHead.NONE, "<|..", "Realisierung"),

    /** Solid line + filled diamond at source: {@code A *-- B} */
    COMPOSITION(LineStyle.SOLID, ArrowHead.NONE, ArrowHead.DIAMOND_FILLED, "*--", "Komposition"),

    /** Solid line + hollow diamond at source: {@code A o-- B} */
    AGGREGATION(LineStyle.SOLID, ArrowHead.NONE, ArrowHead.DIAMOND_OPEN, "o--", "Aggregation"),

    /** Dashed line + filled arrowhead: {@code A ..> B} */
    DEPENDENCY(LineStyle.DOTTED, ArrowHead.NORMAL, ArrowHead.NONE, "..>", "Abhängigkeit"),

    /** Solid line, no arrowheads: {@code A -- B} */
    LINK(LineStyle.SOLID, ArrowHead.NONE, ArrowHead.NONE, "--", "Verbindung");

    private final LineStyle lineStyle;
    private final ArrowHead headType;
    private final ArrowHead tailType;
    private final String mermaidSyntax;
    private final String displayName;

    RelationType(LineStyle lineStyle, ArrowHead headType, ArrowHead tailType,
                 String mermaidSyntax, String displayName) {
        this.lineStyle = lineStyle;
        this.headType = headType;
        this.tailType = tailType;
        this.mermaidSyntax = mermaidSyntax;
        this.displayName = displayName;
    }

    /** Line style (solid or dotted). */
    public LineStyle getLineStyle() { return lineStyle; }

    /** Arrowhead at the target end. */
    public ArrowHead getHeadType() { return headType; }

    /** Arrowhead at the source end. */
    public ArrowHead getTailType() { return tailType; }

    /** Mermaid syntax for this relation. */
    public String getMermaidSyntax() { return mermaidSyntax; }

    /** Human-readable name. */
    public String getDisplayName() { return displayName; }

    /**
     * Generate Mermaid syntax for a class relation.
     *
     * @param sourceId source class name
     * @param targetId target class name
     * @param label    optional label (may be null)
     * @return e.g. {@code "Animal <|-- Dog : extends"}
     */
    public String toMermaid(String sourceId, String targetId, String label) {
        String line = sourceId + " " + mermaidSyntax + " " + targetId;
        if (label != null && !label.isEmpty()) {
            line += " : " + label;
        }
        return line;
    }

    @Override
    public String toString() { return displayName; }
}

