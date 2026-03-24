package com.aresstack.mermaid.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Complete render result of a Mermaid diagram: the SVG markup plus
 * extracted layout metadata for every node and edge.
 *
 * <p>Obtain via {@code Mermaid.renderWithLayout(diagramCode)} or
 * {@code DiagramLayoutExtractor.extract(svg)}.
 *
 * <h3>Usage example</h3>
 * <pre>
 *   RenderedDiagram diagram = Mermaid.renderWithLayout("graph TD; A--&gt;B;");
 *   String svg = diagram.getSvg();
 *
 *   for (DiagramNode node : diagram.getNodes()) {
 *       System.out.println(node.getId() + " at (" + node.getX() + "," + node.getY() + ")");
 *   }
 *
 *   // Hit-test: which node was clicked at (120, 45)?
 *   DiagramNode clicked = diagram.findNodeAt(120, 45);
 * </pre>
 *
 * <p>Immutable once constructed.
 */
public final class RenderedDiagram {

    private final String svg;
    private final String diagramType;
    private final List<DiagramNode> nodes;
    private final List<DiagramEdge> edges;
    private final double viewBoxX;
    private final double viewBoxY;
    private final double viewBoxWidth;
    private final double viewBoxHeight;

    public RenderedDiagram(String svg, String diagramType,
                           List<DiagramNode> nodes, List<DiagramEdge> edges,
                           double viewBoxX, double viewBoxY,
                           double viewBoxWidth, double viewBoxHeight) {
        this.svg = svg;
        this.diagramType = diagramType;
        this.nodes = Collections.unmodifiableList(new ArrayList<DiagramNode>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<DiagramEdge>(edges));
        this.viewBoxX = viewBoxX;
        this.viewBoxY = viewBoxY;
        this.viewBoxWidth = viewBoxWidth;
        this.viewBoxHeight = viewBoxHeight;
    }

    /** The fully processed SVG string (ready to display or rasterise). */
    public String getSvg() { return svg; }

    /**
     * Detected diagram type. One of:
     * {@code "flowchart"}, {@code "sequence"}, {@code "classDiagram"},
     * {@code "erDiagram"}, {@code "mindmap"}, {@code "stateDiagram"},
     * {@code "gantt"}, {@code "pie"}, {@code "requirement"}, {@code "journey"},
     * {@code "gitGraph"}, {@code "c4"}, {@code "unknown"}.
     */
    public String getDiagramType() { return diagramType; }

    /** All nodes found in the diagram, in document order. */
    public List<DiagramNode> getNodes() { return nodes; }

    /** All edges/connections found in the diagram, in document order. */
    public List<DiagramEdge> getEdges() { return edges; }

    /** SVG viewBox origin X. */
    public double getViewBoxX() { return viewBoxX; }

    /** SVG viewBox origin Y. */
    public double getViewBoxY() { return viewBoxY; }

    /** SVG viewBox width. */
    public double getViewBoxWidth() { return viewBoxWidth; }

    /** SVG viewBox height. */
    public double getViewBoxHeight() { return viewBoxHeight; }

    // ── Convenience finders ──────────────────────────────────

    /**
     * Find a node by its logical id (case-sensitive).
     *
     * @param id the node id (e.g. "A", "Customer")
     * @return the node, or {@code null} if not found
     */
    public DiagramNode findNodeById(String id) {
        if (id == null) return null;
        for (DiagramNode n : nodes) {
            if (id.equals(n.getId())) return n;
        }
        return null;
    }

    /**
     * Find the first node whose bounding box contains the given point.
     * Useful for click/hover hit-testing.
     *
     * @param x x-coordinate in SVG user-space
     * @param y y-coordinate in SVG user-space
     * @return the node, or {@code null} if no node contains the point
     */
    public DiagramNode findNodeAt(double x, double y) {
        for (DiagramNode n : nodes) {
            if (n.contains(x, y)) return n;
        }
        return null;
    }

