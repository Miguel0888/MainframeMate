package de.bund.zrb.mermaid;

import com.aresstack.mermaid.layout.*;
import de.bund.zrb.wiki.ui.SvgRenderer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive visual selection &amp; editing test for the mermaid-java layout + highlighting API.
 * <p>
 * Each test case shows a rendered diagram that the tester can click on.
 * The system identifies the clicked element using {@link RenderedDiagram#findNodeAt}
 * and {@link DiagramEdge#containsApprox}, then displays its polymorphic type and
 * properties in a detail panel.  A yellow overlay highlights the selection.
 * <p>
 * Below the detail panel, a <b>polymorphic edit panel</b> allows modifying the
 * selected element (rename, shape change, class member editing, etc.).
 * Changes are applied to the Mermaid source and the diagram is re-rendered.
 */
public final class MermaidSelectionTest {

    private MermaidSelectionTest() {}

    // ═══════════════════════════════════════════════════════════
    //  Data model
    // ═══════════════════════════════════════════════════════════

    private static final class SelectionSpec {
        final String id, title, task, mermaidCode;

        SelectionSpec(String id, String title, String task, String mermaidCode) {
            this.id = id;
            this.title = title;
            this.task = task;
            this.mermaidCode = mermaidCode;
        }
    }

    /** Mutable rendering state for one card — supports re-rendering after edits. */
    private static final class CardState {
        final SelectionSpec spec;
        String currentSource;
        BufferedImage image;
        String svg;
        RenderedDiagram diagram;
        boolean renderError, rasterError;

        // Currently selected element
        DiagramNode selectedNode;
        DiagramEdge selectedEdge;

        // Drag state for edge creation / reconnection
        DiagramNode dragSourceNode;
        Point dragStart;
        Point dragCurrent;
        boolean dragging;

        // Edge reconnection drag state
        DiagramEdge dragEdge;
        boolean dragFromSource; // true = dragging source end, false = dragging target end

        CardState(SelectionSpec spec) {
            this.spec = spec;
            this.currentSource = spec.mermaidCode;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Test-case catalogue
    // ═══════════════════════════════════════════════════════════

    private static List<SelectionSpec> buildSpecs() {
        List<SelectionSpec> s = new ArrayList<SelectionSpec>();

        // ── 1. Flowchart: Node shapes ──
        s.add(new SelectionSpec("fc-shapes",
                "1 — Flowchart: Knotenformen selektieren",
                "Klicken Sie auf Knoten. Im Edit-Panel können Sie\n"
                + "den Namen ändern. Das Diagramm wird neu gerendert.",
                "graph LR\n"
                + "    Rechteck[Rechteck] --> Rund([Rund])\n"
                + "    Rund --> Kreis((Kreis))\n"
                + "    Kreis --> Raute{Raute}\n"
                + "    Raute --> Sechseck{{Sechseck}}\n"
                + "    Sechseck --> Trapez[/Trapez/]"));

        // ── 2. Flowchart: Edge styles ──
        s.add(new SelectionSpec("fc-edges",
                "2 — Flowchart: Kantenstile selektieren",
                "Klicken Sie auf die Linien zwischen den Knoten.\n"
                + "Im Edit-Panel können Sie das Label ändern.",
                "graph LR\n"
                + "    Alpha --> Delta\n"
                + "    Beta -.->|dashed| Echo\n"
                + "    Gamma ==>|thick| Foxtrot"));

        // ── 3. Class diagram ──
        s.add(new SelectionSpec("class-sel",
                "3 — Klassendiagramm: Klassen und Relationen",
                "Klicken Sie auf Klassen. Im Edit-Panel können Sie\n"
                + "den Namen ändern sowie Felder und Methoden hinzufügen/entfernen.",
                "classDiagram\n"
                + "    class Animal {\n"
                + "        <<abstract>>\n"
                + "        +String name\n"
                + "        +int age\n"
                + "        +speak() void\n"
                + "    }\n"
                + "    class Dog {\n"
                + "        +fetch() void\n"
                + "    }\n"
                + "    class Cat {\n"
                + "        +purr() void\n"
                + "    }\n"
                + "    Animal <|-- Dog\n"
                + "    Animal <|-- Cat"));

        // ── 4. Sequence diagram ──
        s.add(new SelectionSpec("seq-sel",
                "4 — Sequenzdiagramm: Akteure und Nachrichten",
                "Klicken Sie auf Actor-Boxen (oben oder unten).\n"
                + "Im Edit-Panel können Sie den Akteur umbenennen.",
                "sequenceDiagram\n"
                + "    participant Chef\n"
                + "    participant Alice\n"
                + "    participant Bob\n"
                + "    Chef->>Alice: Agenda\n"
                + "    Chef->>Bob: Agenda\n"
                + "    loop Diskussion\n"
                + "        Alice->>Bob: Vorschlag\n"
                + "        Bob-->>Alice: Feedback\n"
                + "    end\n"
                + "    Alice->>Chef: Ergebnis"));

        // ── 5. ER diagram ──
        s.add(new SelectionSpec("er-sel",
                "5 — ER-Diagramm: Entitäten und Beziehungen",
                "Klicken Sie auf Entitäten.\n"
                + "Im Edit-Panel können Sie den Entity-Namen ändern.",
                "erDiagram\n"
                + "    BUCH {\n"
                + "        string isbn PK\n"
                + "        string titel\n"
                + "        int jahr\n"
                + "    }\n"
                + "    AUTOR {\n"
                + "        int id PK\n"
                + "        string name\n"
                + "    }\n"
                + "    VERLAG {\n"
                + "        int id PK\n"
                + "        string name\n"
                + "    }\n"
                + "    AUTOR ||--o{ BUCH : schreibt\n"
                + "    VERLAG ||--o{ BUCH : verlegt"));

        // ── 6. State diagram ──
        s.add(new SelectionSpec("state-sel",
                "6 — Zustandsdiagramm: Zustände selektieren",
                "Klicken Sie auf Zustände. Im Edit-Panel\n"
                + "können Sie den Zustandsnamen ändern.",
                "stateDiagram-v2\n"
                + "    [*] --> Rot\n"
                + "    Rot --> Rot_Gelb : warten\n"
                + "    Rot_Gelb --> Gruen\n"
                + "    Gruen --> Gelb\n"
                + "    Gelb --> Rot"));

        // ── 7. Mindmap ──
        s.add(new SelectionSpec("mm-sel",
                "7 — Mindmap: Knoten in der Baumstruktur",
                "Klicken Sie auf Mindmap-Knoten.\n"
                + "Im Edit-Panel können Sie den Knotennamen ändern.",
                "mindmap\n"
                + "  root((Humus))\n"
                + "    Arten\n"
                + "      Naehrhumus\n"
                + "      Dauerhumus\n"
                + "    Entstehung\n"
                + "      Zersetzung\n"
                + "      Bodenorganismen\n"
                + "    Bestandteile\n"
                + "      Organische Substanz\n"
                + "      Mineralstoffe"));

        return s;
    }

    // ═══════════════════════════════════════════════════════════
    //  main — render, then show interactive UI
    // ═══════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.err.println("[MermaidSelectionTest] Initialising renderer...");
        final MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.err.println("mermaid.min.js not found on classpath");
            System.exit(1);
            return;
        }

        List<SelectionSpec> specs = buildSpecs();
        final List<CardState> cards = new ArrayList<CardState>();

        for (SelectionSpec spec : specs) {
            CardState card = new CardState(spec);
            renderCard(renderer, card);
            cards.add(card);
        }

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { showDialog(cards, renderer); }
        });
    }

    /** Render (or re-render) a single card from its currentSource. */
    private static void renderCard(MermaidRenderer renderer, CardState card) {
        System.err.println("[MermaidSelectionTest] Rendering: " + card.spec.title);
        card.renderError = false;
        card.rasterError = false;
        card.selectedNode = null;
        card.selectedEdge = null;

        String svg = null;
        try {
            svg = renderer.renderToSvg(card.currentSource);
            card.renderError = (svg == null || !svg.contains("<svg"));
        } catch (Exception e) {
            System.err.println("[MermaidSelectionTest] Render error: " + e);
            card.renderError = true;
        }
        if (card.renderError) {
            card.image = null;
            card.svg = null;
            card.diagram = null;
            return;
        }

        card.diagram = DiagramLayoutExtractor.extract(svg);
        svg = MermaidSvgFixup.fixForBatik(svg, card.currentSource);
        card.svg = svg;

        try {
            byte[] svgBytes = svg.getBytes("UTF-8");
            card.image = SvgRenderer.renderToBufferedImage(svgBytes);
        } catch (Exception e) {
            System.err.println("[MermaidSelectionTest] Raster error: " + e);
            card.image = null;
        }
        if (card.image == null) card.rasterError = true;
    }

    // ═══════════════════════════════════════════════════════════
    //  Swing dialog — interactive selection + editing UI
    // ═══════════════════════════════════════════════════════════

    private static void showDialog(final List<CardState> cards, final MermaidRenderer renderer) {
        final JFrame frame = new JFrame("Mermaid Selection Test — Selektion & Bearbeitung");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(0, 0));

        // ── TOP BAR ──
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        topBar.setBackground(new Color(40, 40, 40));

        JLabel titleLabel = new JLabel("  \u2699 Mermaid Diagramm-Editor  ");
        titleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        titleLabel.setForeground(new Color(100, 200, 255));
        topBar.add(titleLabel, BorderLayout.WEST);

        JLabel hintLabel = new JLabel("Klicken Sie auf Diagramm-Elemente, dann bearbeiten Sie sie im Edit-Panel");
        hintLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        hintLabel.setForeground(new Color(200, 200, 200));
        hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topBar.add(hintLabel, BorderLayout.CENTER);

        frame.add(topBar, BorderLayout.NORTH);

        // ── CENTER: card row ──
        JPanel cardRow = new JPanel();
        cardRow.setLayout(new BoxLayout(cardRow, BoxLayout.X_AXIS));
        cardRow.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (int idx = 0; idx < cards.size(); idx++) {
            final CardState cs = cards.get(idx);

            final JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(100, 100, 100), 2),
                            cs.spec.title,
                            TitledBorder.CENTER, TitledBorder.TOP,
                            new Font(Font.SANS_SERIF, Font.BOLD, 14)),
                    BorderFactory.createEmptyBorder(6, 8, 8, 8)));
            card.setBackground(Color.WHITE);

            // ── Task instructions ──
            JTextArea taskArea = new JTextArea("AUFGABE:\n" + cs.spec.task);
            taskArea.setEditable(false);
            taskArea.setLineWrap(true);
            taskArea.setWrapStyleWord(true);
            taskArea.setRows(3);
            taskArea.setBackground(new Color(220, 240, 255));
            taskArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            taskArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(150, 180, 220)),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            taskArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            card.add(taskArea);
            card.add(Box.createVerticalStrut(4));

            // ── Detail panel for selected element ──
            final JTextArea detailArea = new JTextArea("Klicken Sie auf ein Element im Diagramm...");
            detailArea.setEditable(false);
            detailArea.setLineWrap(true);
            detailArea.setWrapStyleWord(true);
            detailArea.setRows(7);
            detailArea.setBackground(new Color(255, 255, 230));
            detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            detailArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(200, 200, 100)),
                            "Selektiertes Element",
                            TitledBorder.LEFT, TitledBorder.TOP,
                            new Font(Font.SANS_SERIF, Font.BOLD, 11)),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            detailArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

            // ── Edit panel (will be populated on selection) ──
            final JPanel editPanel = new JPanel();
            editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
            editPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(80, 160, 80)),
                            "\u270E Bearbeitung",
                            TitledBorder.LEFT, TitledBorder.TOP,
                            new Font(Font.SANS_SERIF, Font.BOLD, 11)),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            editPanel.setBackground(new Color(235, 255, 235));
            editPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 350));
            JLabel noSelLabel = new JLabel("Kein Element ausgewählt");
            noSelLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
            noSelLabel.setForeground(Color.GRAY);
            editPanel.add(noSelLabel);

            // ── Mermaid source panel ──
            final JTextArea sourceArea = new JTextArea(cs.currentSource);
            sourceArea.setEditable(false);
            sourceArea.setLineWrap(false);
            sourceArea.setRows(5);
            sourceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            sourceArea.setBackground(new Color(245, 245, 245));
            JScrollPane sourceScroll = new JScrollPane(sourceArea);
            sourceScroll.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(180, 180, 180)),
                            "Mermaid-Quelltext",
                            TitledBorder.LEFT, TitledBorder.TOP,
                            new Font(Font.SANS_SERIF, Font.BOLD, 10)),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            sourceScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

            // ── Diagram panel container (swappable) ──
            final JPanel diagramContainer = new JPanel(new BorderLayout());
            diagramContainer.setPreferredSize(new Dimension(500, 350));
            diagramContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

            // Selection state
            final Rectangle[] highlightRect = {null};

            /** Callback to rebuild the diagram panel content */
            final Runnable[] rebuildDiagram = new Runnable[1];

            /** Callback to refresh edit panel after selection */
            final Runnable[] refreshEditPanel = new Runnable[1];

            refreshEditPanel[0] = new Runnable() {
                @Override public void run() {
                    editPanel.removeAll();

                    if (cs.selectedNode != null) {
                        buildNodeEditPanel(editPanel, cs, renderer, new Runnable() {
                            @Override public void run() {
                                renderCard(renderer, cs);
                                sourceArea.setText(cs.currentSource);
                                highlightRect[0] = null;
                                detailArea.setText("Diagramm neu gerendert.\nKlicken Sie erneut, um ein Element zu selektieren.");
                                rebuildDiagram[0].run();
                                editPanel.removeAll();
                                JLabel done = new JLabel("\u2705 Änderung angewendet — neu gerendert");
                                done.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                                done.setForeground(new Color(0, 120, 0));
                                editPanel.add(done);
                                editPanel.revalidate();
                                editPanel.repaint();
                            }
                        });
                    } else if (cs.selectedEdge != null) {
                        buildEdgeEditPanel(editPanel, cs, renderer, new Runnable() {
                            @Override public void run() {
                                renderCard(renderer, cs);
                                sourceArea.setText(cs.currentSource);
                                highlightRect[0] = null;
                                detailArea.setText("Diagramm neu gerendert.\nKlicken Sie erneut, um ein Element zu selektieren.");
                                rebuildDiagram[0].run();
                                editPanel.removeAll();
                                JLabel done = new JLabel("\u2705 Änderung angewendet — neu gerendert");
                                done.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                                done.setForeground(new Color(0, 120, 0));
                                editPanel.add(done);
                                editPanel.revalidate();
                                editPanel.repaint();
                            }
                        });
                    } else {
                        JLabel noSel = new JLabel("Kein Element ausgewählt");
                        noSel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
                        noSel.setForeground(Color.GRAY);
                        editPanel.add(noSel);
                    }
                    editPanel.revalidate();
                    editPanel.repaint();
                }
            };

            rebuildDiagram[0] = new Runnable() {
                @Override public void run() {
                    diagramContainer.removeAll();

                    if (cs.image != null && cs.diagram != null) {
                        final BufferedImage baseImg = cs.image;
                        final RenderedDiagram diagram = cs.diagram;
                        final int imgW = baseImg.getWidth();
                        final int imgH = baseImg.getHeight();

                        final Color HL_COLOR = new Color(255, 255, 0, 100);
                        final Color HL_BORDER = new Color(255, 180, 0, 200);

                        final JPanel dPanel = new JPanel() {
                            @Override protected void paintComponent(Graphics g) {
                                super.paintComponent(g);
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                int pw = getWidth(), ph = getHeight();
                                double scale = Math.min((double) pw / imgW, (double) ph / imgH);
                                int dw = (int)(imgW * scale), dh = (int)(imgH * scale);
                                int ox = (pw - dw) / 2, oy = (ph - dh) / 2;
                                g2.drawImage(baseImg, ox, oy, dw, dh, null);

                                if (highlightRect[0] != null) {
                                    Rectangle r = highlightRect[0];
                                    int hx = (int)(r.x * scale) + ox;
                                    int hy = (int)(r.y * scale) + oy;
                                    int hw = (int)(r.width * scale);
                                    int hh = (int)(r.height * scale);
                                    g2.setColor(HL_COLOR);
                                    g2.fillRect(hx, hy, hw, hh);
                                    g2.setColor(HL_BORDER);
                                    g2.setStroke(new BasicStroke(2));
                                    g2.drawRect(hx, hy, hw, hh);
                                }

                                // Draw drag line for edge creation / reconnection
                                if (cs.dragging && cs.dragStart != null && cs.dragCurrent != null) {
                                    Color lineColor = cs.dragEdge != null
                                            ? new Color(255, 140, 0, 200)  // orange for reconnection
                                            : new Color(0, 100, 255, 180); // blue for new edge
                                    g2.setColor(lineColor);
                                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND,
                                            BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0));
                                    g2.drawLine(cs.dragStart.x, cs.dragStart.y,
                                            cs.dragCurrent.x, cs.dragCurrent.y);
                                    // Arrow head at current position
                                    drawArrowHead(g2, cs.dragStart, cs.dragCurrent);
                                }
                                g2.dispose();
                            }
                        };
                        dPanel.setBackground(Color.WHITE);
                        dPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

                        // ── Mouse press: start drag or prepare for click ──
                        dPanel.addMouseListener(new MouseAdapter() {
                            @Override public void mousePressed(MouseEvent e) {
                                int pw = dPanel.getWidth(), ph = dPanel.getHeight();
                                double scale = Math.min((double) pw / imgW, (double) ph / imgH);
                                int dw = (int)(imgW * scale), dh = (int)(imgH * scale);
                                int ox = (pw - dw) / 2, oy = (ph - dh) / 2;
                                double imgPx = (e.getX() - ox) / scale;
                                double imgPy = (e.getY() - oy) / scale;
                                double vbX = diagram.getViewBoxX();
                                double vbY = diagram.getViewBoxY();
                                double vbW = diagram.getViewBoxWidth();
                                double vbH = diagram.getViewBoxHeight();
                                double svgX = vbX + (imgPx / imgW) * vbW;
                                double svgY = vbY + (imgPy / imgH) * vbH;

                                // Check if pressing near an edge endpoint for reconnection
                                cs.dragEdge = null;
                                if (cs.selectedEdge != null) {
                                    double edgeThreshold = Math.max(vbW, vbH) * 0.04;
                                    DiagramNode srcNode = diagram.findNodeById(cs.selectedEdge.getSourceId());
                                    DiagramNode tgtNode = diagram.findNodeById(cs.selectedEdge.getTargetId());
                                    if (srcNode != null) {
                                        double dx = svgX - srcNode.getCenterX();
                                        double dy = svgY - srcNode.getCenterY();
                                        if (Math.sqrt(dx * dx + dy * dy) < edgeThreshold) {
                                            cs.dragEdge = cs.selectedEdge;
                                            cs.dragFromSource = true;
                                            cs.dragStart = new Point(e.getX(), e.getY());
                                            cs.dragging = false;
                                            cs.dragSourceNode = null;
                                            return;
                                        }
                                    }
                                    if (tgtNode != null) {
                                        double dx = svgX - tgtNode.getCenterX();
                                        double dy = svgY - tgtNode.getCenterY();
                                        if (Math.sqrt(dx * dx + dy * dy) < edgeThreshold) {
                                            cs.dragEdge = cs.selectedEdge;
                                            cs.dragFromSource = false;
                                            cs.dragStart = new Point(e.getX(), e.getY());
                                            cs.dragging = false;
                                            cs.dragSourceNode = null;
                                            return;
                                        }
                                    }
                                }

                                DiagramNode nodeAtPress = diagram.findNodeAt(svgX, svgY);
                                if (nodeAtPress != null && !(nodeAtPress instanceof SequenceFragment)) {
                                    cs.dragSourceNode = nodeAtPress;
                                    cs.dragStart = new Point(e.getX(), e.getY());
                                    cs.dragging = false; // will become true on drag
                                } else {
                                    cs.dragSourceNode = null;
                                    cs.dragStart = null;
                                    cs.dragging = false;
                                }
                            }

                            @Override public void mouseReleased(MouseEvent e) {
                                // Handle edge reconnection drag
                                if (cs.dragging && cs.dragEdge != null) {
                                    int pw = dPanel.getWidth(), ph = dPanel.getHeight();
                                    double scale = Math.min((double) pw / imgW, (double) ph / imgH);
                                    int dw = (int)(imgW * scale), dh = (int)(imgH * scale);
                                    int ox = (pw - dw) / 2, oy = (ph - dh) / 2;
                                    double imgPx = (e.getX() - ox) / scale;
                                    double imgPy = (e.getY() - oy) / scale;
                                    double vbX = diagram.getViewBoxX();
                                    double vbY = diagram.getViewBoxY();
                                    double vbW = diagram.getViewBoxWidth();
                                    double vbH = diagram.getViewBoxHeight();
                                    double svgX = vbX + (imgPx / imgW) * vbW;
                                    double svgY = vbY + (imgPy / imgH) * vbH;
                                    DiagramNode targetNode = diagram.findNodeAt(svgX, svgY);

                                    if (targetNode != null && !(targetNode instanceof SequenceFragment)) {
                                        String newSrc = cs.dragFromSource ? targetNode.getId() : cs.dragEdge.getSourceId();
                                        String newTgt = cs.dragFromSource ? cs.dragEdge.getTargetId() : targetNode.getId();
                                        if (!newSrc.equals(cs.dragEdge.getSourceId()) || !newTgt.equals(cs.dragEdge.getTargetId())) {
                                            cs.currentSource = reconnectEdgeInSource(cs.currentSource,
                                                    cs.diagram.getDiagramType(), cs.dragEdge, newSrc, newTgt);
                                            cs.dragging = false;
                                            cs.dragEdge = null;
                                            cs.dragSourceNode = null;
                                            cs.dragStart = null;
                                            cs.dragCurrent = null;
                                            renderCard(renderer, cs);
                                            sourceArea.setText(cs.currentSource);
                                            highlightRect[0] = null;
                                            String end = cs.dragFromSource ? "Quelle" : "Ziel";
                                            detailArea.setText("\u2705 Kante umgehängt (" + end + " \u2192 " + targetNode.getId() + ")");
                                            rebuildDiagram[0].run();
                                            refreshEditPanel[0].run();
                                            return;
                                        }
                                    }
                                    cs.dragging = false;
                                    cs.dragEdge = null;
                                    cs.dragStart = null;
                                    cs.dragCurrent = null;
                                    dPanel.repaint();
                                    return;
                                }

                                if (cs.dragging && cs.dragSourceNode != null) {
                                    // Complete drag: find target node
                                    int pw = dPanel.getWidth(), ph = dPanel.getHeight();
                                    double scale = Math.min((double) pw / imgW, (double) ph / imgH);
                                    int dw = (int)(imgW * scale), dh = (int)(imgH * scale);
                                    int ox = (pw - dw) / 2, oy = (ph - dh) / 2;
                                    double imgPx = (e.getX() - ox) / scale;
                                    double imgPy = (e.getY() - oy) / scale;
                                    double vbX = diagram.getViewBoxX();
                                    double vbY = diagram.getViewBoxY();
                                    double vbW = diagram.getViewBoxWidth();
                                    double vbH = diagram.getViewBoxHeight();
                                    double svgX = vbX + (imgPx / imgW) * vbW;
                                    double svgY = vbY + (imgPy / imgH) * vbH;
                                    DiagramNode targetNode = diagram.findNodeAt(svgX, svgY);

                                    if (targetNode != null && targetNode != cs.dragSourceNode
                                            && !(targetNode instanceof SequenceFragment)) {
                                        // Determine direction from drag vector
                                        boolean leftToRight = e.getX() >= cs.dragStart.x;
                                        String srcId = leftToRight ? cs.dragSourceNode.getId() : targetNode.getId();
                                        String tgtId = leftToRight ? targetNode.getId() : cs.dragSourceNode.getId();
                                        cs.currentSource = addEdgeToSource(cs.currentSource,
                                                cs.diagram.getDiagramType(), srcId, tgtId);
                                        cs.dragging = false;
                                        cs.dragSourceNode = null;
                                        cs.dragStart = null;
                                        cs.dragCurrent = null;
                                        renderCard(renderer, cs);
                                        sourceArea.setText(cs.currentSource);
                                        highlightRect[0] = null;
                                        detailArea.setText("\u2705 Neue Kante erzeugt: " + srcId + " \u2192 " + tgtId);
                                        rebuildDiagram[0].run();
                                        refreshEditPanel[0].run();
                                        return;
                                    }
                                }
                                cs.dragging = false;
                                cs.dragSourceNode = null;
                                cs.dragEdge = null;
                                cs.dragStart = null;
                                cs.dragCurrent = null;
                                dPanel.repaint();
                            }

                            @Override public void mouseClicked(MouseEvent e) {
                                // Only handle click if not a drag
                                if (cs.dragging) return;
                                handleClick(e, dPanel, cs, diagram, imgW, imgH,
                                        highlightRect, detailArea, refreshEditPanel[0]);
                            }
                        });

                        // ── Mouse drag: draw rubber-band line ──
                        dPanel.addMouseMotionListener(new MouseMotionAdapter() {
                            @Override public void mouseDragged(MouseEvent e) {
                                // Edge reconnection drag
                                if (cs.dragEdge != null && cs.dragStart != null) {
                                    int dx = e.getX() - cs.dragStart.x;
                                    int dy = e.getY() - cs.dragStart.y;
                                    if (!cs.dragging && (dx * dx + dy * dy) > 25) {
                                        cs.dragging = true;
                                    }
                                    if (cs.dragging) {
                                        cs.dragCurrent = new Point(e.getX(), e.getY());
                                        dPanel.repaint();
                                    }
                                    return;
                                }
                                // Node-to-node edge creation drag
                                if (cs.dragSourceNode != null && cs.dragStart != null) {
                                    int dx = e.getX() - cs.dragStart.x;
                                    int dy = e.getY() - cs.dragStart.y;
                                    if (!cs.dragging && (dx * dx + dy * dy) > 25) {
                                        cs.dragging = true;
                                    }
                                    if (cs.dragging) {
                                        cs.dragCurrent = new Point(e.getX(), e.getY());
                                        dPanel.repaint();
                                    }
                                }
                            }
                        });

                        diagramContainer.add(dPanel, BorderLayout.CENTER);
                    } else {
                        JLabel errLabel = new JLabel(cs.renderError
                                ? "\u274c SVG-Rendering fehlgeschlagen"
                                : "\u274c Rasterisierung fehlgeschlagen");
                        errLabel.setForeground(Color.RED);
                        errLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                        errLabel.setHorizontalAlignment(SwingConstants.CENTER);
                        diagramContainer.add(errLabel, BorderLayout.CENTER);
                    }
                    diagramContainer.revalidate();
                    diagramContainer.repaint();
                }
            };

            // Initial render
            rebuildDiagram[0].run();

            card.add(diagramContainer);
            card.add(Box.createVerticalStrut(2));

            // ── Node creation toolbar ──
            final JPanel createBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            createBar.setOpaque(true);
            createBar.setBackground(new Color(230, 240, 255));
            createBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(150, 170, 220)),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            createBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            createBar.add(label("\u2795 Neuer Knoten:"));
            final JTextField newNodeName = new JTextField("Neu", 8);
            newNodeName.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            createBar.add(newNodeName);

            // Shape/type combo adapts to diagram type
            final JComboBox<String> newNodeType = new JComboBox<String>(new String[]{
                    "RECTANGLE", "ROUND_RECT", "STADIUM", "CIRCLE", "DIAMOND",
                    "HEXAGON", "CYLINDER"});
            newNodeType.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            createBar.add(newNodeType);

            JButton addNodeBtn = new JButton("Hinzufügen");
            addNodeBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            addNodeBtn.setMargin(new Insets(1, 4, 1, 4));
            createBar.add(addNodeBtn);

            JLabel dragHint = new JLabel(" | Drag: Knoten \u2192 Knoten = Kante");
            dragHint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 9));
            dragHint.setForeground(new Color(80, 80, 130));
            createBar.add(dragHint);

            // ── Sequence fragment insert combo (only visible for sequence diagrams) ──
            final JComboBox<String> fragTypeCb = new JComboBox<String>(new String[]{
                    "loop", "alt", "opt", "par", "critical", "break"});
            fragTypeCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            final JTextField fragCondField = new JTextField("Bedingung", 8);
            fragCondField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            final JButton insertFragBtn = new JButton("+ Fragment");
            insertFragBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            insertFragBtn.setMargin(new Insets(1, 4, 1, 4));

            createBar.add(new JLabel(" | "));
            createBar.add(fragTypeCb);
            createBar.add(fragCondField);
            createBar.add(insertFragBtn);

            insertFragBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    String kw = (String) fragTypeCb.getSelectedItem();
                    String cond = fragCondField.getText().trim();
                    if (cond.isEmpty()) cond = "Neu";
                    cs.currentSource = insertSequenceFragment(cs.currentSource, kw, cond);
                    renderCard(renderer, cs);
                    sourceArea.setText(cs.currentSource);
                    highlightRect[0] = null;
                    detailArea.setText("\u2705 Fragment eingefügt: " + kw + " " + cond);
                    rebuildDiagram[0].run();
                    refreshEditPanel[0].run();
                }
            });

            addNodeBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    String name = newNodeName.getText().trim();
                    if (name.isEmpty()) return;
                    // Check for duplicates
                    if (cs.diagram != null && cs.diagram.findNodeById(name) != null) {
                        name = name + "_" + (cs.diagram.getNodes().size() + 1);
                    }
                    String shapeStr = (String) newNodeType.getSelectedItem();
                    cs.currentSource = addNodeToSource(cs.currentSource,
                            cs.diagram != null ? cs.diagram.getDiagramType() : "flowchart",
                            name, shapeStr);
                    renderCard(renderer, cs);
                    sourceArea.setText(cs.currentSource);
                    highlightRect[0] = null;
                    detailArea.setText("\u2705 Neuer Knoten hinzugefügt: " + name);
                    rebuildDiagram[0].run();
                    refreshEditPanel[0].run();
                }
            });

            card.add(createBar);
            card.add(Box.createVerticalStrut(2));
            card.add(detailArea);
            card.add(Box.createVerticalStrut(4));
            card.add(editPanel);
            card.add(Box.createVerticalStrut(4));
            card.add(sourceScroll);

            card.setPreferredSize(new Dimension(550, 900));
            card.setMinimumSize(new Dimension(480, 700));
            card.setMaximumSize(new Dimension(600, 1200));

            cardRow.add(card);
            if (idx < cards.size() - 1) cardRow.add(Box.createHorizontalStrut(12));
        }

        JScrollPane mainScroll = new JScrollPane(cardRow,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        mainScroll.getHorizontalScrollBar().setUnitIncrement(40);
        frame.add(mainScroll, BorderLayout.CENTER);

        frame.pack();
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    // ═══════════════════════════════════════════════════════════
    //  Polymorphic Edit Panel — Node
    // ═══════════════════════════════════════════════════════════

    private static void buildNodeEditPanel(JPanel panel, final CardState cs,
                                           final MermaidRenderer renderer,
                                           final Runnable onApplied) {
        final DiagramNode node = cs.selectedNode;
        if (node == null) return;

        // ── Rename row (available for ALL node types except fragments) ──
        if (!(node instanceof SequenceFragment)) {
            JPanel renameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            renameRow.setOpaque(false);
            renameRow.add(label("Name/ID:"));
            final JTextField nameField = new JTextField(node.getId(), 14);
            nameField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            renameRow.add(nameField);
            JButton renameBtn = new JButton("Umbenennen");
            renameBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            renameRow.add(renameBtn);
            renameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            panel.add(renameRow);

            renameBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    String newName = nameField.getText().trim();
                    if (newName.isEmpty() || newName.equals(node.getId())) return;
                    cs.currentSource = renameNodeInSource(cs.currentSource,
                            cs.diagram.getDiagramType(), node.getId(), node.getLabel(), newName);
                    onApplied.run();
                }
            });

            // ── Delete node button ──
            JPanel deleteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            deleteRow.setOpaque(false);
            deleteRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            JButton deleteNodeBtn = new JButton("\u2716 Knoten entfernen");
            deleteNodeBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            deleteNodeBtn.setForeground(new Color(180, 0, 0));
            deleteRow.add(deleteNodeBtn);
            panel.add(deleteRow);

            deleteNodeBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    cs.currentSource = deleteNodeFromSource(cs.currentSource,
                            cs.diagram.getDiagramType(), node);
                    cs.selectedNode = null;
                    onApplied.run();
                }
            });

            // If label differs from ID, offer separate label edit
            if (!node.getLabel().equals(node.getId()) && !node.getLabel().isEmpty()) {
                JPanel labelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
                labelRow.setOpaque(false);
                labelRow.add(label("Label:"));
                final JTextField labelField = new JTextField(node.getLabel(), 14);
                labelField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                labelRow.add(labelField);
                JButton labelBtn = new JButton("Label ändern");
                labelBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                labelRow.add(labelBtn);
                labelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                panel.add(labelRow);

                labelBtn.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        String newLabel = labelField.getText().trim();
                        if (newLabel.isEmpty() || newLabel.equals(node.getLabel())) return;
                        cs.currentSource = cs.currentSource.replace(node.getLabel(), newLabel);
                        onApplied.run();
                    }
                });
            }
        }

        // ── ClassNode-specific: fields & methods ──
        if (node instanceof ClassNode) {
            buildClassMemberEditor(panel, (ClassNode) node, cs, onApplied);
        }

        // ── FlowchartNode-specific: shape selector ──
        if (node instanceof FlowchartNode) {
            buildShapeSelector(panel, (FlowchartNode) node, cs, onApplied);
        }

        // ── ErEntityNode-specific: attribute editing ──
        if (node instanceof ErEntityNode) {
            buildErEntityEditor(panel, (ErEntityNode) node, cs, onApplied);
        }

        // ── SequenceFragment-specific: type + condition ──
        if (node instanceof SequenceFragment) {
            buildSequenceFragmentEditor(panel, (SequenceFragment) node, cs, onApplied);
        }
    }

    /** Class diagram — add/remove fields and methods using polymorphism. */
    private static void buildClassMemberEditor(JPanel panel, final ClassNode cn,
                                                final CardState cs,
                                                final Runnable onApplied) {
        panel.add(Box.createVerticalStrut(4));
        panel.add(separator("Klassen-Member"));

        // ── Existing fields ──
        final List<ClassMember> fields = cn.getFields();
        if (!fields.isEmpty()) {
            panel.add(label("Felder:"));
            for (int i = 0; i < fields.size(); i++) {
                final ClassMember f = fields.get(i);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                JLabel memberLabel = new JLabel("  " + f.toMermaid());
                memberLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                row.add(memberLabel);
                JButton delBtn = smallButton("\u2716");
                delBtn.setToolTipText("Feld entfernen");
                row.add(delBtn);
                delBtn.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        cs.currentSource = removeClassMember(cs.currentSource, cn.getId(), f.toMermaid());
                        onApplied.run();
                    }
                });
                panel.add(row);
            }
        }

        // ── Existing methods ──
        final List<ClassMember> methods = cn.getMethods();
        if (!methods.isEmpty()) {
            panel.add(label("Methoden:"));
            for (int i = 0; i < methods.size(); i++) {
                final ClassMember m = methods.get(i);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                JLabel memberLabel = new JLabel("  " + m.toMermaid());
                memberLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                row.add(memberLabel);
                JButton delBtn = smallButton("\u2716");
                delBtn.setToolTipText("Methode entfernen");
                row.add(delBtn);
                delBtn.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        cs.currentSource = removeClassMember(cs.currentSource, cn.getId(), m.toMermaid());
                        onApplied.run();
                    }
                });
                panel.add(row);
            }
        }

        // ── Add field ──
        panel.add(Box.createVerticalStrut(4));
        JPanel addFieldRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        addFieldRow.setOpaque(false);
        addFieldRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        final JComboBox<String> fieldVis = new JComboBox<String>(new String[]{"+", "-", "#", "~"});
        fieldVis.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        addFieldRow.add(fieldVis);

        final JTextField fieldType = new JTextField("String", 6);
        fieldType.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        addFieldRow.add(fieldType);

        final JTextField fieldName = new JTextField("newField", 8);
        fieldName.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        addFieldRow.add(fieldName);

        JButton addFieldBtn = smallButton("+ Feld");
        addFieldRow.add(addFieldBtn);
        panel.add(addFieldRow);

        addFieldBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String vis = (String) fieldVis.getSelectedItem();
                String type = fieldType.getText().trim();
                String name = fieldName.getText().trim();
                if (name.isEmpty()) return;
                String member = vis + (type.isEmpty() ? "" : type + " ") + name;
                cs.currentSource = addClassMember(cs.currentSource, cn.getId(), member);
                onApplied.run();
            }
        });

        // ── Add method ──
        JPanel addMethodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        addMethodRow.setOpaque(false);
        addMethodRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        final JComboBox<String> methodVis = new JComboBox<String>(new String[]{"+", "-", "#", "~"});
        methodVis.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        addMethodRow.add(methodVis);

        final JTextField methodName = new JTextField("newMethod", 8);
        methodName.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        addMethodRow.add(methodName);

        final JTextField methodRet = new JTextField("void", 5);
        methodRet.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        addMethodRow.add(methodRet);

        JButton addMethodBtn = smallButton("+ Methode");
        addMethodRow.add(addMethodBtn);
        panel.add(addMethodRow);

        addMethodBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String vis = (String) methodVis.getSelectedItem();
                String name = methodName.getText().trim();
                String ret = methodRet.getText().trim();
                if (name.isEmpty()) return;
                String member = vis + name + "()" + (ret.isEmpty() ? "" : " " + ret);
                cs.currentSource = addClassMember(cs.currentSource, cn.getId(), member);
                onApplied.run();
            }
        });
    }

    /** Flowchart node — shape selector dropdown. */
    private static void buildShapeSelector(JPanel panel, final FlowchartNode fn,
                                           final CardState cs,
                                           final Runnable onApplied) {
        panel.add(Box.createVerticalStrut(4));
        JPanel shapeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        shapeRow.setOpaque(false);
        shapeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        shapeRow.add(label("Form:"));

        final NodeShape[] shapes = NodeShape.values();
        String[] shapeNames = new String[shapes.length];
        int selected = 0;
        for (int i = 0; i < shapes.length; i++) {
            shapeNames[i] = shapes[i].name();
            if (shapes[i] == fn.getShape()) selected = i;
        }
        final JComboBox<String> shapeCb = new JComboBox<String>(shapeNames);
        shapeCb.setSelectedIndex(selected);
        shapeCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        shapeRow.add(shapeCb);

        JButton applyShape = new JButton("Anwenden");
        applyShape.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        shapeRow.add(applyShape);
        panel.add(shapeRow);

        applyShape.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                NodeShape newShape = shapes[shapeCb.getSelectedIndex()];
                if (newShape == fn.getShape()) return;
                String oldSyntax = fn.getShape().toMermaid(fn.getId(), fn.getLabel());
                String newSyntax = newShape.toMermaid(fn.getId(), fn.getLabel());
                cs.currentSource = cs.currentSource.replace(oldSyntax, newSyntax);
                onApplied.run();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Polymorphic Edit Panel — Edge
    // ═══════════════════════════════════════════════════════════

    private static void buildEdgeEditPanel(JPanel panel, final CardState cs,
                                           final MermaidRenderer renderer,
                                           final Runnable onApplied) {
        final DiagramEdge edge = cs.selectedEdge;
        if (edge == null) return;

        // ── Label edit (available for all edge types) ──
        JPanel labelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        labelRow.setOpaque(false);
        labelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        labelRow.add(label("Label:"));
        final JTextField labelField = new JTextField(edge.getLabel(), 14);
        labelField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        labelRow.add(labelField);
        JButton applyLabelBtn = new JButton("Anwenden");
        applyLabelBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        labelRow.add(applyLabelBtn);
        panel.add(labelRow);

        applyLabelBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String newLabel = labelField.getText().trim();
                if (newLabel.equals(edge.getLabel())) return;
                cs.currentSource = changeEdgeLabelInSource(cs.currentSource,
                        cs.diagram.getDiagramType(), edge, newLabel);
                onApplied.run();
            }
        });

        // ── Delete edge button (available for all edge types) ──
        JPanel deleteEdgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        deleteEdgeRow.setOpaque(false);
        deleteEdgeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JButton deleteEdgeBtn = new JButton("\u2716 Kante entfernen");
        deleteEdgeBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        deleteEdgeBtn.setForeground(new Color(180, 0, 0));
        deleteEdgeRow.add(deleteEdgeBtn);
        panel.add(deleteEdgeRow);

        deleteEdgeBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                cs.currentSource = deleteEdgeFromSource(cs.currentSource,
                        cs.diagram.getDiagramType(), edge);
                cs.selectedEdge = null;
                onApplied.run();
            }
        });

        // ── Direction reversal (available for all edge types) ──
        JPanel dirRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        dirRow.setOpaque(false);
        dirRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JButton reverseBtn = new JButton("\u21C4 Richtung umkehren");
        reverseBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        dirRow.add(reverseBtn);
        panel.add(dirRow);

        reverseBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                cs.currentSource = reverseEdgeInSource(cs.currentSource,
                        cs.diagram.getDiagramType(), edge);
                onApplied.run();
            }
        });

        // ── FlowchartEdge: line style + arrowhead editing ──
        if (edge instanceof FlowchartEdge) {
            final FlowchartEdge fe = (FlowchartEdge) edge;
            panel.add(Box.createVerticalStrut(4));
            panel.add(separator("Kantenstil"));

            // Line style combo
            JPanel styleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            styleRow.setOpaque(false);
            styleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            styleRow.add(label("Linie:"));
            // Only offer flowchart-relevant line styles
            final LineStyle[] lineStyles = {LineStyle.SOLID, LineStyle.DASHED, LineStyle.THICK};
            String[] styleNames = new String[lineStyles.length];
            int selStyle = 0;
            for (int i = 0; i < lineStyles.length; i++) {
                styleNames[i] = lineStyles[i].getDisplayName();
                if (lineStyles[i] == fe.getLineStyle()) selStyle = i;
            }
            final JComboBox<String> styleCb = new JComboBox<String>(styleNames);
            styleCb.setSelectedIndex(selStyle);
            styleCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            styleRow.add(styleCb);

            // Head type combo — only flowchart-valid arrowheads
            styleRow.add(label(" Spitze:"));
            final ArrowHead[] arrowHeads = {ArrowHead.NORMAL, ArrowHead.NONE,
                    ArrowHead.CIRCLE, ArrowHead.CROSS};
            String[] headNames = new String[arrowHeads.length];
            int selHead = 0;
            for (int i = 0; i < arrowHeads.length; i++) {
                headNames[i] = arrowHeads[i].getDisplayName();
                if (arrowHeads[i] == fe.getHeadType()) selHead = i;
            }
            final JComboBox<String> headCb = new JComboBox<String>(headNames);
            headCb.setSelectedIndex(selHead);
            headCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            styleRow.add(headCb);

            JButton applyStyle = new JButton("Stil ändern");
            applyStyle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            styleRow.add(applyStyle);
            panel.add(styleRow);

            applyStyle.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    LineStyle newStyle = lineStyles[styleCb.getSelectedIndex()];
                    ArrowHead newHead = arrowHeads[headCb.getSelectedIndex()];
                    cs.currentSource = changeFlowchartEdgeStyle(cs.currentSource,
                            fe.getSourceId(), fe.getTargetId(), fe.getLabel(),
                            fe.getLineStyle(), fe.getHeadType(),
                            newStyle, newHead);
                    onApplied.run();
                }
            });
        }

        // ── ClassRelation: relation type editing ──
        if (edge instanceof ClassRelation) {
            final ClassRelation cr = (ClassRelation) edge;
            panel.add(Box.createVerticalStrut(4));
            panel.add(separator("Beziehungstyp"));

            JPanel relRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            relRow.setOpaque(false);
            relRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            relRow.add(label("Typ:"));

            final RelationType[] relTypes = RelationType.values();
            String[] relNames = new String[relTypes.length];
            int selRel = 0;
            for (int i = 0; i < relTypes.length; i++) {
                relNames[i] = relTypes[i].getDisplayName() + " (" + relTypes[i].getMermaidSyntax() + ")";
                if (relTypes[i] == cr.getRelationType()) selRel = i;
            }
            final JComboBox<String> relCb = new JComboBox<String>(relNames);
            relCb.setSelectedIndex(selRel);
            relCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            relRow.add(relCb);

            JButton applyRel = new JButton("Anwenden");
            applyRel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            relRow.add(applyRel);
            panel.add(relRow);

            applyRel.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    RelationType newType = relTypes[relCb.getSelectedIndex()];
                    if (newType == cr.getRelationType()) return;
                    String oldLine = cr.getRelationType().toMermaid(
                            cr.getSourceId(), cr.getTargetId(), cr.getLabel());
                    String newLine = newType.toMermaid(
                            cr.getSourceId(), cr.getTargetId(), cr.getLabel());
                    cs.currentSource = cs.currentSource.replace(
                            oldLine.trim(), newLine.trim());
                    onApplied.run();
                }
            });
        }

        // ── ErRelationship: cardinality editing ──
        if (edge instanceof ErRelationship) {
            final ErRelationship er = (ErRelationship) edge;
            panel.add(Box.createVerticalStrut(4));
            panel.add(separator("Kardinalitäten"));

            JPanel cardRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            cardRow.setOpaque(false);
            cardRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            cardRow.add(label(er.getSourceId() + ":"));
            final ErCardinality[] cards = ErCardinality.values();
            String[] srcNames = new String[cards.length];
            String[] tgtNames = new String[cards.length];
            int selSrc = 0, selTgt = 0;
            for (int i = 0; i < cards.length; i++) {
                srcNames[i] = cards[i].getDisplayName() + " (" + cards[i].getMermaidSyntax() + ")";
                tgtNames[i] = srcNames[i];
                if (cards[i] == er.getSourceCardinality()) selSrc = i;
                if (cards[i] == er.getTargetCardinality()) selTgt = i;
            }

            final JComboBox<String> srcCardCb = new JComboBox<String>(srcNames);
            srcCardCb.setSelectedIndex(selSrc);
            srcCardCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            cardRow.add(srcCardCb);

            cardRow.add(label(" " + er.getTargetId() + ":"));
            final JComboBox<String> tgtCardCb = new JComboBox<String>(tgtNames);
            tgtCardCb.setSelectedIndex(selTgt);
            tgtCardCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            cardRow.add(tgtCardCb);

            JButton applyCard = new JButton("Anwenden");
            applyCard.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            cardRow.add(applyCard);
            panel.add(cardRow);

            applyCard.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    ErCardinality newSrc = cards[srcCardCb.getSelectedIndex()];
                    ErCardinality newTgt = cards[tgtCardCb.getSelectedIndex()];
                    cs.currentSource = changeErCardinalityInSource(cs.currentSource,
                            er.getSourceId(), er.getTargetId(), er.getLabel(),
                            er.getSourceCardinality(), er.getTargetCardinality(),
                            newSrc, newTgt, er.isIdentifying());
                    onApplied.run();
                }
            });
        }

        // ── SequenceMessage: message type editing ──
        if (edge instanceof SequenceMessage) {
            final SequenceMessage sm = (SequenceMessage) edge;
            panel.add(Box.createVerticalStrut(4));
            panel.add(separator("Nachrichtentyp"));

            // Message text rename
            JPanel msgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            msgRow.setOpaque(false);
            msgRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            msgRow.add(label("Nachricht:"));
            final JTextField msgField = new JTextField(edge.getLabel(), 14);
            msgField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            msgRow.add(msgField);
            JButton msgBtn = new JButton("Anwenden");
            msgBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            msgRow.add(msgBtn);
            panel.add(msgRow);

            msgBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    String newMsg = msgField.getText().trim();
                    String oldMsg = edge.getLabel();
                    if (newMsg.equals(oldMsg)) return;
                    if (!oldMsg.isEmpty()) {
                        cs.currentSource = cs.currentSource.replace(": " + oldMsg, ": " + newMsg);
                    }
                    onApplied.run();
                }
            });

            // Message type combo
            JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            typeRow.setOpaque(false);
            typeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            typeRow.add(label("Typ:"));

            final MessageType[] msgTypes = MessageType.values();
            String[] typeNames = new String[msgTypes.length];
            int selType = 0;
            for (int i = 0; i < msgTypes.length; i++) {
                typeNames[i] = msgTypes[i].getDisplayName() + " (" + msgTypes[i].getMermaidSyntax() + ")";
                if (msgTypes[i] == sm.getMessageType()) selType = i;
            }
            final JComboBox<String> typeCb = new JComboBox<String>(typeNames);
            typeCb.setSelectedIndex(selType);
            typeCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            typeRow.add(typeCb);

            JButton applyType = new JButton("Typ ändern");
            applyType.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            typeRow.add(applyType);
            panel.add(typeRow);

            applyType.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    MessageType newType = msgTypes[typeCb.getSelectedIndex()];
                    if (newType == sm.getMessageType()) return;
                    cs.currentSource = changeSequenceMessageType(cs.currentSource,
                            sm.getSourceId(), sm.getTargetId(), sm.getLabel(),
                            sm.getMessageType(), newType);
                    onApplied.run();
                }
            });
        }

        // ── StateTransition: guard/label editing ──
        if (edge instanceof StateTransition) {
            final StateTransition st = (StateTransition) edge;
            panel.add(Box.createVerticalStrut(4));
            panel.add(separator("Transition"));

            JPanel guardRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            guardRow.setOpaque(false);
            guardRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            guardRow.add(label("Guard/Label:"));
            final JTextField guardField = new JTextField(st.getGuard(), 14);
            guardField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            guardRow.add(guardField);
            JButton guardBtn = new JButton("Anwenden");
            guardBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            guardRow.add(guardBtn);
            panel.add(guardRow);

            guardBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    String newGuard = guardField.getText().trim();
                    String oldGuard = st.getGuard();
                    if (newGuard.equals(oldGuard)) return;
                    String srcId = st.getSourceId();
                    String tgtId = st.getTargetId();
                    if (oldGuard.isEmpty() && !newGuard.isEmpty()) {
                        // Add guard
                        cs.currentSource = cs.currentSource.replace(
                                srcId + " --> " + tgtId,
                                srcId + " --> " + tgtId + " : " + newGuard);
                    } else if (!oldGuard.isEmpty() && newGuard.isEmpty()) {
                        // Remove guard
                        cs.currentSource = cs.currentSource.replace(
                                srcId + " --> " + tgtId + " : " + oldGuard,
                                srcId + " --> " + tgtId);
                    } else {
                        // Change guard
                        cs.currentSource = cs.currentSource.replace(
                                " : " + oldGuard, " : " + newGuard);
                    }
                    onApplied.run();
                }
            });
        }

        // ── Edge reconnection (change source or target) ──
        panel.add(Box.createVerticalStrut(4));
        panel.add(separator("Endpunkte ändern"));
        JPanel reconnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        reconnRow.setOpaque(false);
        reconnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        reconnRow.add(label("Quelle:"));
        final JTextField srcField = new JTextField(edge.getSourceId(), 8);
        srcField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        reconnRow.add(srcField);
        reconnRow.add(label("Ziel:"));
        final JTextField tgtField = new JTextField(edge.getTargetId(), 8);
        tgtField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        reconnRow.add(tgtField);
        JButton reconnBtn = new JButton("Umhängen");
        reconnBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        reconnRow.add(reconnBtn);
        panel.add(reconnRow);

        reconnBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String newSrc = srcField.getText().trim();
                String newTgt = tgtField.getText().trim();
                if (newSrc.equals(edge.getSourceId()) && newTgt.equals(edge.getTargetId())) return;
                cs.currentSource = reconnectEdgeInSource(cs.currentSource,
                        cs.diagram.getDiagramType(), edge, newSrc, newTgt);
                onApplied.run();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  ER Entity attribute editor
    // ═══════════════════════════════════════════════════════════

    private static void buildErEntityEditor(JPanel panel, final ErEntityNode en,
                                            final CardState cs,
                                            final Runnable onApplied) {
        panel.add(Box.createVerticalStrut(4));
        panel.add(separator("Entity-Attribute"));

        // Parse attributes directly from Mermaid source (SVG extraction may miss them)
        List<String> sourceAttrs = parseErAttributeLinesFromSource(cs.currentSource, en.getId());
        // Fallback: use SVG-extracted attributes if source parsing found nothing
        if (sourceAttrs.isEmpty() && !en.getAttributes().isEmpty()) {
            for (ErAttribute attr : en.getAttributes()) {
                sourceAttrs.add(attr.toMermaid());
            }
        }

        // Show existing attributes with delete buttons
        for (int i = 0; i < sourceAttrs.size(); i++) {
            final String attrLine = sourceAttrs.get(i);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            JLabel attrLabel = new JLabel("  " + attrLine);
            attrLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            row.add(attrLabel);
            JButton delBtn = smallButton("\u2716");
            delBtn.setToolTipText("Attribut entfernen");
            row.add(delBtn);
            delBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    cs.currentSource = removeErAttribute(cs.currentSource, en.getId(), attrLine);
                    onApplied.run();
                }
            });
            panel.add(row);
        }

        if (sourceAttrs.isEmpty()) {
            JLabel noAttrs = new JLabel("  (keine Attribute gefunden)");
            noAttrs.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
            noAttrs.setForeground(Color.GRAY);
            panel.add(noAttrs);
        }

        // Add new attribute row
        panel.add(Box.createVerticalStrut(4));
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        addRow.setOpaque(false);
        addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        final JTextField attrType = new JTextField("string", 6);
        attrType.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        addRow.add(attrType);

        final JTextField attrName = new JTextField("new_field", 8);
        attrName.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        addRow.add(attrName);

        final JComboBox<String> attrKey = new JComboBox<String>(new String[]{"-", "PK", "FK"});
        attrKey.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        addRow.add(attrKey);

        JButton addBtn = smallButton("+ Attribut");
        addRow.add(addBtn);
        panel.add(addRow);

        addBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String type = attrType.getText().trim();
                String name = attrName.getText().trim();
                if (name.isEmpty()) return;
                String key = (String) attrKey.getSelectedItem();
                String member = type + " " + name;
                if ("PK".equals(key)) member += " PK";
                else if ("FK".equals(key)) member += " FK";
                cs.currentSource = addErAttribute(cs.currentSource, en.getId(), member);
                onApplied.run();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Sequence Fragment editor (loop, alt, opt, …)
    // ═══════════════════════════════════════════════════════════

    private static void buildSequenceFragmentEditor(JPanel panel, final SequenceFragment frag,
                                                     final CardState cs,
                                                     final Runnable onApplied) {
        panel.add(separator("Sequenz-Fragment"));

        // Fragment type combo
        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        typeRow.setOpaque(false);
        typeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        typeRow.add(label("Typ:"));

        final SequenceFragment.FragmentType[] fragTypes = SequenceFragment.FragmentType.values();
        String[] typeNames = new String[fragTypes.length];
        int selType = 0;
        for (int i = 0; i < fragTypes.length; i++) {
            typeNames[i] = fragTypes[i].getDisplayName() + " (" + fragTypes[i].getKeyword() + ")";
            if (fragTypes[i] == frag.getFragmentType()) selType = i;
        }
        final JComboBox<String> typeCb = new JComboBox<String>(typeNames);
        typeCb.setSelectedIndex(selType);
        typeCb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        typeRow.add(typeCb);
        panel.add(typeRow);

        // Condition text
        JPanel condRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        condRow.setOpaque(false);
        condRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        condRow.add(label("Bedingung:"));
        final JTextField condField = new JTextField(frag.getCondition(), 14);
        condField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        condRow.add(condField);

        JButton applyFrag = new JButton("Anwenden");
        applyFrag.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        condRow.add(applyFrag);
        panel.add(condRow);

        applyFrag.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                SequenceFragment.FragmentType newType = fragTypes[typeCb.getSelectedIndex()];
                String newCond = condField.getText().trim();
                String oldHeader = frag.getFragmentType().getKeyword()
                        + (frag.getCondition().isEmpty() ? "" : " " + frag.getCondition());
                String newHeader = newType.getKeyword()
                        + (newCond.isEmpty() ? "" : " " + newCond);
                if (oldHeader.equals(newHeader)) return;
                cs.currentSource = cs.currentSource.replace(oldHeader, newHeader);
                onApplied.run();
            }
        });

        // Button to insert a new fragment
        panel.add(Box.createVerticalStrut(4));
        JPanel insertRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        insertRow.setOpaque(false);
        insertRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JButton insertBtn = new JButton("\u2795 Neues Fragment einfügen");
        insertBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        insertRow.add(insertBtn);
        panel.add(insertRow);

        insertBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                // Insert a new loop block before "end" of the current fragment
                cs.currentSource = insertSequenceFragment(cs.currentSource, "loop", "Neu");
                onApplied.run();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Source manipulation helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Rename a node in the Mermaid source code.
     * Handles different diagram types and ID vs. label contexts.
     */
    private static String renameNodeInSource(String source, String diagramType, String oldId,
                                              String oldLabel, String newName) {
        // For flowcharts: handle the ID[Label] pattern
        if ("flowchart".equals(diagramType)) {
            // First try to replace explicit shape syntax: oldId[oldLabel] or oldId([oldLabel]) etc.
            for (NodeShape shape : NodeShape.values()) {
                String oldSyntax = oldId + shape.getOpenSyntax() + oldLabel + shape.getCloseSyntax();
                String newSyntax = newName + shape.getOpenSyntax() + newName + shape.getCloseSyntax();
                if (source.contains(oldSyntax)) {
                    source = source.replace(oldSyntax, newSyntax);
                }
            }
        }
        // General word-boundary replacement for all remaining occurrences
        String escaped = Pattern.quote(oldId);
        source = source.replaceAll("(?<=^|[\\s,;:{}()|<>\\[\\]])(" + escaped + ")(?=$|[\\s,;:{}()|<>\\[\\]])",
                Matcher.quoteReplacement(newName));
        // Fallback: simple \b replacement
        if (source.contains(oldId)) {
            source = source.replaceAll("\\b" + escaped + "\\b", Matcher.quoteReplacement(newName));
        }
        return source;
    }

    /**
     * Change an edge label in the source. Handles both existing labels
     * and adding labels to previously unlabelled edges.
     */
    private static String changeEdgeLabelInSource(String source, String diagramType,
                                                   DiagramEdge edge, String newLabel) {
        String oldLabel = edge.getLabel();

        if (edge instanceof SequenceMessage) {
            // Sequence: "Source->>Target: OldLabel"
            if (!oldLabel.isEmpty()) {
                return source.replace(": " + oldLabel, ": " + newLabel);
            } else {
                // Add label to unlabelled message — find the arrow line
                String pattern = Pattern.quote(edge.getSourceId()) + "\\s*(->>|-->>|-\\)|--\\)|->|-->|-x|--x)\\s*"
                        + Pattern.quote(edge.getTargetId());
                return source.replaceFirst(pattern,
                        Matcher.quoteReplacement(edge.getSourceId()) + "$1"
                                + Matcher.quoteReplacement(edge.getTargetId())
                                + ": " + Matcher.quoteReplacement(newLabel));
            }
        }

        if (edge instanceof ErRelationship) {
            // ER: "A ||--o{ B : label"
            if (!oldLabel.isEmpty()) {
                return source.replace(": " + oldLabel, newLabel.isEmpty() ? "" : ": " + newLabel);
            } else if (!newLabel.isEmpty()) {
                // Add label: find the ER line and append " : label"
                String srcId = Pattern.quote(edge.getSourceId());
                String tgtId = Pattern.quote(edge.getTargetId());
                // Match the ER relationship line
                String pattern = "(" + srcId + "\\s+[|o}{]+--[|o}{]+\\s+" + tgtId + ")";
                return source.replaceFirst(pattern, "$1 : " + Matcher.quoteReplacement(newLabel));
            }
            return source;
        }

        if (edge instanceof ClassRelation) {
            // Class: "A <|-- B : label"
            if (!oldLabel.isEmpty()) {
                return source.replace(": " + oldLabel, newLabel.isEmpty() ? "" : ": " + newLabel);
            } else if (!newLabel.isEmpty()) {
                String srcId = Pattern.quote(edge.getSourceId());
                String tgtId = Pattern.quote(edge.getTargetId());
                String pattern = "(" + srcId + "\\s+(?:<\\|--|<\\|\\.\\.|\\*--|o--|-->|\\.\\.>|--)\\s+" + tgtId + ")";
                return source.replaceFirst(pattern, "$1 : " + Matcher.quoteReplacement(newLabel));
            }
            return source;
        }

        // Flowchart/state edges: "|label|" syntax
        if (!oldLabel.isEmpty()) {
            return source.replace("|" + oldLabel + "|",
                    newLabel.isEmpty() ? "" : "|" + newLabel + "|");
        } else if (!newLabel.isEmpty()) {
            // Add label to unlabelled edge: find arrow between source and target, insert |label|
            String srcId = Pattern.quote(edge.getSourceId());
            String tgtId = Pattern.quote(edge.getTargetId());
            // Match arrow operators: -->, --.-> , ==>, ---, -.- , ===
            String pattern = "(" + srcId + "\\s*(?:-->|-.->|==>|---|-\\.-|===))\\s*(" + tgtId + ")";
            return source.replaceFirst(pattern, "$1|" + Matcher.quoteReplacement(newLabel) + "| $2");
        }
        return source;
    }

    /**
     * Change the line style and arrowhead of a flowchart edge.
     */
    private static String changeFlowchartEdgeStyle(String source,
                                                    String sourceId, String targetId, String label,
                                                    LineStyle oldStyle, ArrowHead oldHead,
                                                    LineStyle newStyle, ArrowHead newHead) {
        String oldArrow = buildFlowchartArrow(oldStyle, oldHead);
        String newArrow = buildFlowchartArrow(newStyle, newHead);
        if (oldArrow.equals(newArrow)) return source;

        // Build the old and new edge patterns
        String labelPart = (label != null && !label.isEmpty()) ? "|" + label + "|" : "";
        String escaped = Pattern.quote(sourceId) + "\\s*" + Pattern.quote(oldArrow + labelPart) + "\\s*" + Pattern.quote(targetId);
        String replacement = Matcher.quoteReplacement(sourceId + " " + newArrow + labelPart + " " + targetId);
        String result = source.replaceFirst(escaped, replacement);

        // Fallback: try simple string replace
        if (result.equals(source)) {
            String oldFrag = oldArrow + labelPart;
            String newFrag = newArrow + labelPart;
            result = source.replace(sourceId + " " + oldFrag + " " + targetId,
                    sourceId + " " + newFrag + " " + targetId);
        }
        if (result.equals(source)) {
            // Try without spaces
            String oldFrag = oldArrow + labelPart;
            String newFrag = newArrow + labelPart;
            result = source.replace(oldFrag, newFrag);
        }
        return result;
    }

    /** Build a Mermaid arrow operator from line style and arrowhead. */
    private static String buildFlowchartArrow(LineStyle style, ArrowHead head) {
        // Arrowhead suffix
        String suffix;
        switch (head) {
            case CIRCLE: suffix = "o"; break;
            case CROSS:  suffix = "x"; break;
            case NONE:   suffix = "";  break;
            default:     suffix = ">";  break;  // NORMAL and others
        }
        // Line stem
        switch (style) {
            case DASHED:
            case DOTTED:
                return suffix.isEmpty() ? "-.-" : "-.-" + suffix;
            case THICK:
                return suffix.isEmpty() ? "===" : "==" + suffix;
            default:
                return suffix.isEmpty() ? "---" : "--" + suffix;
        }
    }

    /** Reverse the direction of an edge in the source (works for all diagram types). */
    private static String reverseEdgeInSource(String source, String diagramType,
                                               DiagramEdge edge) {
        String sourceId = edge.getSourceId();
        String targetId = edge.getTargetId();
        String label = edge.getLabel();

        if (edge instanceof SequenceMessage) {
            SequenceMessage sm = (SequenceMessage) edge;
            String arrow = sm.getMessageType().getMermaidSyntax();
            String labelPart = (label != null && !label.isEmpty()) ? ": " + label : "";
            String old = sourceId + arrow + targetId + labelPart;
            String rev = targetId + arrow + sourceId + labelPart;
            if (source.contains(old)) return source.replace(old, rev);
            // Try with spaces around arrow
            old = sourceId + " " + arrow + " " + targetId + labelPart;
            rev = targetId + " " + arrow + " " + sourceId + labelPart;
            if (source.contains(old)) return source.replace(old, rev);
            return source;
        }

        if (edge instanceof ClassRelation) {
            ClassRelation cr = (ClassRelation) edge;
            String syntax = cr.getRelationType().getMermaidSyntax();
            String labelPart = (label != null && !label.isEmpty()) ? " : " + label : "";
            String old = sourceId + " " + syntax + " " + targetId + labelPart;
            String rev = targetId + " " + syntax + " " + sourceId + labelPart;
            if (source.contains(old)) return source.replace(old, rev);
            return source;
        }

        if (edge instanceof ErRelationship) {
            // For ER, swap entities AND swap cardinalities
            ErRelationship er = (ErRelationship) edge;
            String labelPart = (label != null && !label.isEmpty()) ? " : " + label : "";
            // Use regex to find the ER line
            String srcQ = Pattern.quote(sourceId);
            String tgtQ = Pattern.quote(targetId);
            String pattern = srcQ + "\\s+([|o{}]+)(--|-\\.|==)([|o{}]+)\\s+" + tgtQ;
            Matcher m = Pattern.compile(pattern).matcher(source);
            if (m.find()) {
                String leftCard = m.group(1);
                String connector = m.group(2);
                String rightCard = m.group(3);
                // Swap: new left = reversed old right, new right = reversed old left
                String newLeft = reverseSyntax(rightCard);
                String newRight = reverseSyntax(leftCard);
                String newLine = targetId + " " + newLeft + connector + newRight + " " + sourceId;
                return source.substring(0, m.start()) + newLine + source.substring(m.end());
            }
            return source;
        }

        if (edge instanceof StateTransition) {
            String labelPart = (label != null && !label.isEmpty()) ? " : " + label : "";
            String old = sourceId + " --> " + targetId + labelPart;
            String rev = targetId + " --> " + sourceId + labelPart;
            if (source.contains(old)) return source.replace(old, rev);
            return source;
        }

        // Flowchart edges (default)
        String[] arrows = {"-->", "-.->", "==>", "---", "-.-", "===",
                "--o", "--x", "o--o", "x--x",
                "<-->", "<-.->", "<==>", "<-->"};
        for (String arrow : arrows) {
            String labelPart = (label != null && !label.isEmpty()) ? "|" + label + "|" : "";
            String old = sourceId + " " + arrow + labelPart + " " + targetId;
            String rev = targetId + " " + arrow + labelPart + " " + sourceId;
            if (source.contains(old)) {
                return source.replace(old, rev);
            }
        }
        return source;
    }

    /**
     * Change ER diagram cardinalities.
     * Uses regex to find the ER line and replaces with correct left/right syntax.
     */
    private static String changeErCardinalityInSource(String source,
                                                       String sourceId, String targetId, String label,
                                                       ErCardinality oldSrcCard, ErCardinality oldTgtCard,
                                                       ErCardinality newSrcCard, ErCardinality newTgtCard,
                                                       boolean identifying) {
        // Use regex to find the ER relationship line between sourceId and targetId
        String srcQ = Pattern.quote(sourceId);
        String tgtQ = Pattern.quote(targetId);
        String labelQ = (label != null && !label.isEmpty()) ? "\\s*:\\s*" + Pattern.quote(label) : "";
        // Match: ENTITY1 <cardinality><connector><cardinality> ENTITY2 [: label]
        String pattern = srcQ + "\\s+([|o{}]+)(--|==)([|o{}]+)\\s+" + tgtQ + labelQ;
        Matcher m = Pattern.compile(pattern).matcher(source);
        if (m.find()) {
            String connector = m.group(2);
            String labelPart = (label != null && !label.isEmpty()) ? " : " + label : "";
            String newRel = sourceId + " " + toLeftSyntax(newSrcCard)
                    + connector + toRightSyntax(newTgtCard) + " " + targetId + labelPart;
            return source.substring(0, m.start()) + newRel + source.substring(m.end());
        }
        return source;
    }

    /** Left-side cardinality Mermaid syntax (faces connector from the left). */
    private static String toLeftSyntax(ErCardinality card) {
        switch (card) {
            case EXACTLY_ONE:  return "||";
            case ZERO_OR_ONE:  return "|o";
            case ZERO_OR_MORE: return "}o";
            case ONE_OR_MORE:  return "}|";
            default:           return "||";
        }
    }

    /** Right-side cardinality Mermaid syntax (faces connector from the right). */
    private static String toRightSyntax(ErCardinality card) {
        switch (card) {
            case EXACTLY_ONE:  return "||";
            case ZERO_OR_ONE:  return "o|";
            case ZERO_OR_MORE: return "o{";
            case ONE_OR_MORE:  return "|{";
            default:           return "||";
        }
    }

    /** Reverse ER cardinality syntax for target side: |{ → }|, o{ → }o, etc. */
    private static String reverseSyntax(String syntax) {
        // Reverse the characters in the cardinality syntax
        StringBuilder sb = new StringBuilder();
        for (int i = syntax.length() - 1; i >= 0; i--) {
            char c = syntax.charAt(i);
            // Mirror brackets
            if (c == '{') sb.append('}');
            else if (c == '}') sb.append('{');
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Change a sequence message type (arrow style).
     */
    private static String changeSequenceMessageType(String source,
                                                     String sourceId, String targetId, String label,
                                                     MessageType oldType, MessageType newType) {
        String oldArrow = oldType.getMermaidSyntax();
        String newArrow = newType.getMermaidSyntax();
        // Pattern: SourceId->>TargetId: label
        String oldLine = sourceId + oldArrow + targetId;
        String newLine = sourceId + newArrow + targetId;
        return source.replace(oldLine, newLine);
    }

    /**
     * Reconnect an edge to different source/target nodes.
     */
    private static String reconnectEdgeInSource(String source, String diagramType,
                                                 DiagramEdge edge,
                                                 String newSourceId, String newTargetId) {
        if (edge instanceof SequenceMessage) {
            SequenceMessage sm = (SequenceMessage) edge;
            String oldLine = edge.getSourceId() + sm.getMessageType().getMermaidSyntax()
                    + edge.getTargetId();
            String newLine = newSourceId + sm.getMessageType().getMermaidSyntax() + newTargetId;
            return source.replace(oldLine, newLine);
        }

        if (edge instanceof FlowchartEdge) {
            FlowchartEdge fe = (FlowchartEdge) edge;
            String arrow = buildFlowchartArrow(fe.getLineStyle(), fe.getHeadType());
            String label = fe.getLabel().isEmpty() ? "" : "|" + fe.getLabel() + "|";
            String oldLine = edge.getSourceId() + " " + arrow + label + " " + edge.getTargetId();
            String newLine = newSourceId + " " + arrow + label + " " + newTargetId;
            return source.replace(oldLine, newLine);
        }

        if (edge instanceof ErRelationship) {
            // Find the ER line containing both old source and target
            String[] lines = source.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (line.contains(edge.getSourceId()) && line.contains(edge.getTargetId())
                        && (line.contains("--") || line.contains("=="))) {
                    line = line.replace(edge.getSourceId(), newSourceId)
                               .replace(edge.getTargetId(), newTargetId);
                }
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        }

        // Generic fallback: replace IDs in the relevant source line
        String[] lines = source.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.contains(edge.getSourceId()) && line.contains(edge.getTargetId())) {
                line = line.replace(edge.getSourceId(), newSourceId)
                           .replace(edge.getTargetId(), newTargetId);
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Add a new node to the Mermaid source, adapting to the diagram type.
     */
    private static String addNodeToSource(String source, String diagramType,
                                           String nodeName, String shapeStr) {
        if ("erDiagram".equals(diagramType)) {
            return source + "\n    " + nodeName + " {\n        int id PK\n    }";
        }
        if ("classDiagram".equals(diagramType)) {
            return source + "\n    class " + nodeName + " {\n    }";
        }
        if ("sequence".equals(diagramType)) {
            // Add participant after the first line
            String[] lines = source.split("\n");
            StringBuilder sb = new StringBuilder();
            boolean inserted = false;
            for (String line : lines) {
                sb.append(line).append("\n");
                if (!inserted && line.trim().startsWith("participant")) {
                    sb.append("    participant ").append(nodeName).append("\n");
                    inserted = true;
                }
            }
            if (!inserted) {
                sb.append("    participant ").append(nodeName).append("\n");
            }
            return sb.toString().trim();
        }
        if ("stateDiagram".equals(diagramType)) {
            return source + "\n    " + nodeName;
        }
        if ("mindmap".equals(diagramType)) {
            return source + "\n    " + nodeName;
        }

        // Flowchart (default)
        NodeShape shape = NodeShape.RECTANGLE;
        try {
            shape = NodeShape.valueOf(shapeStr);
        } catch (Exception ignored) {}
        return source + "\n    " + shape.toMermaid(nodeName, nodeName);
    }

    /**
     * Add a new edge between two nodes, adapting to the diagram type.
     */
    private static String addEdgeToSource(String source, String diagramType,
                                           String sourceId, String targetId) {
        if ("erDiagram".equals(diagramType)) {
            return source + "\n    " + sourceId + " ||--o{ " + targetId + " : verknüpft";
        }
        if ("classDiagram".equals(diagramType)) {
            return source + "\n    " + sourceId + " --> " + targetId;
        }
        if ("sequence".equals(diagramType)) {
            return source + "\n    " + sourceId + "->>" + targetId + ": Nachricht";
        }
        if ("stateDiagram".equals(diagramType)) {
            return source + "\n    " + sourceId + " --> " + targetId;
        }
        // Flowchart default
        return source + "\n    " + sourceId + " --> " + targetId;
    }

    /**
     * Insert a new sequence fragment (loop/alt/opt) into the source.
     */
    private static String insertSequenceFragment(String source, String keyword, String condition) {
        // Find a good position: after the last message line, before any trailing "end"
        String[] lines = source.split("\n");
        StringBuilder sb = new StringBuilder();
        int lastMsgLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.contains("->>") || trimmed.contains("-->>")
                    || trimmed.contains("->") || trimmed.contains("-->")) {
                lastMsgLine = i;
            }
        }
        int insertAfter = lastMsgLine >= 0 ? lastMsgLine : lines.length - 1;
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
            if (i == insertAfter) {
                // Determine actors from source for a placeholder message
                String actor1 = "Alice";
                String actor2 = "Bob";
                for (String line : lines) {
                    String t = line.trim();
                    if (t.startsWith("participant ")) {
                        String name = t.substring("participant ".length()).trim();
                        if (actor1.equals("Alice")) actor1 = name;
                        else if (actor2.equals("Bob")) actor2 = name;
                    }
                }
                sb.append("    ").append(keyword).append(" ").append(condition).append("\n");
                sb.append("        ").append(actor1).append("->>").append(actor2).append(": Nachricht\n");
                sb.append("    end\n");
            }
        }
        return sb.toString().trim();
    }

    /** Remove a class member line from a class block in the Mermaid source. */
    private static String removeClassMember(String source, String className, String memberMermaid) {
        String escaped = Pattern.quote(memberMermaid.trim());
        String pattern = "(?m)^[ \\t]*" + escaped + "[ \\t]*$\\n?";
        return source.replaceFirst(pattern, "");
    }

    /** Add a class member line to a class block in the Mermaid source. */
    private static String addClassMember(String source, String className, String memberMermaid) {
        String classBlockPattern = "(class\\s+" + Pattern.quote(className) + "\\s*\\{[^}]*)(\\})";
        Matcher m = Pattern.compile(classBlockPattern, Pattern.DOTALL).matcher(source);
        if (m.find()) {
            String before = m.group(1);
            if (!before.endsWith("\n")) before += "\n";
            return source.substring(0, m.start())
                    + before + "        " + memberMermaid + "\n"
                    + "    }"
                    + source.substring(m.end());
        }
        return source;
    }

    /** Remove an ER attribute line from an entity block. */
    private static String removeErAttribute(String source, String entityName, String attrMermaid) {
        // Try exact line match first
        String escaped = Pattern.quote(attrMermaid.trim());
        String pattern = "(?m)^[ \\t]*" + escaped + "[ \\t]*$\\n?";
        String result = source.replaceFirst(pattern, "");
        if (!result.equals(source)) return result;

        // Fallback: try to find and remove within the entity block
        String blockPattern = Pattern.quote(entityName) + "\\s*\\{";
        Matcher bm = Pattern.compile(blockPattern).matcher(source);
        if (bm.find()) {
            int blockStart = bm.end();
            int braceDepth = 1;
            int blockEnd = blockStart;
            while (blockEnd < source.length() && braceDepth > 0) {
                char c = source.charAt(blockEnd);
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;
                blockEnd++;
            }
            // Within the block, find and remove the attribute line
            String block = source.substring(blockStart, blockEnd - 1);
            String[] lines = block.split("\n");
            StringBuilder sb = new StringBuilder();
            boolean removed = false;
            for (String line : lines) {
                if (!removed && line.trim().equals(attrMermaid.trim())) {
                    removed = true;
                    continue;
                }
                sb.append(line).append("\n");
            }
            if (removed) {
                return source.substring(0, blockStart) + sb.toString()
                        + source.substring(blockEnd - 1);
            }
        }
        return source;
    }

    /**
     * Parse ER attribute lines directly from Mermaid source code for a given entity.
     * Returns the raw attribute lines (e.g. "string isbn PK", "int jahr").
     */
    private static List<String> parseErAttributeLinesFromSource(String source, String entityName) {
        List<String> attrs = new ArrayList<String>();
        // Find the entity block: ENTITY_NAME {  ...  }
        String blockPattern = Pattern.quote(entityName) + "\\s*\\{";
        Matcher m = Pattern.compile(blockPattern).matcher(source);
        if (!m.find()) return attrs;

        int blockStart = m.end();
        int braceDepth = 1;
        int blockEnd = blockStart;
        while (blockEnd < source.length() && braceDepth > 0) {
            char c = source.charAt(blockEnd);
            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            blockEnd++;
        }
        // blockEnd now points past the closing }
        String block = source.substring(blockStart, blockEnd - 1);
        String[] lines = block.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // An ER attribute line has at least "type name"
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2) {
                attrs.add(trimmed);
            }
        }
        return attrs;
    }

    /** Add an ER attribute line to an entity block. */
    private static String addErAttribute(String source, String entityName, String attrMermaid) {
        String blockPattern = "(" + Pattern.quote(entityName) + "\\s*\\{[^}]*)(\\})";
        Matcher m = Pattern.compile(blockPattern, Pattern.DOTALL).matcher(source);
        if (m.find()) {
            String before = m.group(1);
            if (!before.endsWith("\n")) before += "\n";
            return source.substring(0, m.start())
                    + before + "        " + attrMermaid + "\n"
                    + "    }"
                    + source.substring(m.end());
        }
        return source;
    }

    // ═══════════════════════════════════════════════════════════
    //  UI helper methods
    // ═══════════════════════════════════════════════════════════

    /**
     * Delete a node from the Mermaid source, including all edges referencing it.
     */
    private static String deleteNodeFromSource(String source, String diagramType, DiagramNode node) {
        String id = node.getId();
        String escaped = Pattern.quote(id);

        if ("erDiagram".equals(diagramType)) {
            // Remove entity block: ENTITY { ... }
            String blockPattern = "(?m)^[ \\t]*" + escaped + "\\s*\\{[^}]*\\}\\s*$";
            source = source.replaceFirst(blockPattern, "").trim();
            // Remove relationships referencing this entity
            source = source.replaceAll("(?m)^[ \\t]*" + escaped + "\\s+[|o{}]+(?:--|==)[|o{}]+\\s+\\S+.*$\\n?", "");
            source = source.replaceAll("(?m)^[ \\t]*\\S+\\s+[|o{}]+(?:--|==)[|o{}]+\\s+" + escaped + ".*$\\n?", "");
            return source.trim();
        }

        if ("classDiagram".equals(diagramType)) {
            // Remove class block
            String blockPattern = "(?s)\\s*class\\s+" + escaped + "\\s*\\{[^}]*\\}";
            source = source.replaceFirst(blockPattern, "");
            // Remove relations referencing this class
            source = source.replaceAll("(?m)^[ \\t]*" + escaped + "\\s+(?:<\\|--|<\\|\\.\\.|\\*--|o--|-->|\\.\\.>|--)\\s+\\S+.*$\\n?", "");
            source = source.replaceAll("(?m)^[ \\t]*\\S+\\s+(?:<\\|--|<\\|\\.\\.|\\*--|o--|-->|\\.\\.>|--)\\s+" + escaped + ".*$\\n?", "");
            return source.trim();
        }

        if ("sequence".equals(diagramType)) {
            // Remove participant declaration
            source = source.replaceAll("(?m)^[ \\t]*participant\\s+" + escaped + "\\s*$\\n?", "");
            // Remove messages involving this participant
            source = source.replaceAll("(?m)^[ \\t]*" + escaped + "\\s*(?:->>|-->>|-\\)|--\\)|->|-->|-x|--x)\\s*\\S+.*$\\n?", "");
            source = source.replaceAll("(?m)^[ \\t]*\\S+\\s*(?:->>|-->>|-\\)|--\\)|->|-->|-x|--x)\\s*" + escaped + ".*$\\n?", "");
            return source.trim();
        }

        if ("stateDiagram".equals(diagramType)) {
            // Remove transitions involving this state
            source = source.replaceAll("(?m)^[ \\t]*" + escaped + "\\s+-->\\s+\\S+.*$\\n?", "");
            source = source.replaceAll("(?m)^[ \\t]*\\S+\\s+-->\\s+" + escaped + ".*$\\n?", "");
            // Remove standalone state declaration
            source = source.replaceAll("(?m)^[ \\t]*" + escaped + "\\s*$\\n?", "");
            return source.trim();
        }

        // Flowchart / mindmap / generic
        // Remove lines containing edges referencing this node
        String[] lines = source.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip lines that define or reference this node in edges
            if (trimmed.contains(id)) {
                // Check if it's an edge line (contains arrow)
                boolean isEdgeLine = trimmed.contains("-->") || trimmed.contains("-.->")
                        || trimmed.contains("==>") || trimmed.contains("---")
                        || trimmed.contains("-.-") || trimmed.contains("===")
                        || trimmed.contains("--o") || trimmed.contains("--x");
                // Check if it's a standalone node definition
                boolean isNodeDef = trimmed.startsWith(id + "[") || trimmed.startsWith(id + "(")
                        || trimmed.startsWith(id + "{") || trimmed.startsWith(id + "[/")
                        || trimmed.equals(id);
                if (isEdgeLine || isNodeDef) continue;
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString().trim();
    }

    /**
     * Delete an edge from the Mermaid source.
     */
    private static String deleteEdgeFromSource(String source, String diagramType, DiagramEdge edge) {
        String srcId = edge.getSourceId();
        String tgtId = edge.getTargetId();
        String label = edge.getLabel();

        if (edge instanceof SequenceMessage) {
            SequenceMessage sm = (SequenceMessage) edge;
            String arrow = sm.getMessageType().getMermaidSyntax();
            String labelPart = (label != null && !label.isEmpty()) ? ": " + label : "";
            // Try to remove the exact line
            String pattern = "(?m)^[ \\t]*" + Pattern.quote(srcId) + "\\s*"
                    + Pattern.quote(arrow) + "\\s*" + Pattern.quote(tgtId)
                    + Pattern.quote(labelPart) + "\\s*$\\n?";
            String result = source.replaceFirst(pattern, "");
            if (!result.equals(source)) return result.trim();
        }

        if (edge instanceof ErRelationship) {
            // Remove the ER relationship line
            String srcQ = Pattern.quote(srcId);
            String tgtQ = Pattern.quote(tgtId);
            String pattern = "(?m)^[ \\t]*" + srcQ + "\\s+[|o{}]+(?:--|==)[|o{}]+\\s+" + tgtQ + ".*$\\n?";
            String result = source.replaceFirst(pattern, "");
            if (!result.equals(source)) return result.trim();
        }

        if (edge instanceof ClassRelation) {
            ClassRelation cr = (ClassRelation) edge;
            String syntax = cr.getRelationType().getMermaidSyntax();
            String labelPart = (label != null && !label.isEmpty()) ? " : " + label : "";
            String pattern = "(?m)^[ \\t]*" + Pattern.quote(srcId) + "\\s+"
                    + Pattern.quote(syntax) + "\\s+" + Pattern.quote(tgtId)
                    + Pattern.quote(labelPart) + "\\s*$\\n?";
            String result = source.replaceFirst(pattern, "");
            if (!result.equals(source)) return result.trim();
        }

        if (edge instanceof StateTransition) {
            String labelPart = (label != null && !label.isEmpty()) ? " : " + label : "";
            String pattern = "(?m)^[ \\t]*" + Pattern.quote(srcId) + "\\s+-->\\s+"
                    + Pattern.quote(tgtId) + Pattern.quote(labelPart) + "\\s*$\\n?";
            String result = source.replaceFirst(pattern, "");
            if (!result.equals(source)) return result.trim();
        }

        // Flowchart / generic: find and remove the edge line
        String[] lines = source.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean removed = false;
        for (String line : lines) {
            if (!removed && line.contains(srcId) && line.contains(tgtId)) {
                String trimmed = line.trim();
                boolean isEdgeLine = trimmed.contains("-->") || trimmed.contains("-.->")
                        || trimmed.contains("==>") || trimmed.contains("---")
                        || trimmed.contains("-.-") || trimmed.contains("===")
                        || trimmed.contains("--o") || trimmed.contains("--x");
                if (isEdgeLine) {
                    removed = true;
                    continue;
                }
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString().trim();
    }

    /** Handle a click on the diagram panel — find and select the element at the click position. */
    private static void handleClick(MouseEvent e, JPanel dPanel, CardState cs,
                                     RenderedDiagram diagram, int imgW, int imgH,
                                     Rectangle[] highlightRect,
                                     JTextArea detailArea, Runnable refreshEditPanel) {
        int pw = dPanel.getWidth(), ph = dPanel.getHeight();
        double scale = Math.min((double) pw / imgW, (double) ph / imgH);
        int dw = (int)(imgW * scale), dh = (int)(imgH * scale);
        int ox = (pw - dw) / 2, oy = (ph - dh) / 2;
        double imgPx = (e.getX() - ox) / scale;
        double imgPy = (e.getY() - oy) / scale;
        double vbX = diagram.getViewBoxX();
        double vbY = diagram.getViewBoxY();
        double vbW = diagram.getViewBoxWidth();
        double vbH = diagram.getViewBoxHeight();
        double svgX = vbX + (imgPx / imgW) * vbW;
        double svgY = vbY + (imgPy / imgH) * vbH;

        // ── Try to find a node (smallest non-fragment first, then smallest fragment) ──
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
        // Fall back to fragment if no regular node found at click position
        if (foundNode == null) foundNode = foundFragment;
        if (foundNode != null) {
            int rx = (int)((foundNode.getX() - vbX) / vbW * imgW);
            int ry = (int)((foundNode.getY() - vbY) / vbH * imgH);
            int rw = (int)(foundNode.getWidth() / vbW * imgW);
            int rh = (int)(foundNode.getHeight() / vbH * imgH);
            highlightRect[0] = new Rectangle(rx, ry, rw, rh);
            cs.selectedNode = foundNode;
            cs.selectedEdge = null;
            detailArea.setText(formatNodeDetails(foundNode));
            detailArea.setCaretPosition(0);
            refreshEditPanel.run();
            dPanel.repaint();
            return;
        }

        // ── Try to find an edge (prefer smallest bounding box for precision) ──
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
            int rx = (int)((foundEdge.getX() - vbX) / vbW * imgW);
            int ry = (int)((foundEdge.getY() - vbY) / vbH * imgH);
            int rw = (int)(foundEdge.getWidth() / vbW * imgW);
            int rh = (int)(foundEdge.getHeight() / vbH * imgH);
            highlightRect[0] = new Rectangle(rx, ry, Math.max(rw, 10), Math.max(rh, 10));
            cs.selectedNode = null;
            cs.selectedEdge = foundEdge;
            detailArea.setText(formatEdgeDetails(foundEdge));
            detailArea.setCaretPosition(0);
            refreshEditPanel.run();
            dPanel.repaint();
            return;
        }

        // ── Nothing found ──
        highlightRect[0] = null;
        cs.selectedNode = null;
        cs.selectedEdge = null;
        detailArea.setText(String.format(
                "Kein Element an Position (%.0f, %.0f) gefunden.\n"
                        + "SVG-Koordinaten: (%.1f, %.1f)\n"
                        + "Vorhandene Nodes: %d\n"
                        + "Vorhandene Edges: %d",
                (double) e.getX(), (double) e.getY(),
                svgX, svgY,
                diagram.getNodes().size(),
                diagram.getEdges().size()));
        refreshEditPanel.run();
        dPanel.repaint();
    }

    /** Draw a small arrowhead at the 'to' point on the drag line. */
    private static void drawArrowHead(Graphics2D g2, Point from, Point to) {
        double angle = Math.atan2(to.y - from.y, to.x - from.x);
        int arrowLen = 12;
        double spread = Math.toRadians(25);
        int x1 = to.x - (int)(arrowLen * Math.cos(angle - spread));
        int y1 = to.y - (int)(arrowLen * Math.sin(angle - spread));
        int x2 = to.x - (int)(arrowLen * Math.cos(angle + spread));
        int y2 = to.y - (int)(arrowLen * Math.sin(angle + spread));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawLine(to.x, to.y, x1, y1);
        g2.drawLine(to.x, to.y, x2, y2);
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        return l;
    }

    private static JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        b.setMargin(new Insets(1, 4, 1, 4));
        return b;
    }

    private static JPanel separator(String title) {
        JPanel sep = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sep.setOpaque(false);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel l = new JLabel("\u2500\u2500 " + title + " \u2500\u2500");
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        l.setForeground(new Color(60, 120, 60));
        sep.add(l);
        return sep;
    }

    // ═══════════════════════════════════════════════════════════
    //  Element detail formatting
    // ═══════════════════════════════════════════════════════════

    private static String formatNodeDetails(DiagramNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NODE SELEKTIERT ===\n");
        sb.append("Java-Typ:  ").append(node.getClass().getSimpleName()).append("\n");
        sb.append("ID:        ").append(node.getId()).append("\n");
        sb.append("Label:     ").append(node.getLabel()).append("\n");
        sb.append("Kind:      ").append(node.getKind()).append("\n");
        sb.append("SVG-ID:    ").append(node.getSvgId()).append("\n");
        sb.append("Bounds:    ").append(String.format(Locale.US,
                "[%.0f, %.0f, %.0f x %.0f]",
                node.getX(), node.getY(), node.getWidth(), node.getHeight())).append("\n");

        // ── Type-specific properties ──
        if (node instanceof FlowchartNode) {
            FlowchartNode fn = (FlowchartNode) node;
            sb.append("\n--- FlowchartNode ---\n");
            sb.append("Shape:     ").append(fn.getShape()).append(" (").append(fn.getShape().name()).append(")\n");
            sb.append("Syntax:    ").append(fn.getShape().toMermaid(fn.getId(), fn.getLabel())).append("\n");
        }
        else if (node instanceof ClassNode) {
            ClassNode cn = (ClassNode) node;
            sb.append("\n--- ClassNode ---\n");
            sb.append("Stereotype: ").append(cn.getStereotype().isEmpty() ? "(keiner)" : "<<" + cn.getStereotype() + ">>").append("\n");
            sb.append("Felder:    ").append(cn.getFields().size()).append("\n");
            for (ClassMember f : cn.getFields()) {
                sb.append("  ").append(f.toMermaid()).append("\n");
            }
            sb.append("Methoden:  ").append(cn.getMethods().size()).append("\n");
            for (ClassMember m : cn.getMethods()) {
                sb.append("  ").append(m.toMermaid()).append("\n");
            }
        }
        else if (node instanceof ErEntityNode) {
            ErEntityNode en = (ErEntityNode) node;
            sb.append("\n--- ErEntityNode ---\n");
            sb.append("Attribute: ").append(en.getAttributes().size()).append("\n");
            for (ErAttribute a : en.getAttributes()) {
                sb.append("  ").append(a.toMermaid()).append("\n");
            }
            ErAttribute pk = en.getPrimaryKey();
            if (pk != null) sb.append("PK:        ").append(pk.getName()).append("\n");
        }
        else if (node instanceof SequenceActorNode) {
            SequenceActorNode sa = (SequenceActorNode) node;
            sb.append("\n--- SequenceActorNode ---\n");
            sb.append("ActorType: ").append(sa.getActorType()).append("\n");
        }
        else if (node instanceof StateDiagramNode) {
            StateDiagramNode sn = (StateDiagramNode) node;
            sb.append("\n--- StateDiagramNode ---\n");
            sb.append("isStart:   ").append(sn.isStartState()).append("\n");
            sb.append("isEnd:     ").append(sn.isEndState()).append("\n");
            sb.append("composite: ").append(sn.isComposite()).append("\n");
        }
        else if (node instanceof MindmapItemNode) {
            MindmapItemNode mi = (MindmapItemNode) node;
            sb.append("\n--- MindmapItemNode ---\n");
            sb.append("Depth:     ").append(mi.getDepth()).append("\n");
            sb.append("isRoot:    ").append(mi.isRoot()).append("\n");
        }
        else if (node instanceof RequirementItemNode) {
            RequirementItemNode ri = (RequirementItemNode) node;
            sb.append("\n--- RequirementItemNode ---\n");
            sb.append("ReqNodeType: ").append(ri.getReqNodeType()).append("\n");
            sb.append("ReqID:     ").append(ri.getReqId()).append("\n");
            sb.append("Risk:      ").append(ri.getRisk()).append("\n");
        }
        else if (node instanceof SequenceFragment) {
            SequenceFragment sf = (SequenceFragment) node;
            sb.append("\n--- SequenceFragment ---\n");
            sb.append("FragType:  ").append(sf.getFragmentType()).append(" (").append(sf.getFragmentType().getKeyword()).append(")\n");
            sb.append("Condition: ").append(sf.getCondition().isEmpty() ? "(keine)" : sf.getCondition()).append("\n");
            sb.append("Header:    ").append(sf.toMermaidHeader()).append("\n");
        }

        if (!node.getProperties().isEmpty()) {
            sb.append("\nProperties:\n");
            for (Map.Entry<String, String> p : node.getProperties().entrySet()) {
                sb.append("  ").append(p.getKey()).append(" = ").append(p.getValue()).append("\n");
            }
        }

        return sb.toString();
    }

    private static String formatEdgeDetails(DiagramEdge edge) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EDGE SELEKTIERT ===\n");
        sb.append("Java-Typ:  ").append(edge.getClass().getSimpleName()).append("\n");
        sb.append("ID:        ").append(edge.getId()).append("\n");
        sb.append("Source:    ").append(edge.getSourceId()).append("\n");
        sb.append("Target:    ").append(edge.getTargetId()).append("\n");
        sb.append("Label:     ").append(edge.getLabel().isEmpty() ? "(keins)" : edge.getLabel()).append("\n");
        sb.append("Kind:      ").append(edge.getKind()).append("\n");
        sb.append("Bounds:    ").append(String.format(Locale.US,
                "[%.0f, %.0f, %.0f x %.0f]",
                edge.getX(), edge.getY(), edge.getWidth(), edge.getHeight())).append("\n");

        if (edge instanceof FlowchartEdge) {
            FlowchartEdge fe = (FlowchartEdge) edge;
            sb.append("\n--- FlowchartEdge ---\n");
            sb.append("LineStyle: ").append(fe.getLineStyle()).append(" (").append(fe.getLineStyle().name()).append(")\n");
            sb.append("HeadType:  ").append(fe.getHeadType()).append(" (").append(fe.getHeadType().name()).append(")\n");
            sb.append("TailType:  ").append(fe.getTailType()).append(" (").append(fe.getTailType().name()).append(")\n");
            sb.append("Arrow:     ").append(fe.toMermaidArrow()).append("\n");
        }
        else if (edge instanceof ClassRelation) {
            ClassRelation cr = (ClassRelation) edge;
            sb.append("\n--- ClassRelation ---\n");
            sb.append("RelationType: ").append(cr.getRelationType()).append(" (").append(cr.getRelationType().name()).append(")\n");
            sb.append("LineStyle:    ").append(cr.getLineStyle()).append("\n");
            sb.append("HeadType:     ").append(cr.getHeadType()).append("\n");
            sb.append("TailType:     ").append(cr.getTailType()).append("\n");
            sb.append("SrcMultipl.:  ").append(cr.getSourceMultiplicity().isEmpty() ? "-" : cr.getSourceMultiplicity()).append("\n");
            sb.append("TgtMultipl.:  ").append(cr.getTargetMultiplicity().isEmpty() ? "-" : cr.getTargetMultiplicity()).append("\n");
            sb.append("Mermaid:      ").append(cr.toMermaid()).append("\n");
        }
        else if (edge instanceof ErRelationship) {
            ErRelationship er = (ErRelationship) edge;
            sb.append("\n--- ErRelationship ---\n");
            sb.append("SourceCard:   ").append(er.getSourceCardinality()).append("\n");
            sb.append("TargetCard:   ").append(er.getTargetCardinality()).append("\n");
            sb.append("Identifying:  ").append(er.isIdentifying()).append("\n");
        }
        else if (edge instanceof SequenceMessage) {
            SequenceMessage sm = (SequenceMessage) edge;
            sb.append("\n--- SequenceMessage ---\n");
            sb.append("MessageType:  ").append(sm.getMessageType()).append(" (").append(sm.getMessageType().name()).append(")\n");
            sb.append("isDashed:     ").append(sm.isDashed()).append("\n");
            sb.append("activating:   ").append(sm.isActivating()).append("\n");
            sb.append("deactivating: ").append(sm.isDeactivating()).append("\n");
        }
        else if (edge instanceof StateTransition) {
            StateTransition st = (StateTransition) edge;
            sb.append("\n--- StateTransition ---\n");
            sb.append("Guard:        ").append(st.getGuard().isEmpty() ? "-" : st.getGuard()).append("\n");
        }

        if (edge.getPathData() != null) {
            String pd = edge.getPathData();
            sb.append("\nPath (").append(pd.length()).append(" chars): ")
              .append(pd.length() > 80 ? pd.substring(0, 80) + "..." : pd).append("\n");
        }

        return sb.toString();
    }
}

