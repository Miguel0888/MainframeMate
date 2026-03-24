package de.bund.zrb.mermaid;

import com.aresstack.mermaid.layout.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.wiki.ui.SvgRenderer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

/**
 * Interactive visual selection test for the mermaid-java layout + highlighting API.
 * <p>
 * Tests whether nodes and edges can be correctly identified via click coordinates
 * and displays their type-specific properties — simulating the core interaction
 * pattern of a graphical diagram editor like <em>mermaid-designer</em>.
 * <p>
 * Each test case shows a rendered diagram that the tester can click on.
 * The system identifies the clicked element using {@link RenderedDiagram#findNodeAt}
 * and {@link DiagramEdge#containsApprox}, then displays its polymorphic type and
 * properties in a detail panel.  A yellow overlay highlights the selection.
 * <p>
 * <b>Test procedure:</b>
 * <ol>
 *   <li>Click on the elements described in the task instructions</li>
 *   <li>Verify the detail panel shows the correct type &amp; properties</li>
 *   <li>Answer the questions for each test case</li>
 *   <li>Submit results when all questions are answered</li>
 * </ol>
 */
public final class MermaidSelectionTest {

    private static final int COUNTDOWN_SECONDS = 15 * 60;

    private MermaidSelectionTest() {}

    // ═══════════════════════════════════════════════════════════
    //  Data model
    // ═══════════════════════════════════════════════════════════

    public static final class TestCaseResult {
        public String id;
        public String title;
        public String mermaidCode;
        public String annotation;
        public Map<String, String> questionAnswers;
        public boolean renderError;
        public boolean rasterError;
        public int clickCount;
        public List<String> selectedElements;
    }

    private static final class SelectionSpec {
        final String id, title, task, mermaidCode;
        final Map<String, String> questions;

        SelectionSpec(String id, String title, String task,
                      String mermaidCode, String[][] questions) {
            this.id = id;
            this.title = title;
            this.task = task;
            this.mermaidCode = mermaidCode;
            this.questions = new LinkedHashMap<String, String>();
            for (String[] q : questions) {
                this.questions.put(q[0], q[1]);
            }
        }
    }

    private static final class RenderedCase {
        final SelectionSpec spec;
        final BufferedImage image;
        final String svg;
        final RenderedDiagram diagram;
        final boolean renderError, rasterError;

        RenderedCase(SelectionSpec spec, BufferedImage image, String svg,
                     RenderedDiagram diagram, boolean renderError, boolean rasterError) {
            this.spec = spec;
            this.image = image;
            this.svg = svg;
            this.diagram = diagram;
            this.renderError = renderError;
            this.rasterError = rasterError;
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
                "Klicken Sie nacheinander auf jeden der 6 Knoten.\n"
                + "Prüfen Sie im Detail-Panel: Wird der korrekte Typ (FlowchartNode)\n"
                + "und die richtige Form (Rechteck, Stadium, Kreis, Raute, Sechseck, Trapez) angezeigt?",
                "graph LR\n"
                + "    Rechteck[Rechteck] --> Rund([Rund])\n"
                + "    Rund --> Kreis((Kreis))\n"
                + "    Kreis --> Raute{Raute}\n"
                + "    Raute --> Sechseck{{Sechseck}}\n"
                + "    Sechseck --> Trapez[/Trapez/]",
                new String[][] {
                    {"node-click", "Wird beim Klick auf einen Knoten ein Element erkannt (gelb markiert)?"},
                    {"correct-type", "Zeigt das Detail-Panel den Typ 'FlowchartNode' an?"},
                    {"correct-shape", "Stimmt die angezeigte Form (Shape) mit der visuellen Darstellung überein?"},
                    {"correct-id", "Stimmt die angezeigte ID mit dem Knotennamen überein?"},
                    {"all-clickable", "Lassen sich alle 6 Knoten einzeln anklicken und selektieren?"}
                }));

