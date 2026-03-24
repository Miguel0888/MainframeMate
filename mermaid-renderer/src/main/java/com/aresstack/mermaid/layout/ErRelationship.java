package com.aresstack.mermaid.layout;

/**
 * An ER diagram relationship with cardinality information.
 *
 * <h3>Mermaid syntax examples</h3>
 * <pre>
 *   AUTOR ||--o{ BUCH : schreibt     (one to many)
 *   BUCH  }o--|| VERLAG : verlegt    (many to one)
 *   A     ||--|| B : hat             (one to one)
 * </pre>
 */
public class ErRelationship extends DiagramEdge {

    private final ErCardinality sourceCardinality;
    private final ErCardinality targetCardinality;
    private final boolean identifying;

    public ErRelationship(String id, String sourceId, String targetId, String label,
                          String pathData,
                          double x, double y, double width, double height,
                          ErCardinality sourceCardinality,
                          ErCardinality targetCardinality,
                          boolean identifying) {
        super(id, sourceId, targetId, label, "er-link", pathData,
                x, y, width, height);
        this.sourceCardinality = sourceCardinality != null
                ? sourceCardinality : ErCardinality.EXACTLY_ONE;
        this.targetCardinality = targetCardinality != null
                ? targetCardinality : ErCardinality.EXACTLY_ONE;
        this.identifying = identifying;
    }

    /** Cardinality at the source end. */
    public ErCardinality getSourceCardinality() { return sourceCardinality; }

    /** Cardinality at the target end. */
    public ErCardinality getTargetCardinality() { return targetCardinality; }

    /** True if this is an identifying relationship (double line). */
    public boolean isIdentifying() { return identifying; }

    /**
     * Generate Mermaid syntax for this ER relationship.
     */
    public String toMermaid() {
        String src = sourceCardinality.getMermaidSyntax();
        String tgt = targetCardinality.getMermaidSyntax();
        String connector = identifying ? "==" : "--";
        String line = getSourceId() + " " + src + connector + tgt + " " + getTargetId();
        if (!getLabel().isEmpty()) {
            line += " : " + getLabel();
        }
        return line;
    }

    @Override
    public String toString() {
        return "ErRelationship{" + getSourceId()
                + " " + sourceCardinality + "--" + targetCardinality
                + " " + getTargetId()
                + (getLabel().isEmpty() ? "" : " [" + getLabel() + "]") + "}";
    }
}

