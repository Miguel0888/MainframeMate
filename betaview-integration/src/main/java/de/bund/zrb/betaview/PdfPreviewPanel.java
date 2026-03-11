package de.bund.zrb.betaview;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

/**
 * Shows either a rendered PDF page or plain text, depending on what the
 * server delivers.
 */
public final class PdfPreviewPanel extends JPanel {

    private static final String CARD_IMAGE = "image";
    private static final String CARD_TEXT  = "text";

    private final CardLayout cards;
    private final JPanel cardPanel;
    private final ImagePanel imagePanel;
    private final JTextArea textArea;
    private final JLabel statusLabel;

    public PdfPreviewPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Vorschau"));

        cards = new CardLayout();
        cardPanel = new JPanel(cards);

        // image view for PDFs
        imagePanel = new ImagePanel();
        JScrollPane imageScroll = new JScrollPane(imagePanel);
        cardPanel.add(imageScroll, CARD_IMAGE);

        // text view for plain text / CSV
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setBackground(Color.WHITE);
        JScrollPane textScroll = new JScrollPane(textArea);
        cardPanel.add(textScroll, CARD_TEXT);

        add(cardPanel, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        add(statusLabel, BorderLayout.SOUTH);
    }

    /**
     * Shows the content based on the download result's content type.
     */
    public void showContent(DownloadResult result) {
        if (result == null || result.data().length == 0) {
            showMessage("Keine Daten");
            return;
        }

        if (result.isPdf()) {
            showPdf(result.data());
        } else {
            // treat everything else as text (plain text, CSV, unknown)
            showText(result.data(), result.contentType());
        }
    }

    /**
     * Renders the first page of the given PDF bytes.
     */
    public void showPdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            showMessage("Keine Daten");
            return;
        }

        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(0, 150);
            imagePanel.setImage(image);
            cards.show(cardPanel, CARD_IMAGE);
            statusLabel.setText("PDF (" + pdfBytes.length / 1024 + " KB)");
        } catch (Exception e) {
            // Not a valid PDF – try showing as text
            showText(pdfBytes, "application/octet-stream");
        }
    }

    /**
     * Shows raw bytes interpreted as text.
     */
    public void showText(byte[] data, String contentType) {
        String charset = extractCharset(contentType);
        String text;
        try {
            text = new String(data, charset);
        } catch (Exception e) {
            text = new String(data, StandardCharsets.UTF_8);
        }
        textArea.setText(text);
        textArea.setCaretPosition(0);
        cards.show(cardPanel, CARD_TEXT);
        statusLabel.setText("Text (" + data.length / 1024 + " KB, " + charset + ")");
    }

    public void showPlainText(String text) {
        textArea.setText(text);
        textArea.setCaretPosition(0);
        cards.show(cardPanel, CARD_TEXT);
        statusLabel.setText("Text (" + text.length() + " Zeichen)");
    }

    public void showMessage(String msg) {
        textArea.setText(msg);
        textArea.setCaretPosition(0);
        cards.show(cardPanel, CARD_TEXT);
        statusLabel.setText(msg);
    }

    public void clear() {
        imagePanel.setImage(null);
        textArea.setText("");
        statusLabel.setText(" ");
    }

    // ----------------------------------------------------------------

    private static String extractCharset(String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase();
            int idx = lower.indexOf("charset=");
            if (idx >= 0) {
                String cs = contentType.substring(idx + 8).trim();
                int semi = cs.indexOf(';');
                if (semi > 0) cs = cs.substring(0, semi);
                return cs.trim();
            }
        }
        return "UTF-8";
    }

    private static final class ImagePanel extends JPanel {
        private BufferedImage image;

        ImagePanel() {
            setBackground(Color.LIGHT_GRAY);
        }

        void setImage(BufferedImage img) {
            this.image = img;
            if (img != null) {
                setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
            } else {
                setPreferredSize(new Dimension(0, 0));
            }
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(image, 0, 0, null);
            }
        }
    }
}
