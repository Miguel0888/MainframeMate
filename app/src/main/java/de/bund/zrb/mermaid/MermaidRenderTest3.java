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
 * Advanced visual test tool for Mermaid → SVG → BufferedImage rendering.
 * <p>
 * Tests diagram types NOT covered by {@link MermaidRenderTest}:
 * Class, State, ER, User Journey, Gantt, Pie, Quadrant, Git Graph,
 * Timeline, Sankey, XY Chart, Block, Packet, Kanban, Architecture.
 * <p>
 * Requires Mermaid ≥ 11.1.0 (bundled: 11.13.0).
 */
public final class MermaidRenderTest3 {

    private static final int COUNTDOWN_SECONDS = 15 * 60;   // 15 minutes (more test cases)

    private MermaidRenderTest3() {}

    // ═══════════════════════════════════════════════════════════
    //  Image helpers (copied from MermaidRenderTest)
    // ═══════════════════════════════════════════════════════════

    private static BufferedImage autoCrop(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        int top = 0, bottom = h - 1, left = 0, right = w - 1;
        outer_top:
        for (; top < h; top++)
            for (int x = 0; x < w; x++)
                if (isContentPixel(src.getRGB(x, top))) break outer_top;
        outer_bottom:
        for (; bottom > top; bottom--)
            for (int x = 0; x < w; x++)
                if (isContentPixel(src.getRGB(x, bottom))) break outer_bottom;
        outer_left:
        for (; left < w; left++)
            for (int y = top; y <= bottom; y++)
                if (isContentPixel(src.getRGB(left, y))) break outer_left;
        outer_right:
        for (; right > left; right--)
            for (int y = top; y <= bottom; y++)
                if (isContentPixel(src.getRGB(right, y))) break outer_right;
        int margin = 8;
        top = Math.max(0, top - margin);
        bottom = Math.min(h - 1, bottom + margin);
        left = Math.max(0, left - margin);
        right = Math.min(w - 1, right + margin);
        int cw = right - left + 1;
        int ch = bottom - top + 1;
        if (cw >= w - 2 && ch >= h - 2) return src;
        return src.getSubimage(left, top, cw, ch);
    }

