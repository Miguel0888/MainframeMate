package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.ImageRef;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Expandable thumbnail panel showing image previews from a wiki page.
 * <p>
 * Thumbnails resize dynamically when the enclosing splitter is dragged wider or narrower.
 * Animated GIFs show a static first-frame thumbnail with a ▶ play overlay;
 * clicking it starts the animation, scrolling or resizing stops it again.
 * <p>
 * Click on a non-GIF thumbnail (or double-click on any) opens the full-size
 * image overlay dialog (via {@link ImageStripPanel#openOverlay}).
 * A "▶▶" button at the bottom collapses the panel back to the narrow {@link ImageStripPanel}.
 * <p>
 * The panel supports synchronised scrolling with the text pane via {@link #scrollToImage(int)}.
 */
public class ImageThumbnailPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(ImageThumbnailPanel.class.getName());

    private static final int PADDING = 6;
    /** Diameter of the play/pause overlay circle, in pixels. */
    private static final int OVERLAY_SIZE = 40;

    private final List<ImageRef> images;
    private final List<ThumbnailEntry> entries = new ArrayList<ThumbnailEntry>();
    private final JPanel contentPanel;
    private final JScrollPane scrollPane;
    private Runnable collapseCallback;

    /** Tracks the last width used for scaling to avoid redundant rescale passes. */
    private int lastScaleWidth = -1;

    // ═══════════════════════════════════════════════════════════
    //  Thumbnail entry
    // ═══════════════════════════════════════════════════════════

    private static class ThumbnailEntry {
        final ImageRef imageRef;
        final int index;
        final JPanel panel;
        /** Custom painting label: draws the thumbnail + play/pause overlay. */
        final ThumbnailLabel imageLabel;
        final JLabel captionLabel;

        BufferedImage originalImage;   // first-frame (static) — null until loaded
        byte[] gifBytes;               // raw GIF data for animation — null if not animated GIF
        boolean animatedGif;           // true if this image is an animated GIF
        boolean playing;               // true while the GIF animation is running
        boolean loaded;

        ThumbnailEntry(ImageRef ref, int idx) {
            this.imageRef = ref;
            this.index = idx;

            panel = new JPanel(new BorderLayout(0, 2));
            panel.setBorder(BorderFactory.createEmptyBorder(4, PADDING, 4, PADDING));
            panel.setOpaque(false);

            imageLabel = new ThumbnailLabel();
            imageLabel.setOpaque(true);
            imageLabel.setBackground(new Color(230, 230, 230));
            imageLabel.setPreferredSize(new Dimension(80, 60));
            imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            String desc = ref.description();
            if (desc.length() > 60) desc = desc.substring(0, 57) + "\u2026";
            captionLabel = new JLabel(desc, SwingConstants.CENTER);
            captionLabel.setFont(captionLabel.getFont().deriveFont(10f));
            captionLabel.setForeground(Color.GRAY);

            panel.add(imageLabel, BorderLayout.CENTER);
            panel.add(captionLabel, BorderLayout.SOUTH);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Custom JLabel that paints a play/pause overlay on top
    // ═══════════════════════════════════════════════════════════

    /**
     * JLabel subclass that draws the standard icon/image and then paints
     * a circular play (▶) or pause (⏸) overlay in the centre for animated GIFs.
     */
    private static class ThumbnailLabel extends JLabel {
        boolean showPlayOverlay;  // true → draw ▶
        boolean showPauseOverlay; // true → draw ⏸  (shown while playing)

        ThumbnailLabel() {
            super((String) null, SwingConstants.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!showPlayOverlay && !showPauseOverlay) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int r = OVERLAY_SIZE / 2;

            // Semi-transparent black circle
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g2.setColor(Color.BLACK);
            g2.fillOval(cx - r, cy - r, OVERLAY_SIZE, OVERLAY_SIZE);

            // White icon
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
            g2.setColor(Color.WHITE);

            if (showPlayOverlay) {
                // ▶ triangle
                int triH = OVERLAY_SIZE / 3;
                int triW = (int) (triH * 0.9);
                int tx = cx - triW / 2 + 2; // slight right offset for optical centering
                int ty = cy - triH / 2;
                g2.fillPolygon(
                        new int[]{tx, tx, tx + triW},
                        new int[]{ty, ty + triH, cy},
                        3);
            } else {
                // ⏸ two bars
                int barW = 4;
                int barH = OVERLAY_SIZE / 3;
                int gap = 4;
                g2.fillRect(cx - gap / 2 - barW, cy - barH / 2, barW, barH);
                g2.fillRect(cx + gap / 2, cy - barH / 2, barW, barH);
            }

            g2.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Constructor
    // ═══════════════════════════════════════════════════════════

    public ImageThumbnailPanel(final List<ImageRef> images) {
        this.images = images;
        setLayout(new BorderLayout(0, 0));
        setMinimumSize(new Dimension(80, 0));

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(248, 248, 248));

        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(30);
        scrollPane.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY));
        add(scrollPane, BorderLayout.CENTER);

        // ── Collapse button at bottom (same style as expand ◀◀ in ImageStripPanel) ──
        JLabel collapseBtn = new JLabel("\u25b6\u25b6");
        collapseBtn.setFont(collapseBtn.getFont().deriveFont(Font.BOLD, 11f));
        collapseBtn.setHorizontalAlignment(SwingConstants.CENTER);
        collapseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        collapseBtn.setToolTipText("Miniaturansicht zuklappen");
        collapseBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (collapseCallback != null) collapseCallback.run();
            }
        });
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        bottomBar.add(collapseBtn);
        add(bottomBar, BorderLayout.SOUTH);

        // ── Create placeholder entries ──
        for (int i = 0; i < images.size(); i++) {
            final ThumbnailEntry entry = new ThumbnailEntry(images.get(i), i);
            final int idx = i;
            entry.imageLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (entry.animatedGif && isInsideOverlayCircle(entry.imageLabel, e.getX(), e.getY())) {
                        toggleAnimation(entry);
                    } else {
                        ImageStripPanel.openOverlay(ImageThumbnailPanel.this, images, idx);
                    }
                }
            });
            entries.add(entry);
            contentPanel.add(entry.panel);
        }

        // ── Rescale thumbnails on resize → also stops all animations ──
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int newW = getAvailableWidth();
                if (newW != lastScaleWidth && newW > 20) {
                    lastScaleWidth = newW;
                    stopAllAnimations();
                    rescaleAll();
                }
            }
        });

        // ── Load thumbnails asynchronously ──
        loadThumbnailsAsync();
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════

    public void setCollapseCallback(Runnable callback) {
        this.collapseCallback = callback;
    }

    /**
     * Scroll the thumbnail list so that the image at the given index is visible.
     * Used for synchronised scrolling with the text pane.
     * Also stops all running animations (user is reading, not watching).
     */
    public void scrollToImage(final int index) {
        if (index < 0 || index >= entries.size()) return;
        stopAllAnimations();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JPanel target = entries.get(index).panel;
                Rectangle bounds = target.getBounds();
                scrollPane.getViewport().setViewPosition(
                        new Point(0, Math.max(0, bounds.y - 4)));
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  GIF animation play / pause
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if the given (x,y) coordinate falls within the
     * play/pause overlay circle drawn in the centre of the label.
     */
    private static boolean isInsideOverlayCircle(JLabel label, int x, int y) {
        int cx = label.getWidth() / 2;
        int cy = label.getHeight() / 2;
        int r = OVERLAY_SIZE / 2;
        double dist = Math.sqrt((double) (x - cx) * (x - cx) + (double) (y - cy) * (y - cy));
        return dist <= r;
    }

    private void toggleAnimation(ThumbnailEntry entry) {
        if (!entry.animatedGif || entry.gifBytes == null) return;

        if (entry.playing) {
            // ── Pause: go back to static thumbnail ──
            entry.playing = false;
            entry.imageLabel.showPlayOverlay = true;
            entry.imageLabel.showPauseOverlay = false;
            rescaleEntry(entry); // restores static BufferedImage
        } else {
            // ── Play: replace with animated ImageIcon ──
            entry.playing = true;
            entry.imageLabel.showPlayOverlay = false;
            entry.imageLabel.showPauseOverlay = true;

            int availW = getAvailableWidth();
            if (availW < 30) availW = 30;
            double scale = (double) availW / entry.originalImage.getWidth();
            int thumbW = availW;
            int thumbH = Math.max(1, (int) (entry.originalImage.getHeight() * scale));

            ImageIcon animIcon = new ImageIcon(entry.gifBytes);
            // Scale the animated icon to thumbnail size
            Image scaled = animIcon.getImage().getScaledInstance(thumbW, thumbH, Image.SCALE_DEFAULT);
            ImageIcon scaledIcon = new ImageIcon(scaled);
            scaledIcon.setImageObserver(entry.imageLabel); // drives animation repaints

            entry.imageLabel.setIcon(scaledIcon);
            entry.imageLabel.repaint();
        }
    }

    /**
     * Stop all currently playing GIF animations and revert to static thumbnails.
     */
    private void stopAllAnimations() {
        for (ThumbnailEntry entry : entries) {
            if (entry.playing) {
                entry.playing = false;
                entry.imageLabel.showPlayOverlay = true;
                entry.imageLabel.showPauseOverlay = false;
                rescaleEntry(entry);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Async thumbnail loading
    // ═══════════════════════════════════════════════════════════

    private void loadThumbnailsAsync() {
        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < images.size(); i++) {
                    try {
                        byte[] data = ImageStripPanel.downloadBytes(images.get(i).src());
                        if (data == null || data.length == 0) continue;

                        boolean isGif = data.length > 3
                                && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';

                        BufferedImage img = decodeToBufferedImage(data, isGif);
                        if (img != null) {
                            ThumbnailEntry entry = entries.get(i);
                            entry.originalImage = img;
                            entry.loaded = true;
                            if (isGif && isAnimatedGif(data)) {
                                entry.animatedGif = true;
                                entry.gifBytes = data;
                            }
                            publish(i);
                        }
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[Thumbnail] Load failed: " + images.get(i).src(), e);
                    }
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> indices) {
                for (int idx : indices) {
                    ThumbnailEntry entry = entries.get(idx);
                    rescaleEntry(entry);
                    // Show ▶ overlay on animated GIFs
                    if (entry.animatedGif) {
                        entry.imageLabel.showPlayOverlay = true;
                        entry.imageLabel.repaint();
                    }
                }
            }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════════
    //  Thumbnail scaling
    // ═══════════════════════════════════════════════════════════

    private int getAvailableWidth() {
        int sbWidth = scrollPane.getVerticalScrollBar().isVisible()
                ? scrollPane.getVerticalScrollBar().getWidth() : 12;
        return getWidth() - PADDING * 2 - sbWidth - 4;
    }

    private void rescaleAll() {
        for (ThumbnailEntry entry : entries) {
            if (entry.loaded) {
                rescaleEntry(entry);
            }
        }
    }

    private void rescaleEntry(ThumbnailEntry entry) {
        if (entry.originalImage == null) return;

        int availW = getAvailableWidth();
        if (availW < 30) availW = 30;

        BufferedImage orig = entry.originalImage;
        double scale = (double) availW / orig.getWidth();
        int thumbW = availW;
        int thumbH = Math.max(1, (int) (orig.getHeight() * scale));

        // Cap very tall images to a reasonable height
        int maxH = Math.max(400, getHeight());
        if (thumbH > maxH) {
            thumbH = maxH;
            thumbW = Math.max(1, (int) (orig.getWidth() * ((double) maxH / orig.getHeight())));
        }

        BufferedImage scaled = scaleImage(orig, thumbW, thumbH);
        entry.imageLabel.setIcon(new ImageIcon(scaled));
        entry.imageLabel.setText(null);
        entry.imageLabel.setBackground(null);
        entry.imageLabel.setOpaque(false);
        entry.imageLabel.setPreferredSize(new Dimension(thumbW, thumbH));
        entry.panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, thumbH + 24));

        contentPanel.revalidate();
    }

    // ═══════════════════════════════════════════════════════════
    //  Image decoding
    // ═══════════════════════════════════════════════════════════

    /**
     * High-quality image scaling using Graphics2D with bilinear interpolation.
     */
    private static BufferedImage scaleImage(BufferedImage src, int w, int h) {
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dest.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return dest;
    }

    /**
     * Decode raw image bytes into a static {@link BufferedImage} (first frame).
     * For GIFs, uses {@link ImageIcon}/{@link Toolkit} + {@code PixelGrabber}
     * because {@code ImageIO.read()} returns {@code null} for some animated GIFs.
     */
    private static BufferedImage decodeToBufferedImage(byte[] data, boolean isGif) {
        if (isGif) {
            ImageIcon icon = new ImageIcon(data);
            int w = icon.getIconWidth();
            int h = icon.getIconHeight();
            if (w <= 0 || h <= 0) return null;

            int[] pixels = new int[w * h];
            java.awt.image.PixelGrabber pg =
                    new java.awt.image.PixelGrabber(icon.getImage(), 0, 0, w, h, pixels, 0, w);
            try {
                pg.grabPixels();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            bi.setRGB(0, 0, w, h, pixels, 0, w);
            return bi;
        }

        // Non-GIF: use ImageIO
        try {
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
            if (bi != null) return bi;
        } catch (Exception e) {
            LOG.log(Level.FINE, "[Thumbnail] ImageIO.read failed, trying Toolkit fallback", e);
        }

        // Fallback
        ImageIcon icon = new ImageIcon(data);
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        if (w <= 0 || h <= 0) return null;
        int[] pixels = new int[w * h];
        java.awt.image.PixelGrabber pg =
                new java.awt.image.PixelGrabber(icon.getImage(), 0, 0, w, h, pixels, 0, w);
        try {
            pg.grabPixels();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, w, h, pixels, 0, w);
        return bi;
    }

    /**
     * Check whether a GIF byte array contains multiple image frames (animated).
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
                i += 9;
                if (i >= data.length) break;
                int packed = data[i - 1] & 0xFF;
                if ((packed & 0x80) != 0) {
                    int lctSize = 3 * (1 << ((packed & 0x07) + 1));
                    i += lctSize;
                }
                if (i >= data.length) break;
                i++; // LZW minimum code size
                while (i < data.length) {
                    int blockSize = data[i] & 0xFF;
                    i++;
                    if (blockSize == 0) break;
                    i += blockSize;
                }
            } else if (b == 0x21) { // Extension
                i += 2;
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
}
