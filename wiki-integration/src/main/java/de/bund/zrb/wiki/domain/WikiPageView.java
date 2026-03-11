package de.bund.zrb.wiki.domain;

/**
 * Result of loading and rendering a wiki page.
 */
public final class WikiPageView {
    private final String title;
    private final String cleanedHtml;
    private final OutlineNode outline;

    public WikiPageView(String title, String cleanedHtml, OutlineNode outline) {
        this.title = title;
        this.cleanedHtml = cleanedHtml;
        this.outline = outline;
    }

    public String title() { return title; }
    public String cleanedHtml() { return cleanedHtml; }
    public OutlineNode outline() { return outline; }
}

