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
     * Uses "Für X: URL" format and relative paths where possible.
     * Format:
     * <pre>
     * Du bist auf: &lt;title&gt; (&lt;url&gt;)
     *
     * ── Seiteninhalt ──
     * &lt;excerpt&gt;
     *
     * ── Hier kannst du weiternavigieren ──
     * Für Bundesliga:  /sport/bundesliga/
     * Für Politik:     /politik/
     * ...
     * </pre>
     */
    public String toCompactText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Du bist auf: ").append(title != null ? title : "(no title)");
        sb.append(" (").append(url != null ? url : "unknown").append(")\n");

        if (excerpt != null && !excerpt.isEmpty()) {
            sb.append("\n── Seiteninhalt ──\n");
            sb.append(excerpt);
            if (excerpt.length() >= 2900) {
                sb.append("\n[… truncated]");
            }
            sb.append("\n");
        }

        if (menuItems.isEmpty()) {
            sb.append("\n── Keine Links auf dieser Seite gefunden ──\n");
        } else {
            sb.append("\n── Hier kannst du weiternavigieren (").append(menuItems.size()).append(" Links) ──\n");
            for (MenuItem item : menuItems) {
                sb.append("  ").append(item.toCompactStringWithRelativeUrl(url)).append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toCompactText();
    }
}

