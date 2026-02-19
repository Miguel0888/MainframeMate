package de.bund.zrb.ingestion.usecase;

import de.bund.zrb.ingestion.infrastructure.render.RendererRegistry;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.port.render.DocumentRenderer;
import de.bund.zrb.ingestion.port.render.RenderFormat;

/**
 * Use case for rendering documents to different formats.
 * Selects the appropriate renderer based on target format.
 */
public class RenderDocumentUseCase {

    private final RendererRegistry registry;

    public RenderDocumentUseCase() {
        this(RendererRegistry.createDefault());
    }

    public RenderDocumentUseCase(RendererRegistry registry) {
        this.registry = registry;
    }

    /**
     * Render a document to the specified format.
     *
     * @param document the document to render
     * @param format the target format
     * @return rendered string
     * @throws IllegalArgumentException if no renderer is available for the format
     */
    public String render(Document document, RenderFormat format) {
        if (document == null) {
            return "";
        }

        DocumentRenderer renderer = registry.getDocumentRenderer(format);
        if (renderer == null) {
            throw new IllegalArgumentException("No renderer available for format: " + format);
        }

        return renderer.render(document);
    }

    /**
     * Render a document to Markdown (for LLM/export).
     */
    public String renderToMarkdown(Document document) {
        return render(document, RenderFormat.MARKDOWN);
    }

    /**
     * Render a document to Plaintext (for LLM, minimal overhead).
     */
    public String renderToPlaintext(Document document) {
        return render(document, RenderFormat.PLAINTEXT);
    }

    /**
     * Render a document to HTML (for UI).
     */
    public String renderToHtml(Document document) {
        return render(document, RenderFormat.HTML);
    }

    /**
     * Get the renderer registry for customization.
     */
    public RendererRegistry getRegistry() {
        return registry;
    }
}