        // ── 2. Flowchart: Edge styles ──
        s.add(new SelectionSpec("fc-edges",
                "2 — Flowchart: Kantenstile selektieren",
                "Klicken Sie auf die Linien zwischen den Knoten (nicht auf die Knoten selbst).\n"
                + "Es gibt 3 Kanten: durchgezogen (Alpha→Delta), gestrichelt (Beta→Echo),\n"
                + "dick (Gamma→Foxtrot). Prüfen Sie den erkannten LineStyle im Detail-Panel.",
                "graph LR\n"
                + "    Alpha --> Delta\n"
                + "    Beta -.->|dashed| Echo\n"
                + "    Gamma ==>|thick| Foxtrot",
                new String[][] {
                    {"edge-click", "Wird beim Klick auf eine Kante ein Edge-Element erkannt?"},
                    {"correct-edge-type", "Zeigt das Detail-Panel 'FlowchartEdge' als Typ an?"},
                    {"line-style", "Wird der LineStyle (SOLID, DASHED, THICK) korrekt erkannt?"},
                    {"source-target", "Sind Source und Target der Kante korrekt zugeordnet?"},
                    {"edge-label", "Werden die Labels 'dashed' und 'thick' angezeigt?"}
                }));

        // ── 3. Class diagram ──
        s.add(new SelectionSpec("class-sel",
                "3 — Klassendiagramm: Klassen und Relationen",
                "Klicken Sie auf die Klasse 'Animal': Es sollte ein ClassNode\n"
                + "mit Stereotype '<<abstract>>' und Feldern/Methoden erscheinen.\n"
                + "Klicken Sie dann auf die Vererbungslinie Dog→Animal:\n"
                + "Es sollte eine ClassRelation mit RelationType INHERITANCE erscheinen.",
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
                + "    Animal <|-- Cat",
                new String[][] {
                    {"class-node", "Wird beim Klick auf eine Klasse ein 'ClassNode' erkannt?"},
                    {"members", "Zeigt das Detail-Panel Felder und Methoden an?"},
                    {"relation", "Wird die Vererbungslinie als 'ClassRelation' erkannt?"},
                    {"rel-type", "Ist der RelationType als INHERITANCE angezeigt?"},
                    {"all-three", "Lassen sich alle drei Klassen (Animal, Dog, Cat) einzeln selektieren?"}
                }));

        // ── 4. Sequence diagram ──
        s.add(new SelectionSpec("seq-sel",
                "4 — Sequenzdiagramm: Akteure und Nachrichten",
                "Klicken Sie auf die Actor-Boxen (Chef, Alice, Bob):\n"
                + "Es sollte ein SequenceActorNode mit ActorType PARTICIPANT erscheinen.\n"
                + "Klicken Sie dann auf eine Nachrichtenlinie: Es sollte eine\n"
                + "SequenceMessage mit dem korrekten MessageType (SYNC_SOLID oder SYNC_DOTTED) erscheinen.",
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
                + "    Alice->>Chef: Ergebnis",
                new String[][] {
                    {"actor-click", "Wird beim Klick auf eine Actor-Box ein 'SequenceActorNode' erkannt?"},
                    {"actor-type", "Ist der ActorType als PARTICIPANT angezeigt?"},
                    {"msg-click", "Wird beim Klick auf eine Nachrichtenlinie eine 'SequenceMessage' erkannt?"},
                    {"msg-type", "Ist der MessageType korrekt (SYNC_SOLID vs. SYNC_DOTTED für Feedback)?"},
                    {"msg-label", "Wird das Message-Label (z.B. 'Agenda') korrekt angezeigt?"}
                }));

        // ── 5. ER diagram ──
        s.add(new SelectionSpec("er-sel",
                "5 — ER-Diagramm: Entitäten und Beziehungen",
                "Klicken Sie auf die Entitäten BUCH, AUTOR, VERLAG.\n"
                + "Es sollte ein ErEntityNode erkannt werden.\n"
                + "Klicken Sie dann auf eine Beziehungslinie: Es sollte\n"
                + "eine ErRelationship erscheinen.",
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
                + "    VERLAG ||--o{ BUCH : verlegt",
                new String[][] {
                    {"entity-click", "Wird beim Klick auf eine Entität ein 'ErEntityNode' erkannt?"},
                    {"entity-id", "Stimmt die angezeigte Entity-ID (BUCH, AUTOR, VERLAG)?"},
                    {"er-rel", "Wird eine Beziehungslinie als 'ErRelationship' erkannt?"},
                    {"all-entities", "Lassen sich alle 3 Entitäten einzeln selektieren?"}
                }));

