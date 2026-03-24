package com.aresstack.mermaid.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A rendered diagram node with its position and dimensions.
 * Coordinates are in SVG user-space units (typically pixels).
 *
 * <p>Base class for all diagram-specific node types.
 * Subclasses add type-specific properties:
 * <ul>
 *   <li>{@link FlowchartNode} — node shape (rectangle, diamond, circle, …)</li>
 *   <li>{@link ClassNode} — stereotype, fields, methods</li>
 *   <li>{@link ErEntityNode} — entity attributes with types and PK/FK</li>
 *   <li>{@link SequenceActorNode} — actor type (participant, actor)</li>
 *   <li>{@link StateDiagramNode} — start/end state markers</li>
 *   <li>{@link MindmapItemNode} — depth in the tree</li>
 *   <li>{@link RequirementItemNode} — requirement metadata</li>
 * </ul>
 *
 * <p>For diagram types without a specialised subclass, the base
 * {@code DiagramNode} is used and ad-hoc properties can be stored
 * in {@link #getProperties()}.
 *
 * <p>Immutable value object — safe to share across threads.
 */
public class DiagramNode {

    private final String id;
    private final String label;
    private final String kind;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final String svgId;
    private final Map<String, String> properties;

    public DiagramNode(String id, String label, String kind,
                       double x, double y, double width, double height,
                       String svgId) {
        this(id, label, kind, x, y, width, height, svgId,
                Collections.<String, String>emptyMap());
    }

    public DiagramNode(String id, String label, String kind,
                       double x, double y, double width, double height,
                       String svgId, Map<String, String> properties) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.svgId = svgId;
        this.properties = properties != null && !properties.isEmpty()
                ? Collections.unmodifiableMap(new LinkedHashMap<String, String>(properties))
                : Collections.<String, String>emptyMap();
    }

    /** Logical node identifier derived from the Mermaid source, e.g. "A", "Customer". */
    public String getId() { return id; }

    /** Visible label text of this node. */
    public String getLabel() { return label; }

    /**
     * Node kind/type. One of:
     * {@code "node"}, {@code "entity"}, {@code "class"}, {@code "actor"},
     * {@code "mindmap-node"}, {@code "state"}, {@code "requirement"}.
     */
    public String getKind() { return kind; }

    /** Left edge x-coordinate (SVG user-space). */
    public double getX() { return x; }

    /** Top edge y-coordinate (SVG user-space). */
    public double getY() { return y; }

    /** Node width in SVG user-space units. */
    public double getWidth() { return width; }

    /** Node height in SVG user-space units. */
    public double getHeight() { return height; }

    /** Centre x-coordinate. */
    public double getCenterX() { return x + width / 2.0; }

    /** Centre y-coordinate. */
    public double getCenterY() { return y + height / 2.0; }

    /** Raw SVG {@code id} attribute of the {@code <g>} element, e.g. {@code "flowchart-A-0"}. */
    public String getSvgId() { return svgId; }

    /**
     * Additional ad-hoc properties for diagram types without a dedicated
     * subclass.  Returns an unmodifiable map (may be empty, never null).
     */
    public Map<String, String> getProperties() { return properties; }

    /**
     * Test whether a point (in SVG user-space) falls inside this node's
     * bounding box.  Useful for hit-testing / click detection.
     */
    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "', label='" + label
                + "', kind='" + kind
                + "', bounds=[" + fmt(x) + "," + fmt(y) + " "
                + fmt(width) + "x" + fmt(height) + "]}";
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }
}
