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
                                g2.dispose();
                            }
                        };
                        dPanel.setBackground(Color.WHITE);
                        dPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

                        dPanel.addMouseListener(new MouseAdapter() {
                            @Override public void mouseClicked(MouseEvent e) {
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

                                // ── Try to find a node ──
                                DiagramNode foundNode = diagram.findNodeAt(svgX, svgY);
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
                                    refreshEditPanel[0].run();
                                    dPanel.repaint();
                                    return;
                                }

                                // ── Try to find an edge ──
                                DiagramEdge foundEdge = null;
                                for (DiagramEdge edge : diagram.getEdges()) {
                                    if (edge.containsApprox(svgX, svgY)) {
                                        foundEdge = edge;
                                        break;
                                    }
                                }
                                if (foundEdge != null) {
                                    int rx = (int)((foundEdge.getX() - vbX) / vbW * imgW);
                                    int ry = (int)((foundEdge.getY() - vbY) / vbH * imgH);
                                    int rw = (int)(foundEdge.getWidth() / vbW * imgW);
                                    int rh = (int)(foundEdge.getHeight() / vbH * imgH);
                                    highlightRect[0] = new Rectangle(rx, ry,
                                            Math.max(rw, 10), Math.max(rh, 10));
                                    cs.selectedNode = null;
                                    cs.selectedEdge = foundEdge;
                                    detailArea.setText(formatEdgeDetails(foundEdge));
                                    detailArea.setCaretPosition(0);
                                    refreshEditPanel[0].run();
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
                                        (double)e.getX(), (double)e.getY(),
                                        svgX, svgY,
                                        diagram.getNodes().size(),
                                        diagram.getEdges().size()));
                                refreshEditPanel[0].run();
                                dPanel.repaint();
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
            card.add(Box.createVerticalStrut(4));
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

        // ── Rename row (available for ALL node types) ──
        JPanel renameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        renameRow.setOpaque(false);
        renameRow.add(label("Name:"));
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
                cs.currentSource = renameInSource(cs.currentSource, node.getId(), newName);
                onApplied.run();
            }
        });

        // ── ClassNode-specific: fields & methods ──
        if (node instanceof ClassNode) {
            buildClassMemberEditor(panel, (ClassNode) node, cs, onApplied);
        }

        // ── FlowchartNode-specific: shape selector ──
        if (node instanceof FlowchartNode) {
            buildShapeSelector(panel, (FlowchartNode) node, cs, onApplied);
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
        JButton applyBtn = new JButton("Anwenden");
        applyBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        labelRow.add(applyBtn);
        panel.add(labelRow);

        applyBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String newLabel = labelField.getText().trim();
                if (newLabel.equals(edge.getLabel())) return;
                if (!edge.getLabel().isEmpty()) {
                    // Flowchart/state edge labels use |label| syntax
                    cs.currentSource = cs.currentSource.replace(
                            "|" + edge.getLabel() + "|",
                            newLabel.isEmpty() ? "" : "|" + newLabel + "|");
                }
                onApplied.run();
            }
        });

        // ── SequenceMessage: message text edit ──
        if (edge instanceof SequenceMessage) {
            panel.add(Box.createVerticalStrut(4));
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
                    if (newMsg.equals(oldMsg) || oldMsg.isEmpty()) return;
                    // Sequence messages: "Source->>Target: Label"
                    cs.currentSource = cs.currentSource.replace(
                            ": " + oldMsg, ": " + newMsg);
                    onApplied.run();
                }
            });
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Source manipulation helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Rename an element in the Mermaid source code.
     * Uses word-boundary-aware replacement to avoid partial matches.
     */
    private static String renameInSource(String source, String oldName, String newName) {
        String escaped = Pattern.quote(oldName);
        return source.replaceAll("\\b" + escaped + "\\b", Matcher.quoteReplacement(newName));
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

    // ═══════════════════════════════════════════════════════════
    //  UI helper methods
    // ═══════════════════════════════════════════════════════════

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

