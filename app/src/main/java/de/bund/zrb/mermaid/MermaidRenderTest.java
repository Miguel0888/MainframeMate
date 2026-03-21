package de.bund.zrb.mermaid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.wiki.ui.SvgRenderer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive visual test tool for Mermaid → SVG → BufferedImage rendering.
 * <p>
 * Each test case is a focused micro-test with:
 * <ol>
 *   <li>A description of what SHOULD be visible (above the image)</li>
 *   <li>The rendered diagram image</li>
 *   <li>A free-text annotation field for guidance/notes</li>
 *   <li>Specific yes/no/partial questions from the AI about the rendering</li>
 * </ol>
 * On submit, results are written to {@code mermaid-test-result.json} and stdout.
 */
public final class MermaidRenderTest {

    /** Test time limit in seconds. */
    private static final int COUNTDOWN_SECONDS = 10 * 60;   // 10 minutes

    private MermaidRenderTest() {}

    // ═══════════════════════════════════════════════════════════
    //  Data model — serialised to JSON
    // ═══════════════════════════════════════════════════════════

    /** JSON-serialisable result for one visual test case. */
    public static final class TestCaseResult {
        public String id;
        public String title;
        public String expectedDescription;
        public String mermaidCode;
        /** Free-text annotation from the tester (guidance for the AI) */
        public String annotation;
        /** Map of question-id → "YES" / "NO" / "PARTIAL" */
        public Map<String, String> questionAnswers;
        /** true if SVG rendering itself failed */
        public boolean renderError;
        /** true if Batik rasterisation failed */
        public boolean rasterError;
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal specs + rendered holder
    // ═══════════════════════════════════════════════════════════

    private static final class DiagramSpec {
        final String id, title, expectedDescription, mermaidCode;
        /** Questions the AI wants answered.  Key = question-id, Value = question text */
        final Map<String, String> questions;

        DiagramSpec(String id, String title, String expectedDescription,
                    String mermaidCode, String[][] questions) {
            this.id = id;
            this.title = title;
            this.expectedDescription = expectedDescription;
            this.mermaidCode = mermaidCode;
            this.questions = new LinkedHashMap<String, String>();
            for (String[] q : questions) {
                this.questions.put(q[0], q[1]);
            }
        }
    }

    private static final class RenderedCase {
        final DiagramSpec spec;
        final BufferedImage image;
        final String svg;
        final boolean renderError, rasterError;