    /**
     * Find all edges connecting a specific node (as source or target).
     *
     * @param nodeId the logical node id
     * @return edges connected to this node (may be empty, never null)
     */
    public List<DiagramEdge> findEdgesFor(String nodeId) {
        if (nodeId == null) return Collections.emptyList();
        List<DiagramEdge> result = new ArrayList<DiagramEdge>();
        for (DiagramEdge e : edges) {
            if (nodeId.equals(e.getSourceId()) || nodeId.equals(e.getTargetId())) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Find edges between two specific nodes.
     *
     * @param sourceId source node id
     * @param targetId target node id
     * @return matching edges (may be empty)
     */
    public List<DiagramEdge> findEdgesBetween(String sourceId, String targetId) {
        List<DiagramEdge> result = new ArrayList<DiagramEdge>();
        for (DiagramEdge e : edges) {
            if (sourceId.equals(e.getSourceId()) && targetId.equals(e.getTargetId())) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Find a node by its raw SVG element id (e.g. {@code "flowchart-A-0"}).
     * This is the reverse of {@link DiagramNode#getSvgId()} and is needed
     * when mapping a click on an SVG element back to a logical node.
     *
     * @param svgId the SVG id attribute value
     * @return the node, or {@code null} if not found
     */
    public DiagramNode findNodeBySvgId(String svgId) {
        if (svgId == null) return null;
        for (DiagramNode n : nodes) {
            if (svgId.equals(n.getSvgId())) return n;
        }
        return null;
    }

    /**
     * Find an edge by its computed id (e.g. {@code "A->B"}).
     *
     * @param edgeId the edge id
     * @return the edge, or {@code null} if not found
     */
    public DiagramEdge findEdgeById(String edgeId) {
        if (edgeId == null) return null;
        for (DiagramEdge e : edges) {
            if (edgeId.equals(e.getId())) return e;
        }
        return null;
    }

    /**
     * Collect all SVG element ids of nodes and edges — useful for
     * programmatically walking the SVG DOM for highlighting.
     *
     * @return list of all SVG ids (nodes first, then edges)
     */
    public List<String> getAllSvgIds() {
        List<String> ids = new ArrayList<String>();
        for (DiagramNode n : nodes) {
            if (n.getSvgId() != null && !n.getSvgId().isEmpty()) {
                ids.add(n.getSvgId());
            }
        }
        return ids;
    }

    // ── Typed finders ────────────────────────────────────────

    /**
     * Get all nodes of a specific subtype.
     * <pre>
     *   List&lt;ClassNode&gt; classes = diagram.getNodesOfType(ClassNode.class);
     * </pre>
     *
     * @param type the node subclass to filter by
     * @return matching nodes (may be empty, never null)
     */
    public <T extends DiagramNode> List<T> getNodesOfType(Class<T> type) {
        List<T> result = new ArrayList<T>();
        for (DiagramNode n : nodes) {
            if (type.isInstance(n)) {
                result.add(type.cast(n));
            }
        }
        return result;
    }

    /**
     * Get all edges of a specific subtype.
     * <pre>
     *   List&lt;FlowchartEdge&gt; links = diagram.getEdgesOfType(FlowchartEdge.class);
     * </pre>
     *
     * @param type the edge subclass to filter by
     * @return matching edges (may be empty, never null)
     */
    public <T extends DiagramEdge> List<T> getEdgesOfType(Class<T> type) {
        List<T> result = new ArrayList<T>();
        for (DiagramEdge e : edges) {
            if (type.isInstance(e)) {
                result.add(type.cast(e));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "RenderedDiagram{type='" + diagramType + "', nodes=" + nodes.size()
                + ", edges=" + edges.size()
                + ", viewBox=[" + viewBoxX + "," + viewBoxY + " "
                + viewBoxWidth + "x" + viewBoxHeight + "]}";
    }
}
