package de.bund.zrb.ingestion.port.render;

import de.bund.zrb.ingestion.model.document.Document;

/**
 * Port interface for rendering a Document to a specific format.
 */
public interface DocumentRenderer {

    /**
     * Render the document to a string in the target format.
     *
     * @param document the document to render
     * @return rendered string
     */
    String render(Document document);

    /**
     * Get the output format of this renderer.
     */
    RenderFormat getFormat();
}

