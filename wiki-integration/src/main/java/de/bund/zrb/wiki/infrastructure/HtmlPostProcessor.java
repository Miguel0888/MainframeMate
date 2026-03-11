package de.bund.zrb.wiki.infrastructure;

import de.bund.zrb.wiki.domain.ImageRef;
import de.bund.zrb.wiki.domain.OutlineNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Cleans MediaWiki HTML output, extracts a heading outline,
 * and strips images from the flow (collecting them as ImageRefs).
 */
final class HtmlPostProcessor {

    Result cleanAndBuildOutline(String wikiHtmlFragment) {
        Document doc = Jsoup.parseBodyFragment(wikiHtmlFragment != null ? wikiHtmlFragment : "");

        // Remove MediaWiki TOC, edit sections, scripts
        doc.select("#toc, .toc, .mw-toc").remove();
        doc.select(".mw-editsection").remove();
        doc.select("script").remove();

        OutlineNode outlineRoot = buildOutline(doc);

        // Extract images before cleaning the body
        List<ImageRef> images = extractAndRemoveImages(doc);

        // Inject <a name="..."></a> before headings for Swing's scrollToReference()
        injectNameAnchors(doc);

        String cleaned = doc.body().html();
        return new Result(cleaned, outlineRoot, images);
    }

    // ═══════════════════════════════════════════════════════════
    //  Image extraction
    // ═══════════════════════════════════════════════════════════

    /**
     * Extract all &lt;img&gt; tags, collect their metadata as {@link ImageRef},
     * then remove the images (and their wrapping figure/thumbnail containers)
     * from the document so the HTML text flow is clean.
     */
    private List<ImageRef> extractAndRemoveImages(Document doc) {
        List<ImageRef> images = new ArrayList<ImageRef>();

        // Collect all <img> elements
        Elements imgs = doc.select("img");
        for (Element img : imgs) {
            String src = img.absUrl("src");
            if (src.isEmpty()) {
                src = img.attr("src");
            }
            // Skip tiny icons/badges (< 30px) — they are UI chrome, not content images
            int w = parseIntAttr(img, "width", 0);
            int h = parseIntAttr(img, "height", 0);
            if (w > 0 && w < 30 && h > 0 && h < 30) {
                img.remove();
                continue;
            }

            String alt = img.attr("alt");
            String title = img.attr("title");
            if (title.isEmpty()) {
                // Try parent <a> title
                Element parent = img.parent();
                if (parent != null && parent.hasAttr("title")) {
                    title = parent.attr("title");
                }
            }

            // Resolve relative wiki image URLs
            if (!src.isEmpty()) {
                if (src.startsWith("//")) {
                    src = "https:" + src;
                }
                images.add(new ImageRef(src, alt, title, w, h));
            }
        }

        // Remove common MediaWiki image wrapper elements
        // (thumbs, figures, image containers) that would leave empty boxes
        doc.select(".thumb, .thumbinner, figure, .mw-file-element").remove();
        // Also remove any remaining <img> tags
        doc.select("img").remove();
        // Clean up empty <a> tags that only contained an image
        for (Element a : doc.select("a.image, a.mw-file-description")) {
            if (a.text().trim().isEmpty()) {
                a.remove();
            }
        }

        return images;
    }

    private static int parseIntAttr(Element el, String attr, int defaultVal) {
        String val = el.attr(attr);
        if (val == null || val.isEmpty()) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Outline
    // ═══════════════════════════════════════════════════════════

    private OutlineNode buildOutline(Document doc) {
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");

        MutableNode root = new MutableNode("ROOT", null, 0);
        Stack<MutableNode> stack = new Stack<MutableNode>();
        stack.push(root);

        for (Element h : headings) {
            int level = parseLevel(h.tagName());
            String anchor = extractAnchor(h);
            String text = extractText(h);

            MutableNode node = new MutableNode(text, anchor, level);
            while (!stack.isEmpty() && stack.peek().level >= level) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                stack.push(root);
            }
            stack.peek().children.add(node);
            stack.push(node);
        }

        return root.toImmutable();
    }

    private int parseLevel(String tagName) {
        try {
            return Integer.parseInt(tagName.substring(1));
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractText(Element heading) {
        Element headline = heading.selectFirst(".mw-headline");
        return headline != null ? headline.text() : heading.text();
    }

    private void injectNameAnchors(Document doc) {
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
        for (Element h : headings) {
            String anchor = extractAnchor(h);
            if (anchor != null && !anchor.isEmpty()) {
                h.before("<a name=\"" + anchor + "\"></a>");
            }
        }
    }

    private String extractAnchor(Element heading) {
        Element headline = heading.selectFirst(".mw-headline[id]");
        if (headline != null) return headline.attr("id");
        if (heading.hasAttr("id")) return heading.attr("id");
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Result & helpers
    // ═══════════════════════════════════════════════════════════

    static final class Result {
        final String cleanedHtml;
        final OutlineNode outlineRoot;
        final List<ImageRef> images;

        Result(String cleanedHtml, OutlineNode outlineRoot, List<ImageRef> images) {
            this.cleanedHtml = cleanedHtml;
            this.outlineRoot = outlineRoot;
            this.images = images;
        }
    }

    private static final class MutableNode {
        final String text;
        final String anchor;
        final int level;
        final List<MutableNode> children = new ArrayList<MutableNode>();

        MutableNode(String text, String anchor, int level) {
            this.text = text;
            this.anchor = anchor;
            this.level = level;
        }

        OutlineNode toImmutable() {
            List<OutlineNode> mapped = new ArrayList<OutlineNode>();
            for (MutableNode c : children) {
                mapped.add(c.toImmutable());
            }
            return new OutlineNode(text, anchor, mapped);
        }
    }
}

