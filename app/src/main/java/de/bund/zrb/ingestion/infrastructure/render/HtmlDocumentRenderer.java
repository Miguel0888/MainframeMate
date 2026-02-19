package de.bund.zrb.ingestion.infrastructure.render;

import de.bund.zrb.ingestion.model.document.Block;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.port.render.BlockRenderer;
import de.bund.zrb.ingestion.port.render.DocumentRenderer;
import de.bund.zrb.ingestion.port.render.RenderFormat;

/**
 * Renders a Document to HTML format (with proper escaping).
 */
public class HtmlDocumentRenderer implements DocumentRenderer {

    private final RendererRegistry registry;
    private final boolean includeWrapper;

    public HtmlDocumentRenderer(RendererRegistry registry) {
        this(registry, false);
    }

    public HtmlDocumentRenderer(RendererRegistry registry, boolean includeWrapper) {
        this.registry = registry;
        this.includeWrapper = includeWrapper;
    }

    @Override
    public String render(Document document) {
        if (document == null || document.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();

        if (includeWrapper) {
            out.append("<div class=\"document\">\n");
        }

        for (Block block : document.getBlocks()) {
            BlockRenderer renderer = registry.findBlockRenderer(RenderFormat.HTML, block);
            if (renderer != null) {
                renderer.render(block, out);
            } else {
                // Fallback: render as escaped paragraph
                out.append("<p class=\"unsupported-block\">[Block: ")
                   .append(HtmlBlockRenderers.escapeHtml(block.getType().toString()))
                   .append("]</p>\n");
            }
        }

        if (includeWrapper) {
            out.append("</div>\n");
        }

        return out.toString();
    }

    @Override
    public RenderFormat getFormat() {
        return RenderFormat.HTML;
    }

    /**
     * Render as a complete HTML page with styles.
     */
    public String renderAsPage(Document document, String title) {
        StringBuilder out = new StringBuilder();
        out.append("<!DOCTYPE html>\n");
        out.append("<html>\n<head>\n");
        out.append("  <meta charset=\"UTF-8\">\n");
        out.append("  <title>").append(HtmlBlockRenderers.escapeHtml(title != null ? title : "Document")).append("</title>\n");
        out.append("  <style>\n");
        out.append("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        out.append("           max-width: 800px; margin: 40px auto; padding: 0 20px; line-height: 1.6; }\n");
        out.append("    pre { background: #f4f4f4; padding: 12px; border-radius: 4px; overflow-x: auto; }\n");
        out.append("    code { background: #f4f4f4; padding: 2px 4px; border-radius: 2px; }\n");
        out.append("    pre code { background: none; padding: 0; }\n");
        out.append("    blockquote { border-left: 3px solid #ddd; margin: 0; padding-left: 16px; color: #666; }\n");
        out.append("    table { border-collapse: collapse; width: 100%; }\n");
        out.append("    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        out.append("    th { background: #f4f4f4; }\n");
        out.append("  </style>\n");
        out.append("</head>\n<body>\n");
        out.append(render(document));
        out.append("</body>\n</html>");
        return out.toString();
    }
}

