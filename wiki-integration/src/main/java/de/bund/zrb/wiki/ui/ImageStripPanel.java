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

    /** All images on this page — kept for navigation inside the overlay. */
    private List<ImageRef> allImages;

    public ImageStripPanel(List<ImageRef> images) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        setPreferredSize(new Dimension(ICON_SIZE + 8, 0));
        this.allImages = images;

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
            final int imgIndex = allImages.indexOf(img);
            iconLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showImageOverlay(imgIndex);
                }
            });

            add(iconLabel);
            add(Box.createVerticalStrut(GAP));
        }

        add(Box.createVerticalGlue());
    }

    /**
     * Show an overlay dialog with the full image, download button,
     * and ◀/▶ navigation to flip through all images on the page.
     */
    private void showImageOverlay(int startIndex) {
        final int[] currentIndex = { Math.max(0, Math.min(startIndex, allImages.size() - 1)) };

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame ? (Frame) owner : null,
                allImages.get(currentIndex[0]).description(), true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Determine usable screen area (leave some margin)
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration());
        final int screenMaxW = screen.width - screenInsets.left - screenInsets.right - 80;
        final int screenMaxH = screen.height - screenInsets.top - screenInsets.bottom - 120;

        JLabel imageLabel = new JLabel("⏳ Lade Bild…", SwingConstants.CENTER);
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // Bottom bar: [◀] [▶]  info  [💾 Herunterladen] [Schließen]
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        JLabel infoLabel = new JLabel(" ");
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC));

        // Navigation buttons
        JButton prevBtn = new JButton("◀");
        prevBtn.setToolTipText("Vorheriges Bild");
        prevBtn.setMargin(new Insets(2, 6, 2, 6));

        JButton nextBtn = new JButton("▶");
        nextBtn.setToolTipText("Nächstes Bild");
        nextBtn.setMargin(new Insets(2, 6, 2, 6));

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navPanel.add(prevBtn);
        navPanel.add(nextBtn);

        JButton downloadBtn = new JButton("💾 Herunterladen");
        downloadBtn.setEnabled(false);

        JButton closeBtn = new JButton("Schließen");
        closeBtn.addActionListener(e -> dialog.dispose());

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtons.add(downloadBtn);
        rightButtons.add(closeBtn);

        bottomPanel.add(navPanel, BorderLayout.WEST);
        bottomPanel.add(infoLabel, BorderLayout.CENTER);
        bottomPanel.add(rightButtons, BorderLayout.EAST);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        // Start with a reasonable loading size
        dialog.setSize(Math.min(700, screenMaxW), Math.min(500, screenMaxH));
        dialog.setLocationRelativeTo(owner);

        // --- Runnable to load & display image at currentIndex ---
        final Runnable[] loadCurrent = new Runnable[1];
        loadCurrent[0] = () -> {
            ImageRef img = allImages.get(currentIndex[0]);
            dialog.setTitle(img.description());
            imageLabel.setIcon(null);
            imageLabel.setText("⏳ Lade Bild…");
            downloadBtn.setEnabled(false);
            prevBtn.setEnabled(currentIndex[0] > 0);
            nextBtn.setEnabled(currentIndex[0] < allImages.size() - 1);
            infoLabel.setText((currentIndex[0] + 1) + " / " + allImages.size()
                    + "  —  " + img.description());

            new SwingWorker<BufferedImage, Void>() {
                @Override
                protected BufferedImage doInBackground() throws Exception {
                    InputStream in = openImageStream(img.src());
                    try {
                        return ImageIO.read(in);
                    } finally {
                        in.close();
                    }
                }

                @Override
                protected void done() {
                    try {
                        BufferedImage bi = get();
                        if (bi == null) {
                            imageLabel.setText("❌ Bild konnte nicht geladen werden");
                            return;
                        }

                        int imgW = bi.getWidth();
                        int imgH = bi.getHeight();

                        int chromeW = 40;
                        int chromeH = 90;
                        int availW = screenMaxW - chromeW;
                        int availH = screenMaxH - chromeH;

                        ImageIcon icon;
                        if (imgW <= availW && imgH <= availH) {
                            icon = new ImageIcon(bi);
                        } else {
                            double scale = Math.min((double) availW / imgW, (double) availH / imgH);
                            int scaledW = (int) (imgW * scale);
                            int scaledH = (int) (imgH * scale);
                            icon = new ImageIcon(bi.getScaledInstance(scaledW, scaledH, Image.SCALE_SMOOTH));
                        }

                        imageLabel.setIcon(icon);
                        imageLabel.setText(null);
                        downloadBtn.setEnabled(true);

                        int dialogW = Math.max(Math.min(icon.getIconWidth() + chromeW, screenMaxW), 400);
                        int dialogH = Math.max(Math.min(icon.getIconHeight() + chromeH, screenMaxH), 300);
                        dialog.setSize(dialogW, dialogH);
                        dialog.setLocationRelativeTo(owner);

                        infoLabel.setText((currentIndex[0] + 1) + " / " + allImages.size()
                                + "  —  " + img.description()
                                + "  (" + imgW + " × " + imgH + ")");
                    } catch (Exception ex) {
                        LOG.log(Level.FINE, "[ImageStrip] Failed to load: " + img.src(), ex);
                        imageLabel.setText("❌ Fehler: " + ex.getMessage());
                    }
                }
            }.execute();
        };

        // Wire navigation
        prevBtn.addActionListener(e -> {
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                loadCurrent[0].run();
            }
        });
        nextBtn.addActionListener(e -> {
            if (currentIndex[0] < allImages.size() - 1) {
                currentIndex[0]++;
                loadCurrent[0].run();
            }
        });
        downloadBtn.addActionListener(e -> downloadImage(allImages.get(currentIndex[0])));

        // Load the first image
        loadCurrent[0].run();

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
