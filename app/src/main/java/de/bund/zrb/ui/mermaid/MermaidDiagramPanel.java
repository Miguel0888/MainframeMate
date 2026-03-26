package de.bund.zrb.ui.mermaid;

import com.aresstack.mermaid.editor.SourceEditBridge;
import com.aresstack.mermaid.layout.*;
import de.bund.zrb.mermaid.MermaidRenderer;
import de.bund.zrb.mermaid.MermaidSvgFixup;
import de.bund.zrb.wiki.ui.SvgRenderer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable Swing panel that renders a Mermaid diagram as an interactive SVG image.
 * <p>
 * Features:
 * <ul>
 *   <li>Mouse-wheel zoom (centered on cursor) + floating overlay buttons</li>
 *   <li>Left-click to select, left-drag to pan; fit-to-panel on first layout</li>
 *   <li>Click-to-select nodes and edges (yellow highlight overlay)</li>
 *   <li>Detail display of the selected element (exposed for external sidebar)</li>
 *   <li>If editable: rename nodes, change labels — changes are applied to source and re-rendered</li>
 *   <li>Right-drag node→node to create a new edge (blue line)</li>
 *   <li>Right-drag edge endpoint→node to reconnect (orange line)</li>
 * </ul>
 */
public class MermaidDiagramPanel extends JPanel {

    private boolean editable;

    // State
    private String mermaidSource;
    private BufferedImage image;
    private RenderedDiagram diagram;
    private String svg;
    private boolean renderError;

    // Selection (highlight is computed dynamically from these in paintDiagram)
    private DiagramNode selectedNode;
    private DiagramEdge selectedEdge;

    // Drag (edge creation / reconnection)
    private DiagramNode dragSourceNode;
    private DiagramEdge dragEdge;
    private boolean dragFromSource;
    private Point dragStart;
    private Point dragCurrent;
    private boolean dragging;

    // ── Zoom & Pan state ──
    private double zoom = 1.0;
    private double panOffsetX = 0;
    private double panOffsetY = 0;
    private Point panDragStart;
    private boolean fitDone = false;
    private int baseW, baseH;
    /** Where left-button was pressed (for detecting click vs. drag). */
    private Point leftPressPoint;
    /** true once the left-button drag exceeded the motion threshold. */
    private boolean leftDragged;

    // ── Auto-refresh after zoom ──
    private double lastRefreshZoom = 1.0;
    /** Threshold in absolute zoom-percent points (e.g. 1.0 = 1%). */
    private double autoRefreshThresholdPercent = 1.0;
    private Timer autoRefreshTimer;

    // UI — image rendered on this panel (inside JLayeredPane for overlay)
    private final JPanel imagePanel;
    private final JLabel zoomLabel;

    // ── Detail / edit panels (exposed for external sidebar use) ──
    private final JTextArea detailArea;
    private final JPanel editPanel;
    private final JLabel statusLabel;

    // Listener for source changes
    private SourceChangeListener sourceChangeListener;

    /** Listener notified when the Mermaid source is modified via diagram interaction. */
    public interface SourceChangeListener {
        void onSourceChanged(String newMermaidSource);
    }

    /** Listener notified when the detail / edit content changes (for external sidebar). */
    public interface DetailChangeListener {
        void onDetailChanged();
    }
    private DetailChangeListener detailChangeListener;

    /** Optional listener notified when the user selects a node (for external outline sync). */
    public interface NodeSelectionListener {
        void onNodeSelected(DiagramNode node);
    }
    private NodeSelectionListener nodeSelectionListener;

