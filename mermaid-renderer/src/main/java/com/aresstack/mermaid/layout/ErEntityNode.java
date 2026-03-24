package com.aresstack.mermaid.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ER diagram entity with typed attributes.
 *
 * <h3>Mermaid source example</h3>
 * <pre>
 *   BUCH {
 *       string isbn PK
 *       string titel
 *       int jahr
 *   }
 * </pre>
 */
public class ErEntityNode extends DiagramNode {

    private final List<ErAttribute> attributes;

    public ErEntityNode(String id, String label,
                        double x, double y, double width, double height,
                        String svgId, List<ErAttribute> attributes) {
        super(id, label, "entity", x, y, width, height, svgId);
        this.attributes = attributes != null
                ? Collections.unmodifiableList(new ArrayList<ErAttribute>(attributes))
                : Collections.<ErAttribute>emptyList();
    }

    /** Entity attributes in declaration order. */
    public List<ErAttribute> getAttributes() { return attributes; }

    /** Find the primary key attribute, or null if none. */
    public ErAttribute getPrimaryKey() {
        for (ErAttribute a : attributes) {
            if (a.isPrimaryKey()) return a;
        }
        return null;
    }

    /**
     * Generate Mermaid entity block.
     */
    public String toMermaid() {
        StringBuilder sb = new StringBuilder();
        sb.append(getId()).append(" {\n");
        for (ErAttribute a : attributes) {
            sb.append("    ").append(a.toMermaid()).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ErEntityNode{id='" + getId() + "', attributes=" + attributes.size()
                + ", bounds=[" + fmt(getX()) + "," + fmt(getY()) + " "
                + fmt(getWidth()) + "x" + fmt(getHeight()) + "]}";
    }
}

