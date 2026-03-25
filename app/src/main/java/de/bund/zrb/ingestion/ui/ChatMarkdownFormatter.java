package de.bund.zrb.ingestion.ui;

import de.bund.zrb.mermaid.MermaidRenderer;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat-specific Markdown formatter using commonmark library with GFM extensions.
 * Converts Markdown text to HTML for display in the chat UI.
 *
 * This formatter:
 * - Uses library-based parsing (no regex)
 * - Produces safe HTML output (escapes HTML in input)
 * - Preserves raw Markdown for copy functionality
 * - Supports GFM-style tables
 */
public class ChatMarkdownFormatter {

    private static final ChatMarkdownFormatter INSTANCE = new ChatMarkdownFormatter();

    private final Parser parser;
    private final HtmlRenderer renderer;

    public ChatMarkdownFormatter() {
        // Enable GFM tables extension
        List<Extension> extensions = Arrays.<Extension>asList(TablesExtension.create());

        this.parser = Parser.builder()
                .extensions(extensions)
                .build();
        this.renderer = HtmlRenderer.builder()
                .escapeHtml(true) // Escape any HTML in the input for security
                .extensions(extensions)
                .build();
    }

    /**
     * Get the singleton instance.
     */
    public static ChatMarkdownFormatter getInstance() {
        return INSTANCE;
    }

    /**
     * Static convenience method for formatting Markdown to HTML.
     * Used by ChatFormatter for compatibility.
     *
     * @param markdown the raw Markdown text
     * @return HTML string for display
     */
    public static String format(String markdown) {
        String html = INSTANCE.renderToHtml(markdown);
        // Allow controlled <br> tags in the output
        // The user/bot may have typed <br> which got escaped to &lt;br&gt;
        // We normalize these back to <br/> for line breaks
        html = normalizeBreakTags(html);
        return html;
    }

    /**
     * Normalize escaped <br> tags back to actual line breaks.
     * Only allows &lt;br&gt;, &lt;br/&gt;, &lt;br /&gt; to become <br/>.
     * This is a controlled exception to the HTML escaping.
     */
    private static String normalizeBreakTags(String html) {
        if (html == null) return "";
        // Normalize various escaped br tag forms to <br/>
        return html
                .replace("&lt;br&gt;", "<br/>")
                .replace("&lt;br/&gt;", "<br/>")
                .replace("&lt;br /&gt;", "<br/>")
                .replace("&lt;BR&gt;", "<br/>")
                .replace("&lt;BR/&gt;", "<br/>")
                .replace("&lt;BR /&gt;", "<br/>");
    }

