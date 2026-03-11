package de.bund.zrb.wiki.domain;

import java.util.Collections;
import java.util.List;

/**
 * Result of loading and rendering a wiki page.
 */
public final class WikiPageView {
    private final String title;
    private final String cleanedHtml;
    private final OutlineNode outline;
    private final List<ImageRef> images;

    public WikiPageView(String title, String cleanedHtml, OutlineNode outline) {
        this(title, cleanedHtml, outline, Collections.<ImageRef>emptyList());
    }

    public WikiPageView(String title, String cleanedHtml, OutlineNode outline, List<ImageRef> images) {
        this.title = title;
        this.cleanedHtml = cleanedHtml;
        this.outline = outline;
        this.images = images != null ? images : Collections.<ImageRef>emptyList();
    }

    public String title() { return title; }
    public String cleanedHtml() { return cleanedHtml; }
    public OutlineNode outline() { return outline; }
    public List<ImageRef> images() { return images; }
}