        RenderedCase(DiagramSpec spec, BufferedImage image, String svg,
                     boolean renderError, boolean rasterError) {
            this.spec = spec;
            this.image = image;
            this.svg = svg;
            this.renderError = renderError;
            this.rasterError = rasterError;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Test-case catalogue — focused micro-tests
    // ═══════════════════════════════════════════════════════════

    private static List<DiagramSpec> buildSpecs() {
        List<DiagramSpec> s = new ArrayList<DiagramSpec>();

        // 1 — Arrowhead test
        s.add(new DiagramSpec("arrowhead",
                "1 \u2014 Pfeilspitze",
                "Zwei Rechtecke \"A\" und \"B\" verbunden durch eine Linie. "
                        + "Am Ende bei B muss eine ausgef\u00fcllte DREIECKIGE PFEILSPITZE sichtbar sein.",
                "graph LR\n    A --> B",
                new String[][] {
                        {"arrow-visible", "Ist eine dreieckige Pfeilspitze am Ende der Linie (bei B) sichtbar?"},
                        {"arrow-filled", "Ist die Pfeilspitze ausgef\u00fcllt (dunkel), nicht nur ein Umriss?"}
                }));

        // 2 — Text inside box
        s.add(new DiagramSpec("text-in-box",
                "2 \u2014 Text im Kasten",
                "Ein Rechteck mit dem Text \"Hallo\" darin. "
                        + "Der Text muss ZENTRIERT INNERHALB des Kastens stehen, nicht daneben oder darunter.",
                "graph LR\n    A[Hallo]",
                new String[][] {
                        {"text-inside", "Steht der Text \"Hallo\" INNERHALB des Rechtecks?"},
                        {"text-centered", "Ist der Text horizontal und vertikal zentriert im Kasten?"}
                }));

        // 3 — Line visibility
        s.add(new DiagramSpec("line-visible",
                "3 \u2014 Linie sichtbar",
                "Drei Rechtecke \"X\", \"Y\", \"Z\" in einer Reihe. "
                        + "Zwischen X\u2192Y und Y\u2192Z muss je eine sichtbare Verbindungslinie sein.",
                "graph LR\n    X --> Y --> Z",
                new String[][] {
                        {"lines-visible", "Sind die Verbindungslinien zwischen den Bl\u00f6cken sichtbar?"},
                        {"three-boxes", "Sind genau drei separate Bl\u00f6cke (Rechtecke) sichtbar?"}
                }));

        // 4 — Diamond shape
        s.add(new DiagramSpec("diamond",
                "4 \u2014 Rautenform",
                "Ein einzelner Knoten in RAUTENFORM (auf der Spitze stehendes Quadrat) "
                        + "mit dem Text \"Ja?\" darin.",
                "graph TD\n    D{Ja?}",
                new String[][] {
                        {"is-diamond", "Hat der Knoten eine Rautenform (Rhombus/Diamant), NICHT ein Rechteck?"},
                        {"text-in-diamond", "Steht der Text \"Ja?\" innerhalb der Raute?"}
                }));

        // 5 — Edge label
        s.add(new DiagramSpec("edge-label",
                "5 \u2014 Kantenbeschriftung",
                "Zwei Rechtecke \"X\" und \"Y\" mit einer Linie dazwischen. "
                        + "AUF oder NEBEN der Linie steht das Wort \"Label\". "
                        + "Das Label soll einen sichtbaren Hintergrund haben und die Linie NICHT durch den Text laufen.",
                "graph LR\n    X -->|Label| Y",
                new String[][] {
                        {"label-visible", "Ist das Wort \"Label\" auf oder neben der Verbindungslinie sichtbar?"},
                        {"label-background", "Hat das Label einen Hintergrund (nicht direkt auf der Linie ohne Hintergrund)?"},
                        {"line-not-through-text", "L\u00e4uft die Linie um das Label herum oder stoppt davor (statt durch den Text)?"}
                }));

        // 6 — Subgraph border
        s.add(new DiagramSpec("subgraph",
                "6 \u2014 Subgraph-Rahmen",
                "Ein umrandeter Bereich (Rechteck-Rahmen) mit der \u00dcberschrift \"Gruppe\". "
                        + "Innerhalb des Rahmens zwei Bl\u00f6cke \"G1\" und \"G2\" mit Pfeil. "
                        + "Ausserhalb ein Block \"Aussen\" mit Pfeil zu G1.",
                "graph TD\n    subgraph Gruppe\n        G1 --> G2\n    end\n    Aussen --> G1",
                new String[][] {
                        {"border-visible", "Ist ein umrandeter Bereich (Rahmen) mit \u00dcberschrift \"Gruppe\" sichtbar?"},
                        {"inside-nodes", "Sind G1 und G2 innerhalb des Rahmens?"},
                        {"outside-node", "Ist \"Aussen\" ausserhalb des Rahmens?"}
                }));

        // 7 — Sequence diagram
        s.add(new DiagramSpec("sequence",
                "7 \u2014 Sequenzdiagramm",
                "Zwei senkrechte Lebenslinien mit den Namen \"Alice\" (links) und \"Bob\" (rechts). "
                        + "Ein Pfeil von Alice nach Bob mit dem Text \"Hallo\". "
                        + "Ein gestrichelter R\u00fcckpfeil von Bob nach Alice mit \"Hi\".",
                "sequenceDiagram\n    Alice->>Bob: Hallo\n    Bob-->>Alice: Hi",
                new String[][] {
                        {"rendered", "Wird das Diagramm \u00fcberhaupt angezeigt (kein roter Fehler)?"},
                        {"names-visible", "Sind die Namen \"Alice\" und \"Bob\" lesbar?"},
                        {"arrows-visible", "Sind Pfeile zwischen den Lebenslinien sichtbar?"}
                }));

        // 8 — Stadium node shape
        s.add(new DiagramSpec("stadium",
                "8 \u2014 Stadion-Knoten",
                "Links ein Knoten mit abgerundeten Seiten (\"Stadion\"-Form) mit Text \"Rund\". "
                        + "Rechts ein normales Rechteck \"Eckig\". Pfeil dazwischen.",
                "graph LR\n    A([Rund]) --> B[Eckig]",
                new String[][] {
                        {"stadium-shape", "Hat der linke Knoten abgerundete Seiten (nicht rechteckig)?"},
                        {"rect-shape", "Ist der rechte Knoten ein normales Rechteck?"},
                        {"arrow-between", "Gibt es einen Pfeil (mit Spitze) zwischen beiden Knoten?"}
                }));

        return s;
    }

    // ═══════════════════════════════════════════════════════════
    //  main — render, then show UI
    // ═══════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.err.println("[MermaidRenderTest] Initialising renderer...");
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.out.println("{\"error\":\"mermaid.min.js not found on classpath\"}");
            System.exit(1);
            return;
        }

