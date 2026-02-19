package de.bund.zrb.ingestion.infrastructure.render;

import de.bund.zrb.ingestion.model.document.Block;
import de.bund.zrb.ingestion.port.render.BlockRenderer;
import de.bund.zrb.ingestion.port.render.DocumentRenderer;
import de.bund.zrb.ingestion.port.render.RenderFormat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for document and block renderers.
 * Maintains renderers by format and provides lookup functionality.
 */
public class RendererRegistry {

    private final Map<RenderFormat, DocumentRenderer> documentRenderers;
    private final Map<RenderFormat, List<BlockRenderer>> blockRenderers;

    public RendererRegistry() {
        this.documentRenderers = new EnumMap<>(RenderFormat.class);
        this.blockRenderers = new EnumMap<>(RenderFormat.class);
        for (RenderFormat format : RenderFormat.values()) {
            blockRenderers.put(format, new ArrayList<BlockRenderer>());
        }
    }

    /**
     * Register a document renderer.
     */
    public void registerDocumentRenderer(DocumentRenderer renderer) {
        documentRenderers.put(renderer.getFormat(), renderer);
    }

    /**
     * Register a block renderer for a specific format.
     */
    public void registerBlockRenderer(RenderFormat format, BlockRenderer renderer) {
        blockRenderers.get(format).add(renderer);
    }

    /**
     * Get the document renderer for a format.
     */
    public DocumentRenderer getDocumentRenderer(RenderFormat format) {
        return documentRenderers.get(format);
    }

    /**
     * Find a block renderer that supports the given block.
     */
    public BlockRenderer findBlockRenderer(RenderFormat format, Block block) {
        List<BlockRenderer> renderers = blockRenderers.get(format);
        for (BlockRenderer renderer : renderers) {
            if (renderer.supports(block)) {
                return renderer;
            }
        }
        return null;
    }

    /**
     * Get all block renderers for a format.
     */
    public List<BlockRenderer> getBlockRenderers(RenderFormat format) {
        return new ArrayList<>(blockRenderers.get(format));
    }

    /**
     * Create a registry with default renderers pre-registered.
     */
    public static RendererRegistry createDefault() {
        RendererRegistry registry = new RendererRegistry();

        // Register Markdown renderer
        MarkdownDocumentRenderer markdownRenderer = new MarkdownDocumentRenderer(registry);
        registry.registerDocumentRenderer(markdownRenderer);
        registry.registerBlockRenderer(RenderFormat.MARKDOWN, new MarkdownBlockRenderers.HeadingRenderer());
        registry.registerBlockRenderer(RenderFormat.MARKDOWN, new MarkdownBlockRenderers.ParagraphRenderer());
        registry.registerBlockRenderer(RenderFormat.MARKDOWN, new MarkdownBlockRenderers.ListRenderer());
        registry.registerBlockRenderer(RenderFormat.MARKDOWN, new MarkdownBlockRenderers.TableRenderer());
        registry.registerBlockRenderer(RenderFormat.MARKDOWN, new MarkdownBlockRenderers.CodeRenderer());
        registry.registerBlockRenderer(RenderFormat.MARKDOWN, new MarkdownBlockRenderers.QuoteRenderer());

        // Register Plaintext renderer
        PlaintextDocumentRenderer plaintextRenderer = new PlaintextDocumentRenderer(registry);
        registry.registerDocumentRenderer(plaintextRenderer);
        registry.registerBlockRenderer(RenderFormat.PLAINTEXT, new PlaintextBlockRenderers.HeadingRenderer());
        registry.registerBlockRenderer(RenderFormat.PLAINTEXT, new PlaintextBlockRenderers.ParagraphRenderer());
        registry.registerBlockRenderer(RenderFormat.PLAINTEXT, new PlaintextBlockRenderers.ListRenderer());
        registry.registerBlockRenderer(RenderFormat.PLAINTEXT, new PlaintextBlockRenderers.TableRenderer());
        registry.registerBlockRenderer(RenderFormat.PLAINTEXT, new PlaintextBlockRenderers.CodeRenderer());
        registry.registerBlockRenderer(RenderFormat.PLAINTEXT, new PlaintextBlockRenderers.QuoteRenderer());

        // Register HTML renderer
        HtmlDocumentRenderer htmlRenderer = new HtmlDocumentRenderer(registry);
        registry.registerDocumentRenderer(htmlRenderer);
        registry.registerBlockRenderer(RenderFormat.HTML, new HtmlBlockRenderers.HeadingRenderer());
        registry.registerBlockRenderer(RenderFormat.HTML, new HtmlBlockRenderers.ParagraphRenderer());
        registry.registerBlockRenderer(RenderFormat.HTML, new HtmlBlockRenderers.ListRenderer());
        registry.registerBlockRenderer(RenderFormat.HTML, new HtmlBlockRenderers.TableRenderer());
        registry.registerBlockRenderer(RenderFormat.HTML, new HtmlBlockRenderers.CodeRenderer());
        registry.registerBlockRenderer(RenderFormat.HTML, new HtmlBlockRenderers.QuoteRenderer());

        return registry;
    }
}

