package de.bund.zrb.ui.preview;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panel that renders all pages of a PDF document vertically using PDFBox.
 * Each page is rendered as a {@link BufferedImage} at the specified DPI and
 * stacked top-to-bottom with a small gap and page number labels.
 * <p>
 * This provides a true visual PDF preview (layout, fonts, images) instead of
 * just showing extracted text.
 */
public class PdfPagesPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(PdfPagesPanel.class.getName());

    /** DPI for rendering — 150 is a good balance between quality and speed. */
    private static final float RENDER_DPI = 150f;
    /** Vertical gap between pages (pixels). */
    private static final int PAGE_GAP = 12;
    /** Background colour between pages. */
    private static final Color BG_COLOR = new Color(0x7F, 0x7F, 0x7F);

    private final List<BufferedImage> pageImages = new ArrayList<>();
    private int totalPages;

    public PdfPagesPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG_COLOR);
    }

    /**
     * Render PDF bytes in a background thread, then display pages on the EDT.
     *
     * @param pdfBytes raw PDF document bytes
     */
    public void loadAsync(final byte[] pdfBytes) {
        // Show loading placeholder immediately
        removeAll();
        JLabel loading = new JLabel("⏳ PDF wird gerendert…", SwingConstants.CENTER);
        loading.setFont(loading.getFont().deriveFont(14f));
        loading.setForeground(Color.WHITE);
        loading.setAlignmentX(CENTER_ALIGNMENT);
        add(Box.createVerticalStrut(40));
        add(loading);
        revalidate();
        repaint();

        new Thread(new Runnable() {
            @Override
            public void run() {
                renderPages(pdfBytes);
            }
        }, "PdfPageRenderer").start();
    }

    /**
     * Render PDF bytes synchronously (call from background thread only).
     */
    private void renderPages(byte[] pdfBytes) {
        PDDocument document = null;
        try {
            document = PDDocument.load(new ByteArrayInputStream(pdfBytes));
            totalPages = document.getNumberOfPages();
            final PDFRenderer renderer = new PDFRenderer(document);

            final List<BufferedImage> rendered = new ArrayList<>();
            for (int i = 0; i < totalPages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, RENDER_DPI);
                rendered.add(img);
            }

            final PDDocument docRef = document;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    buildPageLayout(rendered);
                    try {
                        docRef.close();
                    } catch (Exception ignored) { }
                }
            });
            document = null; // ownership transferred to EDT runnable

        } catch (final Exception e) {
            LOG.log(Level.WARNING, "PDF page rendering failed", e);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showError(e.getMessage());
                }
            });
            if (document != null) {
                try { document.close(); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * Build the Swing layout from pre-rendered page images.
     * Called on the EDT.
     */
    private void buildPageLayout(List<BufferedImage> images) {
        removeAll();
        pageImages.clear();
        pageImages.addAll(images);

        add(Box.createVerticalStrut(PAGE_GAP));

        for (int i = 0; i < images.size(); i++) {
            BufferedImage img = images.get(i);

            // Page label
            JLabel pageLabel = new JLabel("Seite " + (i + 1) + " / " + images.size());
            pageLabel.setFont(pageLabel.getFont().deriveFont(Font.PLAIN, 11f));
            pageLabel.setForeground(new Color(0xCC, 0xCC, 0xCC));
            pageLabel.setAlignmentX(CENTER_ALIGNMENT);
            add(pageLabel);
            add(Box.createVerticalStrut(4));

            // Page image panel with drop shadow
            PageImagePanel pagePanel = new PageImagePanel(img);
            pagePanel.setAlignmentX(CENTER_ALIGNMENT);
            add(pagePanel);

            add(Box.createVerticalStrut(PAGE_GAP));
        }

        revalidate();
        repaint();

        // Scroll to top
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                scrollRectToVisible(new Rectangle(0, 0, 1, 1));
            }
        });
    }

    private void showError(String message) {
        removeAll();
        JLabel errorLabel = new JLabel(
                "<html><div style='padding:20px; color:#FF6B6B;'>" +
                "⚠ PDF-Rendering fehlgeschlagen:<br>" + message + "</div></html>");
        errorLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(Box.createVerticalStrut(40));
        add(errorLabel);
        revalidate();
        repaint();
    }

    /**
     * Returns the number of rendered pages.
     */
    public int getTotalPages() {
        return totalPages;
    }

    // =========================================================================
    //  Inner panel that draws a single page image with a white background
    //  and subtle shadow.
    // =========================================================================
    private static class PageImagePanel extends JPanel {
        private final BufferedImage image;

        PageImagePanel(BufferedImage img) {
            this.image = img;
            int w = img.getWidth();
            int h = img.getHeight();
            Dimension size = new Dimension(w + 4, h + 4); // 2px shadow offset
            setPreferredSize(size);
            setMaximumSize(size);
            setMinimumSize(size);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Drop shadow
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRect(3, 3, image.getWidth(), image.getHeight());

            // White page background
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, image.getWidth(), image.getHeight());

            // Page image
            g2.drawImage(image, 0, 0, null);

            // Thin border
            g2.setColor(new Color(0, 0, 0, 30));
            g2.drawRect(0, 0, image.getWidth() - 1, image.getHeight() - 1);

            g2.dispose();
        }
    }
}

