package de.bund.zrb.ingestion.infrastructure.render;

import de.bund.zrb.ingestion.model.document.*;
import de.bund.zrb.ingestion.port.render.BlockRenderer;

import java.util.List;

/**
 * Block renderers for Markdown output.
 */
public final class MarkdownBlockRenderers {

    private MarkdownBlockRenderers() {}

    public static class HeadingRenderer implements BlockRenderer {
        @Override
        public boolean supports(Block block) {
            return block instanceof HeadingBlock;
        }

        @Override
        public void render(Block block, StringBuilder out) {
            HeadingBlock heading = (HeadingBlock) block;
            for (int i = 0; i < heading.getLevel(); i++) {
                out.append('#');
            }
            out.append(' ').append(heading.getText()).append("\n\n");
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
            out.append(para.getText()).append("\n\n");
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
            List<String> items = list.getItems();
            for (int i = 0; i < items.size(); i++) {
                if (list.isOrdered()) {
                    out.append(i + 1).append(". ");
                } else {
                    out.append("- ");
                }
                out.append(items.get(i)).append("\n");
            }
            out.append("\n");
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

            // Headers
            if (!headers.isEmpty()) {
                out.append("| ");
                for (String header : headers) {
                    out.append(header).append(" | ");
                }
                out.append("\n");

                // Separator
                out.append("| ");
                for (int i = 0; i < headers.size(); i++) {
                    out.append("--- | ");
                }
                out.append("\n");
            }

            // Rows
            for (List<String> row : rows) {
                out.append("| ");
                for (String cell : row) {
                    out.append(cell).append(" | ");
                }
                out.append("\n");
            }
            out.append("\n");
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
            out.append("```");
            if (code.hasLanguage()) {
                out.append(code.getLanguage());
            }
            out.append("\n");
            out.append(code.getCode());
            if (!code.getCode().endsWith("\n")) {
                out.append("\n");
            }
            out.append("```\n\n");
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
            String[] lines = quote.getText().split("\n");
            for (String line : lines) {
                out.append("> ").append(line).append("\n");
            }
            out.append("\n");
        }
    }
}

