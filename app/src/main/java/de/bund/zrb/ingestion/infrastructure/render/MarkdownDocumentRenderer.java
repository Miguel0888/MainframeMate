package de.bund.zrb.ingestion.infrastructure.render;

import de.bund.zrb.ingestion.model.document.Block;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.port.render.BlockRenderer;
import de.bund.zrb.ingestion.port.render.DocumentRenderer;
import de.bund.zrb.ingestion.port.render.RenderFormat;

/**
 * Renders a Document to Markdown format.
 */
public class MarkdownDocumentRenderer implements DocumentRenderer {

    private final RendererRegistry registry;

    public MarkdownDocumentRenderer(RendererRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String render(Document document) {
        if (document == null || document.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();

        for (Block block : document.getBlocks()) {
            BlockRenderer renderer = registry.findBlockRenderer(RenderFormat.MARKDOWN, block);
            if (renderer != null) {
                renderer.render(block, out);
            } else {
                // Fallback: render as paragraph with block info
                out.append("[Unsupported block: ").append(block.getType()).append("]\n\n");
            }
        }

        // Trim trailing whitespace
        return out.toString().trim();
    }

    @Override
    public RenderFormat getFormat() {
        return RenderFormat.MARKDOWN;
    }
}

