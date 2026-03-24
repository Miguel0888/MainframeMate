package com.aresstack.mermaid.layout;

/**
 * A flowchart / graph node with a specific visual shape.
 *
 * <p>Extends {@link DiagramNode} with the node's {@link NodeShape}
 * so the UI can display a shape picker and generate correct Mermaid syntax.
 *
 * <h3>Example</h3>
 * <pre>
 *   FlowchartNode node = (FlowchartNode) diagram.findNodeById("A");
 *   NodeShape shape = node.getShape();   // e.g. DIAMOND
 *   // Change shape in UI → regenerate Mermaid:
 *   String newSyntax = NodeShape.HEXAGON.toMermaid("A", node.getLabel());
 * </pre>
 */
public class FlowchartNode extends DiagramNode {

    private final NodeShape shape;

    public FlowchartNode(String id, String label,
                         double x, double y, double width, double height,
                         String svgId, NodeShape shape) {
        super(id, label, "node", x, y, width, height, svgId);
        this.shape = shape != null ? shape : NodeShape.RECTANGLE;
    }

    /** The visual shape of this flowchart node. */
    public NodeShape getShape() { return shape; }

    @Override
    public String toString() {
        return "FlowchartNode{id='" + getId() + "', shape=" + shape
                + ", label='" + getLabel() + "'"
                + ", bounds=[" + fmt(getX()) + "," + fmt(getY()) + " "
                + fmt(getWidth()) + "x" + fmt(getHeight()) + "]}";
    }
}

