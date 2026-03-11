package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.ImageRef;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Narrow vertical strip showing 🖼 icons for each image extracted from a wiki page.
 * <ul>
 *   <li>Hover → tooltip with image description</li>
 *   <li>Click → overlay dialog with full image + download button</li>
 * </ul>
 */
public class ImageStripPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(ImageStripPanel.class.getName());
    private static final int ICON_SIZE = 28;
    private static final int GAP = 4;

    /**
     * User-Agent sent when downloading images.
     * Wikimedia requires a descriptive UA — bare "Java/x" gets 403.
     * See https://meta.wikimedia.org/wiki/User-Agent_policy
     */
    private static final String USER_AGENT =
            "MainframeMate/1.0 (https://github.com/Miguel0888/MainframeMate; Java)";

    public ImageStripPanel(List<ImageRef> images) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        setPreferredSize(new Dimension(ICON_SIZE + 8, 0));

        if (images == null || images.isEmpty()) {
            setVisible(false);
            return;
        }

        for (final ImageRef img : images) {
            JLabel iconLabel = new JLabel("🖼");
            iconLabel.setFont(iconLabel.getFont().deriveFont(16f));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            iconLabel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
            iconLabel.setMaximumSize(new Dimension(ICON_SIZE, ICON_SIZE));
            iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Tooltip: image description
            String tooltip = "<html><b>" + escHtml(img.description()) + "</b>";
            if (img.width() > 0 && img.height() > 0) {
                tooltip += "<br>" + img.width() + " × " + img.height();
            }
            tooltip += "</html>";
            iconLabel.setToolTipText(tooltip);

            // Click: show overlay
            iconLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showImageOverlay(img);
                }
            });

            add(iconLabel);
            add(Box.createVerticalStrut(GAP));
        }

        add(Box.createVerticalGlue());
    }

    /**
     * Show an overlay dialog with the full image and a download button.
     */
    private void showImageOverlay(ImageRef img) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame ? (Frame) owner : null,
                img.description(), true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JLabel imageLabel = new JLabel("⏳ Lade Bild…", SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(500, 400));
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        JLabel infoLabel = new JLabel(img.description());
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC));
        bottomPanel.add(infoLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        JButton downloadBtn = new JButton("💾 Herunterladen");
        downloadBtn.setEnabled(false);
        downloadBtn.addActionListener(e -> downloadImage(img));
        buttonPanel.add(downloadBtn);

        JButton closeBtn = new JButton("Schließen");
        closeBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeBtn);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(owner);

        // Load image in background
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                InputStream in = openImageStream(img.src());
                try {
                    BufferedImage bi = ImageIO.read(in);
                    if (bi == null) return null;

                    // Scale down if too large for the dialog
                    int maxW = 560;
                    int maxH = 420;
                    if (bi.getWidth() > maxW || bi.getHeight() > maxH) {
                        double scale = Math.min((double) maxW / bi.getWidth(),
                                (double) maxH / bi.getHeight());
                        int w = (int) (bi.getWidth() * scale);
                        int h = (int) (bi.getHeight() * scale);
                        Image scaled = bi.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                        return new ImageIcon(scaled);
                    }
                    return new ImageIcon(bi);
                } finally {
                    in.close();
                }
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        imageLabel.setIcon(icon);
                        imageLabel.setText(null);
                        downloadBtn.setEnabled(true);
                        dialog.pack();
                        dialog.setSize(
                                Math.min(icon.getIconWidth() + 60, 900),
                                Math.min(icon.getIconHeight() + 120, 700));
                        dialog.setLocationRelativeTo(owner);
                    } else {
                        imageLabel.setText("❌ Bild konnte nicht geladen werden");
                    }
                } catch (Exception ex) {
                    LOG.log(Level.FINE, "[ImageStrip] Failed to load image: " + img.src(), ex);
                    imageLabel.setText("❌ Fehler: " + ex.getMessage());
                }
            }
        }.execute();

        dialog.setVisible(true);
    }

    /**
     * Download the image to a user-chosen location.
     */
    private void downloadImage(ImageRef img) {
        JFileChooser chooser = new JFileChooser();
        String fileName = guessFileName(img.src());
        chooser.setSelectedFile(new File(fileName));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    InputStream in = openImageStream(img.src());
                    try {
                        java.io.FileOutputStream out = new java.io.FileOutputStream(target);
                        try {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) != -1) {
                                out.write(buf, 0, n);
                            }
                        } finally {
                            out.close();
                        }
                    } finally {
                        in.close();
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(ImageStripPanel.this,
                                "Bild gespeichert: " + target.getName(),
                                "Download", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(ImageStripPanel.this,
                                "Fehler beim Speichern: " + ex.getMessage(),
                                "Download-Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HTTP helper
    // ═══════════════════════════════════════════════════════════

    /**
     * Open an InputStream to the given image URL with a proper User-Agent header.
     * Wikimedia (and many other image hosts) reject requests without a descriptive
     * User-Agent with HTTP 403.
     */
    private static InputStream openImageStream(String imageUrl) throws java.io.IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(true);
        return conn.getInputStream();
    }

    private static String guessFileName(String src) {
        if (src == null || src.isEmpty()) return "image.png";
        String path = src;
        int q = path.indexOf('?');
        if (q > 0) path = path.substring(0, q);
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            return path.substring(slash + 1);
        }
        return "image.png";
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
