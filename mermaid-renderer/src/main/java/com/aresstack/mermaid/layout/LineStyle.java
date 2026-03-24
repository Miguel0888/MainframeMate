package com.aresstack.mermaid.layout;

/**
 * Visual line style of an edge / connection.
 *
 * <h3>Mermaid syntax mapping (flowchart)</h3>
 * <pre>
 *   SOLID   →  A --&gt; B        (durchgezogene Linie)
 *   DASHED  →  A -.-> B        (gestrichelte Linie)
 *   THICK   →  A ==&gt; B        (dicke Linie)
 *   DOTTED  →  A ···> B        (gepunktete Linie)
 * </pre>
 */
public enum LineStyle {

    /** Normal solid line: {@code -->} */
    SOLID("-->", "Durchgezogen"),

    /** Dashed line: {@code -.->} */
    DASHED("-.->", "Gestrichelt"),

    /** Thick / bold line: {@code ==>} */
    THICK("==>", "Dick"),

    /** Dotted line (used in some class diagram relations). */
    DOTTED("..>", "Gepunktet");

    private final String mermaidSyntax;
    private final String displayName;

    LineStyle(String mermaidSyntax, String displayName) {
        this.mermaidSyntax = mermaidSyntax;
        this.displayName = displayName;
    }

    /** Representative Mermaid arrow syntax. */
    public String getMermaidSyntax() { return mermaidSyntax; }

    /** Human-readable name for UI. */
    public String getDisplayName() { return displayName; }

    /**
     * Detect line style from SVG stroke attributes.
     *
     * @param strokeDasharray the {@code stroke-dasharray} CSS value, or null
     * @param strokeWidth     the {@code stroke-width} CSS value, or 0
     * @return detected line style
     */
    public static LineStyle detect(String strokeDasharray, double strokeWidth) {
        if (strokeDasharray != null && !strokeDasharray.isEmpty()
                && !"none".equalsIgnoreCase(strokeDasharray) && !"0".equals(strokeDasharray)) {
            return DASHED;
        }
        if (strokeWidth > 2.5) {
            return THICK;
        }
        return SOLID;
    }

    @Override
    public String toString() { return displayName; }
}

