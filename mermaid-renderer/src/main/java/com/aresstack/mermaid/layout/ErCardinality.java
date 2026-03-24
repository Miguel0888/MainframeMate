package com.aresstack.mermaid.layout;

/**
 * ER diagram cardinality markers.
 *
 * <h3>Mermaid syntax mapping</h3>
 * <pre>
 *   EXACTLY_ONE   →  ||   (one and only one)
 *   ZERO_OR_ONE   →  |o   (zero or one)
 *   ZERO_OR_MORE  →  o{   (zero or more)
 *   ONE_OR_MORE   →  |{   (one or more)
 * </pre>
 */
public enum ErCardinality {

    /** {@code ||} — exactly one */
    EXACTLY_ONE("||", "Genau eins"),

    /** {@code |o} or {@code o|} — zero or one */
    ZERO_OR_ONE("|o", "Null oder eins"),

    /** {@code o{} or {@code }o} — zero or more */
    ZERO_OR_MORE("o{", "Null oder viele"),

    /** {@code |{} or {@code }|} — one or more */
    ONE_OR_MORE("|{", "Eins oder viele");

    private final String mermaidSyntax;
    private final String displayName;

    ErCardinality(String mermaidSyntax, String displayName) {
        this.mermaidSyntax = mermaidSyntax;
        this.displayName = displayName;
    }

    /** Mermaid notation for this cardinality side. */
    public String getMermaidSyntax() { return mermaidSyntax; }

    /** Human-readable name. */
    public String getDisplayName() { return displayName; }

    /**
     * Parse cardinality from a Mermaid syntax fragment.
     *
     * @param syntax e.g. {@code "||"}, {@code "o{"}, {@code "|o"}
     * @return the matching cardinality, or {@code EXACTLY_ONE} as fallback
     */
    public static ErCardinality parse(String syntax) {
        if (syntax == null) return EXACTLY_ONE;
        String s = syntax.trim();
        for (ErCardinality c : values()) {
            if (s.contains(c.mermaidSyntax)) return c;
        }
        // Try reversed forms
        if (s.contains("}|")) return ONE_OR_MORE;
        if (s.contains("}o")) return ZERO_OR_MORE;
        if (s.contains("o|")) return ZERO_OR_ONE;
        return EXACTLY_ONE;
    }

    @Override
    public String toString() { return displayName; }
}

