package de.bund.zrb.mcpserver.browser;

/**
 * A stable, short reference to a DOM node (e.g. "n1", "n2").
 * Used by the bot to refer to elements across tool calls.
 */
public class NodeRef {
    private final String id;
    private final String tag;
    private final String text;
    private final String role;
    private final String name;
    private final boolean interactive;

    public NodeRef(String id, String tag, String text, String role, String name, boolean interactive) {
        this.id = id;
        this.tag = tag;
        this.text = text != null && text.length() > 80 ? text.substring(0, 80) + "…" : text;
        this.role = role;
        this.name = name;
        this.interactive = interactive;
    }

    public String getId() { return id; }
    public String getTag() { return tag; }
    public String getText() { return text; }
    public String getRole() { return role; }
    public String getName() { return name; }
    public boolean isInteractive() { return interactive; }

    /** Compact one-line representation for the bot. */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("] <").append(tag != null ? tag : "?").append(">");
        if (role != null && !role.isEmpty()) {
            sb.append(" role=").append(role);
        }
        if (name != null && !name.isEmpty()) {
            sb.append(" \"").append(name).append("\"");
        } else if (text != null && !text.isEmpty()) {
            sb.append(" \"").append(text).append("\"");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toCompactString();
    }
}

