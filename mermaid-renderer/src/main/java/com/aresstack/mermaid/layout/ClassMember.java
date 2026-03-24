package com.aresstack.mermaid.layout;

/**
 * A single member (field or method) of a UML class.
 *
 * <h3>Mermaid syntax examples</h3>
 * <pre>
 *   +String name          →  PUBLIC field, type "String", name "name"
 *   -int age              →  PRIVATE field, type "int", name "age"
 *   +speak() void         →  PUBLIC method, name "speak", returnType "void"
 *   #getAge() int         →  PROTECTED method, name "getAge", returnType "int"
 * </pre>
 *
 * <p>Immutable value object.
 */
public final class ClassMember {

    private final Visibility visibility;
    private final String name;
    private final String type;
    private final boolean method;
    private final String parameters;

    /**
     * @param visibility access modifier (may be null if unspecified)
     * @param name       member name
     * @param type       field type or method return type (may be empty)
     * @param method     true if this is a method, false for field
     * @param parameters method parameter signature, e.g. "x: int, y: int" (empty for fields)
     */
    public ClassMember(Visibility visibility, String name, String type,
                       boolean method, String parameters) {
        this.visibility = visibility;
        this.name = name != null ? name : "";
        this.type = type != null ? type : "";
        this.method = method;
        this.parameters = parameters != null ? parameters : "";
    }

    /** Access modifier, or {@code null} if not specified. */
    public Visibility getVisibility() { return visibility; }

    /** Member name (field name or method name). */
    public String getName() { return name; }

    /** Type (field type or method return type). May be empty. */
    public String getType() { return type; }

    /** True if this is a method, false if field/attribute. */
    public boolean isMethod() { return method; }

    /** Method parameters, e.g. "x: int, y: int". Empty for fields. */
    public String getParameters() { return parameters; }

    /**
     * Generate Mermaid syntax for this member.
     *
     * @return e.g. {@code "+String name"} or {@code "+speak() void"}
     */
    public String toMermaid() {
        StringBuilder sb = new StringBuilder();
        if (visibility != null) sb.append(visibility.getSymbol());
        if (method) {
            sb.append(name).append("(").append(parameters).append(")");
            if (!type.isEmpty()) sb.append(" ").append(type);
        } else {
            if (!type.isEmpty()) sb.append(type).append(" ");
            sb.append(name);
        }
        return sb.toString();
    }

    /**
     * Parse a class member from a Mermaid text line.
     *
     * @param text e.g. {@code "+String name"} or {@code "+speak() void"}
     * @return parsed member
     */
    public static ClassMember parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ClassMember(null, "", "", false, "");
        }
        String t = text.trim();

        // Parse visibility
        Visibility vis = null;
        if (!t.isEmpty()) {
            vis = Visibility.fromChar(t.charAt(0));
            if (vis != null) t = t.substring(1).trim();
        }

        // Detect method (contains parentheses)
        boolean isMethod = t.contains("(") && t.contains(")");

        if (isMethod) {
            int parenOpen = t.indexOf('(');
            int parenClose = t.indexOf(')');
            String methodName = t.substring(0, parenOpen).trim();
            String params = t.substring(parenOpen + 1, parenClose).trim();
            String returnType = (parenClose + 1 < t.length())
                    ? t.substring(parenClose + 1).trim() : "";
            return new ClassMember(vis, methodName, returnType, true, params);
        } else {
            // Field: may be "Type name" or just "name"
            String[] parts = t.split("\\s+", 2);
            if (parts.length == 2) {
                return new ClassMember(vis, parts[1], parts[0], false, "");
            } else {
                return new ClassMember(vis, parts[0], "", false, "");
            }
        }
    }

    @Override
    public String toString() {
        return toMermaid();
    }
}

