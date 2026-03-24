package com.aresstack.mermaid.layout;

/**
 * Visual shape of a flowchart/graph node.
 * Maps 1:1 to the Mermaid syntax for node shapes.
 *
 * <h3>Mermaid syntax mapping</h3>
 * <pre>
 *   RECTANGLE       →  A[text]
 *   ROUND_RECT      →  A(text)
 *   STADIUM         →  A([text])
 *   CIRCLE          →  A((text))
 *   DIAMOND         →  A{text}
 *   HEXAGON         →  A{{text}}
 *   TRAPEZOID       →  A[/text/]
 *   TRAPEZOID_ALT   →  A[\text\]
 *   PARALLELOGRAM   →  A[/text\]
 *   PARALLELOGRAM_ALT → A[\text/]
 *   CYLINDER        →  A[(text)]
 *   SUBROUTINE      →  A[[text]]
 *   DOUBLE_CIRCLE   →  A(((text)))
 *   ASYMMETRIC      →  A>text]
 * </pre>
 */
public enum NodeShape {

    /** Default rectangular box: {@code A[text]} */
    RECTANGLE("[", "]", "Rechteck"),

    /** Rounded rectangle: {@code A(text)} */
    ROUND_RECT("(", ")", "Abgerundetes Rechteck"),

    /** Stadium / pill shape: {@code A([text])} */
    STADIUM("([", "])", "Stadium"),

    /** Circle: {@code A((text))} */
    CIRCLE("((", "))", "Kreis"),

    /** Diamond / rhombus (decision): {@code A\{text\}} */
    DIAMOND("{", "}", "Raute"),

    /** Hexagon: {@code A\{\{text\}\}} */
    HEXAGON("{{", "}}", "Sechseck"),

    /** Trapezoid: {@code A[/text/]} */
    TRAPEZOID("[/", "/]", "Trapez"),

    /** Inverted trapezoid: {@code A[\\text\\]} */
    TRAPEZOID_ALT("[\\", "\\]", "Trapez (umgekehrt)"),

    /** Parallelogram (lean right): {@code A[/text\\]} */
    PARALLELOGRAM("[/", "\\]", "Parallelogramm"),

    /** Parallelogram (lean left): {@code A[\\text/]} */
    PARALLELOGRAM_ALT("[\\", "/]", "Parallelogramm (links)"),

    /** Cylinder (database): {@code A[(text)]} */
    CYLINDER("[(", ")]", "Zylinder"),

    /** Subroutine (double border): {@code A[[text]]} */
    SUBROUTINE("[[", "]]", "Unterprogramm"),

    /** Double circle: {@code A(((text)))} */
    DOUBLE_CIRCLE("(((", ")))", "Doppelkreis"),

    /** Asymmetric / flag shape: {@code A>text]} */
    ASYMMETRIC(">", "]", "Asymmetrisch");

    private final String openSyntax;
    private final String closeSyntax;
    private final String displayName;

    NodeShape(String openSyntax, String closeSyntax, String displayName) {
        this.openSyntax = openSyntax;
        this.closeSyntax = closeSyntax;
        this.displayName = displayName;
    }

    /** Opening bracket(s) in Mermaid syntax, e.g. {@code "["} for rectangle. */
    public String getOpenSyntax() { return openSyntax; }

    /** Closing bracket(s) in Mermaid syntax, e.g. {@code "]"} for rectangle. */
    public String getCloseSyntax() { return closeSyntax; }

    /** Human-readable name for UI display. */
    public String getDisplayName() { return displayName; }

    /**
     * Generate Mermaid syntax for a node with this shape.
     *
     * @param id    node identifier
     * @param label display text
     * @return e.g. {@code "A([Start])"}
     */
    public String toMermaid(String id, String label) {
        return id + openSyntax + label + closeSyntax;
    }

    @Override
    public String toString() { return displayName; }
}

