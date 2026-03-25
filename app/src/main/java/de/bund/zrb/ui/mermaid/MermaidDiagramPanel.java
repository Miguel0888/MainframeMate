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
 *   <li>Mouse-wheel zoom (centered on cursor) + zoom toolbar</li>
 *   <li>Right-click drag to pan; fit-to-panel on first layout</li>
 *   <li>Click-to-select nodes and edges (yellow highlight overlay)</li>
 *   <li>Detail display of the selected element</li>
 *   <li>If editable: rename nodes, change labels — changes are applied to source and re-rendered</li>
 *   <li>Drag node→node to create a new edge (blue line)</li>
 *   <li>Drag edge endpoint→node to reconnect (orange line)</li>
 * </ul>
 * <p>
 * Used by {@link de.bund.zrb.ui.preview.SplitPreviewTab} to show an interactive
 * Mermaid diagram generated from JCL/COBOL/Natural outline models.
 */
public class MermaidDiagramPanel extends JPanel {

    private final boolean editable;

    // State
    private String mermaidSource;
    private BufferedImage image;
    private RenderedDiagram diagram;
    private String svg;
    private boolean renderError;

    // Selection
    private DiagramNode selectedNode;
    private DiagramEdge selectedEdge;
    private Rectangle highlightRect;

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
    private Point panDragStart;           // non-null while right-button dragging
    private boolean fitDone = false;      // true after first fit-to-panel
    private int baseW, baseH;             // image dimensions used as zoom reference

    // UI
    private final JPanel imagePanel;
    private final JTextArea detailArea;
    private final JPanel editPanel;
    private final JLabel statusLabel;
    private final JLabel zoomLabel;

    // Listener for source changes
    private SourceChangeListener sourceChangeListener;

    /** Listener notified when the Mermaid source is modified via diagram interaction. */
    public interface SourceChangeListener {
        void onSourceChanged(String newMermaidSource);
    }