    /**
     * Render Markdown to HTML for display.
     * Mermaid code blocks (```mermaid) are rendered to inline SVG before parsing.
     *
     * @param markdown the raw Markdown text
     * @return HTML string for display
     */
    public String renderToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        try {
            String preprocessed = replaceMermaidBlocks(markdown);
            Node document = parser.parse(preprocessed);
            String html = renderer.render(document);
            // Re-inject raw SVG that was protected during markdown parsing
            html = restoreMermaidSvg(html);
            return html;
        } catch (Exception e) {
            // On error, return escaped plaintext
            return escapeHtml(markdown);
        }
    }

    /**
     * Render Markdown to HTML with a wrapper div.
     *
     * @param markdown the raw Markdown text
     * @param cssClass CSS class for the wrapper
     * @return HTML string wrapped in a div
     */
    public String renderToHtmlWithWrapper(String markdown, String cssClass) {
        String html = renderToHtml(markdown);
        if (cssClass != null && !cssClass.isEmpty()) {
            return "<div class=\"" + escapeHtmlAttribute(cssClass) + "\">" + html + "</div>";
        }
        return "<div>" + html + "</div>";
    }

    /**
     * Check if text appears to contain Markdown formatting.
     */
    public boolean containsMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Check for common Markdown patterns
        return text.contains("# ") ||
               text.contains("## ") ||
               text.contains("### ") ||
               text.contains("```") ||
               text.contains("**") ||
               text.contains("__") ||
               (text.contains("[") && text.contains("](")) ||
               text.contains("\n- ") ||
               text.contains("\n* ") ||
               text.contains("\n1. ");
    }

    /**
     * Extract all code blocks from Markdown.
     * Returns array of code block contents (without fences).
     */
    public String[] extractCodeBlocks(String markdown) {
        if (markdown == null || !markdown.contains("```")) {
            return new String[0];
        }

        java.util.List<String> blocks = new java.util.ArrayList<>();
        String[] parts = markdown.split("```");

        // Odd-indexed parts are inside code fences
        for (int i = 1; i < parts.length; i += 2) {
            String block = parts[i];
            // Remove language identifier from first line
            int newlineIndex = block.indexOf('\n');
            if (newlineIndex > 0 && newlineIndex < 30) {
                String firstLine = block.substring(0, newlineIndex).trim();
                // If first line looks like a language identifier (no spaces, short)
                if (!firstLine.contains(" ") && firstLine.length() < 20) {
                    block = block.substring(newlineIndex + 1);
                }
            }
            blocks.add(block.trim());
        }

        return blocks.toArray(new String[0]);
    }

    /**
     * Escape HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Escape HTML attribute value.
     */
    private String escapeHtmlAttribute(String text) {
        return escapeHtml(text).replace("\n", "").replace("\r", "");
    }

    // ── Mermaid diagram support ─────────────────────────────────────────────

    /** Pattern matching fenced code blocks with the "mermaid" language tag. */
    private static final Pattern MERMAID_BLOCK_PATTERN =
            Pattern.compile("```mermaid\\s*\\n(.*?)```", Pattern.DOTALL);

    /** Placeholder prefix used to protect rendered SVGs from the markdown HTML-escaper. */
    private static final String MERMAID_PLACEHOLDER_PREFIX = "MERMAID_SVG_PLACEHOLDER_";

    /** Collected SVGs from the last {@link #replaceMermaidBlocks} call. Thread-confined. */
    private final ThreadLocal<List<String>> mermaidSvgs = new ThreadLocal<List<String>>();

    /**
     * Replace {@code ```mermaid} code blocks with placeholders and render SVGs.
     * The placeholders survive the commonmark parser and are restored afterwards.
     */
    private String replaceMermaidBlocks(String markdown) {
        MermaidRenderer mermaidRenderer;
        try {
            mermaidRenderer = MermaidRenderer.getInstance();
        } catch (Exception e) {
            return markdown; // renderer not on classpath — leave as-is
        }
        if (!mermaidRenderer.isAvailable()) {
            return markdown;
        }

        Matcher matcher = MERMAID_BLOCK_PATTERN.matcher(markdown);
        if (!matcher.find()) {
            return markdown;
        }

        List<String> svgs = new ArrayList<String>();
        mermaidSvgs.set(svgs);
        StringBuffer sb = new StringBuffer();
        matcher.reset();

        while (matcher.find()) {
            String diagramCode = matcher.group(1).trim();
            String svg = null;
            try {
                svg = mermaidRenderer.renderToSvg(diagramCode);
            } catch (Exception e) {
                // rendering failed — keep the original code block
            }
            if (svg != null && svg.contains("<svg")) {
                int index = svgs.size();
                svgs.add(svg);
                // Replace the mermaid block with a placeholder paragraph that survives markdown parsing
                matcher.appendReplacement(sb, MERMAID_PLACEHOLDER_PREFIX + index + "_END");
            } else {
                // Keep the original fenced block (it will render as a normal code block)
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Replace placeholder strings in the rendered HTML with actual SVG markup.
     */
    private String restoreMermaidSvg(String html) {
        List<String> svgs = mermaidSvgs.get();
        if (svgs == null || svgs.isEmpty()) {
            return html;
        }
        try {
            for (int i = 0; i < svgs.size(); i++) {
                String placeholder = MERMAID_PLACEHOLDER_PREFIX + i + "_END";
                // The placeholder may appear inside a <p> tag or escaped — handle both
                String escapedPlaceholder = escapeHtml(placeholder);
                String svgDiv = "<div class=\"mermaid-diagram\" style=\"text-align:center;margin:8px 0;\">"
                        + svgs.get(i) + "</div>";
                html = html.replace(placeholder, svgDiv);
                html = html.replace(escapedPlaceholder, svgDiv);
            }
            return html;
        } finally {
            mermaidSvgs.remove();
        }
    }
}

