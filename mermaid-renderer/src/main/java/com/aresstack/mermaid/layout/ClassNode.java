package com.aresstack.mermaid.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A UML class diagram node with stereotype, fields and methods.
 *
 * <h3>Mermaid source example</h3>
 * <pre>
 *   class Animal {
 *       &lt;&lt;abstract&gt;&gt;
 *       +String name
 *       +int age
 *       +speak() void
 *   }
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 *   ClassNode cls = (ClassNode) diagram.findNodeById("Animal");
 *   String stereo = cls.getStereotype();        // "abstract"
 *   List&lt;ClassMember&gt; fields  = cls.getFields();   // [+String name, +int age]
 *   List&lt;ClassMember&gt; methods = cls.getMethods();  // [+speak() void]
 * </pre>
 */
public class ClassNode extends DiagramNode {

    private final String stereotype;
    private final List<ClassMember> members;

    public ClassNode(String id, String label,
                     double x, double y, double width, double height,
                     String svgId, String stereotype, List<ClassMember> members) {
        super(id, label, "class", x, y, width, height, svgId);
        this.stereotype = stereotype != null ? stereotype : "";
        this.members = members != null
                ? Collections.unmodifiableList(new ArrayList<ClassMember>(members))
                : Collections.<ClassMember>emptyList();
    }

    /**
     * UML stereotype, e.g. "abstract", "interface", "enumeration".
     * Empty string if none.
     */
    public String getStereotype() { return stereotype; }

    /** All members (fields + methods) in declaration order. */
    public List<ClassMember> getMembers() { return members; }

    /** Convenience: only fields (non-method members). */
    public List<ClassMember> getFields() {
        List<ClassMember> result = new ArrayList<ClassMember>();
        for (ClassMember m : members) {
            if (!m.isMethod()) result.add(m);
        }
        return result;
    }

    /** Convenience: only methods. */
    public List<ClassMember> getMethods() {
        List<ClassMember> result = new ArrayList<ClassMember>();
        for (ClassMember m : members) {
            if (m.isMethod()) result.add(m);
        }
        return result;
    }

    /**
     * Generate Mermaid class definition block.
     *
     * @return e.g. {@code "class Animal {\n  <<abstract>>\n  +String name\n  +speak() void\n}"}
     */
    public String toMermaid() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(getId()).append(" {\n");
        if (!stereotype.isEmpty()) {
            sb.append("    <<").append(stereotype).append(">>\n");
        }
        for (ClassMember m : members) {
            sb.append("    ").append(m.toMermaid()).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ClassNode{id='" + getId() + "'"
                + (stereotype.isEmpty() ? "" : ", <<" + stereotype + ">>")
                + ", members=" + members.size()
                + ", bounds=[" + fmt(getX()) + "," + fmt(getY()) + " "
                + fmt(getWidth()) + "x" + fmt(getHeight()) + "]}";
    }
}