        // ── 6. State diagram ──
        s.add(new SelectionSpec("state-sel",
                "6 — Zustandsdiagramm: Zustände selektieren",
                "Klicken Sie auf die Zustände (Rot, Rot_Gelb, Gruen, Gelb).\n"
                + "Es sollte ein StateDiagramNode erkannt werden.\n"
                + "Prüfen Sie, ob Start-/End-Marker korrekt angezeigt werden.",
                "stateDiagram-v2\n"
                + "    [*] --> Rot\n"
                + "    Rot --> Rot_Gelb : warten\n"
                + "    Rot_Gelb --> Gruen\n"
                + "    Gruen --> Gelb\n"
                + "    Gelb --> Rot",
                new String[][] {
                    {"state-click", "Wird beim Klick auf einen Zustand ein 'StateDiagramNode' erkannt?"},
                    {"state-id", "Stimmt die angezeigte State-ID?"},
                    {"multiple", "Lassen sich mehrere Zustände nacheinander selektieren?"}
                }));

        // ── 7. Mindmap ──
        s.add(new SelectionSpec("mm-sel",
                "7 — Mindmap: Knoten in der Baumstruktur",
                "Klicken Sie auf den Wurzelknoten 'Humus' und dann auf Unterknoten.\n"
                + "Es sollte jeweils ein MindmapItemNode erkannt werden.\n"
                + "Der Root-Knoten sollte isRoot=true anzeigen.",
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
                + "      Mineralstoffe",
                new String[][] {
                    {"mm-click", "Wird beim Klick auf einen Mindmap-Knoten ein 'MindmapItemNode' erkannt?"},
                    {"root", "Zeigt der Wurzelknoten 'Humus' isRoot=true an?"},
                    {"label", "Stimmen die Labels der selektierten Knoten?"},
                    {"sub-click", "Lassen sich auch kleine Unterknoten (z.B. Naehrhumus) selektieren?"}
                }));

