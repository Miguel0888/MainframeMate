package com.aresstack.mermaid.layout;

/**
 * A flowchart edge with line style and arrowhead information.
 *
 * <h3>Mermaid syntax examples</h3>
 * <pre>
 *   A --&gt; B          SOLID   + NORMAL head
 *   A -.-> B          DASHED  + NORMAL head
 *   A ==&gt; B          THICK   + NORMAL head
 *   A --- B           SOLID   + NONE   head
 *   A -.-  B          DASHED  + NONE   head
 * </pre>
 */
public class FlowchartEdge extends DiagramEdge {

    private final LineStyle lineStyle;
    private final ArrowHead headType;
    private final ArrowHead tailType;

    public FlowchartEdge(String id, String sourceId, String targetId, String label,
                         String pathData,
                         double x, double y, double width, double height,
                         LineStyle lineStyle, ArrowHead headType, ArrowHead tailType) {
        super(id, sourceId, targetId, label, "flowchart-link", pathData,
                x, y, width, height);
        this.lineStyle = lineStyle != null ? lineStyle : LineStyle.SOLID;
        this.headType = headType != null ? headType : ArrowHead.NORMAL;
        this.tailType = tailType != null ? tailType : ArrowHead.NONE;
    }

    /** Visual line style: solid, dashed, or thick. */
    public LineStyle getLineStyle() { return lineStyle; }

    /** Arrowhead at the target end. */
    public ArrowHead getHeadType() { return headType; }

    /** Arrowhead at the source end (usually NONE). */
    public ArrowHead getTailType() { return tailType; }

    /**
     * Generate the Mermaid arrow operator for this edge's style.
     *
     * @return e.g. {@code "-->"}, {@code "-.->|label|"}, {@code "==>"}
     */
    public String toMermaidArrow() {
        String arrow;
        switch (lineStyle) {
            case DASHED:  arrow = headType == ArrowHead.NONE ? "-.-" : "-.->"; break;
            case THICK:   arrow = headType == ArrowHead.NONE ? "===" : "==>"; break;
            default:      arrow = headType == ArrowHead.NONE ? "---" : "-->"; break;
        }
        if (!getLabel().isEmpty()) {
            arrow += "|" + getLabel() + "|";
        }
        return arrow;
    }

    @Override
    public String toString() {
        return "FlowchartEdge{" + getSourceId() + " " + lineStyle + " " + getTargetId()
                + ", head=" + headType + ", tail=" + tailType
                + (getLabel().isEmpty() ? "" : " [" + getLabel() + "]") + "}";
    }
}

