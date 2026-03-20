package de.bund.zrb.wiki.domain;

import java.util.Collections;
import java.util.List;

/**
 * Result of loading and rendering a wiki page.
 */
public final class WikiPageView {
    private final String title;
    private final String cleanedHtml;
    /** HTML with {@code <img>} tags still inline (absolute URLs). For rendered mode with images. */
    private final String htmlWithImages;
    private final OutlineNode outline;
    private final List<ImageRef> images;

    public WikiPageView(String title, String cleanedHtml, OutlineNode outline) {
        this(title, cleanedHtml, null, outline, Collections.<ImageRef>emptyList());
    }

    public WikiPageView(String title, String cleanedHtml, OutlineNode outline, List<ImageRef> images) {
        this(title, cleanedHtml, null, outline, images);
    }

    public WikiPageView(String title, String cleanedHtml, String htmlWithImages,
                        OutlineNode outline, List<ImageRef> images) {
        this.title = title;
        this.cleanedHtml = cleanedHtml;
        this.htmlWithImages = htmlWithImages;
        this.outline = outline;
        this.images = images != null ? images : Collections.<ImageRef>emptyList();
    }

    public String title() { return title; }
    public String cleanedHtml() { return cleanedHtml; }
    /** @return HTML with inline images, or {@code null} if not available */
    public String htmlWithImages() { return htmlWithImages; }
    public OutlineNode outline() { return outline; }
    public List<ImageRef> images() { return images; }
}
