package com.aresstack.mermaid.layout;

/**
 * A single attribute of an ER entity.
 *
 * <h3>Mermaid syntax example</h3>
 * <pre>
 *   BUCH {
 *       string isbn PK       →  type "string", name "isbn", isPrimaryKey true
 *       string titel         →  type "string", name "titel"
 *       int    jahr          →  type "int",    name "jahr"
 *   }
 * </pre>
 *
 * <p>Immutable value object.
 */
public final class ErAttribute {

    private final String name;
    private final String type;
    private final boolean primaryKey;
    private final boolean foreignKey;
    private final String comment;

    public ErAttribute(String name, String type, boolean primaryKey,
                       boolean foreignKey, String comment) {
        this.name = name != null ? name : "";
        this.type = type != null ? type : "";
        this.primaryKey = primaryKey;
        this.foreignKey = foreignKey;
        this.comment = comment != null ? comment : "";
    }

    /** Attribute name, e.g. "isbn". */
    public String getName() { return name; }

    /** Data type, e.g. "string", "int". */
    public String getType() { return type; }

    /** True if this is a primary key. */
    public boolean isPrimaryKey() { return primaryKey; }

    /** True if this is a foreign key. */
    public boolean isForeignKey() { return foreignKey; }

    /** Optional comment / description. */
    public String getComment() { return comment; }

    /**
     * Generate Mermaid syntax for this attribute.
     *
     * @return e.g. {@code "string isbn PK"}
     */
    public String toMermaid() {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append(" ").append(name);
        if (primaryKey) sb.append(" PK");
        if (foreignKey) sb.append(" FK");
        if (!comment.isEmpty()) sb.append(" \"").append(comment).append("\"");
        return sb.toString();
    }

    /**
     * Parse an ER attribute from a Mermaid text line.
     *
     * @param text e.g. "string isbn PK" or "int jahr"
     * @return parsed attribute
     */
    public static ErAttribute parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ErAttribute("", "", false, false, "");
        }
        String t = text.trim();
        String[] parts = t.split("\\s+");

        String attrType = parts.length > 0 ? parts[0] : "";
        String attrName = parts.length > 1 ? parts[1] : "";
        boolean pk = false;
        boolean fk = false;
        StringBuilder commentBuf = new StringBuilder();

        for (int i = 2; i < parts.length; i++) {
            if ("PK".equalsIgnoreCase(parts[i])) pk = true;
            else if ("FK".equalsIgnoreCase(parts[i])) fk = true;
            else {
                if (commentBuf.length() > 0) commentBuf.append(" ");
                commentBuf.append(parts[i].replace("\"", ""));
            }
        }

        return new ErAttribute(attrName, attrType, pk, fk, commentBuf.toString());
    }

    @Override
    public String toString() {
        return toMermaid();
    }
}

