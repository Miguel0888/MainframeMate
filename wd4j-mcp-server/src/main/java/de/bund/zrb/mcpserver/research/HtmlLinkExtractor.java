package de.bund.zrb.mcpserver.research;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Server-side HTML parser using Jsoup.
 * Extracts links, title, and text excerpt from an HTML body –
 * no browser JS injection required.
 */
public class HtmlLinkExtractor {

    private static final Logger LOG = Logger.getLogger(HtmlLinkExtractor.class.getName());

    /**
     * Parse an HTML body and extract page metadata + links.
     *
     * @param html    raw HTML body
     * @param pageUrl the URL of the page (for resolving relative links)
     * @param maxLinks  max number of links to return
     * @param excerptLen max excerpt length
     * @return parsed result
     */
    public static ParsedPageResult parse(String html, String pageUrl, int maxLinks, int excerptLen) {
        ParsedPageResult result = new ParsedPageResult();
        result.url = pageUrl != null ? pageUrl : "";

        if (html == null || html.isEmpty()) {
            return result;
        }

        try {
            Document doc = Jsoup.parse(html, pageUrl != null ? pageUrl : "");

            // ── Title ──
            result.title = extractTitle(doc);

            // ── Excerpt ──
            result.excerpt = extractExcerpt(doc, excerptLen);

            // ── Links ──
            result.links = extractLinks(doc, pageUrl, maxLinks);

        } catch (Exception e) {
            LOG.warning("[HtmlLinkExtractor] Parse error: " + e.getMessage());
        }

        return result;
    }

    private static String extractTitle(Document doc) {
        // Priority: <title>, og:title, first <h1>
        String title = doc.title();
        if (title != null && !title.trim().isEmpty()) {
            return title.trim();
        }
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            String content = ogTitle.attr("content");
            if (content != null && !content.trim().isEmpty()) return content.trim();
        }
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            String text = h1.text().trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static String extractExcerpt(Document doc, int maxLen) {
        // Try semantic containers first, fall back to body
        String[] selectors = {"article", "[role=main]", "main", "#content", ".content"};
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String text = cleanText(el);
                if (text.length() > 50) {
                    return text.length() > maxLen ? text.substring(0, maxLen) : text;
                }
            }
        }

        // Fallback: body text, stripped of nav/footer/script noise
        Element body = doc.body();
        if (body != null) {
            // Remove noisy elements before extracting text
            Document clone = doc.clone();
            clone.select("script, style, nav, footer, header, noscript, " +
                    "[aria-hidden=true], .ad, .ads, .advertisement, " +
                    ".cookie-banner, .consent, #cookie-banner").remove();
            String text = cleanText(clone.body());
            if (text.length() > maxLen) text = text.substring(0, maxLen);
            return text;
        }

        return "";
    }

    private static List<LinkInfo> extractLinks(Document doc, String baseUrl, int maxLinks) {
        // Collect all links into categorized buckets for prioritization:
        // Content links first, then section nav, then external/footer
        List<LinkInfo> contentLinks = new ArrayList<>();
        List<LinkInfo> sectionLinks = new ArrayList<>();
        List<LinkInfo> otherLinks = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        String baseHost = null;
        try {
            if (baseUrl != null && !baseUrl.isEmpty()) {
                baseHost = new java.net.URL(baseUrl).getHost().toLowerCase();
            }
        } catch (Exception ignored) {}

        Elements anchors = doc.select("a[href]");
        for (Element a : anchors) {
            String href = a.absUrl("href");
            if (href.isEmpty()) {
                href = a.attr("href");
            }

            // Skip non-navigable links
            if (href.isEmpty() || href.startsWith("javascript:") || href.startsWith("mailto:")
                    || href.startsWith("tel:") || href.equals("#")) {
                continue;
            }

            // Skip fragment-only links (e.g. https://de.yahoo.com/#convenience-bar)
            // These only scroll the page and don't navigate to new content
            if (isFragmentOnly(href, baseUrl)) {
                continue;
            }

            // Dedupe by URL
            if (seenUrls.contains(href)) continue;
            seenUrls.add(href);

            String label = a.text().trim();
            if (label.isEmpty()) {
                // Try aria-label, title, or alt of child img
                label = a.attr("aria-label").trim();
                if (label.isEmpty()) label = a.attr("title").trim();
                if (label.isEmpty()) {
                    Element img = a.selectFirst("img[alt]");
                    if (img != null) label = img.attr("alt").trim();
                }
            }
            if (label.length() > 100) label = label.substring(0, 100) + "…";

            // Skip links with no meaningful label
            if (label.isEmpty()) continue;

            LinkInfo link = new LinkInfo();
            link.label = label;
            link.href = href;
            link.isExternal = isExternal(href, baseUrl);

            // Categorize: content links (deep paths on same host) get priority
            if (!link.isExternal && baseHost != null) {
                int pathDepth = getPathDepth(href);
                if (pathDepth >= 2) {
                    // Deep path = likely article/content (e.g. /nachrichten/some-article-123.html)
                    contentLinks.add(link);
                } else {
                    // Shallow path = section navigation (e.g. /sport/, /finanzen/)
                    sectionLinks.add(link);
                }
            } else {
                otherLinks.add(link);
            }
        }

        // Merge: content first, then sections, then external/other
        List<LinkInfo> result = new ArrayList<>();
        for (LinkInfo l : contentLinks) {
            if (result.size() >= maxLinks) break;
            result.add(l);
        }
        for (LinkInfo l : sectionLinks) {
            if (result.size() >= maxLinks) break;
            result.add(l);
        }
        for (LinkInfo l : otherLinks) {
            if (result.size() >= maxLinks) break;
            result.add(l);
        }

        return result;
    }

    /**
     * Check if the href is just a fragment on the same page (e.g. baseUrl + "#section").
     */
    private static boolean isFragmentOnly(String href, String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) return false;
        try {
            java.net.URL hrefUrl = new java.net.URL(href);
            java.net.URL base = new java.net.URL(baseUrl);

            // Same host + same path + has fragment → fragment-only
            if (hrefUrl.getHost().equalsIgnoreCase(base.getHost())) {
                String hrefPath = hrefUrl.getPath();
                String basePath = base.getPath();
                if (hrefPath == null || hrefPath.isEmpty()) hrefPath = "/";
                if (basePath == null || basePath.isEmpty()) basePath = "/";

                if (hrefPath.equals(basePath) && hrefUrl.getRef() != null) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Get path depth: /foo/ = 1, /foo/bar/ = 2, /foo/bar/baz.html = 3
     */
    private static int getPathDepth(String href) {
        try {
            String path = new java.net.URL(href).getPath();
            if (path == null || path.isEmpty() || path.equals("/")) return 0;
            // Remove trailing slash, then count segments
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String[] segments = path.split("/");
            // First segment is empty (before leading /)
            return segments.length - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isExternal(String href, String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) return false;
        try {
            String hrefHost = new java.net.URL(href).getHost().toLowerCase();
            String baseHost = new java.net.URL(baseUrl).getHost().toLowerCase();
            return !hrefHost.equals(baseHost) && !hrefHost.endsWith("." + baseHost);
        } catch (Exception e) {
            return false;
        }
    }

    private static String cleanText(Element el) {
        if (el == null) return "";
        return el.text()
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ── Data classes ──

    public static class ParsedPageResult {
        public String url = "";
        public String title = "";
        public String excerpt = "";
        public List<LinkInfo> links = new ArrayList<>();
    }

    public static class LinkInfo {
        public String label = "";
        public String href = "";
        public boolean isExternal = false;
    }
}
