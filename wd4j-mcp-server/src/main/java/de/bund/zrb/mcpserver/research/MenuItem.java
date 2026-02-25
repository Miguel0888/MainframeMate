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
     * Format: "Für {label}: {url}"
     * The URL is what the bot passes back to research_navigate.
     * This "Für X: URL" format is optimized for small LLMs – they understand it
     * as an actionable instruction ("if you want X, go to URL").
     */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Für ");
        sb.append(label != null && !label.isEmpty() ? label : "(no label)");
        if (actionHint != null && !actionHint.isEmpty()) {
            sb.append(" (").append(actionHint).append(")");
        }
        sb.append(":  ");
        if (href != null && !href.isEmpty()) {
            sb.append(href);
        } else {
            sb.append("(no URL)");
        }
        return sb.toString();
    }

    /**
     * Return the href as a relative path if it's on the same domain as the given base URL.
     * This saves tokens and is more readable for the bot.
     */
    public String getRelativeHref(String baseUrl) {
        if (href == null || href.isEmpty()) return href;
        if (baseUrl == null || baseUrl.isEmpty()) return href;
        try {
            java.net.URL base = new java.net.URL(baseUrl);
            java.net.URL link = new java.net.URL(href);
            // Same host (including subdomains)?
            if (base.getHost().equalsIgnoreCase(link.getHost())) {
                String path = link.getPath();
                String query = link.getQuery();
                if (query != null && !query.isEmpty()) {
                    return path + "?" + query;
                }
                return path;
            }
        } catch (Exception ignored) {}
        return href;
    }

    /**
     * Compact one-line representation with relative URL (saves tokens).
     * Format: "Für {label}: {relativeUrl}"
     */
    public String toCompactStringWithRelativeUrl(String pageUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("Für ");
        sb.append(label != null && !label.isEmpty() ? label : "(no label)");
        if (actionHint != null && !actionHint.isEmpty()) {
            sb.append(" (").append(actionHint).append(")");
        }
        sb.append(":  ");
        String displayUrl = getRelativeHref(pageUrl);
        if (displayUrl != null && !displayUrl.isEmpty()) {
            sb.append(displayUrl);
        } else {
            sb.append("(no URL)");
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

