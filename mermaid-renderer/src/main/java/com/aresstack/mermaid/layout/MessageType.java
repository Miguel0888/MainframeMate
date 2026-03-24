package com.aresstack.mermaid.layout;

/**
 * Message types in sequence diagrams.
 *
 * <h3>Mermaid syntax mapping</h3>
 * <pre>
 *   SYNC_SOLID     →  A -&gt;&gt; B     (solid line, filled arrowhead)
 *   SYNC_DOTTED    →  A --&gt;&gt; B    (dashed line, filled arrowhead — reply)
 *   ASYNC_SOLID    →  A -) B       (solid line, open arrowhead)
 *   ASYNC_DOTTED   →  A --) B      (dashed line, open arrowhead)
 *   SOLID_OPEN     →  A -&gt; B      (solid line, open arrowhead — older syntax)
 *   DOTTED_OPEN    →  A --&gt; B     (dashed line, open arrowhead — older syntax)
 *   SOLID_CROSS    →  A -x B       (solid line, cross arrowhead — lost message)
 *   DOTTED_CROSS   →  A --x B      (dashed line, cross arrowhead)
 * </pre>
 */
public enum MessageType {

    /** Synchronous call (solid line, filled arrow): {@code ->>} */
    SYNC_SOLID(false, false, "->>", "Synchron"),

    /** Reply (dashed line, filled arrow): {@code -->>} */
    SYNC_DOTTED(true, false, "-->>", "Antwort"),

    /** Async (solid line, open arrow): {@code -)  } */
    ASYNC_SOLID(false, true, "-)", "Asynchron"),

    /** Async reply (dashed line, open arrow): {@code --)} */
    ASYNC_DOTTED(true, true, "--)", "Asynchrone Antwort"),

    /** Older-style open arrow (solid): {@code ->} */
    SOLID_OPEN(false, false, "->", "Solid (offen)"),

    /** Older-style open arrow (dashed): {@code -->} */
    DOTTED_OPEN(true, false, "-->", "Gestrichelt (offen)"),

    /** Lost message (solid, cross): {@code -x} */
    SOLID_CROSS(false, false, "-x", "Verlorene Nachricht"),

    /** Lost message (dashed, cross): {@code --x} */
    DOTTED_CROSS(true, false, "--x", "Verlorene Antwort");

    private final boolean dashed;
    private final boolean async;
    private final String mermaidSyntax;
    private final String displayName;

    MessageType(boolean dashed, boolean async, String mermaidSyntax, String displayName) {
        this.dashed = dashed;
        this.async = async;
        this.mermaidSyntax = mermaidSyntax;
        this.displayName = displayName;
    }

    /** Whether the line is dashed (typically a reply). */
    public boolean isDashed() { return dashed; }

    /** Whether the message is asynchronous. */
    public boolean isAsync() { return async; }

    /** Mermaid arrow syntax. */
    public String getMermaidSyntax() { return mermaidSyntax; }

    /** Human-readable name. */
    public String getDisplayName() { return displayName; }

    /**
     * Detect message type from the SVG class attribute of a message line.
     *
     * @param cssClass e.g. {@code "messageLine0"} (solid) or {@code "messageLine1"} (dashed)
     * @return detected type (SYNC_SOLID or SYNC_DOTTED)
     */
    public static MessageType fromCssClass(String cssClass) {
        if (cssClass != null && cssClass.contains("messageLine1")) {
            return SYNC_DOTTED;
        }
        return SYNC_SOLID;
    }

    @Override
    public String toString() { return displayName; }
}

