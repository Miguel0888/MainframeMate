package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.ImageRef;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to extract {@link ImageRef} objects from arbitrary HTML and to
 * strip {@code <img>} tags for text-mode display.
 * <p>
 * Used by both the Wiki and Confluence integration to extract images from HTML
 * content so they can be shown in an {@link ImageStripPanel} instead of inline.
 */
public final class HtmlImageExtractor {

    private static final Pattern IMG_TAG = Pattern.compile(
            "<img\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC_ATTR = Pattern.compile(
            "src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT_ATTR = Pattern.compile(
            "alt\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_ATTR = Pattern.compile(
            "title\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIDTH_ATTR = Pattern.compile(
            "(?:data-)?width\\s*=\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEIGHT_ATTR = Pattern.compile(
            "(?:data-)?height\\s*=\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE);

    private HtmlImageExtractor() { /* utility */ }

    /**
     * Extract all {@code <img>} references from the given HTML body.
     *
     * @param html    raw HTML body (may be {@code null})
     * @param baseUrl optional base URL prepended to relative src values
     * @return list of {@link ImageRef}; never {@code null}
     */
    public static List<ImageRef> extractImages(String html, String baseUrl) {
        List<ImageRef> images = new ArrayList<ImageRef>();
        if (html == null) return images;

        Matcher m = IMG_TAG.matcher(html);
        while (m.find()) {
            String tag = m.group();

            Matcher srcM = SRC_ATTR.matcher(tag);
            if (!srcM.find()) continue;
            String src = decodeHtmlEntities(srcM.group(1));

            // Skip data URIs and tiny decoration images (emoticons, icons)
            if (src.startsWith("data:")) continue;
            if (src.contains("/emoticons/") || src.contains("/icons/")) continue;

            // Resolve relative URLs against baseUrl
            if (baseUrl != null && !src.startsWith("http://") && !src.startsWith("https://")) {
                if (src.startsWith("/")) {
                    src = baseUrl + src;
                } else {
                    src = baseUrl + "/" + src;
                }
            }

            String alt = extractAttr(tag, ALT_ATTR);
            String title = extractAttr(tag, TITLE_ATTR);
            int width = extractInt(tag, WIDTH_ATTR);
            int height = extractInt(tag, HEIGHT_ATTR);

            images.add(new ImageRef(src, alt, title, width, height));
        }
        return images;
    }

    /**
     * Remove all {@code <img …>} tags from the HTML so the text-mode view
     * shows plain text without inline images.
     */
    public static String stripImgTags(String html) {
        if (html == null) return null;
        return IMG_TAG.matcher(html).replaceAll("");
    }

    private static String extractAttr(String tag, Pattern p) {
        Matcher m = p.matcher(tag);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Decode common HTML entities in attribute values.
     * Confluence REST API returns rendered HTML where query parameter separators
     * appear as {@code &amp;} instead of plain {@code &}.
     */
    private static String decodeHtmlEntities(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static int extractInt(String tag, Pattern p) {
        Matcher m = p.matcher(tag);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) { }
        }
        return 0;
    }
}

