package com.aresstack.mermaid.layout;

/**
 * A rendered diagram node with its position and dimensions.
 * Coordinates are in SVG user-space units (typically pixels).
 *
 * <p>Immutable value object — safe to share across threads.
 */
public final class DiagramNode {

    private final String id;
    private final String label;
    private final String kind;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final String svgId;

    public DiagramNode(String id, String label, String kind,
                       double x, double y, double width, double height,
                       String svgId) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.svgId = svgId;
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
     * Test whether a point (in SVG user-space) falls inside this node's
     * bounding box.  Useful for hit-testing / click detection.
     */
    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    @Override
    public String toString() {
        return "DiagramNode{id='" + id + "', label='" + label + "', kind='" + kind
                + "', bounds=[" + fmt(x) + "," + fmt(y) + " " + fmt(width) + "x" + fmt(height) + "]}";
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }
}

