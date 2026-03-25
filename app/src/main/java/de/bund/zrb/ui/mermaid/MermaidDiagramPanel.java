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
import java.util.List;

/**
 * Reusable Swing panel that renders a Mermaid diagram as an interactive SVG image.
 * <p>
 * Features:
 * <ul>
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

    // Drag
    private DiagramNode dragSourceNode;
    private DiagramEdge dragEdge;
    private boolean dragFromSource;
    private Point dragStart;
    private Point dragCurrent;
    private boolean dragging;

    // UI
    private final JPanel imagePanel;
    private final JTextArea detailArea;
    private final JPanel editPanel;
    private final JLabel statusLabel;

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

        // ── Detail area ──
        detailArea = new JTextArea("Klicken Sie auf ein Element…");
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setRows(3);
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

        // ── Layout ──
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.add(new JScrollPane(detailArea));
        if (editable) {
            bottomPanel.add(editPanel);
        }
        bottomPanel.add(statusLabel);

        add(new JScrollPane(imagePanel), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
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
                imagePanel.repaint();
                if (error) {
                    statusLabel.setText("⚠ Rendering fehlgeschlagen");
                } else {
                    int nodes = diagram != null ? diagram.getNodes().size() : 0;
                    int edges = diagram != null ? diagram.getEdges().size() : 0;
                    statusLabel.setText(nodes + " Knoten, " + edges + " Kanten"
                            + (editable ? " — Klicken zum Bearbeiten" : ""));
                }
            }
        };
        worker.execute();
    }

    public String getMermaidSource() {
        return mermaidSource;
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

        int pw = imagePanel.getWidth(), ph = imagePanel.getHeight();
        int imgW = image.getWidth(), imgH = image.getHeight();
        double scale = Math.min((double) pw / imgW, (double) ph / imgH);
        int dw = (int) (imgW * scale), dh = (int) (imgH * scale);
        int ox = (pw - dw) / 2, oy = (ph - dh) / 2;

        g.drawImage(image, ox, oy, dw, dh, null);

        // Highlight
        if (highlightRect != null) {
            int hx = (int) (highlightRect.x * scale) + ox;
            int hy = (int) (highlightRect.y * scale) + oy;
            int hw = (int) (highlightRect.width * scale);
            int hh = (int) (highlightRect.height * scale);
            g.setColor(new Color(255, 255, 0, 100));
            g.fillRect(hx, hy, hw, hh);
            g.setColor(new Color(255, 180, 0, 200));
            g.setStroke(new BasicStroke(2));
            g.drawRect(hx, hy, hw, hh);
        }

        // Drag line
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
        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (image == null || diagram == null) return;
                double[] svgCoords = screenToSvg(e.getX(), e.getY());
                if (svgCoords == null) return;
                double svgX = svgCoords[0], svgY = svgCoords[1];

                // Parse viewBox for thresholds
                double vbW = image.getWidth(), vbH = image.getHeight();

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

                // ── Check nodes ──
                DiagramNode node = diagram.findNodeAt(svgX, svgY);
                if (node != null) {
                    selectedNode = node;
                    selectedEdge = null;
                    highlightRect = toImageRect(node.getX(), node.getY(),
                            node.getWidth(), node.getHeight());
                    if (editable) {
                        dragSourceNode = node;
                        dragStart = new Point(e.getX(), e.getY());
                        dragging = false;
                    }
                    updateDetailArea();
                    updateEditPanel();
                    imagePanel.repaint();
                    return;
                }

                // ── Check edges ──
                for (DiagramEdge edge : diagram.getEdges()) {
                    if (edge.containsApprox(svgX, svgY)) {
                        selectedEdge = edge;
                        selectedNode = null;
                        highlightRect = toImageRect(edge.getX(), edge.getY(),
                                edge.getWidth(), edge.getHeight());
                        updateDetailArea();
                        updateEditPanel();
                        imagePanel.repaint();
                        return;
                    }
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

    /** Convert screen coordinates to SVG viewBox coordinates. */
    private double[] screenToSvg(int sx, int sy) {
        if (image == null) return null;
        int pw = imagePanel.getWidth(), ph = imagePanel.getHeight();
        int imgW = image.getWidth(), imgH = image.getHeight();
        double scale = Math.min((double) pw / imgW, (double) ph / imgH);
        int dw = (int) (imgW * scale), dh = (int) (imgH * scale);
        int ox = (pw - dw) / 2, oy = (ph - dh) / 2;
        double svgX = (sx - ox) / scale;
        double svgY = (sy - oy) / scale;
        return new double[]{svgX, svgY};
    }

    private Rectangle toImageRect(double x, double y, double w, double h) {
        return new Rectangle((int) x, (int) y, (int) w, (int) h);
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
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        row.add(new JLabel("Name:"));
        final JTextField nameField = new JTextField(selectedNode.getLabel(), 20);
        row.add(nameField);
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
        row.add(applyBtn);
        editPanel.add(row);
    }

    private void buildEdgeEdit() {
        // Label edit
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        row.add(new JLabel("Label:"));
        String currentLabel = selectedEdge.getLabel() != null ? selectedEdge.getLabel() : "";
        final JTextField labelField = new JTextField(currentLabel, 20);
        row.add(labelField);
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
        row.add(applyBtn);
        editPanel.add(row);

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

        JButton deleteBtn = new JButton("🗑 Kante löschen");
        deleteBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String updated = SourceEditBridge.deleteEdge(mermaidSource,
                        detectDiagramType(), selectedEdge);
                applySourceChange(updated);
            }
        });
        actRow.add(deleteBtn);
        editPanel.add(actRow);
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
}

