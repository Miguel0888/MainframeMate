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
     * and translucent ◀/▶ arrows on the left/right edges of the image
     * that appear on hover — click to navigate between images.
     */
    private void showImageOverlay(int startIndex) {
        final int[] currentIndex = { Math.max(0, Math.min(startIndex, allImages.size() - 1)) };

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame ? (Frame) owner : null,
                allImages.get(currentIndex[0]).description(), true);
        dialog.setLayout(new BorderLayout(0, 0));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Determine usable screen area
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration());
        final int screenMaxW = screen.width - screenInsets.left - screenInsets.right - 80;
        final int screenMaxH = screen.height - screenInsets.top - screenInsets.bottom - 120;

        // Image label inside a scroll pane
        final JLabel imageLabel = new JLabel("⏳ Lade Bild…", SwingConstants.CENTER);
        final JScrollPane scrollPane = new JScrollPane(imageLabel);
        scrollPane.setBorder(null);

        // ── Overlay navigation arrows ──
        final int ARROW_STRIP_W = 48;

        final JPanel leftArrow = createArrowPanel("◀", ARROW_STRIP_W);
        final JPanel rightArrow = createArrowPanel("▶", ARROW_STRIP_W);

        // Download overlay button (top-right corner, same translucent style)
        final int DL_BTN_W = 44;
        final int DL_BTN_H = 44;
        final JPanel downloadOverlay = createArrowPanel("💾", DL_BTN_W);
        downloadOverlay.setToolTipText("Herunterladen");
        downloadOverlay.setVisible(false);

        // Use a layered pane to overlay arrows + download on top of the scroll pane
        final JLayeredPane layered = new JLayeredPane();
        layered.setLayout(null);

        layered.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
        layered.add(leftArrow, JLayeredPane.PALETTE_LAYER);
        layered.add(rightArrow, JLayeredPane.PALETTE_LAYER);
        layered.add(downloadOverlay, JLayeredPane.PALETTE_LAYER);

        // Keep children sized to the layered pane
        layered.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layered.getWidth();
                int h = layered.getHeight();
                scrollPane.setBounds(0, 0, w, h);
                leftArrow.setBounds(0, 0, ARROW_STRIP_W, h);
                rightArrow.setBounds(w - ARROW_STRIP_W, 0, ARROW_STRIP_W, h);
                downloadOverlay.setBounds(w - DL_BTN_W - 8, 8, DL_BTN_W, DL_BTN_H);
            }
        });

        // Arrows hidden by default
        leftArrow.setVisible(false);
        rightArrow.setVisible(false);

        dialog.add(layered, BorderLayout.CENTER);

        // ── Slim info bar at the bottom ──
        final JLabel infoLabel = new JLabel(" ", SwingConstants.CENTER);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC, 11f));
        infoLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        dialog.add(infoLabel, BorderLayout.SOUTH);

        // ── Load & display logic (declared early so glass pane can reference it) ──
        final Runnable[] loadCurrent = new Runnable[1];

        // ── Transparent glass pane over the entire dialog for flicker-free hover tracking ──
        JPanel glass = new JPanel(null);
        glass.setOpaque(false);
        dialog.setGlassPane(glass);
        glass.setVisible(true);

        glass.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(glass, e.getPoint(), layered);
                int w = layered.getWidth();
                int h = layered.getHeight();

                boolean inImage = p.x >= 0 && p.x < w && p.y >= 0 && p.y < h;
                boolean showLeft = inImage && currentIndex[0] > 0 && p.x < ARROW_STRIP_W;
                boolean showRight = inImage && currentIndex[0] < allImages.size() - 1 && p.x >= w - ARROW_STRIP_W;
                boolean showDl = inImage && p.x >= w - DL_BTN_W - 16 && p.y <= DL_BTN_H + 16;

                leftArrow.setVisible(showLeft);
                rightArrow.setVisible(showRight);
                downloadOverlay.setVisible(showDl);
            }
        });
        glass.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                leftArrow.setVisible(false);
                rightArrow.setVisible(false);
                downloadOverlay.setVisible(false);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(glass, e.getPoint(), layered);
                int w = layered.getWidth();
                int h = layered.getHeight();

                boolean inImage = p.x >= 0 && p.x < w && p.y >= 0 && p.y < h;
                if (!inImage) return;

                if (currentIndex[0] > 0 && p.x < ARROW_STRIP_W) {
                    currentIndex[0]--;
                    loadCurrent[0].run();
                } else if (currentIndex[0] < allImages.size() - 1 && p.x >= w - ARROW_STRIP_W) {
                    currentIndex[0]++;
                    loadCurrent[0].run();
                } else if (p.x >= w - DL_BTN_W - 16 && p.y <= DL_BTN_H + 16) {
                    downloadImage(allImages.get(currentIndex[0]));
                }
            }
        });

        // Initial size
        dialog.setSize(Math.min(700, screenMaxW), Math.min(500, screenMaxH));
        dialog.setLocationRelativeTo(owner);

        // ── Assign load logic ──
        loadCurrent[0] = () -> {
            ImageRef img = allImages.get(currentIndex[0]);
            dialog.setTitle(img.description());
            imageLabel.setIcon(null);
            imageLabel.setText("⏳ Lade Bild…");
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
                            icon = new ImageIcon(bi.getScaledInstance(
                                    (int) (imgW * scale), (int) (imgH * scale), Image.SCALE_SMOOTH));
                        }

                        imageLabel.setIcon(icon);
                        imageLabel.setText(null);

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


        // Keyboard navigation
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("LEFT"), "prev");
        dialog.getRootPane().getActionMap().put("prev", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (currentIndex[0] > 0) { currentIndex[0]--; loadCurrent[0].run(); }
            }
        });
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("RIGHT"), "next");
        dialog.getRootPane().getActionMap().put("next", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (currentIndex[0] < allImages.size() - 1) { currentIndex[0]++; loadCurrent[0].run(); }
            }
        });

        loadCurrent[0].run();
        dialog.setVisible(true);
    }

    /**
     * Create a translucent arrow panel used as an overlay on the left or right edge
     * of the image area. The arrow symbol is large and centered; the background is
     * semi-transparent dark so it is visible on any image.
     */
    private static JPanel createArrowPanel(String arrow, int stripWidth) {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                g2.setColor(Color.BLACK);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel(arrow, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 28f));
        panel.add(label, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(stripWidth, 0));

        // Hover brightens the strip
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                label.setForeground(new Color(255, 255, 255, 255));
                panel.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                label.setForeground(new Color(255, 255, 255, 200));
                panel.repaint();
            }
        });

        return panel;
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