        List<DiagramSpec> specs = buildSpecs();
        final List<RenderedCase> rendered = new ArrayList<RenderedCase>();

        for (DiagramSpec spec : specs) {
            System.err.println("[MermaidRenderTest] Rendering: " + spec.title);
            String svg = renderer.renderToSvg(spec.mermaidCode);

            boolean renderErr = (svg == null || !svg.contains("<svg"));
            if (renderErr) {
                rendered.add(new RenderedCase(spec, null, null, true, false));
                continue;
            }
            svg = MermaidSvgFixup.fixForBatik(svg);

            // Save for manual inspection
            File svgFile = new File(System.getProperty("user.dir"),
                    "mermaid-test-" + spec.id + ".svg");
            Writer w = new OutputStreamWriter(new FileOutputStream(svgFile), "UTF-8");
            try { w.write(svg); } finally { w.close(); }

            byte[] svgBytes = svg.getBytes("UTF-8");
            BufferedImage img = SvgRenderer.renderToBufferedImage(svgBytes, 600f, 400f);
            rendered.add(new RenderedCase(spec, img, svg, false, img == null));
        }

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { showDialog(rendered); }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Swing dialog
    // ═══════════════════════════════════════════════════════════

    private static void showDialog(final List<RenderedCase> cases) {
        final JFrame frame = new JFrame("Mermaid Render Test");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(0, 0));
        frame.setAlwaysOnTop(true);

        // Per-case data holders
        final JTextArea[] annotationAreas = new JTextArea[cases.size()];
        // questionAnswers[caseIdx][questionIdx] = "YES"/"NO"/"PARTIAL"/""
        final String[][] questionAnswers = new String[cases.size()][];
        for (int i = 0; i < cases.size(); i++) {
            int qCount = cases.get(i).spec.questions.size();
            questionAnswers[i] = new String[qCount];
            Arrays.fill(questionAnswers[i], "");
        }

        // ══════════════════════════════════════════════════════
        //  TOP BAR: countdown + submit
        // ══════════════════════════════════════════════════════
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        topBar.setBackground(new Color(40, 40, 40));

        final JLabel countdownLabel = new JLabel(formatTime(COUNTDOWN_SECONDS));
        countdownLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        countdownLabel.setForeground(Color.RED);
        topBar.add(countdownLabel, BorderLayout.WEST);

        final JLabel hintLabel = new JLabel("Alle Fragen beantworten, dann absenden");
        hintLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        hintLabel.setForeground(new Color(200, 200, 200));
        hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topBar.add(hintLabel, BorderLayout.CENTER);

        final JButton submitBtn = new JButton("  \u2714 Ergebnisse absenden  ");
        submitBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        submitBtn.setEnabled(false);
        submitBtn.setToolTipText("Erst alle Fragen beantworten!");
        topBar.add(submitBtn, BorderLayout.EAST);

        frame.add(topBar, BorderLayout.NORTH);

        // ── Helper: check if all questions answered ──
        final Runnable updateSubmitState = new Runnable() {
            @Override public void run() {
                int total = 0, answered = 0;
                for (int i = 0; i < questionAnswers.length; i++) {
                    for (int j = 0; j < questionAnswers[i].length; j++) {
                        total++;
                        if (!questionAnswers[i][j].isEmpty()) answered++;
                    }
                }
                boolean allDone = (answered == total);
                submitBtn.setEnabled(allDone);
                if (allDone) {
                    hintLabel.setText("\u2705 Alle " + total + " Fragen beantwortet \u2014 Submit freigegeben!");
                    hintLabel.setForeground(new Color(100, 255, 100));
                } else {
                    hintLabel.setText("Noch " + (total - answered) + " von " + total + " Fragen offen");
                    hintLabel.setForeground(new Color(200, 200, 200));
                }
            }
        };

        // ══════════════════════════════════════════════════════
        //  CENTER: horizontal row of test cards
        // ══════════════════════════════════════════════════════
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

