package de.bund.zrb.ingestion.infrastructure.render;

import de.bund.zrb.ingestion.model.document.*;
import de.bund.zrb.ingestion.port.render.BlockRenderer;

import java.util.List;

/**
 * Block renderers for Plaintext output (minimal, compact).
 */
public final class PlaintextBlockRenderers {

    private PlaintextBlockRenderers() {}

    public static class HeadingRenderer implements BlockRenderer {
        @Override
        public boolean supports(Block block) {
            return block instanceof HeadingBlock;
        }

        @Override
        public void render(Block block, StringBuilder out) {
            HeadingBlock heading = (HeadingBlock) block;
            out.append(heading.getText().toUpperCase()).append("\n\n");
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
                    out.append(i + 1).append(") ");
                } else {
                    out.append("* ");
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

            // Simple tab-separated format
            if (!headers.isEmpty()) {
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) out.append("\t");
                    out.append(headers.get(i));
                }
                out.append("\n");
            }

            for (List<String> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) out.append("\t");
                    out.append(row.get(i));
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
            out.append(code.getCode());
            if (!code.getCode().endsWith("\n")) {
                out.append("\n");
            }
            out.append("\n");
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
            out.append("\"").append(quote.getText()).append("\"\n\n");
        }
    }
}