    private static boolean isContentPixel(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a < 10) return false;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return r < 250 || g < 250 || b < 250;
    }

    // ═══════════════════════════════════════════════════════════
    //  Data model
    // ═══════════════════════════════════════════════════════════

    public static final class TestCaseResult {
        public String id;
        public String title;
        public String expectedDescription;
        public String mermaidCode;
        public String annotation;
        public Map<String, String> questionAnswers;
        public boolean renderError;
        public boolean rasterError;
    }

    private static final class DiagramSpec {
        final String id, title, expectedDescription, mermaidCode;
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
    //  Test-case catalogue — advanced diagram types
    // ═══════════════════════════════════════════════════════════


    private static List<DiagramSpec> buildSpecs() {
        List<DiagramSpec> s = new ArrayList<DiagramSpec>();

        // ═══════════════════════════════════════════════════════
        //  RUNDE 7 — Bisher ungetestete Diagrammtypen
        // ═══════════════════════════════════════════════════════

        // ── 1. REQUIREMENT DIAGRAM ────────────────────────────
        s.add(new DiagramSpec("requirement-diagram",
                "1 \u2014 Requirement-Diagramm (Login-System)",
                "Ein Requirement-Diagramm mit einem Haupt-Requirement \"Benutzeranmeldung\".\n"
                        + "Dazu drei Unteranforderungen:\n"
                        + "\u2022 \"Passwort pruefen\"\n"
                        + "\u2022 \"Account sperren nach Fehlversuchen\"\n"
                        + "\u2022 \"Passwort zuruecksetzen\"\n"
                        + "Zus\u00e4tzlich ein Element \"AuthService\" als Komponente/Element, das die Anforderungen erf\u00fcllt.\n"
                        + "Verbindungen und Labels zwischen den Elementen sollen sichtbar sein.",
                "requirementDiagram\n"
                        + "    requirement user_login {\n"
                        + "        id: 1\n"
                        + "        text: Benutzeranmeldung\n"
                        + "        risk: medium\n"
                        + "        verifymethod: test\n"
                        + "    }\n"
                        + "\n"
                        + "    requirement password_check {\n"
                        + "        id: 1.1\n"
                        + "        text: Passwort pruefen\n"
                        + "        risk: low\n"
                        + "        verifymethod: test\n"
                        + "    }\n"
                        + "\n"
                        + "    requirement lock_after_failures {\n"
                        + "        id: 1.2\n"
                        + "        text: Account sperren nach Fehlversuchen\n"
                        + "        risk: high\n"
                        + "        verifymethod: analysis\n"
                        + "    }\n"
                        + "\n"
                        + "    requirement password_reset {\n"
                        + "        id: 1.3\n"
                        + "        text: Passwort zuruecksetzen\n"
                        + "        risk: medium\n"
                        + "        verifymethod: demonstration\n"
                        + "    }\n"
                        + "\n"
                        + "    element auth_service {\n"
                        + "        type: system\n"
                        + "        docref: AuthService\n"
                        + "    }\n"
                        + "\n"
                        + "    user_login - contains -> password_check\n"
                        + "    user_login - contains -> lock_after_failures\n"
                        + "    user_login - contains -> password_reset\n"
                        + "    auth_service - satisfies -> user_login",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt (kein Fehler)?"},
                        {"main-requirement", "Ist das Haupt-Requirement \"Benutzeranmeldung\" sichtbar?"},
                        {"sub-requirements", "Sind die drei Unteranforderungen sichtbar?"},
                        {"element-visible", "Ist das Element \"AuthService\" sichtbar?"},
                        {"connections", "Sind Verbindungslinien zwischen Requirements und Element erkennbar?"},
                        {"labels", "Sind Labels wie \"contains\" oder \"satisfies\" lesbar?"}
                }));

        // ── 2. C4 DIAGRAM ─────────────────────────────────────
        s.add(new DiagramSpec("c4-diagram",
                "2 \u2014 C4-Diagramm (System Context)",
                "Ein C4-System-Context-Diagramm mit:\n"
                        + "\u2022 Person \"Benutzer\"\n"
                        + "\u2022 System \"MainframeMate\"\n"
                        + "\u2022 Externem System \"LDAP\"\n"
                        + "\u2022 Externem System \"Mailserver\"\n"
                        + "Beziehungen:\n"
                        + "\u2022 Benutzer nutzt MainframeMate\n"
                        + "\u2022 MainframeMate authentifiziert gegen LDAP\n"
                        + "\u2022 MainframeMate sendet Benachrichtigungen an Mailserver\n"
                        + "Elemente sollen als C4-typische Boxen mit Text dargestellt werden.",
                "C4Context\n"
                        + "    title System Context Diagramm\n"
                        + "    Person(user, \"Benutzer\", \"Arbeitet mit dem System\")\n"
                        + "    System(system, \"MainframeMate\", \"Verwaltet Mainframe-Wissen\")\n"
                        + "    System_Ext(ldap, \"LDAP\", \"Authentifizierung\")\n"
                        + "    System_Ext(mail, \"Mailserver\", \"Versendet E-Mails\")\n"
                        + "\n"
                        + "    Rel(user, system, \"nutzt\")\n"
                        + "    Rel(system, ldap, \"authentifiziert\")\n"
                        + "    Rel(system, mail, \"sendet Benachrichtigungen\")",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt (kein Fehler)?"},
                        {"person-visible", "Ist die Person \"Benutzer\" sichtbar?"},
                        {"main-system-visible", "Ist das System \"MainframeMate\" sichtbar?"},
                        {"external-systems", "Sind die externen Systeme \"LDAP\" und \"Mailserver\" sichtbar?"},
                        {"relations", "Sind Beziehungen zwischen den Elementen sichtbar?"},
                        {"labels", "Sind Beziehungsbeschriftungen wie \"nutzt\" oder \"authentifiziert\" lesbar?"}
                }));

        // ── 3. ZENUML ──────────────────────────────────────────
        s.add(new DiagramSpec("zenuml",
                "3 \u2014 ZenUML (Login-Ablauf)",
                "Ein ZenUML-Sequenzdiagramm f\u00fcr einen Login-Ablauf:\n"
                        + "\u2022 Client ruft AuthController.login() auf\n"
                        + "\u2022 AuthController ruft AuthService.checkCredentials() auf\n"
                        + "\u2022 AuthService ruft UserRepository.findUser() auf\n"
                        + "\u2022 Danach Rueckgaben zurueck bis zum Client\n"
                        + "Es sollen mehrere Teilnehmer und Aufrufpfeile sichtbar sein.",
                "zenuml\n"
                        + "    title Login Ablauf\n"
                        + "    @Actor Client\n"
                        + "    @Boundary AuthController\n"
                        + "    @Control AuthService\n"
                        + "    @Entity UserRepository\n"
                        + "\n"
                        + "    Client->AuthController.login(username, password) {\n"
                        + "        AuthController->AuthService.checkCredentials(username, password) {\n"
                        + "            AuthService->UserRepository.findUser(username)\n"
                        + "            return user\n"
                        + "        }\n"
                        + "        return token\n"
                        + "    }",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt (kein Fehler)?"},
                        {"participants", "Sind mehrere Teilnehmer (Client, AuthController, AuthService, UserRepository) sichtbar?"},
                        {"calls", "Sind Aufrufpfeile zwischen den Teilnehmern sichtbar?"},
                        {"returns", "Sind Rueckgaben oder Rueckrichtungspfeile sichtbar?"},
                        {"title", "Ist der Titel \"Login Ablauf\" sichtbar?"},
                        {"layout", "Ist der Ablauf klar von oben nach unten lesbar?"}
                }));

        // ── 4. RADAR CHART ────────────────────────────────────
        s.add(new DiagramSpec("radar-chart",
                "4 \u2014 Radar-Chart (Qualitaetsprofil)",
                "Ein Radar-Diagramm mit f\u00fcnf Achsen:\n"
                        + "\u2022 Wartbarkeit\n"
                        + "\u2022 Performance\n"
                        + "\u2022 Sicherheit\n"
                        + "\u2022 Testbarkeit\n"
                        + "\u2022 Usability\n"
                        + "Zwei Datenreihen:\n"
                        + "\u2022 Version A: 8, 6, 7, 9, 5\n"
                        + "\u2022 Version B: 6, 8, 8, 6, 7\n"
                        + "Es sollen ein Spinnennetz und zwei unterscheidbare Datenfl\u00e4chen oder Linien sichtbar sein.",
                "radar-beta\n"
                        + "    title Qualitaetsprofil\n"
                        + "    axis Wartbarkeit, Performance, Sicherheit, Testbarkeit, Usability\n"
                        + "    curve Version A{8, 6, 7, 9, 5}\n"
                        + "    curve Version B{6, 8, 8, 6, 7}",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt (kein Fehler)?"},
                        {"axes", "Sind mehrere Achsen strahlenfoermig sichtbar?"},
                        {"series", "Sind zwei Datenreihen sichtbar?"},
                        {"labels", "Sind Achsenbeschriftungen wie Wartbarkeit und Sicherheit lesbar?"},
                        {"title", "Ist der Titel \"Qualitaetsprofil\" sichtbar?"},
                        {"radar-shape", "Ist eine typische Radar-/Spinnennetzform erkennbar?"}
                }));

        // ── 5. TREEMAP ────────────────────────────────────────
        s.add(new DiagramSpec("treemap",
                "5 \u2014 Treemap (Speichernutzung)",
                "Eine Treemap zur Speichernutzung mit einem Wurzelelement \"System\".\n"
                        + "Unterelemente:\n"
                        + "\u2022 Backend (40)\n"
                        + "\u2022 Frontend (25)\n"
                        + "\u2022 Datenbank (20)\n"
                        + "\u2022 Logging (15)\n"
                        + "Die Bereiche sollen unterschiedlich gro\u00df sein, Backend am gr\u00f6\u00dften.",
                "treemap-beta\n"
                        + "    root System\n"
                        + "    Backend: 40\n"
                        + "    Frontend: 25\n"
                        + "    Datenbank: 20\n"
                        + "    Logging: 15",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt (kein Fehler)?"},
                        {"rectangles", "Sind mehrere Rechteckflaechen sichtbar?"},
                        {"labels", "Sind die Labels Backend, Frontend, Datenbank und Logging lesbar?"},
                        {"sizes", "Sind die Flaechen unterschiedlich gross?"},
                        {"largest-backend", "Ist Backend die groesste Flaeche?"},
                        {"root-visible", "Ist \"System\" als Oberbegriff oder Kontext sichtbar?"}
                }));

        // ── 6. VENN DIAGRAM ───────────────────────────────────
        s.add(new DiagramSpec("venn-diagram",
                "6 \u2014 Venn-Diagramm (Technologien)",
                "Ein Venn-Diagramm mit drei sich \u00fcberlappenden Mengen:\n"
                        + "\u2022 Java\n"
                        + "\u2022 Spring\n"
                        + "\u2022 React\n"
                        + "Die drei Kreise sollen sichtbar sein und sich teilweise \u00fcberschneiden.\n"
                        + "Im Zentrum soll eine gemeinsame Schnittmenge erkennbar sein.",
                "venn\n"
                        + "    \"Java\" : 10\n"
                        + "    \"Spring\" : 8\n"
                        + "    \"React\" : 7\n"
                        + "    \"Java&Spring\" : 4\n"
                        + "    \"Java&React\" : 2\n"
                        + "    \"Spring&React\" : 2\n"
                        + "    \"Java&Spring&React\" : 1",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt (kein Fehler)?"},
                        {"three-sets", "Sind drei Mengen/Kreise sichtbar?"},
                        {"overlap", "Gibt es sichtbare Ueberlappungen zwischen den Mengen?"},
                        {"center-overlap", "Ist eine gemeinsame Schnittmenge aller drei Mengen erkennbar?"},
                        {"labels", "Sind die Mengenbeschriftungen Java, Spring und React lesbar?"},
                        {"venn-shape", "Wirkt das Diagramm wie ein typisches Venn-Diagramm?"}
                }));

        return s;
    }

    // ═══════════════════════════════════════════════════════════
    //  main — render, then show UI
    // ═══════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.err.println("[MermaidRenderTest3] Initialising renderer...");
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.out.println("{\"error\":\"mermaid.min.js not found on classpath\"}");
            System.exit(1);
            return;
        }

        List<DiagramSpec> specs = buildSpecs();
        final List<RenderedCase> rendered = new ArrayList<RenderedCase>();

        for (DiagramSpec spec : specs) {
            System.err.println("[MermaidRenderTest3] Rendering: " + spec.title);
            String svg = null;
            boolean renderErr = false;
            try {
                svg = renderer.renderToSvg(spec.mermaidCode);
                renderErr = (svg == null || !svg.contains("<svg"));
            } catch (Exception e) {
                System.err.println("[MermaidRenderTest3] Render exception for " + spec.id + ": " + e);
                renderErr = true;
            }
            if (renderErr) {
                rendered.add(new RenderedCase(spec, null, null, true, false));
                continue;
            }
            svg = MermaidSvgFixup.fixForBatik(svg, spec.mermaidCode);

            // Save SVG for manual inspection
            File svgFile = new File(System.getProperty("user.dir"),
                    "mermaid-test3-" + spec.id + ".svg");
            Writer w = new OutputStreamWriter(new FileOutputStream(svgFile), "UTF-8");
            try { w.write(svg); } finally { w.close(); }

            byte[] svgBytes = svg.getBytes("UTF-8");
            BufferedImage img = null;
            boolean rasterErr = false;
            try {
                img = SvgRenderer.renderToBufferedImage(svgBytes);
                img = autoCrop(img);
            } catch (Exception e) {
                System.err.println("[MermaidRenderTest3] Batik error for " + spec.id + ": " + e);
                rasterErr = true;
            }
            if (img == null) rasterErr = true;
            rendered.add(new RenderedCase(spec, img, svg, false, rasterErr));
        }

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { showDialog(rendered); }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Swing dialog (identical structure to MermaidRenderTest)
    // ═══════════════════════════════════════════════════════════

    private static void showDialog(final List<RenderedCase> cases) {
        final JFrame frame = new JFrame("Mermaid Render Test 3 \u2014 Missing Diagram Types");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(0, 0));
        frame.setAlwaysOnTop(true);

        final JTextArea[] annotationAreas = new JTextArea[cases.size()];
        final String[][] questionAnswers = new String[cases.size()][];
        for (int i = 0; i < cases.size(); i++) {
            int qCount = cases.get(i).spec.questions.size();
            questionAnswers[i] = new String[qCount];
            Arrays.fill(questionAnswers[i], "");
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

        final JLabel hintLabel = new JLabel("Alle Fragen beantworten, dann absenden");
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

            // Expected description
            JTextArea descArea = new JTextArea("ERWARTET:\n" + rc.spec.expectedDescription);
            descArea.setEditable(false);
            descArea.setLineWrap(true);
            descArea.setWrapStyleWord(true);
            descArea.setRows(6);
            descArea.setBackground(new Color(255, 255, 220));
            descArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            descArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 150)),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            descArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
            card.add(descArea);
            card.add(Box.createVerticalStrut(4));

            // Rendered image with zoom/pan
            JPanel imgContainer;
            if (rc.image != null) {
                final int baseW = rc.image.getWidth();
                final int baseH = rc.image.getHeight();
                final BufferedImage[] currentImg = {rc.image};

                byte[] svgTmp = null;
                if (rc.svg != null) {
                    try { svgTmp = rc.svg.getBytes("UTF-8"); }
                    catch (Exception ignored) {}
                }
                final byte[] svgData = svgTmp;

                final double[] zoom = {1.0};
                final double[] offX = {0}, offY = {0};
                final Point[] dragStart = {null};

                final JPanel zoomPanel = new JPanel() {
                    @Override protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        int dw = (int) Math.round(baseW * zoom[0]);
                        int dh = (int) Math.round(baseH * zoom[0]);
                        g2.drawImage(currentImg[0],
                                (int) Math.round(offX[0]), (int) Math.round(offY[0]),
                                dw, dh, null);
                        g2.dispose();
                    }
                };
                zoomPanel.setBackground(Color.WHITE);

                final javax.swing.Timer[] rerenderTimer = {null};
                final Runnable scheduleRerender = new Runnable() {
                    @Override public void run() {
                        if (svgData == null) return;
                        double displayW = baseW * zoom[0];
                        int cachedW = currentImg[0].getWidth();
                        if (displayW <= cachedW * 1.2) return;
                        if (rerenderTimer[0] != null) rerenderTimer[0].stop();
                        rerenderTimer[0] = new javax.swing.Timer(350, new ActionListener() {
                            @Override public void actionPerformed(ActionEvent e) {
                                double neededW = baseW * zoom[0] * 1.3;
                                neededW = Math.min(neededW, 8000);
                                int cachedW2 = currentImg[0].getWidth();
                                if (neededW <= cachedW2 * 1.2) return;
                                BufferedImage hi = SvgRenderer.renderToBufferedImageForced(
                                        svgData, (float) neededW);
                                if (hi != null) {
                                    currentImg[0] = autoCrop(hi);
                                    zoomPanel.repaint();
                                }
                            }
                        });
                        rerenderTimer[0].setRepeats(false);
                        rerenderTimer[0].start();
                    }
                };

                zoomPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
                    boolean first = true;
                    @Override public void componentResized(java.awt.event.ComponentEvent e) {
                        if (first && zoomPanel.getWidth() > 0 && zoomPanel.getHeight() > 0) {
                            first = false;
                            double sx = (double) zoomPanel.getWidth() / baseW;
                            double sy = (double) zoomPanel.getHeight() / baseH;
                            zoom[0] = Math.min(sx, sy);
                            offX[0] = (zoomPanel.getWidth() - baseW * zoom[0]) / 2.0;
                            offY[0] = (zoomPanel.getHeight() - baseH * zoom[0]) / 2.0;
                            zoomPanel.repaint();
                        }
                    }
                });

                zoomPanel.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
                    @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                        double factor = e.getWheelRotation() < 0 ? 1.25 : 1.0 / 1.25;
                        double oldZ = zoom[0];
                        zoom[0] = Math.max(0.05, Math.min(zoom[0] * factor, 30.0));
                        double px = e.getX(), py = e.getY();
                        offX[0] = px - (px - offX[0]) * (zoom[0] / oldZ);
                        offY[0] = py - (py - offY[0]) * (zoom[0] / oldZ);
                        zoomPanel.repaint();
                        scheduleRerender.run();
                    }
                });
                zoomPanel.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mousePressed(java.awt.event.MouseEvent e) {
                        dragStart[0] = e.getPoint();
                        zoomPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                    @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                        dragStart[0] = null;
                        zoomPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                });
                zoomPanel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                        if (dragStart[0] != null) {
                            offX[0] += e.getX() - dragStart[0].x;
                            offY[0] += e.getY() - dragStart[0].y;
                            dragStart[0] = e.getPoint();
                            zoomPanel.repaint();
                        }
                    }
                });

                JPanel zoomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
                zoomBar.setBackground(new Color(240, 240, 240));
                JButton btnZoomIn = new JButton("\u2795");
                JButton btnZoomOut = new JButton("\u2796");
                JButton btnFit = new JButton("\uD83D\uDD04 Fit");
                btnZoomIn.setMargin(new Insets(2, 6, 2, 6));
                btnZoomOut.setMargin(new Insets(2, 6, 2, 6));
                btnFit.setMargin(new Insets(2, 6, 2, 6));
                btnZoomIn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                btnZoomOut.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

                btnZoomIn.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        double oldZ = zoom[0];
                        zoom[0] = Math.min(zoom[0] * 1.4, 30.0);
                        double cx = zoomPanel.getWidth() / 2.0, cy = zoomPanel.getHeight() / 2.0;
                        offX[0] = cx - (cx - offX[0]) * (zoom[0] / oldZ);
                        offY[0] = cy - (cy - offY[0]) * (zoom[0] / oldZ);
                        zoomPanel.repaint();
                        scheduleRerender.run();
                    }
                });
                btnZoomOut.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        double oldZ = zoom[0];
                        zoom[0] = Math.max(zoom[0] / 1.4, 0.05);
                        double cx = zoomPanel.getWidth() / 2.0, cy = zoomPanel.getHeight() / 2.0;
                        offX[0] = cx - (cx - offX[0]) * (zoom[0] / oldZ);
                        offY[0] = cy - (cy - offY[0]) * (zoom[0] / oldZ);
                        zoomPanel.repaint();
                    }
                });
                btnFit.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        if (zoomPanel.getWidth() > 0 && zoomPanel.getHeight() > 0) {
                            double sx = (double) zoomPanel.getWidth() / baseW;
                            double sy = (double) zoomPanel.getHeight() / baseH;
                            zoom[0] = Math.min(sx, sy);
                            offX[0] = (zoomPanel.getWidth() - baseW * zoom[0]) / 2.0;
                            offY[0] = (zoomPanel.getHeight() - baseH * zoom[0]) / 2.0;
                            zoomPanel.repaint();
                        }
                    }
                });

                zoomBar.add(btnZoomOut);
                zoomBar.add(btnFit);
                zoomBar.add(btnZoomIn);
                zoomBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

                imgContainer = new JPanel(new BorderLayout());
                imgContainer.add(zoomPanel, BorderLayout.CENTER);
                imgContainer.add(zoomBar, BorderLayout.SOUTH);
                imgContainer.setBackground(Color.WHITE);
            } else {
                imgContainer = new JPanel(new BorderLayout());
                String errMsg = rc.renderError
                        ? "\u274C SVG-Rendering fehlgeschlagen"
                        : "\u274C Batik-Rasterisierung fehlgeschlagen";
                JLabel errLabel = new JLabel(errMsg, SwingConstants.CENTER);
                errLabel.setForeground(Color.RED);
                errLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                imgContainer.add(errLabel, BorderLayout.CENTER);
                imgContainer.setBackground(new Color(255, 230, 230));
            }
            imgContainer.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            imgContainer.setPreferredSize(new Dimension(580, 700));
            imgContainer.setMinimumSize(new Dimension(480, 500));
            imgContainer.setMaximumSize(new Dimension(640, 900));
            card.add(imgContainer);
            card.add(Box.createVerticalStrut(4));

            // Annotation area
            JTextArea annotArea = new JTextArea(2, 30);
            annotArea.setLineWrap(true);
            annotArea.setWrapStyleWord(true);
            annotArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            annotationAreas[idx] = annotArea;
            JScrollPane annotScroll = new JScrollPane(annotArea);
            annotScroll.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(100, 150, 200)),
                    "\u270D Anmerkungen (optional)",
                    TitledBorder.LEFT, TitledBorder.TOP,
                    new Font(Font.SANS_SERIF, Font.ITALIC, 11)));
            annotScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            card.add(annotScroll);
            card.add(Box.createVerticalStrut(4));

            // Questions
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

                JLabel qLabel = new JLabel("<html><b>" + (qIdx + 1) + ".</b> " + qEntry.getValue() + "</html>");
                qLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                qRow.add(qLabel, BorderLayout.CENTER);

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

            questionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50 * rc.spec.questions.size() + 40));
            card.add(questionsPanel);

            card.setPreferredSize(new Dimension(600, 1300));
            card.setMinimumSize(new Dimension(500, 1000));
            card.setMaximumSize(new Dimension(640, 1400));

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

        // ── Submit ──
        submitBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                for (String[] qa : questionAnswers)
                    for (String a : qa)
                        if (a.isEmpty()) {
                            JOptionPane.showMessageDialog(frame,
                                    "Bitte erst alle Fragen beantworten!",
                                    "Validierung", JOptionPane.WARNING_MESSAGE);
                            return;
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

                try {
                    File resultFile = new File(System.getProperty("user.dir"),
                            "mermaid-test3-result.json");
                    Writer fw = new OutputStreamWriter(
                            new FileOutputStream(resultFile), Charset.forName("UTF-8"));
                    try { fw.write(json); } finally { fw.close(); }
                    System.err.println("[MermaidRenderTest3] Written: " + resultFile.getAbsolutePath());
                } catch (Exception ex) {
                    System.err.println("[MermaidRenderTest3] File write error: " + ex);
                }
                frame.dispose();
            }
        });

        // ── Countdown ──
        final int[] remaining = {COUNTDOWN_SECONDS};
        final Timer timer = new Timer(1000, null);
        timer.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                remaining[0]--;
                countdownLabel.setText(formatTime(remaining[0]));
                if (remaining[0] <= 60)
                    countdownLabel.setForeground(new Color(255, 60, 60));
                else if (remaining[0] <= 180)
                    countdownLabel.setForeground(new Color(255, 140, 0));
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

