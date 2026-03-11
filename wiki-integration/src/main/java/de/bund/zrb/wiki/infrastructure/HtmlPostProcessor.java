package de.bund.zrb.wiki.infrastructure;

import de.bund.zrb.wiki.domain.OutlineNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Cleans MediaWiki HTML output and extracts a heading outline.
 */
final class HtmlPostProcessor {

    Result cleanAndBuildOutline(String wikiHtmlFragment) {
        Document doc = Jsoup.parseBodyFragment(wikiHtmlFragment != null ? wikiHtmlFragment : "");

        // Remove MediaWiki TOC, edit sections, scripts
        doc.select("#toc, .toc, .mw-toc").remove();
        doc.select(".mw-editsection").remove();
        doc.select("script").remove();

        OutlineNode outlineRoot = buildOutline(doc);

        // Inject <a name="..."></a> before headings for Swing's scrollToReference()
        // JEditorPane only supports <a name="...">, not id="..." on arbitrary elements
        injectNameAnchors(doc);

        String cleaned = doc.body().html();
        return new Result(cleaned, outlineRoot);
    }

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

    /**
     * Inject {@code <a name="anchor"></a>} right before each heading that has an id.
     * Also checks child elements like {@code <span class="mw-headline" id="...">}.
     * This is necessary because Swing's JEditorPane.scrollToReference() only looks for
     * {@code <a name="...">}, not {@code id="..."} on arbitrary elements.
     */
    private void injectNameAnchors(Document doc) {
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
        for (Element h : headings) {
            String anchor = extractAnchor(h);
            if (anchor != null && !anchor.isEmpty()) {
                // Prepend an <a name="anchor"></a> right before the heading
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

    static final class Result {
        final String cleanedHtml;
        final OutlineNode outlineRoot;

        Result(String cleanedHtml, OutlineNode outlineRoot) {
            this.cleanedHtml = cleanedHtml;
            this.outlineRoot = outlineRoot;
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

