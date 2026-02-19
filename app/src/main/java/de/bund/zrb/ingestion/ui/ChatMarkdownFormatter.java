package de.bund.zrb.ingestion.ui;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Chat-specific Markdown formatter using commonmark library.
 * Converts Markdown text to HTML for display in the chat UI.
 *
 * This formatter:
 * - Uses library-based parsing (no regex)
 * - Produces safe HTML output
 * - Preserves raw Markdown for copy functionality
 */
public class ChatMarkdownFormatter {

    private static final ChatMarkdownFormatter INSTANCE = new ChatMarkdownFormatter();

    private final Parser parser;
    private final HtmlRenderer renderer;

    public ChatMarkdownFormatter() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder()
                .escapeHtml(true) // Escape any HTML in the input
                .build();
    }

    /**
     * Get the singleton instance.
     */
    public static ChatMarkdownFormatter getInstance() {
        return INSTANCE;
    }

    /**
     * Render Markdown to HTML for display.
     * The input Markdown is preserved for copy functionality.
     *
     * @param markdown the raw Markdown text
     * @return HTML string for display
     */
    public String renderToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        try {
            Node document = parser.parse(markdown);
            return renderer.render(document);
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
}

