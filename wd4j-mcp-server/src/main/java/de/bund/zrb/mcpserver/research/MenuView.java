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
     *
     * ── Excerpt ──
     * &lt;excerpt&gt;
     *
     * ── Links (N) ──
     * [m0] link: "Homepage" → https://...
     * [m1] link: "Nachrichten" → /nachrichten/
     * ...
     * </pre>
     */
    public String toCompactText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Page: ").append(title != null ? title : "(no title)").append("\n");
        sb.append("URL: ").append(url != null ? url : "(unknown)").append("\n");

        if (excerpt != null && !excerpt.isEmpty()) {
            sb.append("\n── Excerpt ──────────────────────────────\n");
            sb.append(excerpt);
            if (excerpt.length() >= 2900) {
                sb.append("\n[… truncated]");
            }
            sb.append("\n");
        }

        sb.append("\n── Links (").append(menuItems.size()).append(") ──────────────\n");
        for (MenuItem item : menuItems) {
            sb.append("  ").append(item.toCompactString()).append("\n");
        }

        if (menuItems.isEmpty()) {
            sb.append("  (no links found on this page)\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toCompactText();
    }
}

