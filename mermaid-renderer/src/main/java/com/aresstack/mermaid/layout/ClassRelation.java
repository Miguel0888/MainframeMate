package com.aresstack.mermaid.layout;

/**
 * A UML class diagram relationship with typed semantics.
 *
 * <h3>Mermaid syntax examples</h3>
 * <pre>
 *   Animal &lt;|-- Dog           INHERITANCE  (hollow triangle, solid)
 *   Animal &lt;|.. Dog           REALIZATION  (hollow triangle, dashed)
 *   Firma  *-- Abteilung      COMPOSITION  (filled diamond)
 *   Firma  o-- Mitarbeiter    AGGREGATION  (hollow diamond)
 *   A --&gt; B                   ASSOCIATION  (arrow)
 *   A ..&gt; B                   DEPENDENCY   (arrow, dashed)
 *   A -- B                    LINK         (plain)
 *   A "1" --&gt; "*" B           with multiplicities
 * </pre>
 */
public class ClassRelation extends DiagramEdge {

    private final RelationType relationType;
    private final String sourceMultiplicity;
    private final String targetMultiplicity;

    public ClassRelation(String id, String sourceId, String targetId, String label,
                         String pathData,
                         double x, double y, double width, double height,
                         RelationType relationType,
                         String sourceMultiplicity, String targetMultiplicity) {
        super(id, sourceId, targetId, label, "relation", pathData,
                x, y, width, height);
        this.relationType = relationType != null ? relationType : RelationType.ASSOCIATION;
        this.sourceMultiplicity = sourceMultiplicity != null ? sourceMultiplicity : "";
        this.targetMultiplicity = targetMultiplicity != null ? targetMultiplicity : "";
    }

    /** The UML relationship type (inheritance, composition, etc.). */
    public RelationType getRelationType() { return relationType; }

    /** Line style, derived from the relation type. */
    public LineStyle getLineStyle() { return relationType.getLineStyle(); }

    /** Arrowhead at target, derived from relation type. */
    public ArrowHead getHeadType() { return relationType.getHeadType(); }

    /** Arrowhead at source, derived from relation type. */
    public ArrowHead getTailType() { return relationType.getTailType(); }

    /** Source-side multiplicity label (e.g. "1", "0..*"). Empty if unset. */
    public String getSourceMultiplicity() { return sourceMultiplicity; }

    /** Target-side multiplicity label (e.g. "*", "1..n"). Empty if unset. */
    public String getTargetMultiplicity() { return targetMultiplicity; }

    /**
     * Generate Mermaid syntax for this relation.
     */
    public String toMermaid() {
        return relationType.toMermaid(getSourceId(), getTargetId(), getLabel());
    }

    @Override
    public String toString() {
        return "ClassRelation{" + getSourceId() + " " + relationType + " " + getTargetId()
                + (getLabel().isEmpty() ? "" : " [" + getLabel() + "]")
                + (sourceMultiplicity.isEmpty() ? "" : " src=\"" + sourceMultiplicity + "\"")
                + (targetMultiplicity.isEmpty() ? "" : " tgt=\"" + targetMultiplicity + "\"")
                + "}";
    }
}

