package de.bund.zrb.ingestion.ui;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Markdown renderer for UI display using commonmark-java.
 * Converts Markdown text to HTML for rendering in the chat UI.
 */
public class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().build();
    }

    /**
     * Render Markdown to HTML.
     *
     * @param markdown the Markdown text
     * @return HTML string
     */
    public String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    /**
     * Render Markdown to HTML with wrapper div.
     *
     * @param markdown the Markdown text
     * @param cssClass CSS class for the wrapper div
     * @return HTML string wrapped in a div
     */
    public String renderWithWrapper(String markdown, String cssClass) {
        String html = render(markdown);
        if (cssClass != null && !cssClass.isEmpty()) {
            return "<div class=\"" + escapeHtml(cssClass) + "\">" + html + "</div>";
        }
        return "<div>" + html + "</div>";
    }

    /**
     * Check if the text appears to be Markdown.
     * Simple heuristic check for common Markdown patterns.
     */
    public boolean looksLikeMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Check for common Markdown patterns
        return text.contains("# ") ||           // Headings
               text.contains("## ") ||
               text.contains("- ") ||           // Lists
               text.contains("* ") ||
               text.contains("```") ||          // Code blocks
               text.contains("[") && text.contains("](") ||  // Links
               text.contains("**") ||           // Bold
               text.contains("__") ||
               text.contains("_") ||            // Italic
               text.contains("`");              // Inline code
    }

    /**
     * Extract code blocks from Markdown.
     * Useful for Copy functionality.
     */
    public String[] extractCodeBlocks(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new String[0];
        }

        java.util.List<String> blocks = new java.util.ArrayList<>();
        String[] parts = markdown.split("```");

        // Every odd-indexed part (1, 3, 5, ...) is inside a code fence
        for (int i = 1; i < parts.length; i += 2) {
            String block = parts[i];
            // Remove language identifier from first line
            int newlineIndex = block.indexOf('\n');
            if (newlineIndex > 0 && newlineIndex < 20) {
                // Likely a language identifier
                String firstLine = block.substring(0, newlineIndex).trim();
                if (!firstLine.contains(" ")) {
                    block = block.substring(newlineIndex + 1);
                }
            }
            blocks.add(block.trim());
        }

        return blocks.toArray(new String[0]);
    }

    /**
     * Simple HTML escaping for attribute values.
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Create a basic HTML page with the rendered Markdown.
     */
    public String renderAsPage(String markdown, String title) {
        String html = render(markdown);
        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<head>\n" +
               "  <meta charset=\"UTF-8\">\n" +
               "  <title>" + escapeHtml(title != null ? title : "Document") + "</title>\n" +
               "  <style>\n" +
               "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
               "           max-width: 800px; margin: 40px auto; padding: 0 20px; line-height: 1.6; }\n" +
               "    pre { background: #f4f4f4; padding: 12px; border-radius: 4px; overflow-x: auto; }\n" +
               "    code { background: #f4f4f4; padding: 2px 4px; border-radius: 2px; }\n" +
               "    pre code { background: none; padding: 0; }\n" +
               "    blockquote { border-left: 3px solid #ddd; margin: 0; padding-left: 16px; color: #666; }\n" +
               "  </style>\n" +
               "</head>\n" +
               "<body>\n" + html + "\n</body>\n</html>";
    }
}
