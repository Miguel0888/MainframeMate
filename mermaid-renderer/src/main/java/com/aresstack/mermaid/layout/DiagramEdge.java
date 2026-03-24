package com.aresstack.mermaid.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A rendered diagram edge (connection/relationship) with its geometry.
 * Coordinates are in SVG user-space units.
 *
 * <p>Base class for all diagram-specific edge types.
 * Subclasses add type-specific properties:
 * <ul>
 *   <li>{@link FlowchartEdge} — line style (solid/dashed/thick), arrowheads</li>
 *   <li>{@link ClassRelation} — UML relation type, multiplicities</li>
 *   <li>{@link ErRelationship} — source/target cardinality, identifying</li>
 *   <li>{@link SequenceMessage} — message type (sync/async/reply), activation</li>
 *   <li>{@link StateTransition} — guard condition</li>
 * </ul>
 *
 * <p>Immutable value object — safe to share across threads.
 */
public class DiagramEdge {

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
    private final Map<String, String> properties;

    public DiagramEdge(String id, String sourceId, String targetId, String label,
                       String kind, String pathData,
                       double x, double y, double width, double height) {
        this(id, sourceId, targetId, label, kind, pathData, x, y, width, height,
                Collections.<String, String>emptyMap());
    }

    public DiagramEdge(String id, String sourceId, String targetId, String label,
                       String kind, String pathData,
                       double x, double y, double width, double height,
                       Map<String, String> properties) {
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
        this.properties = properties != null && !properties.isEmpty()
                ? Collections.unmodifiableMap(new LinkedHashMap<String, String>(properties))
                : Collections.<String, String>emptyMap();
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
     * Additional ad-hoc properties for edge types without a dedicated
     * subclass.  Returns an unmodifiable map (may be empty, never null).
     */
    public Map<String, String> getProperties() { return properties; }

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
        return getClass().getSimpleName() + "{id='" + id + "', " + sourceId + " -> " + targetId
                + (label.isEmpty() ? "" : " [" + label + "]")
                + ", kind='" + kind + "'"
                + ", bounds=[" + fmt(x) + "," + fmt(y) + " " + fmt(width) + "x" + fmt(height) + "]}";
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }
}
