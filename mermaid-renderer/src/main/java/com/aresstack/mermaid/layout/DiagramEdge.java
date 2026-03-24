package com.aresstack.mermaid.layout;

/**
 * A rendered diagram edge (connection/relationship) with its geometry.
 * Coordinates are in SVG user-space units.
 *
 * <p>Immutable value object — safe to share across threads.
 */
public final class DiagramEdge {

    private final String id;
    private final String sourceId;
    private final String targetId;
    private final String label;
    private final String kind;
    private final String pathData;
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public DiagramEdge(String id, String sourceId, String targetId, String label,
                       String kind, String pathData,
                       double x, double y, double width, double height) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.label = label;
        this.kind = kind;
        this.pathData = pathData;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Edge identifier, derived from source→target or from the SVG id attribute.
     * May be auto-generated (e.g. {@code "A->B"}) if no explicit id exists.
     */
    public String getId() { return id; }

    /** Logical id of the source node. */
    public String getSourceId() { return sourceId; }

    /** Logical id of the target node. */
    public String getTargetId() { return targetId; }

    /** Edge label text, or empty string if unlabelled. */
    public String getLabel() { return label; }

    /**
     * Edge kind. One of:
     * {@code "flowchart-link"}, {@code "messageLine"}, {@code "relation"},
     * {@code "er-link"}, {@code "edge"}.
     */
    public String getKind() { return kind; }

    /** Raw SVG path {@code d} attribute, or {@code null} if not a path-based edge. */
    public String getPathData() { return pathData; }

    /** Bounding box left x. */
    public double getX() { return x; }

    /** Bounding box top y. */
    public double getY() { return y; }

    /** Bounding box width. */
    public double getWidth() { return width; }

    /** Bounding box height. */
    public double getHeight() { return height; }

    /**
     * Test whether a point (in SVG user-space) falls inside this edge's
     * bounding box.  For precise hit-testing on curves, use {@link #getPathData()}
     * and perform path-based intersection.
     */
    public boolean containsApprox(double px, double py) {
        // Inflate bounding box by 4px for easier clicking on thin lines
        double margin = 4;
        return px >= x - margin && px <= x + width + margin
                && py >= y - margin && py <= y + height + margin;
    }

    @Override
    public String toString() {
        return "DiagramEdge{id='" + id + "', " + sourceId + " -> " + targetId
                + (label.isEmpty() ? "" : " [" + label + "]")
                + ", kind='" + kind + "'"
                + ", bounds=[" + fmt(x) + "," + fmt(y) + " " + fmt(width) + "x" + fmt(height) + "]}";
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }
}