        return s;
    }

    // ═══════════════════════════════════════════════════════════
    //  main — render, then show interactive UI
    // ═══════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.err.println("[MermaidSelectionTest] Initialising renderer...");
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.out.println("{\"error\":\"mermaid.min.js not found on classpath\"}");
            System.exit(1);
            return;
        }

        List<SelectionSpec> specs = buildSpecs();
        final List<RenderedCase> rendered = new ArrayList<RenderedCase>();

        for (SelectionSpec spec : specs) {
            System.err.println("[MermaidSelectionTest] Rendering: " + spec.title);
            String svg = null;
            boolean renderErr = false;
            try {
                svg = renderer.renderToSvg(spec.mermaidCode);
                renderErr = (svg == null || !svg.contains("<svg"));
            } catch (Exception e) {
                System.err.println("[MermaidSelectionTest] Render error: " + e);
                renderErr = true;
            }
            if (renderErr) {
                rendered.add(new RenderedCase(spec, null, null, null, true, false));
                continue;
            }

            // Extract layout BEFORE Batik fixup (the raw SVG has clean ids)
            RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);

            // Apply Batik fixup for rasterisation
            svg = MermaidSvgFixup.fixForBatik(svg, spec.mermaidCode);

            byte[] svgBytes = svg.getBytes("UTF-8");
            BufferedImage img = null;
            boolean rasterErr = false;
            try {
                img = SvgRenderer.renderToBufferedImage(svgBytes);
                // NO auto-crop — we need pixel-perfect coordinate mapping
            } catch (Exception e) {
                System.err.println("[MermaidSelectionTest] Raster error: " + e);
                rasterErr = true;
            }
            if (img == null) rasterErr = true;

            rendered.add(new RenderedCase(spec, img, svg, diagram, false, rasterErr));
        }

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { showDialog(rendered); }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Swing dialog — interactive selection UI
    // ═══════════════════════════════════════════════════════════

    private static void showDialog(final List<RenderedCase> cases) {
        final JFrame frame = new JFrame("Mermaid Selection Test — Klick-Selektion");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(0, 0));
        frame.setAlwaysOnTop(true);

        final JTextArea[] annotationAreas = new JTextArea[cases.size()];
        final String[][] questionAnswers = new String[cases.size()][];
        // Track click counts and selected elements per case for JSON output
        final int[] clickCounts = new int[cases.size()];
        final List<List<String>> selectedElements = new ArrayList<List<String>>();
        for (int i = 0; i < cases.size(); i++) {
            int qCount = cases.get(i).spec.questions.size();
            questionAnswers[i] = new String[qCount];
            Arrays.fill(questionAnswers[i], "");
            selectedElements.add(new ArrayList<String>());
        }

        // ── TOP BAR ──
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        topBar.setBackground(new Color(40, 40, 40));

        final JLabel countdownLabel = new JLabel(formatTime(COUNTDOWN_SECONDS));
        countdownLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        countdownLabel.setForeground(Color.RED);
        topBar.add(countdownLabel, BorderLayout.WEST);

        final JLabel hintLabel = new JLabel("Klicken Sie auf Diagramm-Elemente, dann alle Fragen beantworten");
        hintLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        hintLabel.setForeground(new Color(200, 200, 200));
        hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topBar.add(hintLabel, BorderLayout.CENTER);

        final JButton submitBtn = new JButton("  \u2714 Ergebnisse absenden  ");
        submitBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        submitBtn.setEnabled(false);
        topBar.add(submitBtn, BorderLayout.EAST);
        frame.add(topBar, BorderLayout.NORTH);

        final Runnable updateSubmitState = new Runnable() {
            @Override public void run() {
                int total = 0, answered = 0;
                for (int i = 0; i < questionAnswers.length; i++)
                    for (int j = 0; j < questionAnswers[i].length; j++) {
                        total++;
                        if (!questionAnswers[i][j].isEmpty()) answered++;
                    }
                boolean allDone = (answered == total);
                submitBtn.setEnabled(allDone);
                if (allDone) {
                    hintLabel.setText("\u2705 Alle " + total + " Fragen beantwortet!");
                    hintLabel.setForeground(new Color(100, 255, 100));
                } else {
                    hintLabel.setText("Noch " + (total - answered) + " von " + total + " Fragen offen");
                    hintLabel.setForeground(new Color(200, 200, 200));
                }
            }
        };

        // ── CENTER: card row ──
        JPanel cardRow = new JPanel();
        cardRow.setLayout(new BoxLayout(cardRow, BoxLayout.X_AXIS));
        cardRow.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (int idx = 0; idx < cases.size(); idx++) {
            final int ci = idx;
            final RenderedCase rc = cases.get(idx);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(100, 100, 100), 2),
                            rc.spec.title,
                            TitledBorder.CENTER, TitledBorder.TOP,
                            new Font(Font.SANS_SERIF, Font.BOLD, 14)),
                    BorderFactory.createEmptyBorder(6, 8, 8, 8)));
            card.setBackground(Color.WHITE);

            // ── Task instructions ──
            JTextArea taskArea = new JTextArea("AUFGABE:\n" + rc.spec.task);
            taskArea.setEditable(false);
            taskArea.setLineWrap(true);
            taskArea.setWrapStyleWord(true);
            taskArea.setRows(5);
            taskArea.setBackground(new Color(220, 240, 255));
            taskArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            taskArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(150, 180, 220)),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            taskArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
            card.add(taskArea);
            card.add(Box.createVerticalStrut(4));

            // ── Detail panel for selected element ──
            final JTextArea detailArea = new JTextArea("Klicken Sie auf ein Element im Diagramm...");
            detailArea.setEditable(false);
            detailArea.setLineWrap(true);
            detailArea.setWrapStyleWord(true);
            detailArea.setRows(8);
            detailArea.setBackground(new Color(255, 255, 230));
            detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            detailArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(200, 200, 100)),
                            "Selektiertes Element",
                            TitledBorder.LEFT, TitledBorder.TOP,
                            new Font(Font.SANS_SERIF, Font.BOLD, 11)),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            detailArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

            // ── Clickable diagram panel ──
            JPanel imgContainer;
            if (rc.image != null && rc.diagram != null) {
                final BufferedImage baseImg = rc.image;
                final RenderedDiagram diagram = rc.diagram;
                final int imgW = baseImg.getWidth();
                final int imgH = baseImg.getHeight();

                // Selection state
                final Rectangle[] highlightRect = {null};
                final Color HIGHLIGHT_COLOR = new Color(255, 255, 0, 100);
                final Color HIGHLIGHT_BORDER = new Color(255, 180, 0, 200);

                final JPanel diagramPanel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                        // Fit image to panel
                        int pw = getWidth(), ph = getHeight();
                        double scale = Math.min((double) pw / imgW, (double) ph / imgH);
                        int dw = (int)(imgW * scale), dh = (int)(imgH * scale);
                        int ox = (pw - dw) / 2, oy = (ph - dh) / 2;

                        g2.drawImage(baseImg, ox, oy, dw, dh, null);

                        // Draw highlight overlay
                        if (highlightRect[0] != null) {
                            Rectangle r = highlightRect[0];
                            int hx = (int)(r.x * scale) + ox;
                            int hy = (int)(r.y * scale) + oy;
                            int hw = (int)(r.width * scale);
                            int hh = (int)(r.height * scale);

                            g2.setColor(HIGHLIGHT_COLOR);
                            g2.fillRect(hx, hy, hw, hh);
                            g2.setColor(HIGHLIGHT_BORDER);
                            g2.setStroke(new BasicStroke(2));
                            g2.drawRect(hx, hy, hw, hh);
                        }
                        g2.dispose();
                    }
                };
                diagramPanel.setBackground(Color.WHITE);
                diagramPanel.setPreferredSize(new Dimension(500, 350));
                diagramPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

                // ── Click handler ──
                diagramPanel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        int pw = diagramPanel.getWidth(), ph = diagramPanel.getHeight();
                        double scale = Math.min((double) pw / imgW, (double) ph / imgH);
                        int dw = (int)(imgW * scale), dh = (int)(imgH * scale);
                        int ox = (pw - dw) / 2, oy = (ph - dh) / 2;

                        // Map panel click → image pixel
                        double imgPx = (e.getX() - ox) / scale;
                        double imgPy = (e.getY() - oy) / scale;

                        // Map image pixel → SVG user-space
                        double vbX = diagram.getViewBoxX();
                        double vbY = diagram.getViewBoxY();
                        double vbW = diagram.getViewBoxWidth();
                        double vbH = diagram.getViewBoxHeight();

                        double svgX = vbX + (imgPx / imgW) * vbW;
                        double svgY = vbY + (imgPy / imgH) * vbH;

                        clickCounts[ci]++;

                        // ── Try to find a node ──
                        DiagramNode foundNode = diagram.findNodeAt(svgX, svgY);
                        if (foundNode != null) {
                            // Map node bounds to image pixels for highlight
                            int rx = (int)((foundNode.getX() - vbX) / vbW * imgW);
                            int ry = (int)((foundNode.getY() - vbY) / vbH * imgH);
                            int rw = (int)(foundNode.getWidth() / vbW * imgW);
                            int rh = (int)(foundNode.getHeight() / vbH * imgH);
                            highlightRect[0] = new Rectangle(rx, ry, rw, rh);

                            String info = formatNodeDetails(foundNode);
                            detailArea.setText(info);
                            detailArea.setCaretPosition(0);
                            selectedElements.get(ci).add("NODE:" + foundNode.getId());
                            diagramPanel.repaint();
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

                            String info = formatEdgeDetails(foundEdge);
                            detailArea.setText(info);
                            detailArea.setCaretPosition(0);
                            selectedElements.get(ci).add("EDGE:" + foundEdge.getId());
                            diagramPanel.repaint();
                            return;
                        }

                        // ── Nothing found ──
                        highlightRect[0] = null;
                        detailArea.setText(String.format(
                                "Kein Element an Position (%.0f, %.0f) gefunden.\n"
                                + "SVG-Koordinaten: (%.1f, %.1f)\n"
                                + "Bild-Pixel: (%.0f, %.0f)\n\n"
                                + "Vorhandene Nodes: %d\n"
                                + "Vorhandene Edges: %d",
                                (double)e.getX(), (double)e.getY(),
                                svgX, svgY, imgPx, imgPy,
                                diagram.getNodes().size(),
                                diagram.getEdges().size()));
                        diagramPanel.repaint();
                    }
                });

                imgContainer = diagramPanel;
            } else {
                // Render/raster error
                JLabel errLabel = new JLabel(rc.renderError
                        ? "\u274c SVG-Rendering fehlgeschlagen"
                        : "\u274c Rasterisierung fehlgeschlagen");
                errLabel.setForeground(Color.RED);
                errLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                errLabel.setHorizontalAlignment(SwingConstants.CENTER);
                imgContainer = new JPanel(new BorderLayout());
                imgContainer.add(errLabel, BorderLayout.CENTER);
                imgContainer.setPreferredSize(new Dimension(400, 250));
            }

            imgContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
            card.add(imgContainer);
            card.add(Box.createVerticalStrut(4));
            card.add(detailArea);
            card.add(Box.createVerticalStrut(4));

            // ── Annotation ──
            annotationAreas[ci] = new JTextArea("");
            annotationAreas[ci].setRows(2);
            annotationAreas[ci].setLineWrap(true);
            annotationAreas[ci].setWrapStyleWord(true);
            annotationAreas[ci].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Anmerkung"),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            annotationAreas[ci].setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            card.add(annotationAreas[ci]);
            card.add(Box.createVerticalStrut(4));

            // ── Questions ──
            JPanel qPanel = new JPanel();
            qPanel.setLayout(new BoxLayout(qPanel, BoxLayout.Y_AXIS));
            qPanel.setBackground(Color.WHITE);
            int qi = 0;
            for (Map.Entry<String, String> qEntry : rc.spec.questions.entrySet()) {
                final int qIdx = qi;
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
                row.setBackground(Color.WHITE);
                JLabel qLabel = new JLabel("<html><b>" + qEntry.getValue() + "</b></html>");
                qLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                row.add(qLabel);

                ButtonGroup bg = new ButtonGroup();
                for (final String answer : new String[]{"YES", "NO", "PARTIAL"}) {
                    JRadioButton rb = new JRadioButton(answer);
                    rb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                    rb.setBackground(Color.WHITE);
                    bg.add(rb);
                    row.add(rb);
                    rb.addActionListener(new ActionListener() {
                        @Override public void actionPerformed(ActionEvent e) {
                            questionAnswers[ci][qIdx] = answer;
                            updateSubmitState.run();
                        }
                    });
                }
                qPanel.add(row);
                qi++;
            }
            JScrollPane qScroll = new JScrollPane(qPanel);
            qScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
            card.add(qScroll);

            card.setPreferredSize(new Dimension(550, 900));
            card.setMinimumSize(new Dimension(480, 700));
            card.setMaximumSize(new Dimension(600, 1200));

            cardRow.add(card);
            if (idx < cases.size() - 1) cardRow.add(Box.createHorizontalStrut(12));
        }

        JScrollPane mainScroll = new JScrollPane(cardRow,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        mainScroll.getHorizontalScrollBar().setUnitIncrement(40);
        frame.add(mainScroll, BorderLayout.CENTER);

        // ── Submit handler ──
        submitBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                List<TestCaseResult> results = new ArrayList<TestCaseResult>();
                for (int i = 0; i < cases.size(); i++) {
                    RenderedCase rc = cases.get(i);
                    TestCaseResult r = new TestCaseResult();
                    r.id = rc.spec.id;
                    r.title = rc.spec.title;
                    r.mermaidCode = rc.spec.mermaidCode;
                    r.annotation = annotationAreas[i].getText();
                    r.renderError = rc.renderError;
                    r.rasterError = rc.rasterError;
                    r.clickCount = clickCounts[i];
                    r.selectedElements = selectedElements.get(i);
                    r.questionAnswers = new LinkedHashMap<String, String>();
                    int qi2 = 0;
                    for (String qid : rc.spec.questions.keySet()) {
                        r.questionAnswers.put(qid, questionAnswers[i][qi2++]);
                    }
                    results.add(r);
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(results);
                System.out.println(json);

                try {
                    File resultFile = new File(System.getProperty("user.dir"),
                            "mermaid-selection-test-result.json");
                    Writer w = new OutputStreamWriter(
                            new FileOutputStream(resultFile), "UTF-8");
                    try { w.write(json); } finally { w.close(); }
                    System.err.println("[MermaidSelectionTest] Written: "
                            + resultFile.getAbsolutePath());
                } catch (Exception ex) {
                    System.err.println("[MermaidSelectionTest] File write error: " + ex);
                }
                frame.dispose();
            }
        });

        // ── Countdown timer ──
        final int[] remaining = {COUNTDOWN_SECONDS};
        final javax.swing.Timer timer = new javax.swing.Timer(1000, null);
        timer.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                remaining[0]--;
                countdownLabel.setText(formatTime(remaining[0]));
                if (remaining[0] <= 60) {
                    countdownLabel.setForeground(new Color(255, 60, 60));
                } else if (remaining[0] <= 180) {
                    countdownLabel.setForeground(new Color(255, 140, 0));
                }
                if (remaining[0] <= 0) {
                    timer.stop();
                    countdownLabel.setText("ZEIT ABGELAUFEN");
                    submitBtn.setEnabled(true);
                    submitBtn.doClick();
                }
            }
        });
        timer.setRepeats(true);
        timer.start();

        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { timer.stop(); }
        });

        frame.pack();
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                frame.setAlwaysOnTop(true);
                frame.toFront();
                javax.swing.Timer releaseTimer = new javax.swing.Timer(2000, new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        frame.setAlwaysOnTop(false);
                    }
                });
                releaseTimer.setRepeats(false);
                releaseTimer.start();
            }
        });
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

        // Properties map
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

        // ── Type-specific properties ──
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

    private static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("  %02d:%02d  ", m, s);
    }
}

