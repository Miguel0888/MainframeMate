package de.bund.zrb.ingestion.infrastructure.render;

import de.bund.zrb.ingestion.model.document.*;
import de.bund.zrb.ingestion.port.render.BlockRenderer;

import java.util.List;

/**
 * Block renderers for HTML output (with escaping).
 */
public final class HtmlBlockRenderers {

    private HtmlBlockRenderers() {}

    /**
     * Escape HTML special characters.
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static class HeadingRenderer implements BlockRenderer {
        @Override
        public boolean supports(Block block) {
            return block instanceof HeadingBlock;
        }

        @Override
        public void render(Block block, StringBuilder out) {
            HeadingBlock heading = (HeadingBlock) block;
            int level = heading.getLevel();
            out.append("<h").append(level).append(">");
            out.append(escapeHtml(heading.getText()));
            out.append("</h").append(level).append(">\n");
        }
    }

    public static class ParagraphRenderer implements BlockRenderer {
        @Override
        public boolean supports(Block block) {
            return block instanceof ParagraphBlock;
        }

        @Override
        public void render(Block block, StringBuilder out) {
            ParagraphBlock para = (ParagraphBlock) block;
            out.append("<p>").append(escapeHtml(para.getText())).append("</p>\n");
        }
    }

    public static class ListRenderer implements BlockRenderer {
        @Override
        public boolean supports(Block block) {
            return block instanceof ListBlock;
        }

        @Override
        public void render(Block block, StringBuilder out) {
            ListBlock list = (ListBlock) block;
            String tag = list.isOrdered() ? "ol" : "ul";
            out.append("<").append(tag).append(">\n");
            for (String item : list.getItems()) {
                out.append("  <li>").append(escapeHtml(item)).append("</li>\n");
            }
            out.append("</").append(tag).append(">\n");
        }
    }

    public static class TableRenderer implements BlockRenderer {
        @Override
        public boolean supports(Block block) {
            return block instanceof TableBlock;
        }

        @Override
        public void render(Block block, StringBuilder out) {
            TableBlock table = (TableBlock) block;
            List<String> headers = table.getHeaders();
            List<List<String>> rows = table.getRows();

            out.append("<table>\n");

            // Headers
            if (!headers.isEmpty()) {
                out.append("  <thead>\n    <tr>\n");
                for (String header : headers) {
                    out.append("      <th>").append(escapeHtml(header)).append("</th>\n");
                }
                out.append("    </tr>\n  </thead>\n");
            }

            // Body
            if (!rows.isEmpty()) {
                out.append("  <tbody>\n");
                for (List<String> row : rows) {
                    out.append("    <tr>\n");
                    for (String cell : row) {
                        out.append("      <td>").append(escapeHtml(cell)).append("</td>\n");
                    }
                    out.append("    </tr>\n");
                }
                out.append("  </tbody>\n");
            }

            out.append("</table>\n");
        }
    }

    public static class CodeRenderer implements BlockRenderer {
        @Override
        public boolean supports(Block block) {
            return block instanceof CodeBlock;
        }

        @Override
        public void render(Block block, StringBuilder out) {
            CodeBlock code = (CodeBlock) block;
            out.append("<pre>");
            if (code.hasLanguage()) {
                out.append("<code class=\"language-").append(escapeHtml(code.getLanguage())).append("\">");
            } else {
                out.append("<code>");
            }
            out.append(escapeHtml(code.getCode()));
            out.append("</code></pre>\n");
        }
    }

    public static class QuoteRenderer implements BlockRenderer {
        @Override
        public boolean supports(Block block) {
            return block instanceof QuoteBlock;
        }

        @Override
        public void render(Block block, StringBuilder out) {
            QuoteBlock quote = (QuoteBlock) block;
            out.append("<blockquote>").append(escapeHtml(quote.getText())).append("</blockquote>\n");
        }
    }
}

