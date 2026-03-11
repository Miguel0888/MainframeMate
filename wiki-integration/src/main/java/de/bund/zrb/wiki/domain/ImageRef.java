package de.bund.zrb.wiki.domain;

/**
 * A reference to an image extracted from a wiki page.
 * Images are removed from the inline HTML flow and displayed
 * as hoverable thumbnails in a side strip.
 */
public final class ImageRef {
    private final String src;
    private final String alt;
    private final String title;
    private final int width;
    private final int height;

    public ImageRef(String src, String alt, String title, int width, int height) {
        this.src = src;
        this.alt = alt != null ? alt : "";
        this.title = title != null ? title : "";
        this.width = width;
        this.height = height;
    }

    public String src() { return src; }
    public String alt() { return alt; }
    public String title() { return title; }
    public int width() { return width; }
    public int height() { return height; }

    /** Best available description for tooltip. */
    public String description() {
        if (!title.isEmpty()) return title;
        if (!alt.isEmpty()) return alt;
        return src;
    }
}