    public MermaidDiagramPanel(boolean editable) {
        this.editable = editable;
        setLayout(new BorderLayout(0, 4));
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

        // ── Detail area ──
        detailArea = new JTextArea("Klicken Sie auf ein Element…");
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setRows(5);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        detailArea.setBackground(new Color(255, 255, 230));
        detailArea.setBorder(new EmptyBorder(4, 6, 4, 6));

        // ── Edit panel ──
        editPanel = new JPanel();
        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
        editPanel.setBackground(new Color(235, 255, 235));
        editPanel.setBorder(new EmptyBorder(4, 6, 4, 6));

        // ── Status label ──
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 10f));
        statusLabel.setForeground(Color.GRAY);

        // ── Zoom toolbar ──
        zoomLabel = new JLabel("100 %");
        zoomLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        JPanel zoomBar = buildZoomBar();

        // ── Right sidebar ──
        JPanel sidebar = new JPanel(new BorderLayout(0, 4));
        sidebar.setBackground(new Color(250, 250, 245));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(200, 200, 200)),
                new EmptyBorder(6, 6, 6, 6)));
        sidebar.setPreferredSize(new Dimension(280, 0));

        // Sidebar header
        JLabel sidebarTitle = new JLabel("  Details");
        sidebarTitle.setFont(sidebarTitle.getFont().deriveFont(Font.BOLD, 12f));
        sidebarTitle.setForeground(new Color(80, 80, 80));
        sidebarTitle.setBorder(new EmptyBorder(2, 0, 4, 0));
        sidebar.add(sidebarTitle, BorderLayout.NORTH);

        // Sidebar content (detail + edit + status)
        JPanel sidebarContent = new JPanel();
        sidebarContent.setLayout(new BoxLayout(sidebarContent, BoxLayout.Y_AXIS));
        sidebarContent.setBackground(new Color(250, 250, 245));

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        detailScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarContent.add(detailScroll);

        if (editable) {
            sidebarContent.add(Box.createVerticalStrut(6));
            editPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            editPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            sidebarContent.add(editPanel);
        }

        sidebarContent.add(Box.createVerticalGlue());
        sidebarContent.add(Box.createVerticalStrut(4));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarContent.add(statusLabel);

        sidebar.add(sidebarContent, BorderLayout.CENTER);

        // ── Toggle sidebar button ──
        final JButton toggleSidebar = new JButton("▶");
        toggleSidebar.setToolTipText("Seitenleiste ein-/ausblenden");
        toggleSidebar.setMargin(new Insets(2, 4, 2, 4));
        toggleSidebar.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        toggleSidebar.setFocusable(false);
        toggleSidebar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sidebar.setVisible(!sidebar.isVisible());
                toggleSidebar.setText(sidebar.isVisible() ? "▶" : "◀");
                toggleSidebar.setToolTipText(sidebar.isVisible()
                        ? "Seitenleiste ausblenden" : "Seitenleiste einblenden");
                revalidate();
                repaint();
            }
        });

        zoomBar.add(Box.createHorizontalStrut(12));
        zoomBar.add(toggleSidebar);

        // ── Layout ──
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(imagePanel, BorderLayout.CENTER);
        centerPanel.add(zoomBar, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);
        add(sidebar, BorderLayout.EAST);
    }

    public void setSourceChangeListener(SourceChangeListener listener) {
        this.sourceChangeListener = listener;
    }

    /**
     * Render (or re-render) the given Mermaid source code.
     */
    public void setMermaidSource(String source) {
        this.mermaidSource = source;
        this.selectedNode = null;
        this.selectedEdge = null;
        this.highlightRect = null;
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

        // Render in background to avoid blocking the EDT
        statusLabel.setText("Rendering…");

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
                    // Fit-to-panel when a new image is loaded
                    if (imagePanel.getWidth() > 0 && imagePanel.getHeight() > 0) {
                        fitToPanel();
                        fitDone = true;
                    } else {
                        fitDone = false; // will be done by component listener
                    }
                }

                imagePanel.repaint();
                updateZoomLabel();
                if (error) {
                    statusLabel.setText("⚠ Rendering fehlgeschlagen");
                } else {
                    int nodes = diagram != null ? diagram.getNodes().size() : 0;
                    int edges = diagram != null ? diagram.getEdges().size() : 0;
                    statusLabel.setText(nodes + " Knoten, " + edges + " Kanten"
                            + (editable ? " — Klicken zum Bearbeiten" : "")
                            + " — Mausrad: Zoom, Rechtsklick ziehen: Pan");
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

    /** Fit the diagram into the visible panel area. */
    private void fitToPanel() {
        if (image == null || baseW <= 0 || baseH <= 0) return;
        int pw = imagePanel.getWidth(), ph = imagePanel.getHeight();
        if (pw <= 0 || ph <= 0) return;
        double sx = (double) pw / baseW;
        double sy = (double) ph / baseH;
        zoom = Math.min(sx, sy) * 0.95; // 5 % margin
        double dw = baseW * zoom;
        double dh = baseH * zoom;
        panOffsetX = (pw - dw) / 2.0;
        panOffsetY = (ph - dh) / 2.0;
        updateZoomLabel();
        imagePanel.repaint();
    }

    private void zoomBy(double factor, double pivotX, double pivotY) {
        double oldZ = zoom;
        zoom *= factor;
        zoom = Math.max(0.02, Math.min(zoom, 50.0));
        panOffsetX = pivotX - (pivotX - panOffsetX) * (zoom / oldZ);
        panOffsetY = pivotY - (pivotY - panOffsetY) * (zoom / oldZ);
        updateZoomLabel();
        imagePanel.repaint();
    }

    private void updateZoomLabel() {
        int pct = (int) Math.round(zoom * 100);
        zoomLabel.setText(pct + " %");
    }

    private JPanel buildZoomBar() {
        JPanel zoomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        zoomBar.setBackground(new Color(240, 240, 240));

        JButton btnZoomIn = new JButton("+");
        JButton btnZoomOut = new JButton("\u2013"); // en-dash looks like minus
        JButton btnFit = new JButton("\uD83D\uDD04 Einpassen");
        btnZoomIn.setToolTipText("Vergrößern (Mausrad hoch)");
        btnZoomOut.setToolTipText("Verkleinern (Mausrad runter)");
        btnFit.setToolTipText("An Panel anpassen");
        btnZoomIn.setMargin(new Insets(2, 8, 2, 8));
        btnZoomOut.setMargin(new Insets(2, 8, 2, 8));
        btnFit.setMargin(new Insets(2, 6, 2, 6));
        btnZoomIn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        btnZoomOut.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        btnZoomIn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                double cx = imagePanel.getWidth() / 2.0;
                double cy = imagePanel.getHeight() / 2.0;
                zoomBy(1.4, cx, cy);
            }
        });
        btnZoomOut.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                double cx = imagePanel.getWidth() / 2.0;
                double cy = imagePanel.getHeight() / 2.0;
                zoomBy(1.0 / 1.4, cx, cy);
            }
        });
        btnFit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fitToPanel();
            }
        });

        zoomBar.add(btnZoomOut);
        zoomBar.add(zoomLabel);
        zoomBar.add(btnZoomIn);
        zoomBar.add(Box.createHorizontalStrut(12));
        zoomBar.add(btnFit);

        // ── Re-render button (re-rasterize SVG at current zoom for crisp image) ──
        zoomBar.add(Box.createHorizontalStrut(12));
        JButton btnReRender = new JButton("\uD83D\uDD04 Neu rendern");
        btnReRender.setToolTipText("SVG bei aktuellem Zoom-Level neu rastern (schärferes Bild)");
        btnReRender.setMargin(new Insets(2, 6, 2, 6));
        btnReRender.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reRasterize();
            }
        });
        zoomBar.add(btnReRender);

        zoomBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return zoomBar;
    }

    /**
     * Re-rasterize the existing SVG at a resolution that matches the current zoom level.
     * This avoids running the Mermaid→SVG pipeline again — only the Batik rasterization
     * is repeated at a higher pixel width, resulting in a crisp image at the current zoom.
     */
    private void reRasterize() {
        if (svg == null || svg.isEmpty()) {
            statusLabel.setText("⚠ Kein SVG zum Neu-Rendern vorhanden");
            return;
        }
        statusLabel.setText("Neu rendern…");

        final String currentSvg = this.svg;
        final double currentZoom = this.zoom;
        final double currentPanX = this.panOffsetX;
        final double currentPanY = this.panOffsetY;

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                try {
                    // Compute the desired pixel width: baseW × zoom, plus some headroom
                    float targetWidth = (float) (baseW * Math.max(currentZoom, 1.0) * 1.5);
                    targetWidth = Math.max(targetWidth, 800);
                    targetWidth = Math.min(targetWidth, 16000); // cap to avoid OOM
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
                        // Scale factor between old and new image
                        double scaleRatio = (double) newImage.getWidth() / baseW;
                        image = newImage;
                        baseW = newImage.getWidth();
                        baseH = newImage.getHeight();
                        // Adjust zoom + pan so the view stays the same
                        zoom = currentZoom / scaleRatio;
                        panOffsetX = currentPanX;
                        panOffsetY = currentPanY;
                        updateZoomLabel();
                        imagePanel.repaint();
                        statusLabel.setText("✓ Neu gerendert (" + baseW + "×" + baseH + " px)");
                    } else {
                        statusLabel.setText("⚠ Neu-Rendern fehlgeschlagen");
                    }
                } catch (Exception e) {
                    statusLabel.setText("⚠ Neu-Rendern fehlgeschlagen");
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
            String msg = renderError ? "⚠ Rendering fehlgeschlagen" : "Kein Diagramm geladen";
            g.drawString(msg, 20, 30);
            return;
        }

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Draw image using zoom + pan transform
        int dw = (int) Math.round(baseW * zoom);
        int dh = (int) Math.round(baseH * zoom);
        int ox = (int) Math.round(panOffsetX);
        int oy = (int) Math.round(panOffsetY);
        g.drawImage(image, ox, oy, dw, dh, null);

        // Highlight selected element
        if (highlightRect != null) {
            int hx = (int) (highlightRect.x * zoom) + ox;
            int hy = (int) (highlightRect.y * zoom) + oy;
            int hw = (int) (highlightRect.width * zoom);
            int hh = (int) (highlightRect.height * zoom);
            g.setColor(new Color(255, 255, 0, 100));
            g.fillRect(hx, hy, hw, hh);
            g.setColor(new Color(255, 180, 0, 200));
            g.setStroke(new BasicStroke(2));
            g.drawRect(hx, hy, hw, hh);
        }

        // Drag line (edge creation / reconnection)
        if (dragging && dragStart != null && dragCurrent != null) {
            Color lineColor = dragEdge != null
                    ? new Color(255, 140, 0, 200)   // orange = reconnection
                    : new Color(0, 100, 255, 180);   // blue = new edge
            g.setColor(lineColor);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0));
            g.drawLine(dragStart.x, dragStart.y, dragCurrent.x, dragCurrent.y);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Mouse interaction
    // ═══════════════════════════════════════════════════════════

    private void setupMouseHandlers() {

        // ── Mouse-wheel zoom (centered on cursor) ──
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
                // ── Right-click (or Ctrl+click) → start panning ──
                if (SwingUtilities.isRightMouseButton(e)
                        || (e.getModifiers() & InputEvent.CTRL_MASK) != 0) {
                    panDragStart = new Point(e.getX(), e.getY());
                    imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }

                if (image == null || diagram == null) return;
                double[] svgCoords = screenToSvg(e.getX(), e.getY());
                if (svgCoords == null) return;
                double svgX = svgCoords[0], svgY = svgCoords[1];

                // Use viewBox dimensions for thresholds (SVG coordinate space)
                double vbW = diagram.getViewBoxWidth();
                double vbH = diagram.getViewBoxHeight();

                // ── Check edge endpoints for reconnection (editable only) ──
                dragEdge = null;
                if (editable) {
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
                }

                // ── Check nodes (smallest non-fragment first, then smallest fragment) ──
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
                if (foundNode == null) foundNode = foundFragment;
                if (foundNode != null) {
                    selectedNode = foundNode;
                    selectedEdge = null;
                    highlightRect = toImageRect(foundNode.getX(), foundNode.getY(),
                            foundNode.getWidth(), foundNode.getHeight());
                    if (editable) {
                        dragSourceNode = foundNode;
                        dragStart = new Point(e.getX(), e.getY());
                        dragging = false;
                    }
                    updateDetailArea();
                    updateEditPanel();
                    imagePanel.repaint();
                    return;
                }

                // ── Check edges (prefer smallest bounding box) ──
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
                    highlightRect = toImageRect(foundEdge.getX(), foundEdge.getY(),
                            foundEdge.getWidth(), foundEdge.getHeight());
                    updateDetailArea();
                    updateEditPanel();
                    imagePanel.repaint();
                    return;
                }

                // Deselect
                selectedNode = null;
                selectedEdge = null;
                highlightRect = null;
                updateDetailArea();
                updateEditPanel();
                imagePanel.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // ── End panning ──
                if (panDragStart != null) {
                    panDragStart = null;
                    imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    return;
                }

                if (!editable || image == null || diagram == null) return;

                if (dragging) {
                    double[] svgCoords = screenToSvg(e.getX(), e.getY());
                    if (svgCoords != null) {
                        DiagramNode targetNode = diagram.findNodeAt(svgCoords[0], svgCoords[1]);
                        if (targetNode != null) {
                            if (dragEdge != null) {
                                // Reconnect edge
                                handleReconnect(targetNode);
                            } else if (dragSourceNode != null
                                    && !dragSourceNode.getId().equals(targetNode.getId())) {
                                // Create new edge
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
        });

        imagePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // ── Pan with right-button / Ctrl ──
                if (panDragStart != null) {
                    panOffsetX += e.getX() - panDragStart.x;
                    panOffsetY += e.getY() - panDragStart.y;
                    panDragStart = new Point(e.getX(), e.getY());
                    imagePanel.repaint();
                    return;
                }

                // ── Edge drag (editable only) ──
                if (!editable) return;
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
        });
    }

    /**
     * Convert screen (panel) coordinates to SVG viewBox coordinates.
     * <p>
     * Screen → image pixel → SVG viewBox, matching the approach used in
     * {@code MermaidSelectionTest}.
     */
    private double[] screenToSvg(int sx, int sy) {
        if (image == null || diagram == null || zoom <= 0 || baseW <= 0 || baseH <= 0) return null;
        // Screen → image pixel
        double imgPx = (sx - panOffsetX) / zoom;
        double imgPy = (sy - panOffsetY) / zoom;
        // Image pixel → SVG viewBox
        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();
        double svgX = vbX + (imgPx / baseW) * vbW;
        double svgY = vbY + (imgPy / baseH) * vbH;
        return new double[]{svgX, svgY};
    }

    /**
     * Convert SVG viewBox coordinates to image pixel coordinates (for highlight overlay).
     * Matches the formula used in {@code MermaidSelectionTest#handleClick}.
     */
    private Rectangle toImageRect(double svgX, double svgY, double svgW, double svgH) {
        if (diagram == null || baseW <= 0 || baseH <= 0) {
            return new Rectangle((int) svgX, (int) svgY, (int) svgW, (int) svgH);
        }
        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();
        int rx = (int) ((svgX - vbX) / vbW * baseW);
        int ry = (int) ((svgY - vbY) / vbH * baseH);
        int rw = (int) (svgW / vbW * baseW);
        int rh = (int) (svgH / vbH * baseH);
        return new Rectangle(rx, ry, Math.max(rw, 5), Math.max(rh, 5));
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
                    + "\nGröße: " + (int) n.getWidth() + " × " + (int) n.getHeight());
        } else if (selectedEdge != null) {
            DiagramEdge e = selectedEdge;
            detailArea.setText("Kante: " + e.getSourceId() + " → " + e.getTargetId()
                    + "\nLabel: " + (e.getLabel() != null ? e.getLabel() : "—")
                    + "\nTyp: " + e.getClass().getSimpleName());
        } else {
            detailArea.setText("Klicken Sie auf ein Element…");
        }
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
            JLabel hint = new JLabel("Kein Element ausgewählt");
            hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
            hint.setForeground(Color.GRAY);
            editPanel.add(hint);
        }

        editPanel.revalidate();
        editPanel.repaint();
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

        // Label edit
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

        // Reverse & delete
        JPanel actRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        actRow.setOpaque(false);
        JButton reverseBtn = new JButton("⇄ Umkehren");
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
        JButton deleteBtn = new JButton("\uD83D\uDDD1 Kante löschen");
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

    /**
     * Search for nodes whose label or id contains the given query (case-insensitive).
     * Highlights the first match with a yellow overlay and zooms/pans to center on it.
     *
     * @param query the search text
     * @return the number of matches found
     */
    public int searchAndHighlight(String query) {
        if (query == null || query.trim().isEmpty() || diagram == null) {
            clearSearch();
            return 0;
        }

        String q = query.trim().toLowerCase();

        // Rebuild search results if query changed
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
        }

        if (lastSearchResults.isEmpty()) {
            highlightRect = null;
            selectedNode = null;
            selectedEdge = null;
            statusLabel.setText("Keine Treffer für \"" + query.trim() + "\"");
            imagePanel.repaint();
            return 0;
        }

        // Advance to next result
        lastSearchIndex = (lastSearchIndex + 1) % lastSearchResults.size();
        DiagramNode node = lastSearchResults.get(lastSearchIndex);

        // Select and highlight
        selectedNode = node;
        selectedEdge = null;
        highlightRect = toImageRect(node.getX(), node.getY(),
                node.getWidth(), node.getHeight());
        updateDetailArea();
        updateEditPanel();

        // Zoom + pan to center on the found node
        zoomToNode(node);

        statusLabel.setText("Treffer " + (lastSearchIndex + 1) + " / " + lastSearchResults.size()
                + " — \"" + (node.getLabel() != null ? node.getLabel() : node.getId()) + "\"");
        imagePanel.repaint();
        return lastSearchResults.size();
    }

    /**
     * Advance to the next search result (wraps around).
     * @return the number of total matches, or 0 if no search is active
     */
    public int searchNext() {
        if (lastSearchResults.isEmpty()) return 0;
        return searchAndHighlight(lastSearchQuery);
    }

    /**
     * Go to the previous search result (wraps around).
     * @return the number of total matches, or 0 if no search is active
     */
    public int searchPrev() {
        if (lastSearchResults.isEmpty()) return 0;
        // Move index back by 2 (because searchAndHighlight will advance +1)
        lastSearchIndex -= 2;
        if (lastSearchIndex < -1) lastSearchIndex = lastSearchResults.size() - 2;
        return searchAndHighlight(lastSearchQuery);
    }

    /** Clear search state and remove highlight. */
    public void clearSearch() {
        lastSearchResults = new ArrayList<DiagramNode>();
        lastSearchIndex = -1;
        lastSearchQuery = "";
    }

    /**
     * Zoom and pan so the given node is centered and reasonably large in the viewport.
     */
    private void zoomToNode(DiagramNode node) {
        if (image == null || diagram == null || baseW <= 0 || baseH <= 0) return;
        int pw = imagePanel.getWidth(), ph = imagePanel.getHeight();
        if (pw <= 0 || ph <= 0) return;

        // Convert node center from SVG viewBox → image pixel coords
        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();

        double nodeCenterImgX = ((node.getX() + node.getWidth() / 2.0) - vbX) / vbW * baseW;
        double nodeCenterImgY = ((node.getY() + node.getHeight() / 2.0) - vbY) / vbH * baseH;
        double nodeImgW = node.getWidth() / vbW * baseW;
        double nodeImgH = node.getHeight() / vbH * baseH;

        // Target zoom: node should fill ~30% of the viewport (but don't zoom out)
        double targetZoomW = (pw * 0.3) / Math.max(nodeImgW, 1);
        double targetZoomH = (ph * 0.3) / Math.max(nodeImgH, 1);
        double targetZoom = Math.min(targetZoomW, targetZoomH);
        // Don't zoom out below current level or below fit level, and cap max
        targetZoom = Math.max(targetZoom, zoom);
        targetZoom = Math.min(targetZoom, 8.0);

        // Set zoom and center pan on the node
        zoom = targetZoom;
        panOffsetX = pw / 2.0 - nodeCenterImgX * zoom;
        panOffsetY = ph / 2.0 - nodeCenterImgY * zoom;
        updateZoomLabel();
    }

    /** Returns true if the diagram view has loaded content (image + diagram metadata). */
    public boolean hasDiagram() {
        return image != null && diagram != null;
    }
}

