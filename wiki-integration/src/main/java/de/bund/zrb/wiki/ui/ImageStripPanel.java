package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.ImageRef;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

    /** Callback fired when user clicks the expand button (◀◀). */
    private Runnable expandCallback;

    /** Optional authenticated downloader (e.g. for Confluence mTLS). */
    private static ByteDownloader activeDownloader;

    public ImageStripPanel(List<ImageRef> images) {
        this(images, null);
    }

    public ImageStripPanel(List<ImageRef> images, ByteDownloader downloader) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        setPreferredSize(new Dimension(ICON_SIZE + 8, 0));
        this.allImages = images;
        activeDownloader = downloader;

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

        // ── Expand button at the very bottom ──
        JLabel expandBtn = new JLabel("\u25c0\u25c0");
        expandBtn.setFont(expandBtn.getFont().deriveFont(Font.BOLD, 11f));
        expandBtn.setHorizontalAlignment(SwingConstants.CENTER);
        expandBtn.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
        expandBtn.setMaximumSize(new Dimension(ICON_SIZE, ICON_SIZE));
        expandBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        expandBtn.setToolTipText("Miniaturansicht aufklappen");
        expandBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (expandCallback != null) expandCallback.run();
            }
        });
        add(expandBtn);
    }

    /**
     * Set the callback invoked when the user clicks the expand button (◀◀).
     */
    public void setExpandCallback(Runnable callback) {
        this.expandCallback = callback;
    }

    // ═══════════════════════════════════════════════════════════
    //  Image data holder — supports both static BufferedImage and animated GIF
    // ═══════════════════════════════════════════════════════════

    private static class LoadedImage {
        final BufferedImage staticImage;   // non-null for normal images
        final byte[] rawBytes;             // original bytes (for GIF detection + download)
        final boolean animated;            // true if animated GIF
        final int width, height;

        LoadedImage(BufferedImage img, byte[] raw) {
            this.staticImage = img;
            this.rawBytes = raw;
            this.animated = false;
            this.width = img != null ? img.getWidth() : 0;
            this.height = img != null ? img.getHeight() : 0;
        }

        LoadedImage(byte[] raw, int w, int h) {
            this.staticImage = null;
            this.rawBytes = raw;
            this.animated = true;
            this.width = w;
            this.height = h;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Zoomable image panel — supports both static and animated images
    // ═══════════════════════════════════════════════════════════

    private static class ZoomableImagePanel extends JPanel {
        private BufferedImage staticImage;
        private ImageIcon animatedIcon;  // for animated GIFs
        private boolean isAnimated;
        private int imgW, imgH;
        double zoom = 1.0;
        double offsetX = 0, offsetY = 0;
        Point dragStart;

        ZoomableImagePanel() {
            setBackground(new Color(30, 30, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                @Override public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        offsetX += e.getX() - dragStart.x;
                        offsetY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        void setStaticImage(BufferedImage img) {
            this.staticImage = img;
            this.animatedIcon = null;
            this.isAnimated = false;
            this.imgW = img != null ? img.getWidth() : 0;
            this.imgH = img != null ? img.getHeight() : 0;
            resetView();
        }

        void setAnimatedImage(byte[] gifBytes, int w, int h) {
            this.staticImage = null;
            this.isAnimated = true;
            this.imgW = w;
            this.imgH = h;
            // ImageIcon handles GIF animation natively via its internal GIF decoder
            this.animatedIcon = new ImageIcon(gifBytes);
            // Set the image observer to this panel so animation frames trigger repaints
            this.animatedIcon.setImageObserver(this);
            resetView();
        }

        void clearImage() {
            this.staticImage = null;
            this.animatedIcon = null;
            this.isAnimated = false;
            this.imgW = 0;
            this.imgH = 0;
            repaint();
        }

        boolean hasImage() {
            return staticImage != null || animatedIcon != null;
        }

        void resetView() {
            zoom = 1.0;
            offsetX = 0;
            offsetY = 0;
            if (imgW > 0 && imgH > 0 && getWidth() > 0 && getHeight() > 0) {
                double scaleX = (double) getWidth() / imgW;
                double scaleY = (double) getHeight() / imgH;
                zoom = Math.min(scaleX, scaleY);
                // No cap — always fit to viewport (upscale small images too)
                centerImage();
            }
            repaint();
        }

        private void centerImage() {
            if (imgW <= 0 || imgH <= 0) return;
            double drawW = imgW * zoom;
            double drawH = imgH * zoom;
            offsetX = (getWidth() - drawW) / 2.0;
            offsetY = (getHeight() - drawH) / 2.0;
        }

        void zoomAt(double factor, Point pivot) {
            if (imgW <= 0) return;
            double oldZoom = zoom;
            zoom *= factor;
            zoom = Math.max(0.05, Math.min(zoom, 20.0));
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
            int drawW = (int) Math.round(imgW * zoom);
            int drawH = (int) Math.round(imgH * zoom);
            int dx = (int) Math.round(offsetX);
            int dy = (int) Math.round(offsetY);

            if (isAnimated && animatedIcon != null) {
                // Animated GIF — draw via ImageIcon which handles frame animation
                Graphics2D g2 = (Graphics2D) g.create();
                g2.drawImage(animatedIcon.getImage(), dx, dy, drawW, drawH, this);
                g2.dispose();
            } else if (staticImage != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        zoom < 1.0 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                                   : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(staticImage, dx, dy, drawW, drawH, null);
                g2.dispose();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Overlay dialog
    // ═══════════════════════════════════════════════════════════

    /**
     * Public entry point for opening the image overlay dialog from any context.
     * Used by the inline rendered view to allow image zoom/download on click.
     *
     * @param parent   parent component for dialog positioning
     * @param allImages list of all images (for navigation)
     * @param startIndex index of the initially displayed image
     */
    public static void openOverlay(Component parent, List<ImageRef> allImages, int startIndex) {
        if (allImages == null || allImages.isEmpty()) return;
        openOverlayImpl(parent, allImages, startIndex);
    }

    /**
     * Overload accepting a custom {@link ByteDownloader} for authenticated image access.
     */
    public static void openOverlay(Component parent, List<ImageRef> allImages, int startIndex,
                                   ByteDownloader downloader) {
        if (allImages == null || allImages.isEmpty()) return;
        activeDownloader = downloader;
        openOverlayImpl(parent, allImages, startIndex);
    }

    private void showImageOverlay(int startIndex) {
        openOverlayImpl(this, allImages, startIndex);
    }

    private static void openOverlayImpl(Component parentComp, final List<ImageRef> allImages, int startIndex) {
        final int[] currentIndex = { Math.max(0, Math.min(startIndex, allImages.size() - 1)) };

        Window owner = SwingUtilities.getWindowAncestor(parentComp);
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

        final ZoomableImagePanel imagePanel = new ZoomableImagePanel();
        imagePanel.setPreferredSize(new Dimension(600, 400));

        final JLabel loadingLabel = new JLabel("\u23f3 Lade Bild\u2026", SwingConstants.CENTER);
        loadingLabel.setForeground(Color.WHITE);
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(16f));

        final JLayeredPane layered = new JLayeredPane();
        layered.setLayout(null);

        // ── Overlay arrows (left/right, middle third) ──
        final int ARROW_STRIP_W = 48;
        final JPanel leftArrow = createOverlayButton("\u25c0", ARROW_STRIP_W, 0);
        final JPanel rightArrow = createOverlayButton("\u25b6", ARROW_STRIP_W, 0);

        // ── Download overlay (top-right) ──
        final int OVL_BTN = 40;
        final JPanel downloadOverlay = createOverlayButton("\uD83D\uDCBE", OVL_BTN, OVL_BTN);
        downloadOverlay.setToolTipText("Herunterladen");

        // ── Zoom overlays (bottom-right: ➖ ➕ 🔄) ──
        final int ZOOM_BTN = 36;
        final int ZOOM_GAP = 4;
        final JPanel zoomOutBtn = createOverlayButton("\u2796", ZOOM_BTN, ZOOM_BTN);
        zoomOutBtn.setToolTipText("Verkleinern (-)");
        final JPanel zoomInBtn = createOverlayButton("\u2795", ZOOM_BTN, ZOOM_BTN);
        zoomInBtn.setToolTipText("Vergrößern (+)");
        final JPanel zoomResetBtn = createOverlayButton("\uD83D\uDD04", ZOOM_BTN, ZOOM_BTN);
        zoomResetBtn.setToolTipText("Einpassen (0)");

        layered.add(imagePanel, JLayeredPane.DEFAULT_LAYER);
        layered.add(loadingLabel, JLayeredPane.MODAL_LAYER);
        layered.add(leftArrow, JLayeredPane.PALETTE_LAYER);
        layered.add(rightArrow, JLayeredPane.PALETTE_LAYER);
        layered.add(downloadOverlay, JLayeredPane.PALETTE_LAYER);
        layered.add(zoomOutBtn, JLayeredPane.PALETTE_LAYER);
        layered.add(zoomInBtn, JLayeredPane.PALETTE_LAYER);
        layered.add(zoomResetBtn, JLayeredPane.PALETTE_LAYER);

        layered.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = layered.getWidth();
                int h = layered.getHeight();
                imagePanel.setBounds(0, 0, w, h);
                loadingLabel.setBounds(0, 0, w, h);
                // Arrows in middle third
                int arrowTop = h / 3;
                int arrowH = h / 3;
                leftArrow.setBounds(0, arrowTop, ARROW_STRIP_W, arrowH);
                rightArrow.setBounds(w - ARROW_STRIP_W, arrowTop, ARROW_STRIP_W, arrowH);
                // Download: top-right
                downloadOverlay.setBounds(w - OVL_BTN - 8, 8, OVL_BTN, OVL_BTN);
                // Zoom: bottom-right, horizontal row
                int zoomY = h - ZOOM_BTN - 10;
                int zoomX = w - (3 * ZOOM_BTN + 2 * ZOOM_GAP) - 10;
                zoomOutBtn.setBounds(zoomX, zoomY, ZOOM_BTN, ZOOM_BTN);
                zoomInBtn.setBounds(zoomX + ZOOM_BTN + ZOOM_GAP, zoomY, ZOOM_BTN, ZOOM_BTN);
                zoomResetBtn.setBounds(zoomX + 2 * (ZOOM_BTN + ZOOM_GAP), zoomY, ZOOM_BTN, ZOOM_BTN);
            }
        });

        leftArrow.setVisible(false);
        rightArrow.setVisible(false);
        downloadOverlay.setVisible(false);
        zoomOutBtn.setVisible(false);
        zoomInBtn.setVisible(false);
        zoomResetBtn.setVisible(false);

        dialog.add(layered, BorderLayout.CENTER);

        // ── Info bar at the bottom ──
        final JLabel infoLabel = new JLabel(" ", SwingConstants.CENTER);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC, 11f));
        infoLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        dialog.add(infoLabel, BorderLayout.SOUTH);

        // ── State ──
        final int[] currentImgDims = new int[2];

        final Runnable updateInfo = () -> {
            ImageRef ci = allImages.get(currentIndex[0]);
            String dims = currentImgDims[0] > 0
                    ? "  (" + currentImgDims[0] + " \u00d7 " + currentImgDims[1] + ")" : "";
            infoLabel.setText((currentIndex[0] + 1) + " / " + allImages.size()
                    + "  \u2014  " + ci.description() + dims
                    + "  |  Zoom: " + imagePanel.getZoomPercent() + "%");
        };

        // ── Load & display logic ──
        final Runnable[] loadCurrent = new Runnable[1];

        // ── Glass pane for flicker-free overlay hover + click ──
        JPanel glass = new JPanel(null);
        glass.setOpaque(false);
        dialog.setGlassPane(glass);
        glass.setVisible(true);

        // Hover zone rectangles (computed relative to layered pane)
        glass.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(glass, e.getPoint(), layered);
                int w = layered.getWidth();
                int h = layered.getHeight();
                int arrowTop = h / 3;
                int arrowBottom = arrowTop + h / 3;

                boolean inImage = p.x >= 0 && p.x < w && p.y >= 0 && p.y < h;

                // Download: top-right
                boolean showDl = inImage && p.x >= w - OVL_BTN - 20 && p.y <= OVL_BTN + 20;

                // Zoom: bottom-right area
                int zoomY = h - ZOOM_BTN - 10;
                int zoomX = w - (3 * ZOOM_BTN + 2 * ZOOM_GAP) - 10;
                boolean showZoom = inImage && p.x >= zoomX - 10 && p.y >= zoomY - 10;

                // Arrows: left/right in middle third, not overlapping download/zoom
                boolean inArrowBand = p.y >= arrowTop - 20 && p.y <= arrowBottom + 20;
                boolean showLeft = inImage && !showDl && !showZoom
                        && currentIndex[0] > 0 && p.x < ARROW_STRIP_W && inArrowBand;
                boolean showRight = inImage && !showDl && !showZoom
                        && currentIndex[0] < allImages.size() - 1
                        && p.x >= w - ARROW_STRIP_W && inArrowBand;

                leftArrow.setVisible(showLeft);
                rightArrow.setVisible(showRight);
                downloadOverlay.setVisible(showDl);
                zoomOutBtn.setVisible(showZoom);
                zoomInBtn.setVisible(showZoom);
                zoomResetBtn.setVisible(showZoom);
            }
        });
        glass.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                leftArrow.setVisible(false);
                rightArrow.setVisible(false);
                downloadOverlay.setVisible(false);
                zoomOutBtn.setVisible(false);
                zoomInBtn.setVisible(false);
                zoomResetBtn.setVisible(false);
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

                // Download
                if (p.x >= w - OVL_BTN - 20 && p.y <= OVL_BTN + 20) {
                    downloadImage(parentComp, allImages.get(currentIndex[0]));
                    return;
                }

                // Zoom buttons
                int zoomY = h - ZOOM_BTN - 10;
                int zoomX = w - (3 * ZOOM_BTN + 2 * ZOOM_GAP) - 10;
                if (p.x >= zoomX - 5 && p.y >= zoomY - 5) {
                    // Which button?
                    int relX = p.x - zoomX;
                    if (relX < ZOOM_BTN) {
                        imagePanel.zoomAt(1.0 / 1.25, null); updateInfo.run(); // zoom out
                    } else if (relX < 2 * ZOOM_BTN + ZOOM_GAP) {
                        imagePanel.zoomAt(1.25, null); updateInfo.run(); // zoom in
                    } else {
                        imagePanel.resetView(); updateInfo.run(); // reset
                    }
                    return;
                }

                // Arrows
                if (currentIndex[0] > 0 && p.x < ARROW_STRIP_W
                        && p.y >= arrowTop - 20 && p.y <= arrowBottom + 20) {
                    currentIndex[0]--; loadCurrent[0].run();
                } else if (currentIndex[0] < allImages.size() - 1 && p.x >= w - ARROW_STRIP_W
                        && p.y >= arrowTop - 20 && p.y <= arrowBottom + 20) {
                    currentIndex[0]++; loadCurrent[0].run();
                }
            }
        });

        // Forward mouse wheel from glass to image panel for zooming
        glass.addMouseWheelListener(e -> {
            Point p = SwingUtilities.convertPoint(glass, e.getPoint(), imagePanel);
            double factor = e.getWheelRotation() < 0 ? 1.15 : 1.0 / 1.15;
            imagePanel.zoomAt(factor, p);
            updateInfo.run();
        });

        // Forward drag from glass to image panel for panning
        glass.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                imagePanel.dragStart = SwingUtilities.convertPoint(glass, e.getPoint(), imagePanel);
                imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override public void mouseReleased(MouseEvent e) {
                imagePanel.dragStart = null;
                imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        });
        glass.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (imagePanel.dragStart != null) {
                    Point p = SwingUtilities.convertPoint(glass, e.getPoint(), imagePanel);
                    imagePanel.offsetX += p.x - imagePanel.dragStart.x;
                    imagePanel.offsetY += p.y - imagePanel.dragStart.y;
                    imagePanel.dragStart = p;
                    imagePanel.repaint();
                }
            }
        });

        // Fixed dialog size: ~60% screen height, 16:10 aspect for wide/portrait images
        final int fixedH = Math.min(screen.height * 6 / 10, screenMaxH);
        final int fixedW = Math.min(fixedH * 16 / 10, screenMaxW);
        dialog.setSize(fixedW, fixedH);
        dialog.setLocationRelativeTo(owner);

        // ── Assign load logic ──
        loadCurrent[0] = () -> {
            ImageRef img = allImages.get(currentIndex[0]);
            dialog.setTitle(img.description());
            imagePanel.clearImage();
            loadingLabel.setText("\u23f3 Lade Bild\u2026");
            loadingLabel.setVisible(true);
            currentImgDims[0] = 0;
            currentImgDims[1] = 0;
            infoLabel.setText((currentIndex[0] + 1) + " / " + allImages.size()
                    + "  \u2014  " + img.description());

            new SwingWorker<LoadedImage, Void>() {
                @Override
                protected LoadedImage doInBackground() throws Exception {
                    byte[] raw = downloadBytes(img.src());
                    boolean isGif = raw.length > 3
                            && raw[0] == 'G' && raw[1] == 'I' && raw[2] == 'F';
                    if (isGif) {
                        boolean animated = isAnimatedGif(raw);
                        if (animated) {
                            ImageIcon probe = new ImageIcon(raw);
                            return new LoadedImage(raw, probe.getIconWidth(), probe.getIconHeight());
                        }
                    }
                    BufferedImage bi = ImageIO.read(new java.io.ByteArrayInputStream(raw));
                    return new LoadedImage(bi, raw);
                }

                @Override
                protected void done() {
                    try {
                        LoadedImage loaded = get();
                        loadingLabel.setVisible(false);
                        if (loaded == null || (loaded.width <= 0)) {
                            loadingLabel.setText("\u274c Bild konnte nicht geladen werden");
                            loadingLabel.setVisible(true);
                            return;
                        }

                        currentImgDims[0] = loaded.width;
                        currentImgDims[1] = loaded.height;

                        // Image is always fitted into the fixed-size viewport
                        if (loaded.animated) {
                            imagePanel.setAnimatedImage(loaded.rawBytes, loaded.width, loaded.height);
                        } else {
                            imagePanel.setStaticImage(loaded.staticImage);
                        }
                        updateInfo.run();
                    } catch (Exception ex) {
                        LOG.log(Level.FINE, "[ImageStrip] Failed to load: " + img.src(), ex);
                        loadingLabel.setText("\u274c Fehler: " + ex.getMessage());
                        loadingLabel.setVisible(true);
                    }
                }
            }.execute();
        };

        // ── Keyboard shortcuts ──
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
                imagePanel.zoomAt(1.25, null); updateInfo.run();
            }
        });
        im.put(KeyStroke.getKeyStroke("MINUS"), "zoomOut");
        im.put(KeyStroke.getKeyStroke("SUBTRACT"), "zoomOut");
        am.put("zoomOut", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                imagePanel.zoomAt(1.0 / 1.25, null); updateInfo.run();
            }
        });
        im.put(KeyStroke.getKeyStroke("0"), "zoomReset");
        im.put(KeyStroke.getKeyStroke("NUMPAD0"), "zoomReset");
        am.put("zoomReset", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                imagePanel.resetView(); updateInfo.run();
            }
        });

        loadCurrent[0].run();
        dialog.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════
    //  Overlay button factory
    // ═══════════════════════════════════════════════════════════

    /**
     * Translucent rounded overlay button. If fixedH == 0, height is dynamic (for arrows).
     */
    private static JPanel createOverlayButton(String symbol, int fixedW, int fixedH) {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g2.setColor(Color.BLACK);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel(symbol, SwingConstants.CENTER);
        label.setForeground(new Color(255, 255, 255, 210));
        label.setFont(label.getFont().deriveFont(Font.BOLD, fixedH > 0 ? 16f : 26f));
        panel.add(label, BorderLayout.CENTER);
        if (fixedH > 0) {
            panel.setPreferredSize(new Dimension(fixedW, fixedH));
        } else {
            panel.setPreferredSize(new Dimension(fixedW, 0));
        }

        return panel;
    }

    // ═══════════════════════════════════════════════════════════
    //  Download
    // ═══════════════════════════════════════════════════════════

    private static void downloadImage(final Component parent, ImageRef img) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(guessFileName(img.src())));

        if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    byte[] data = downloadBytes(img.src());
                    java.io.FileOutputStream out = new java.io.FileOutputStream(target);
                    try {
                        out.write(data);
                    } finally {
                        out.close();
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(parent,
                                "Bild gespeichert: " + target.getName(),
                                "Download", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(parent,
                                "Fehler beim Speichern: " + ex.getMessage(),
                                "Download-Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HTTP / IO helpers
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

    /** Download the full image into a byte array. Package-private for reuse by ImageThumbnailPanel. */
    static byte[] downloadBytes(String imageUrl) throws java.io.IOException {
        // Use custom downloader if available (e.g. Confluence with mTLS)
        if (activeDownloader != null) {
            return activeDownloader.download(imageUrl);
        }
        InputStream in = openImageStream(imageUrl);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } finally {
            in.close();
        }
    }

    /**
     * Check if a GIF byte array contains multiple image frames (animated).
     * Scans for more than one Image Descriptor block (0x2C) after the header.
     */
    private static boolean isAnimatedGif(byte[] data) {
        int imageCount = 0;
        int i = 6; // skip header "GIF89a" / "GIF87a"
        while (i < data.length) {
            int b = data[i] & 0xFF;
            if (b == 0x2C) { // Image Descriptor
                imageCount++;
                if (imageCount > 1) return true;
                // Skip past image descriptor (9 bytes) + optional LCT + image data
                i += 9;
                if (i >= data.length) break;
                int packed = data[i - 1] & 0xFF;
                if ((packed & 0x80) != 0) {
                    int lctSize = 3 * (1 << ((packed & 0x07) + 1));
                    i += lctSize;
                }
                if (i >= data.length) break;
                i++; // LZW minimum code size
                // Skip sub-blocks
                while (i < data.length) {
                    int blockSize = data[i] & 0xFF;
                    i++;
                    if (blockSize == 0) break;
                    i += blockSize;
                }
            } else if (b == 0x21) { // Extension
                i += 2; // skip extension type
                // Skip sub-blocks
                while (i < data.length) {
                    int blockSize = data[i] & 0xFF;
                    i++;
                    if (blockSize == 0) break;
                    i += blockSize;
                }
            } else if (b == 0x3B) { // Trailer
                break;
            } else {
                i++;
            }
        }
        return false;
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
