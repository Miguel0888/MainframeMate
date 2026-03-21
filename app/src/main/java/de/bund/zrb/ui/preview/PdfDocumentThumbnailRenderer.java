package de.bund.zrb.ui.preview;

import de.bund.zrb.wiki.domain.AttachmentRef;
import de.bund.zrb.wiki.ui.DocumentThumbnailRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders document thumbnails using available libraries.
 * <ul>
 *   <li><b>PDF</b>: first page rendered via PDFBox {@link PDFRenderer} at 72 DPI</li>
 *   <li>Other types: returns {@code null} (caller shows type icon fallback)</li>
 * </ul>
 * <p>
 * This class lives in the {@code app} module where PDFBox is available.
 * It is injected into {@link de.bund.zrb.wiki.ui.HtmlPreviewPanel} and
 * {@link de.bund.zrb.wiki.ui.ImageThumbnailPanel} via
 * {@link de.bund.zrb.wiki.ui.HtmlPreviewPanel#setDocumentThumbnailRenderer}.
 */
public class PdfDocumentThumbnailRenderer implements DocumentThumbnailRenderer {

    private static final Logger LOG = Logger.getLogger(PdfDocumentThumbnailRenderer.class.getName());

    /** DPI for thumbnail rendering — lower than full preview for speed. */
    private static final float THUMBNAIL_DPI = 72f;

    @Override
    public boolean supports(AttachmentRef.Type type) {
        return type == AttachmentRef.Type.PDF;
    }

    @Override
    public BufferedImage renderThumbnail(AttachmentRef ref, int maxWidth) throws Exception {
        byte[] data = ref.data();
        if (data == null || data.length == 0) {
            LOG.fine("[PdfThumb] No data for: " + ref.name());
            return null;
        }

        PDDocument document = null;
        try {
            document = PDDocument.load(new ByteArrayInputStream(data));
            if (document.getNumberOfPages() == 0) return null;

            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage page = renderer.renderImageWithDPI(0, THUMBNAIL_DPI);

            // Scale down if wider than maxWidth
            if (page.getWidth() > maxWidth && maxWidth > 0) {
                double scale = (double) maxWidth / page.getWidth();
                int newW = maxWidth;
                int newH = Math.max(1, (int) (page.getHeight() * scale));

                BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = scaled.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(page, 0, 0, newW, newH, null);
                g2.dispose();
                return scaled;
            }

            return page;
        } finally {
            if (document != null) {
                try { document.close(); } catch (Exception ignored) { }
            }
        }
    }
}

