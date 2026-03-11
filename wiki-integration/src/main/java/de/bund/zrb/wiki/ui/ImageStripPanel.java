package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.ImageRef;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
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
 *   <li>Click → overlay dialog with full image, zoom, pan, download, navigation</li>
 * </ul>
 */
public class ImageStripPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(ImageStripPanel.class.getName());
    private static final int ICON_SIZE = 28;
    private static final int GAP = 4;

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
            JLabel iconLabel = new JLabel("\uD83D\uDDBC");
            iconLabel.setFont(iconLabel.getFont().deriveFont(16f));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            iconLabel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
            iconLabel.setMaximumSize(new Dimension(ICON_SIZE, ICON_SIZE));
            iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            String tooltip = "<html><b>" + escHtml(img.description()) + "</b>";
            if (img.width() > 0 && img.height() > 0) {
                tooltip += "<br>" + img.width() + " \u00d7 " + img.height();
            }
            tooltip += "</html>";
            iconLabel.setToolTipText(tooltip);

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

    // ═══════════════════════════════════════════════════════════
    //  Zoomable image panel (draws the BufferedImage with scale + translate)
    // ═══════════════════════════════════════════════════════════

    private static class ZoomableImagePanel extends JPanel {
        private BufferedImage image;
        private double zoom = 1.0;
        private double offsetX = 0, offsetY = 0;
        private Point dragStart;

        ZoomableImagePanel() {
            setBackground(new Color(30, 30, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            // Drag to pan
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        offsetX += e.getX() - dragStart.x;
                        offsetY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        void setImage(BufferedImage img) {
            this.image = img;
            resetView();
        }

        /** Zoom to fit the image into the viewport. */
        void resetView() {
            zoom = 1.0;
            offsetX = 0;
            offsetY = 0;
            if (image != null && getWidth() > 0 && getHeight() > 0) {
                double scaleX = (double) getWidth() / image.getWidth();
                double scaleY = (double) getHeight() / image.getHeight();
                zoom = Math.min(scaleX, scaleY);
                if (zoom > 1.0) zoom = 1.0; // don't upscale beyond 100%
                centerImage();
            }
            repaint();
        }

        private void centerImage() {
            if (image == null) return;
            double drawW = image.getWidth() * zoom;
            double drawH = image.getHeight() * zoom;
            offsetX = (getWidth() - drawW) / 2.0;
            offsetY = (getHeight() - drawH) / 2.0;
        }

        void zoomAt(double factor, Point pivot) {
            if (image == null) return;
            double oldZoom = zoom;
            zoom *= factor;
            zoom = Math.max(0.05, Math.min(zoom, 20.0));

            // Adjust offset so the pixel under the cursor stays put
            double px = pivot != null ? pivot.x : getWidth() / 2.0;
            double py = pivot != null ? pivot.y : getHeight() / 2.0;
            offsetX = px - (px - offsetX) * (zoom / oldZoom);
            offsetY = py - (py - offsetY) * (zoom / oldZoom);
            repaint();
        }

        int getZoomPercent() {
            return (int) Math.round(zoom * 100);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    zoom < 1.0 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            int drawW = (int) Math.round(image.getWidth() * zoom);
            int drawH = (int) Math.round(image.getHeight() * zoom);
            g2.drawImage(image, (int) Math.round(offsetX), (int) Math.round(offsetY),
                    drawW, drawH, null);
            g2.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Overlay dialog
    // ═══════════════════════════════════════════════════════════

    private void showImageOverlay(int startIndex) {
        final int[] currentIndex = { Math.max(0, Math.min(startIndex, allImages.size() - 1)) };

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame ? (Frame) owner : null,
                allImages.get(currentIndex[0]).description(), true);
        dialog.setLayout(new BorderLayout(0, 0));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration());
        final int screenMaxW = screen.width - screenInsets.left - screenInsets.right - 80;
        final int screenMaxH = screen.height - screenInsets.top - screenInsets.bottom - 120;

        // Zoomable image panel replaces the old JLabel + JScrollPane
        final ZoomableImagePanel imagePanel = new ZoomableImagePanel();
        imagePanel.setPreferredSize(new Dimension(600, 400));

        // Loading overlay label (shown while image loads)
        final JLabel loadingLabel = new JLabel("\u23f3 Lade Bild\u2026", SwingConstants.CENTER);
        loadingLabel.setForeground(Color.WHITE);
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(16f));

        // Stack the loading label on top of the image panel
        final JLayeredPane layered = new JLayeredPane();
        layered.setLayout(null);

        // ── Overlay arrows ──
        final int ARROW_STRIP_W = 48;
        final JPanel leftArrow = createArrowPanel("\u25c0", ARROW_STRIP_W);
        final JPanel rightArrow = createArrowPanel("\u25b6", ARROW_STRIP_W);

        final int DL_BTN_W = 44;
        final int DL_BTN_H = 44;
        final JPanel downloadOverlay = createArrowPanel("\uD83D\uDCBE", DL_BTN_W);
        downloadOverlay.setToolTipText("Herunterladen");
        downloadOverlay.setVisible(false);

        layered.add(imagePanel, JLayeredPane.DEFAULT_LAYER);
        layered.add(loadingLabel, JLayeredPane.MODAL_LAYER);
        layered.add(leftArrow, JLayeredPane.PALETTE_LAYER);
        layered.add(rightArrow, JLayeredPane.PALETTE_LAYER);
        layered.add(downloadOverlay, JLayeredPane.PALETTE_LAYER);

        layered.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = layered.getWidth();
                int h = layered.getHeight();
                imagePanel.setBounds(0, 0, w, h);
                loadingLabel.setBounds(0, 0, w, h);
                int arrowTop = h / 3;
                int arrowH = h / 3;
                leftArrow.setBounds(0, arrowTop, ARROW_STRIP_W, arrowH);
                rightArrow.setBounds(w - ARROW_STRIP_W, arrowTop, ARROW_STRIP_W, arrowH);
                downloadOverlay.setBounds(w - DL_BTN_W - 8, 8, DL_BTN_W, DL_BTN_H);
            }
        });

        leftArrow.setVisible(false);
        rightArrow.setVisible(false);

        dialog.add(layered, BorderLayout.CENTER);

        // ── Info bar at the bottom ──
        final JLabel infoLabel = new JLabel(" ", SwingConstants.CENTER);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC, 11f));
        infoLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        dialog.add(infoLabel, BorderLayout.SOUTH);

        // ── State for current image dimensions (for info updates) ──
        final int[] currentImgDims = new int[2]; // [width, height]

        // Helper to update info text
        final Runnable updateInfo = () -> {
            ImageRef ci = allImages.get(currentIndex[0]);
            String dims = currentImgDims[0] > 0
                    ? "  (" + currentImgDims[0] + " \u00d7 " + currentImgDims[1] + ")" : "";
            infoLabel.setText((currentIndex[0] + 1) + " / " + allImages.size()
                    + "  \u2014  " + ci.description() + dims
                    + "  |  Zoom: " + imagePanel.getZoomPercent() + "%");
        };

        // ── Mouse wheel zoom on image panel ──
        imagePanel.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 1.15 : 1.0 / 1.15;
                imagePanel.zoomAt(factor, e.getPoint());
                updateInfo.run();
            }
        });

        // ── Load & display logic ──
        final Runnable[] loadCurrent = new Runnable[1];

        // ── Glass pane for flicker-free overlay hover + click ──
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
                int arrowTop = h / 3;
                int arrowBottom = arrowTop + h / 3;

                boolean inImage = p.x >= 0 && p.x < w && p.y >= 0 && p.y < h;
                boolean showDl = inImage && p.x >= w - DL_BTN_W - 20 && p.y <= DL_BTN_H + 20;
                boolean inArrowBand = p.y >= arrowTop - 20 && p.y <= arrowBottom + 20;
                boolean showLeft = inImage && !showDl && currentIndex[0] > 0
                        && p.x < ARROW_STRIP_W && inArrowBand;
                boolean showRight = inImage && !showDl && currentIndex[0] < allImages.size() - 1
                        && p.x >= w - ARROW_STRIP_W && inArrowBand;

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
                int arrowTop = h / 3;
                int arrowBottom = arrowTop + h / 3;

                boolean inImage = p.x >= 0 && p.x < w && p.y >= 0 && p.y < h;
                if (!inImage) return;

                if (p.x >= w - DL_BTN_W - 20 && p.y <= DL_BTN_H + 20) {
                    downloadImage(allImages.get(currentIndex[0]));
                } else if (currentIndex[0] > 0 && p.x < ARROW_STRIP_W
                        && p.y >= arrowTop - 20 && p.y <= arrowBottom + 20) {
                    currentIndex[0]--;
                    loadCurrent[0].run();
                } else if (currentIndex[0] < allImages.size() - 1 && p.x >= w - ARROW_STRIP_W
                        && p.y >= arrowTop - 20 && p.y <= arrowBottom + 20) {
                    currentIndex[0]++;
                    loadCurrent[0].run();
                }
            }
        });

        // Forward mouse wheel from glass to image panel for zooming
        glass.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                Point p = SwingUtilities.convertPoint(glass, e.getPoint(), imagePanel);
                double factor = e.getWheelRotation() < 0 ? 1.15 : 1.0 / 1.15;
                imagePanel.zoomAt(factor, p);
                updateInfo.run();
            }
        });

        // Forward mouse drag from glass to image panel for panning
        glass.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(glass, e.getPoint(), imagePanel);
                imagePanel.dragStart = p;
                imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                imagePanel.dragStart = null;
                imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        });
        glass.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (imagePanel.dragStart != null) {
                    Point p = SwingUtilities.convertPoint(glass, e.getPoint(), imagePanel);
                    imagePanel.offsetX += p.x - imagePanel.dragStart.x;
                    imagePanel.offsetY += p.y - imagePanel.dragStart.y;
                    imagePanel.dragStart = p;
                    imagePanel.repaint();
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
            imagePanel.image = null;
            imagePanel.repaint();
            loadingLabel.setVisible(true);
            currentImgDims[0] = 0;
            currentImgDims[1] = 0;
            infoLabel.setText((currentIndex[0] + 1) + " / " + allImages.size()
                    + "  \u2014  " + img.description());

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
                        loadingLabel.setVisible(false);
                        if (bi == null) {
                            loadingLabel.setText("\u274c Bild konnte nicht geladen werden");
                            loadingLabel.setVisible(true);
                            return;
                        }

                        currentImgDims[0] = bi.getWidth();
                        currentImgDims[1] = bi.getHeight();

                        // Size dialog to fit image (up to screen bounds)
                        int chromeW = 40, chromeH = 60;
                        int dialogW = Math.max(Math.min(bi.getWidth() + chromeW, screenMaxW), 400);
                        int dialogH = Math.max(Math.min(bi.getHeight() + chromeH, screenMaxH), 300);
                        dialog.setSize(dialogW, dialogH);
                        dialog.setLocationRelativeTo(owner);

                        // Set image and fit to view (must happen after dialog is sized)
                        SwingUtilities.invokeLater(() -> {
                            imagePanel.setImage(bi);
                            updateInfo.run();
                        });
                    } catch (Exception ex) {
                        LOG.log(Level.FINE, "[ImageStrip] Failed to load: " + img.src(), ex);
                        loadingLabel.setText("\u274c Fehler: " + ex.getMessage());
                        loadingLabel.setVisible(true);
                    }
                }
            }.execute();
        };

        // ── Keyboard: arrows to navigate, +/-/0 to zoom ──
        InputMap im = dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = dialog.getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke("LEFT"), "prev");
        am.put("prev", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (currentIndex[0] > 0) { currentIndex[0]--; loadCurrent[0].run(); }
            }
        });
        im.put(KeyStroke.getKeyStroke("RIGHT"), "next");
        am.put("next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (currentIndex[0] < allImages.size() - 1) { currentIndex[0]++; loadCurrent[0].run(); }
            }
        });
        im.put(KeyStroke.getKeyStroke("PLUS"), "zoomIn");
        im.put(KeyStroke.getKeyStroke("EQUALS"), "zoomIn");
        im.put(KeyStroke.getKeyStroke("ADD"), "zoomIn");
        am.put("zoomIn", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                imagePanel.zoomAt(1.25, null);
                updateInfo.run();
            }
        });
        im.put(KeyStroke.getKeyStroke("MINUS"), "zoomOut");
        im.put(KeyStroke.getKeyStroke("SUBTRACT"), "zoomOut");
        am.put("zoomOut", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                imagePanel.zoomAt(1.0 / 1.25, null);
                updateInfo.run();
            }
        });
        im.put(KeyStroke.getKeyStroke("0"), "zoomReset");
        im.put(KeyStroke.getKeyStroke("NUMPAD0"), "zoomReset");
        am.put("zoomReset", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                imagePanel.resetView();
                updateInfo.run();
            }
        });

        loadCurrent[0].run();
        dialog.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════
    //  Arrow/overlay panel factory
    // ═══════════════════════════════════════════════════════════

    private static JPanel createArrowPanel(String arrow, int stripWidth) {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                g2.setColor(Color.BLACK);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel(arrow, SwingConstants.CENTER);
        label.setForeground(new Color(255, 255, 255, 200));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 26f));
        panel.add(label, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(stripWidth, 0));

        return panel;
    }

    // ═══════════════════════════════════════════════════════════
    //  Download
    // ═══════════════════════════════════════════════════════════

    private void downloadImage(ImageRef img) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(guessFileName(img.src())));

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
        if (slash >= 0 && slash < path.length() - 1) return path.substring(slash + 1);
        return "image.png";
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