    public MermaidDiagramPanel(boolean editable) {
        this.editable = editable;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // ── Image panel (renders the SVG) ──
        imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintDiagram((Graphics2D) g);
            }
        };
        imagePanel.setBackground(Color.WHITE);
        imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        imagePanel.setPreferredSize(new Dimension(600, 400));
        setupMouseHandlers();

        // ── Fit-to-panel on first valid layout ──
        imagePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!fitDone && image != null
                        && imagePanel.getWidth() > 0 && imagePanel.getHeight() > 0) {
                    fitToPanel();
                    fitDone = true;
                }
            }
        });

        // ── Detail area (exposed via getDetailPanel()) ──
        detailArea = new JTextArea("Klicken Sie auf ein Element\u2026");
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setRows(5);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        detailArea.setBackground(new Color(255, 255, 230));
        detailArea.setBorder(new EmptyBorder(4, 6, 4, 6));

        // ── Edit panel (exposed via getEditPanel()) ──
        editPanel = new JPanel();
        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
        editPanel.setBackground(new Color(235, 255, 235));
        editPanel.setBorder(new EmptyBorder(4, 6, 4, 6));

        // ── Status label (exposed via getStatusLabel()) ──
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 10f));
        statusLabel.setForeground(Color.GRAY);

        // ── Zoom label ──
        zoomLabel = new JLabel("100 %");
        zoomLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        zoomLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // ── Build overlay: JLayeredPane with image + floating zoom buttons ──
        final JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);

        // Overlay buttons (translucent, centered at top)
        final int BTN_SIZE = 36;
        final int BTN_GAP = 4;

        final JPanel btnZoomOut = createOverlayButton("\u2796", BTN_SIZE);  // ➖
        btnZoomOut.setToolTipText("Verkleinern");
        final JPanel btnZoomIn = createOverlayButton("\u2795", BTN_SIZE);   // ➕
        btnZoomIn.setToolTipText("Vergr\u00F6\u00DFern");
        final JPanel btnFit = createOverlayButton("\uD83D\uDDBC", BTN_SIZE); // 🖼 (frame = einpassen)
        btnFit.setToolTipText("Einpassen");
        final JPanel btnReRender = createOverlayButton("\uD83D\uDD04", BTN_SIZE); // 🔄 (refresh)
        btnReRender.setToolTipText("Neu rendern");

        layeredPane.add(imagePanel, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(btnZoomOut, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(zoomLabel, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(btnZoomIn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(btnFit, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(btnReRender, JLayeredPane.PALETTE_LAYER);

        // Position overlay buttons on resize
        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                imagePanel.setBounds(0, 0, w, h);

                // Center the 4 buttons + zoom label at the top
                int totalW = 4 * BTN_SIZE + 3 * BTN_GAP + 50; // 50 for zoom label
                int startX = (w - totalW) / 2;
                int y = 10;
                btnZoomOut.setBounds(startX, y, BTN_SIZE, BTN_SIZE);
                // zoom label between - and +
                zoomLabel.setBounds(startX + BTN_SIZE + BTN_GAP, y + 6, 50, 24);
                btnZoomIn.setBounds(startX + BTN_SIZE + BTN_GAP + 50, y, BTN_SIZE, BTN_SIZE);
                btnFit.setBounds(startX + 2 * BTN_SIZE + 2 * BTN_GAP + 50, y, BTN_SIZE, BTN_SIZE);
                btnReRender.setBounds(startX + 3 * BTN_SIZE + 3 * BTN_GAP + 50, y, BTN_SIZE, BTN_SIZE);
            }
        });

        // Wire overlay button clicks via glass-pane approach (mouse events on JLayeredPane)
        addOverlayClickHandler(btnZoomOut, new Runnable() {
            @Override
            public void run() {
                double cx = imagePanel.getWidth() / 2.0;
                double cy = imagePanel.getHeight() / 2.0;
                zoomBy(1.0 / 1.4, cx, cy);
            }
        });
        addOverlayClickHandler(btnZoomIn, new Runnable() {
            @Override
            public void run() {
                double cx = imagePanel.getWidth() / 2.0;
                double cy = imagePanel.getHeight() / 2.0;
                zoomBy(1.4, cx, cy);
            }
        });
        addOverlayClickHandler(btnFit, new Runnable() {
            @Override
            public void run() {
                fitToPanel();
            }
        });
        addOverlayClickHandler(btnReRender, new Runnable() {
            @Override
            public void run() {
                reRasterize();
            }
        });

        add(layeredPane, BorderLayout.CENTER);
    }

    // ═══════════════════════════════════════════════════════════
    //  Overlay button factory (adapted from WikiImageDialog for light bg)
    // ═══════════════════════════════════════════════════════════

    /**
     * Translucent rounded overlay button for light backgrounds.
     * Semi-transparent white with a subtle border, dark icon.
     */
    private static JPanel createOverlayButton(String symbol, int size) {
        final JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Semi-transparent white background
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
                g2.setColor(new Color(255, 255, 255));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                // Subtle border
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2.setColor(new Color(120, 120, 120));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel(symbol, SwingConstants.CENTER);
        label.setForeground(new Color(60, 60, 60));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(label, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(size, size));
        return panel;
    }

    private static void addOverlayClickHandler(final JPanel btn, final Runnable action) {
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setOpaque(true);
                btn.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setOpaque(false);
                btn.repaint();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API — exposed panels for external sidebar
    // ═══════════════════════════════════════════════════════════

    /** @return the detail text area showing selected element info */
    public JTextArea getDetailArea() { return detailArea; }

    /** @return the edit panel for node/edge editing controls */
    public JPanel getEditPanel() { return editPanel; }

    /** @return the status label showing render info */
    public JLabel getStatusLabel() { return statusLabel; }

    public void setDetailChangeListener(DetailChangeListener listener) {
        this.detailChangeListener = listener;
    }

    public void setNodeSelectionListener(NodeSelectionListener listener) {
        this.nodeSelectionListener = listener;
    }

    public void setSourceChangeListener(SourceChangeListener listener) {
        this.sourceChangeListener = listener;
    }

    /**
     * Set the auto-refresh threshold in absolute zoom-percent points.
     * E.g. 1.0 means a refresh is triggered once the zoom has moved ≥1 percentage-point
     * from the zoom level at the last refresh. Set to 0 to disable auto-refresh.
     */
    public void setAutoRefreshThresholdPercent(double thresholdPercent) {
        this.autoRefreshThresholdPercent = thresholdPercent;
    }

    /**
     * Render (or re-render) the given Mermaid source code.
     */
    public void setMermaidSource(String source) {
        this.mermaidSource = source;
        this.selectedNode = null;
        this.selectedEdge = null;
        this.renderError = false;
        this.dragging = false;

        if (source == null || source.trim().isEmpty()) {
            this.image = null;
            this.diagram = null;
            this.svg = null;
            imagePanel.repaint();
            statusLabel.setText("Kein Diagramm-Quelltext.");
            return;
        }

        statusLabel.setText("Rendering\u2026");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private BufferedImage renderedImage;
            private RenderedDiagram renderedDiagram;
            private String renderedSvg;
            private boolean error;

            @Override
            protected Void doInBackground() {
                try {
                    MermaidRenderer renderer = MermaidRenderer.getInstance();
                    String svgResult = renderer.renderToSvg(source);
                    if (svgResult == null || !svgResult.contains("<svg")) {
                        error = true;
                        return null;
                    }
                    renderedDiagram = DiagramLayoutExtractor.extract(svgResult);
                    renderedSvg = MermaidSvgFixup.fixForBatik(svgResult, source);
                    renderedImage = SvgRenderer.renderToBufferedImage(
                            renderedSvg.getBytes("UTF-8"));
                    if (renderedImage == null) error = true;
                } catch (Exception e) {
                    System.err.println("[MermaidDiagramPanel] Render error: " + e);
                    error = true;
                }
                return null;
            }

            @Override
            protected void done() {
                renderError = error;
                image = renderedImage;
                diagram = renderedDiagram;
                svg = renderedSvg;

                if (!error && image != null) {
                    baseW = image.getWidth();
                    baseH = image.getHeight();
                    if (imagePanel.getWidth() > 0 && imagePanel.getHeight() > 0) {
                        fitToPanel();
                        fitDone = true;
                    } else {
                        fitDone = false;
                    }
                    lastRefreshZoom = zoom;
                }

                imagePanel.repaint();
                updateZoomLabel();
                if (error) {
                    statusLabel.setText("\u26A0 Rendering fehlgeschlagen");
                } else {
                    int nodes = diagram != null ? diagram.getNodes().size() : 0;
                    int edges = diagram != null ? diagram.getEdges().size() : 0;
                    statusLabel.setText(nodes + " Knoten, " + edges + " Kanten"
                            + (editable ? " \u2014 Rechtsklick: Verbindung ziehen" : "")
                            + " \u2014 Mausrad: Zoom, Linksklick: Pan");
                }
            }
        };
        worker.execute();
    }

    public String getMermaidSource() {
        return mermaidSource;
    }

    // ═══════════════════════════════════════════════════════════
    //  Zoom & Pan
    // ═══════════════════════════════════════════════════════════

    private void fitToPanel() {
        if (image == null || baseW <= 0 || baseH <= 0) return;
        int pw = imagePanel.getWidth(), ph = imagePanel.getHeight();
        if (pw <= 0 || ph <= 0) return;
        double sx = (double) pw / baseW;
        double sy = (double) ph / baseH;
        zoom = Math.min(sx, sy) * 0.95;
        double dw = baseW * zoom;
        double dh = baseH * zoom;
        panOffsetX = (pw - dw) / 2.0;
        panOffsetY = (ph - dh) / 2.0;
        updateZoomLabel();
        imagePanel.repaint();
        scheduleAutoRefreshCheck();
    }

    private void zoomBy(double factor, double pivotX, double pivotY) {
        double oldZ = zoom;
        zoom *= factor;
        zoom = Math.max(0.02, Math.min(zoom, 50.0));
        panOffsetX = pivotX - (pivotX - panOffsetX) * (zoom / oldZ);
        panOffsetY = pivotY - (pivotY - panOffsetY) * (zoom / oldZ);
        updateZoomLabel();
        imagePanel.repaint();
        scheduleAutoRefreshCheck();
    }

    /**
     * Debounced check: if zoom changed by more than the threshold from the last
     * refresh, trigger a re-rasterize. Uses a 400 ms delay to avoid rapid re-renders
     * during continuous mouse-wheel scrolling.
     */
    private void scheduleAutoRefreshCheck() {
        if (autoRefreshThresholdPercent <= 0 || svg == null) return;
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
        }
        autoRefreshTimer = new Timer(400, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                double currentPct = zoom * 100.0;
                double lastPct = lastRefreshZoom * 100.0;
                if (Math.abs(currentPct - lastPct) >= autoRefreshThresholdPercent) {
                    reRasterize();
                }
            }
        });
        autoRefreshTimer.setRepeats(false);
        autoRefreshTimer.start();
    }

    private void updateZoomLabel() {
        int pct = (int) Math.round(zoom * 100);
        zoomLabel.setText(pct + " %");
    }

    /**
     * Re-rasterize the existing SVG at a resolution that matches the current zoom level.
     */
    private void reRasterize() {
        if (svg == null || svg.isEmpty()) {
            statusLabel.setText("\u26A0 Kein SVG zum Neu-Rendern vorhanden");
            return;
        }
        statusLabel.setText("Neu rendern\u2026");

        final String currentSvg = this.svg;
        final double currentZoom = this.zoom;
        final double currentPanX = this.panOffsetX;
        final double currentPanY = this.panOffsetY;

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                try {
                    float targetWidth = (float) (baseW * Math.max(currentZoom, 1.0) * 1.5);
                    targetWidth = Math.max(targetWidth, 800);
                    targetWidth = Math.min(targetWidth, 16000);
                    return SvgRenderer.renderToBufferedImageForced(
                            currentSvg.getBytes("UTF-8"), targetWidth);
                } catch (Exception e) {
                    System.err.println("[MermaidDiagramPanel] Re-rasterize error: " + e);
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    BufferedImage newImage = get();
                    if (newImage != null) {
                        double scaleRatio = (double) newImage.getWidth() / baseW;
                        image = newImage;
                        baseW = newImage.getWidth();
                        baseH = newImage.getHeight();
                        zoom = currentZoom / scaleRatio;
                        panOffsetX = currentPanX;
                        panOffsetY = currentPanY;
                        lastRefreshZoom = zoom;
                        updateZoomLabel();
                        imagePanel.repaint();
                        statusLabel.setText("\u2713 Neu gerendert (" + baseW + "\u00D7" + baseH + " px)");
                    } else {
                        statusLabel.setText("\u26A0 Neu-Rendern fehlgeschlagen");
                    }
                } catch (Exception e) {
                    statusLabel.setText("\u26A0 Neu-Rendern fehlgeschlagen");
                }
            }
        };
        worker.execute();
    }

    // ═══════════════════════════════════════════════════════════
    //  Painting
    // ═══════════════════════════════════════════════════════════

    private void paintDiagram(Graphics2D g) {
        if (image == null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
            g.setColor(Color.GRAY);
            String msg = renderError ? "\u26A0 Rendering fehlgeschlagen" : "Kein Diagramm geladen";
            g.drawString(msg, 20, 30);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int dw = (int) Math.round(baseW * zoom);
        int dh = (int) Math.round(baseH * zoom);
        int ox = (int) Math.round(panOffsetX);
        int oy = (int) Math.round(panOffsetY);
        g2.drawImage(image, ox, oy, dw, dh, null);

        // ── Highlight all search results with a lighter colour ──
        if (!lastSearchResults.isEmpty() && diagram != null) {
            g2.setColor(new Color(255, 220, 100, 50));
            for (int i = 0; i < lastSearchResults.size(); i++) {
                if (i == lastSearchIndex) continue; // active result is drawn separately
                DiagramNode sr = lastSearchResults.get(i);
                Rectangle r = computeScreenRect(sr.getX(), sr.getY(), sr.getWidth(), sr.getHeight(), ox, oy);
                g2.fillRect(r.x, r.y, r.width, r.height);
            }
        }

        // ── Highlight selected node or edge (computed fresh from current baseW/zoom) ──
        Rectangle hlRect = null;
        if (selectedNode != null && diagram != null) {
            hlRect = computeScreenRect(selectedNode.getX(), selectedNode.getY(),
                    selectedNode.getWidth(), selectedNode.getHeight(), ox, oy);
        } else if (selectedEdge != null && diagram != null) {
            hlRect = computeScreenRect(selectedEdge.getX(), selectedEdge.getY(),
                    selectedEdge.getWidth(), selectedEdge.getHeight(), ox, oy);
        }
        if (hlRect != null) {
            g2.setColor(new Color(255, 255, 0, 100));
            g2.fillRect(hlRect.x, hlRect.y, hlRect.width, hlRect.height);
            g2.setColor(new Color(255, 180, 0, 200));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(hlRect.x, hlRect.y, hlRect.width, hlRect.height);
        }

        // ── Drag rubber-band line ──
        if (dragging && dragStart != null && dragCurrent != null) {
            Color lineColor = dragEdge != null
                    ? new Color(255, 140, 0, 200)
                    : new Color(0, 100, 255, 180);
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0));
            g2.drawLine(dragStart.x, dragStart.y, dragCurrent.x, dragCurrent.y);
        }
        g2.dispose();
    }

    /**
     * Convert SVG viewBox coordinates to screen rectangle, using the CURRENT
     * baseW / baseH / zoom / panOffset.  This ensures the highlight is always
     * correct even after reRasterize changes baseW.
     */
    private Rectangle computeScreenRect(double svgX, double svgY, double svgW, double svgH,
                                         int ox, int oy) {
        if (diagram == null || baseW <= 0 || baseH <= 0) {
            return new Rectangle(ox, oy, 5, 5);
        }
        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();
        // SVG → image pixel → screen pixel (in one step)
        int hx = (int) Math.round(((svgX - vbX) / vbW * baseW) * zoom) + ox;
        int hy = (int) Math.round(((svgY - vbY) / vbH * baseH) * zoom) + oy;
        int hw = Math.max((int) Math.round((svgW / vbW * baseW) * zoom), 4);
        int hh = Math.max((int) Math.round((svgH / vbH * baseH) * zoom), 4);
        return new Rectangle(hx, hy, hw, hh);
    }

    // ═══════════════════════════════════════════════════════════
    //  Mouse interaction
    // ═══════════════════════════════════════════════════════════

    private void setupMouseHandlers() {

        imagePanel.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (image == null) return;
                double factor = e.getWheelRotation() < 0 ? 1.25 : 1.0 / 1.25;
                zoomBy(factor, e.getX(), e.getY());
            }
        });

        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // ── Left-click → prepare for pan (drag) or click-to-select (release) ──
                if (SwingUtilities.isLeftMouseButton(e)) {
                    panDragStart = new Point(e.getX(), e.getY());
                    leftPressPoint = new Point(e.getX(), e.getY());
                    leftDragged = false;
                    return;
                }

                // ── Right-click → edge creation / reconnection (editable only) ──
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (!editable || image == null || diagram == null) return;

                    double[] svgCoords = screenToSvg(e.getX(), e.getY());
                    if (svgCoords == null) return;
                    double svgX = svgCoords[0], svgY = svgCoords[1];

                    double vbW = diagram.getViewBoxWidth();
                    double vbH = diagram.getViewBoxHeight();

                    // Check edge endpoints for reconnection
                    dragEdge = null;
                    double edgeThreshold = Math.max(vbW, vbH) * 0.035;
                    double bestDist = edgeThreshold;
                    for (DiagramEdge cand : diagram.getEdges()) {
                        String pd = cand.getPathData();
                        if (pd == null || pd.isEmpty()) continue;
                        double[] ep = DiagramLayoutExtractor.parsePathEndpoints(pd);
                        if (ep == null) continue;
                        double dStart = Math.hypot(svgX - ep[0], svgY - ep[1]);
                        double dEnd = Math.hypot(svgX - ep[2], svgY - ep[3]);
                        if (dStart < bestDist) {
                            bestDist = dStart;
                            dragEdge = cand;
                            dragFromSource = true;
                        }
                        if (dEnd < bestDist) {
                            bestDist = dEnd;
                            dragEdge = cand;
                            dragFromSource = false;
                        }
                    }
                    if (dragEdge != null) {
                        dragStart = new Point(e.getX(), e.getY());
                        dragging = false;
                        dragSourceNode = null;
                        selectedEdge = dragEdge;
                        selectedNode = null;
                        updateDetailArea();
                        return;
                    }

                    // Check nodes for new edge creation
                    DiagramNode foundNode = findSmallestNodeAt(svgX, svgY);
                    if (foundNode != null) {
                        selectedNode = foundNode;
                        selectedEdge = null;
                        dragSourceNode = foundNode;
                        dragStart = new Point(e.getX(), e.getY());
                        dragging = false;
                        updateDetailArea();
                        updateEditPanel();
                        imagePanel.repaint();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // ── Left-click release ──
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (!leftDragged) {
                        // Click without significant drag → select element
                        doSelection(e.getX(), e.getY());
                    }
                    panDragStart = null;
                    leftPressPoint = null;
                    imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    return;
                }

                // ── Right-click release → complete edge creation / reconnection ──
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (editable && dragging && image != null && diagram != null) {
                        double[] svgCoords = screenToSvg(e.getX(), e.getY());
                        if (svgCoords != null) {
                            DiagramNode targetNode = diagram.findNodeAt(svgCoords[0], svgCoords[1]);
                            if (targetNode != null) {
                                if (dragEdge != null) {
                                    handleReconnect(targetNode);
                                } else if (dragSourceNode != null
                                        && !dragSourceNode.getId().equals(targetNode.getId())) {
                                    handleNewEdge(targetNode);
                                }
                            }
                        }
                    }
                    dragging = false;
                    dragSourceNode = null;
                    dragEdge = null;
                    dragStart = null;
                    dragCurrent = null;
                    imagePanel.repaint();
                }
            }
        });

        imagePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // ── Pan with left-button ──
                if (SwingUtilities.isLeftMouseButton(e) && panDragStart != null) {
                    if (leftPressPoint != null) {
                        int dx = e.getX() - leftPressPoint.x;
                        int dy = e.getY() - leftPressPoint.y;
                        if (!leftDragged && (dx * dx + dy * dy) > 9) {
                            leftDragged = true;
                            imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        }
                    }
                    if (leftDragged) {
                        panOffsetX += e.getX() - panDragStart.x;
                        panOffsetY += e.getY() - panDragStart.y;
                        panDragStart = new Point(e.getX(), e.getY());
                        imagePanel.repaint();
                    }
                    return;
                }

                // ── Edge drag with right-button (editable only) ──
                if (SwingUtilities.isRightMouseButton(e) && editable) {
                    if (dragStart != null && (dragSourceNode != null || dragEdge != null)) {
                        int dx = e.getX() - dragStart.x;
                        int dy = e.getY() - dragStart.y;
                        if (!dragging && (dx * dx + dy * dy) > 25) {
                            dragging = true;
                        }
                        if (dragging) {
                            dragCurrent = new Point(e.getX(), e.getY());
                            imagePanel.repaint();
                        }
                    }
                }
            }
        });
    }

    /**
     * Perform element selection at the given screen coordinates.
     * Called on left-click release when no drag occurred.
     */
    private void doSelection(int screenX, int screenY) {
        if (image == null || diagram == null) return;

        double[] svgCoords = screenToSvg(screenX, screenY);
        if (svgCoords == null) return;
        double svgX = svgCoords[0], svgY = svgCoords[1];

        // Check nodes
        DiagramNode foundNode = findSmallestNodeAt(svgX, svgY);
        if (foundNode != null) {
            selectedNode = foundNode;
            selectedEdge = null;
            updateDetailArea();
            updateEditPanel();
            imagePanel.repaint();
            if (nodeSelectionListener != null) nodeSelectionListener.onNodeSelected(foundNode);
            return;
        }

        // Check edges (prefer smallest bounding box)
        DiagramEdge foundEdge = null;
        double bestEdgeArea = Double.MAX_VALUE;
        for (DiagramEdge edge : diagram.getEdges()) {
            if (edge.containsApprox(svgX, svgY)) {
                double area = edge.getWidth() * edge.getHeight();
                if (area < bestEdgeArea) {
                    bestEdgeArea = area;
                    foundEdge = edge;
                }
            }
        }
        if (foundEdge != null) {
            selectedEdge = foundEdge;
            selectedNode = null;
            updateDetailArea();
            updateEditPanel();
            imagePanel.repaint();
            return;
        }

        // Deselect
        selectedNode = null;
        selectedEdge = null;
        updateDetailArea();
        updateEditPanel();
        imagePanel.repaint();
    }

    /**
     * Find the smallest (non-fragment first, then fragment) node containing the given SVG point.
     */
    private DiagramNode findSmallestNodeAt(double svgX, double svgY) {
        DiagramNode foundNode = null;
        DiagramNode foundFragment = null;
        double bestNodeArea = Double.MAX_VALUE;
        double bestFragArea = Double.MAX_VALUE;
        for (DiagramNode n : diagram.getNodes()) {
            if (n.contains(svgX, svgY)) {
                double area = n.getWidth() * n.getHeight();
                if (n instanceof SequenceFragment) {
                    if (area < bestFragArea) {
                        bestFragArea = area;
                        foundFragment = n;
                    }
                } else {
                    if (area < bestNodeArea) {
                        bestNodeArea = area;
                        foundNode = n;
                    }
                }
            }
        }
        return foundNode != null ? foundNode : foundFragment;
    }

    private double[] screenToSvg(int sx, int sy) {
        if (image == null || diagram == null || zoom <= 0 || baseW <= 0 || baseH <= 0) return null;
        double imgPx = (sx - panOffsetX) / zoom;
        double imgPy = (sy - panOffsetY) / zoom;
        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();
        double svgX = vbX + (imgPx / baseW) * vbW;
        double svgY = vbY + (imgPy / baseH) * vbH;
        return new double[]{svgX, svgY};
    }

    // ═══════════════════════════════════════════════════════════
    //  Detail & Edit panels
    // ═══════════════════════════════════════════════════════════

    private void updateDetailArea() {
        if (selectedNode != null) {
            DiagramNode n = selectedNode;
            detailArea.setText("Knoten: " + n.getId() + "\nLabel: " + n.getLabel()
                    + "\nTyp: " + n.getClass().getSimpleName()
                    + "\nPosition: (" + (int) n.getX() + ", " + (int) n.getY() + ")"
                    + "\nGr\u00F6\u00DFe: " + (int) n.getWidth() + " \u00D7 " + (int) n.getHeight());
        } else if (selectedEdge != null) {
            DiagramEdge e = selectedEdge;
            detailArea.setText("Kante: " + e.getSourceId() + " \u2192 " + e.getTargetId()
                    + "\nLabel: " + (e.getLabel() != null ? e.getLabel() : "\u2014")
                    + "\nTyp: " + e.getClass().getSimpleName());
        } else {
            detailArea.setText("Klicken Sie auf ein Element\u2026");
        }
        if (detailChangeListener != null) detailChangeListener.onDetailChanged();
    }

    private void updateEditPanel() {
        editPanel.removeAll();

        if (!editable) {
            editPanel.revalidate();
            editPanel.repaint();
            return;
        }

        if (selectedNode != null) {
            buildNodeEdit();
        } else if (selectedEdge != null) {
            buildEdgeEdit();
        } else {
            JLabel hint = new JLabel("Kein Element ausgew\u00E4hlt");
            hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
            hint.setForeground(Color.GRAY);
            editPanel.add(hint);
        }

        editPanel.revalidate();
        editPanel.repaint();
        if (detailChangeListener != null) detailChangeListener.onDetailChanged();
    }

    private void buildNodeEdit() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        nameRow.setOpaque(false);
        nameRow.add(new JLabel("Name:"));
        editPanel.add(row);

        final JTextField nameField = new JTextField(selectedNode.getLabel(), 16);
        nameRow.add(nameField);
        row.add(nameRow);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        btnRow.setOpaque(false);
        JButton applyBtn = new JButton("Anwenden");
        applyBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String newName = nameField.getText().trim();
                if (!newName.isEmpty() && !newName.equals(selectedNode.getLabel())) {
                    String updated = SourceEditBridge.renameNode(mermaidSource,
                            detectDiagramType(), selectedNode.getId(),
                            selectedNode.getLabel(), newName);
                    applySourceChange(updated);
                }
            }
        });
        btnRow.add(applyBtn);
        row.add(btnRow);
    }

    private void buildEdgeEdit() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        row.add(new JLabel("Label:"));
        String currentLabel = selectedEdge.getLabel() != null ? selectedEdge.getLabel() : "";
        final JTextField labelField = new JTextField(currentLabel, 16);
        row.add(labelField);
        container.add(row);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        btnRow.setOpaque(false);
        JButton applyBtn = new JButton("Anwenden");
        applyBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String newLabel = labelField.getText().trim();
                String updated = SourceEditBridge.changeEdgeLabel(mermaidSource,
                        detectDiagramType(), selectedEdge, newLabel);
                applySourceChange(updated);
            }
        });
        btnRow.add(applyBtn);
        container.add(btnRow);

        JPanel actRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        actRow.setOpaque(false);
        JButton reverseBtn = new JButton("\u21C4 Umkehren");
        reverseBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String updated = SourceEditBridge.reverseEdge(mermaidSource,
                        detectDiagramType(), selectedEdge);
                applySourceChange(updated);
            }
        });
        actRow.add(reverseBtn);
        container.add(actRow);

        JPanel delRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        delRow.setOpaque(false);
        JButton deleteBtn = new JButton("\uD83D\uDDD1 Kante l\u00F6schen");
        deleteBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String updated = SourceEditBridge.deleteEdge(mermaidSource,
                        detectDiagramType(), selectedEdge);
                applySourceChange(updated);
            }
        });
        delRow.add(deleteBtn);
        container.add(delRow);

        editPanel.add(container);
    }

    // ═══════════════════════════════════════════════════════════
    //  Source modification helpers
    // ═══════════════════════════════════════════════════════════

    private void handleNewEdge(DiagramNode targetNode) {
        if (dragSourceNode == null || mermaidSource == null) return;
        String updated = SourceEditBridge.addEdge(mermaidSource, detectDiagramType(),
                dragSourceNode.getId(), targetNode.getId());
        applySourceChange(updated);
    }

    private void handleReconnect(DiagramNode targetNode) {
        if (dragEdge == null || mermaidSource == null) return;
        String newSrc = dragFromSource ? targetNode.getId() : dragEdge.getSourceId();
        String newTgt = dragFromSource ? dragEdge.getTargetId() : targetNode.getId();
        String updated = SourceEditBridge.reconnectEdge(mermaidSource, detectDiagramType(),
                dragEdge, newSrc, newTgt);
        applySourceChange(updated);
    }

    private void applySourceChange(String newSource) {
        if (newSource != null && !newSource.equals(mermaidSource)) {
            mermaidSource = newSource;
            setMermaidSource(newSource);
            if (sourceChangeListener != null) {
                sourceChangeListener.onSourceChanged(newSource);
            }
        }
    }

    private String detectDiagramType() {
        if (mermaidSource == null) return "flowchart";
        String first = mermaidSource.trim().split("\\s+")[0].toLowerCase();
        if (first.startsWith("flowchart") || first.startsWith("graph")) return "flowchart";
        if (first.startsWith("erdiagram")) return "erDiagram";
        if (first.startsWith("statediagram")) return "stateDiagram";
        if (first.startsWith("sequencediagram")) return "sequenceDiagram";
        if (first.startsWith("classdiagram")) return "classDiagram";
        return "flowchart";
    }

    // ═══════════════════════════════════════════════════════════
    //  Diagram Search
    // ═══════════════════════════════════════════════════════════

    private List<DiagramNode> lastSearchResults = new ArrayList<DiagramNode>();
    private int lastSearchIndex = -1;
    private String lastSearchQuery = "";
    /** true once the first result has been zoomed-to (subsequent navigations only pan). */
    private boolean searchZoomDone = false;

    /**
     * Search for nodes matching the query (case-insensitive label/id match).
     * On the first call (or when query changes), zooms to the first match.
     * Returns the total number of matches.
     */
    public int searchAndHighlight(String query) {
        if (query == null || query.trim().isEmpty() || diagram == null) {
            clearSearch();
            return 0;
        }

        String q = query.trim().toLowerCase();

        // Build result list when query changes
        if (!q.equals(lastSearchQuery)) {
            lastSearchQuery = q;
            lastSearchResults = new ArrayList<DiagramNode>();
            for (DiagramNode n : diagram.getNodes()) {
                if (n instanceof SequenceFragment) continue;
                String label = n.getLabel() != null ? n.getLabel().toLowerCase() : "";
                String id = n.getId() != null ? n.getId().toLowerCase() : "";
                if (label.contains(q) || id.contains(q)) {
                    lastSearchResults.add(n);
                }
            }
            lastSearchIndex = -1;
            searchZoomDone = false;
        }

        if (lastSearchResults.isEmpty()) {
            selectedNode = null;
            selectedEdge = null;
            statusLabel.setText("Keine Treffer f\u00FCr \"" + query.trim() + "\"");
            imagePanel.repaint();
            return 0;
        }

        lastSearchIndex = (lastSearchIndex + 1) % lastSearchResults.size();
        selectSearchResult();
        return lastSearchResults.size();
    }

    /**
     * Navigate to the next search result. Maintains current zoom, only pans.
     */
    public int searchNext() {
        if (lastSearchResults.isEmpty()) return 0;
        lastSearchIndex = (lastSearchIndex + 1) % lastSearchResults.size();
        selectSearchResult();
        return lastSearchResults.size();
    }

    /**
     * Navigate to the previous search result. Maintains current zoom, only pans.
     */
    public int searchPrev() {
        if (lastSearchResults.isEmpty()) return 0;
        lastSearchIndex--;
        if (lastSearchIndex < 0) lastSearchIndex = lastSearchResults.size() - 1;
        selectSearchResult();
        return lastSearchResults.size();
    }

    /** @return total number of results from the last search, 0 if none. */
    public int getSearchResultCount() {
        return lastSearchResults.size();
    }

    /** @return 1-based index of the currently shown result, 0 if none. */
    public int getSearchResultIndex() {
        return lastSearchResults.isEmpty() ? 0 : lastSearchIndex + 1;
    }

    /**
     * Apply selection, highlight, zoom/pan, and status update for the current search index.
     */
    private void selectSearchResult() {
        DiagramNode node = lastSearchResults.get(lastSearchIndex);
        selectedNode = node;
        selectedEdge = null;
        updateDetailArea();
        updateEditPanel();

        if (!searchZoomDone) {
            zoomToNode(node);
            searchZoomDone = true;
        } else {
            panToNode(node);
        }

        statusLabel.setText("Treffer " + (lastSearchIndex + 1) + " / " + lastSearchResults.size()
                + " \u2014 \"" + (node.getLabel() != null ? node.getLabel() : node.getId()) + "\"");
        imagePanel.repaint();
        if (nodeSelectionListener != null) nodeSelectionListener.onNodeSelected(node);
    }

    public void clearSearch() {
        lastSearchResults = new ArrayList<DiagramNode>();
        lastSearchIndex = -1;
        lastSearchQuery = "";
        searchZoomDone = false;
    }

    /**
     * Zoom to a node: adjusts zoom so the node fills ~30 % of the viewport,
     * then centres on the node. Used for the first search hit.
     */
    private void zoomToNode(DiagramNode node) {
        if (image == null || diagram == null || baseW <= 0 || baseH <= 0) return;
        int pw = imagePanel.getWidth(), ph = imagePanel.getHeight();
        if (pw <= 0 || ph <= 0) return;

        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();

        double nodeCenterImgX = ((node.getX() + node.getWidth() / 2.0) - vbX) / vbW * baseW;
        double nodeCenterImgY = ((node.getY() + node.getHeight() / 2.0) - vbY) / vbH * baseH;
        double nodeImgW = node.getWidth() / vbW * baseW;
        double nodeImgH = node.getHeight() / vbH * baseH;

        double targetZoomW = (pw * 0.3) / Math.max(nodeImgW, 1);
        double targetZoomH = (ph * 0.3) / Math.max(nodeImgH, 1);
        double targetZoom = Math.min(targetZoomW, targetZoomH);
        targetZoom = Math.max(targetZoom, zoom);
        targetZoom = Math.min(targetZoom, 8.0);

        zoom = targetZoom;
        panOffsetX = pw / 2.0 - nodeCenterImgX * zoom;
        panOffsetY = ph / 2.0 - nodeCenterImgY * zoom;
        updateZoomLabel();
    }

    /**
     * Pan to centre a node in the viewport without changing the current zoom level.
     * Used when navigating between search results.
     */
    private void panToNode(DiagramNode node) {
        if (image == null || diagram == null || baseW <= 0 || baseH <= 0) return;
        int pw = imagePanel.getWidth(), ph = imagePanel.getHeight();
        if (pw <= 0 || ph <= 0) return;

        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();

        double nodeCenterImgX = ((node.getX() + node.getWidth() / 2.0) - vbX) / vbW * baseW;
        double nodeCenterImgY = ((node.getY() + node.getHeight() / 2.0) - vbY) / vbH * baseH;

        panOffsetX = pw / 2.0 - nodeCenterImgX * zoom;
        panOffsetY = ph / 2.0 - nodeCenterImgY * zoom;
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigate to element (from outline drawer or external caller)
    // ═══════════════════════════════════════════════════════════

    /**
     * Navigate to a diagram element by label or ID.
     * Highlights the element and pans/zooms to it (like the first search hit).
     * Useful for synchronising with outline tree or drawer navigation.
     *
     * @param elementLabel the label or ID to find (case-insensitive partial match)
     * @return true if the element was found and navigated to
     */
    public boolean navigateToElement(String elementLabel) {
        if (elementLabel == null || elementLabel.trim().isEmpty()
                || diagram == null || image == null) {
            return false;
        }
        String q = elementLabel.trim().toLowerCase();

        // Exact ID match first, then exact label match, then partial
        DiagramNode best = null;
        DiagramNode partialMatch = null;
        for (DiagramNode n : diagram.getNodes()) {
            if (n instanceof SequenceFragment) continue;
            String id = n.getId() != null ? n.getId().toLowerCase() : "";
            String label = n.getLabel() != null ? n.getLabel().toLowerCase() : "";
            if (id.equals(q) || label.equals(q)) {
                best = n;
                break;
            }
            if (partialMatch == null && (id.contains(q) || label.contains(q))) {
                partialMatch = n;
            }
        }
        if (best == null) best = partialMatch;
        if (best == null) return false;

        selectedNode = best;
        selectedEdge = null;
        updateDetailArea();
        updateEditPanel();

        // Zoom to the element if it's far off-screen, otherwise just pan
        if (isNodeVisibleInViewport(best)) {
            panToNode(best);
        } else {
            zoomToNode(best);
        }

        imagePanel.repaint();
        if (nodeSelectionListener != null) nodeSelectionListener.onNodeSelected(best);
        return true;
    }

    /**
     * Check if a node is at least partially visible in the current viewport.
     */
    private boolean isNodeVisibleInViewport(DiagramNode node) {
        if (diagram == null || baseW <= 0 || baseH <= 0) return false;
        int pw = imagePanel.getWidth(), ph = imagePanel.getHeight();
        if (pw <= 0 || ph <= 0) return false;

        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();

        double imgX = (node.getX() + node.getWidth() / 2.0 - vbX) / vbW * baseW;
        double imgY = (node.getY() + node.getHeight() / 2.0 - vbY) / vbH * baseH;
        double screenX = imgX * zoom + panOffsetX;
        double screenY = imgY * zoom + panOffsetY;

        return screenX >= -50 && screenX <= pw + 50 && screenY >= -50 && screenY <= ph + 50;
    }

    /**
     * Get the currently selected node (may be null).
     */
    public DiagramNode getSelectedNode() {
        return selectedNode;
    }

    /**
     * Get the RenderedDiagram metadata (for external navigation).
     */
    public RenderedDiagram getDiagram() {
        return diagram;
    }

    /** Returns true if the diagram view has loaded content (image + diagram metadata). */
    public boolean hasDiagram() {
        return image != null && diagram != null;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }
}
