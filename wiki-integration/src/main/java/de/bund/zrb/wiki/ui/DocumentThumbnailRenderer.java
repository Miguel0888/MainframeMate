package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.AttachmentRef;

import java.awt.image.BufferedImage;

/**
 * Pluggable renderer for document attachment thumbnails.
 * <p>
 * The {@code wiki-integration} module does not depend on PDFBox or other
 * document rendering libraries. Callers (e.g. the {@code app} module) can
 * supply an implementation to generate thumbnails for PDFs, Office files, etc.
 * <p>
 * If no renderer is set, the strip/thumbnail panels fall back to
 * a type-specific icon placeholder.
 *
 * @see ImageThumbnailPanel#setDocumentThumbnailRenderer(DocumentThumbnailRenderer)
 * @see ImageStripPanel#setDocumentThumbnailRenderer(DocumentThumbnailRenderer)
 */
public interface DocumentThumbnailRenderer {

    /**
     * Render a thumbnail for the given document attachment.
     *
     * @param ref      attachment reference (use {@link AttachmentRef#data()} for raw bytes,
     *                 or {@link AttachmentRef#src()} to download/locate the file)
     * @param maxWidth desired maximum thumbnail width in pixels
     * @return a rendered thumbnail image, or {@code null} if this renderer
     *         does not support the given attachment type
     * @throws Exception if rendering fails
     */
    BufferedImage renderThumbnail(AttachmentRef ref, int maxWidth) throws Exception;

    /**
     * Check whether this renderer can handle the given attachment type.
     *
     * @param type attachment type to check
     * @return {@code true} if {@link #renderThumbnail} should be called for this type
     */
    boolean supports(AttachmentRef.Type type);
}

