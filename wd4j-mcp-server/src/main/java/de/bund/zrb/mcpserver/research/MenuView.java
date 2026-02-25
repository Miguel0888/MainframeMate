package de.bund.zrb.mcpserver.research;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of a page as shown to the bot.
 * Contains a viewToken (valid for exactly one snapshot), a text excerpt,
 * and a list of interactive menu items.
 */
public class MenuView {

    private final String viewToken;
    private final String url;
    private final String title;
    private final String excerpt;
    private final List<MenuItem> menuItems;

    public MenuView(String viewToken, String url, String title, String excerpt, List<MenuItem> menuItems) {
        this.viewToken = viewToken;
        this.url = url;
        this.title = title;
        this.excerpt = excerpt;
        this.menuItems = menuItems != null ? Collections.unmodifiableList(menuItems) : Collections.<MenuItem>emptyList();
    }

    public String getViewToken() { return viewToken; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getExcerpt() { return excerpt; }
    public List<MenuItem> getMenuItems() { return menuItems; }

    /**
     * Render a compact text representation for the bot.
     * Format:
     * <pre>
     * Page: &lt;title&gt;
     * URL: &lt;url&gt;
     * viewToken: &lt;token&gt;
     *
     * ── Excerpt ──
     * &lt;excerpt&gt;
     *
     * ── Menu (N items) ──
     * [m0] link: "Homepage" → https://...
     * [m1] button: "Login"
     * ...
     * </pre>
     */
    public String toCompactText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Page: ").append(title != null ? title : "(no title)").append("\n");
        sb.append("URL: ").append(url != null ? url : "(unknown)").append("\n");
        sb.append("viewToken: ").append(viewToken).append("\n");

        if (excerpt != null && !excerpt.isEmpty()) {
            sb.append("\n── Excerpt ──────────────────────────────\n");
            sb.append(excerpt);
            if (excerpt.length() >= 2900) {
                sb.append("\n[… truncated]");
            }
            sb.append("\n");
        }

        sb.append("\n── Menu (").append(menuItems.size()).append(" items) ──────────────\n");
        for (MenuItem item : menuItems) {
            sb.append("  ").append(item.toCompactString()).append("\n");
        }

        if (menuItems.isEmpty()) {
            sb.append("  (no interactive elements found)\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toCompactText();
    }
}