            // ── Expected description (above image) ──
            JTextArea descArea = new JTextArea("ERWARTET:\n" + rc.spec.expectedDescription);
            descArea.setEditable(false);
            descArea.setLineWrap(true);
            descArea.setWrapStyleWord(true);
            descArea.setRows(4);
            descArea.setBackground(new Color(255, 255, 220));
            descArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            descArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 150)),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            descArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
            card.add(descArea);
            card.add(Box.createVerticalStrut(4));

            // ── Rendered image ──
            JPanel imgPanel;
            if (rc.image != null) {
                final BufferedImage img = rc.image;
                imgPanel = new JPanel() {
                    @Override protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        int pw = getWidth(), ph = getHeight();
                        int iw = img.getWidth(), ih = img.getHeight();
                        double scale = Math.min((double) pw / iw, (double) ph / ih);
                        if (scale > 1.5) scale = 1.5;
                        int dw = (int) (iw * scale), dh = (int) (ih * scale);
                        g.drawImage(img, (pw - dw) / 2, (ph - dh) / 2, dw, dh, null);
                    }
                };
                imgPanel.setBackground(Color.WHITE);
            } else {
                imgPanel = new JPanel(new BorderLayout());
                String errMsg = rc.renderError
                        ? "\u274C SVG-Rendering fehlgeschlagen"
                        : "\u274C Batik-Rasterisierung fehlgeschlagen";
                JLabel errLabel = new JLabel(errMsg, SwingConstants.CENTER);
                errLabel.setForeground(Color.RED);
                errLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                imgPanel.add(errLabel, BorderLayout.CENTER);
                imgPanel.setBackground(new Color(255, 230, 230));
            }
            imgPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            imgPanel.setPreferredSize(new Dimension(420, 300));
            imgPanel.setMinimumSize(new Dimension(300, 200));
            imgPanel.setMaximumSize(new Dimension(500, 400));
            card.add(imgPanel);
            card.add(Box.createVerticalStrut(4));

            // ── Annotation text area (for user guidance) ──
            JTextArea annotArea = new JTextArea(2, 30);
            annotArea.setLineWrap(true);
            annotArea.setWrapStyleWord(true);
            annotArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            annotationAreas[idx] = annotArea;
            JScrollPane annotScroll = new JScrollPane(annotArea);
            annotScroll.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(100, 150, 200)),
                    "\u270D Anmerkungen (optional \u2014 z.B. was genau falsch aussieht)",
                    TitledBorder.LEFT, TitledBorder.TOP,
                    new Font(Font.SANS_SERIF, Font.ITALIC, 11)));
            annotScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            card.add(annotScroll);
            card.add(Box.createVerticalStrut(4));

            // ── Questions with YES/NO/PARTIAL buttons ──
            JPanel questionsPanel = new JPanel();
            questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
            questionsPanel.setBackground(new Color(240, 245, 255));
            questionsPanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(80, 120, 200)),
                    "\u2753 Fragen",
                    TitledBorder.LEFT, TitledBorder.TOP,
                    new Font(Font.SANS_SERIF, Font.BOLD, 12)));

            int qIdx = 0;
            for (Map.Entry<String, String> qEntry : rc.spec.questions.entrySet()) {
                final int qi = qIdx;

                JPanel qRow = new JPanel(new BorderLayout(4, 0));
                qRow.setBackground(new Color(240, 245, 255));
                qRow.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

                // Question text
                JLabel qLabel = new JLabel("<html><b>" + (qIdx + 1) + ".</b> " + qEntry.getValue() + "</html>");
                qLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                qRow.add(qLabel, BorderLayout.CENTER);

                // Answer buttons
                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
                btnPanel.setBackground(new Color(240, 245, 255));
                final JLabel statusLbl = new JLabel("  ");
                statusLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

                JButton yBtn = new JButton("\u2705");
                JButton pBtn = new JButton("\u26A0");
                JButton nBtn = new JButton("\u274C");
                yBtn.setToolTipText("Ja");
                pBtn.setToolTipText("Teilweise");
                nBtn.setToolTipText("Nein");
                yBtn.setMargin(new Insets(1, 4, 1, 4));
                pBtn.setMargin(new Insets(1, 4, 1, 4));
                nBtn.setMargin(new Insets(1, 4, 1, 4));

                yBtn.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        questionAnswers[ci][qi] = "YES";
                        statusLbl.setText("\u2705");
                        statusLbl.setForeground(new Color(0, 128, 0));
                        updateSubmitState.run();
                    }
                });
                pBtn.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        questionAnswers[ci][qi] = "PARTIAL";
                        statusLbl.setText("\u26A0");
                        statusLbl.setForeground(new Color(180, 120, 0));
                        updateSubmitState.run();
                    }
                });
                nBtn.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        questionAnswers[ci][qi] = "NO";
                        statusLbl.setText("\u274C");
                        statusLbl.setForeground(Color.RED);
                        updateSubmitState.run();
                    }
                });

                btnPanel.add(yBtn);
                btnPanel.add(pBtn);
                btnPanel.add(nBtn);
                btnPanel.add(statusLbl);
                qRow.add(btnPanel, BorderLayout.EAST);

                questionsPanel.add(qRow);
                qIdx++;
            }

            questionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40 * rc.spec.questions.size() + 30));
            card.add(questionsPanel);

            // Fixed card width
            card.setPreferredSize(new Dimension(460, 750));
            card.setMinimumSize(new Dimension(380, 600));
            card.setMaximumSize(new Dimension(540, 900));

            cardRow.add(card);
            if (idx < cases.size() - 1) {
                cardRow.add(Box.createHorizontalStrut(12));
            }
        }

        JScrollPane hScroll = new JScrollPane(cardRow,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        hScroll.getHorizontalScrollBar().setUnitIncrement(40);
        hScroll.getVerticalScrollBar().setUnitIncrement(20);
        frame.add(hScroll, BorderLayout.CENTER);

        // ══════════════════════════════════════════════════════
        //  Submit action
        // ══════════════════════════════════════════════════════
        submitBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                // Validate all questions answered
                for (String[] qa : questionAnswers) {
                    for (String a : qa) {
                        if (a.isEmpty()) {
                            JOptionPane.showMessageDialog(frame,
                                    "Bitte erst alle Fragen beantworten!",
                                    "Validierung", JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                    }
                }

                List<TestCaseResult> results = new ArrayList<TestCaseResult>();
                for (int i = 0; i < cases.size(); i++) {
                    RenderedCase rc = cases.get(i);
                    TestCaseResult tcr = new TestCaseResult();
                    tcr.id = rc.spec.id;
                    tcr.title = rc.spec.title;
                    tcr.expectedDescription = rc.spec.expectedDescription;
                    tcr.mermaidCode = rc.spec.mermaidCode;
                    tcr.annotation = annotationAreas[i].getText().trim();
                    tcr.renderError = rc.renderError;
                    tcr.rasterError = rc.rasterError;

                    // Build question answers map
                    tcr.questionAnswers = new LinkedHashMap<String, String>();
                    int qi = 0;
                    for (String qId : rc.spec.questions.keySet()) {
                        tcr.questionAnswers.put(qId, questionAnswers[i][qi]);
                        qi++;
                    }
                    results.add(tcr);
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(results);
                System.out.println(json);

                // Write to file
                try {
                    File resultFile = new File(System.getProperty("user.dir"),
                            "mermaid-test-result.json");
                    Writer fw = new OutputStreamWriter(
                            new FileOutputStream(resultFile),
                            Charset.forName("UTF-8"));
                    try { fw.write(json); } finally { fw.close(); }
                    System.err.println("[MermaidRenderTest] Written: " + resultFile.getAbsolutePath());
                } catch (Exception ex) {
                    System.err.println("[MermaidRenderTest] File write error: " + ex);
                }

                frame.dispose();
            }
        });

        // ══════════════════════════════════════════════════════
        //  Countdown timer
        // ══════════════════════════════════════════════════════
        final int[] remaining = {COUNTDOWN_SECONDS};
        final Timer timer = new Timer(1000, null);
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
            @Override public void windowClosed(WindowEvent e) {
                timer.stop();
            }
        });

        // ══════════════════════════════════════════════════════
        //  Show maximised
        // ══════════════════════════════════════════════════════
        frame.pack();
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);

        frame.toFront();
        frame.requestFocus();
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                frame.setAlwaysOnTop(true);
                frame.toFront();
                frame.requestFocusInWindow();
                Timer releaseTimer = new Timer(2000, new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        frame.setAlwaysOnTop(false);
                    }
                });
                releaseTimer.setRepeats(false);
                releaseTimer.start();
            }
        });
    }

    private static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("  %02d:%02d  ", m, s);
    }
}
