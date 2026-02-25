package de.bund.zrb.mcpserver.research;

/**
 * A single menu entry shown to the bot.
 * Represents a clickable/interactable element on the page.
 * The {@code menuItemId} is stable only within the same {@code viewToken}.
 */
public class MenuItem {

    public enum Type {
        LINK, BUTTON, INPUT, SELECT, TAB, MENUITEM, OTHER
    }

    private final String menuItemId;
    private final Type type;
    private final String label;
    private final String href;
    private final String actionHint;

    public MenuItem(String menuItemId, Type type, String label, String href, String actionHint) {
        this.menuItemId = menuItemId;
        this.type = type;
        this.label = label;
        this.href = href;
        this.actionHint = actionHint;
    }

    public String getMenuItemId() { return menuItemId; }
    public Type getType() { return type; }
    public String getLabel() { return label; }
    public String getHref() { return href; }
    public String getActionHint() { return actionHint; }

    /**
     * Compact one-line representation for the bot.
     */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(menuItemId).append("] ");
        sb.append(type.name().toLowerCase()).append(": ");
        sb.append(label != null ? label : "(no label)");
        if (href != null && !href.isEmpty()) {
            String displayHref = href.length() > 120 ? href.substring(0, 120) + "…" : href;
            sb.append(" → ").append(displayHref);
        }
        if (actionHint != null && !actionHint.isEmpty()) {
            sb.append(" (").append(actionHint).append(")");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toCompactString();
    }

    /**
     * Infer Type from HTML tag name.
     */
    public static Type inferType(String tag) {
        if (tag == null) return Type.OTHER;
        switch (tag.toLowerCase()) {
            case "a": return Type.LINK;
            case "button": return Type.BUTTON;
            case "input": return Type.INPUT;
            case "select": return Type.SELECT;
            case "textarea": return Type.INPUT;
            default:
                if (tag.contains("[role=tab]") || tag.contains("tab")) return Type.TAB;
                if (tag.contains("[role=menuitem]") || tag.contains("menuitem")) return Type.MENUITEM;
                if (tag.contains("[role=button]")) return Type.BUTTON;
                if (tag.contains("[role=link]")) return Type.LINK;
                return Type.OTHER;
        }
    }
}

