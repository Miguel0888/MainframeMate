package de.bund.zrb.mermaid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.wiki.ui.SvgRenderer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive visual test tool for Mermaid → SVG → BufferedImage rendering.
 * <p>
 * Shows multiple small diagrams with a description of what should be visible.
 * Below each diagram the user can rate it (OK / Partial / Wrong) and leave
 * a free-text note.  Clicking "Submit" at the bottom prints the full result
 * as a Gson JSON array to stdout and exits the application.
 */
public final class MermaidRenderTest {

    private MermaidRenderTest() {}

    // ═══════════════════════════════════════════════════════════
    //  Data model — serialised to JSON at the end
    // ═══════════════════════════════════════════════════════════

    /** JSON-serialisable result for one visual test case. */
    public static final class TestCaseResult {
        public String id;
        public String title;
        public String expectedDescription;
        public String mermaidCode;
        /** "YES", "PARTIAL", "NO", or "UNRATED" */
        public String rating;
        /** Free-text note from the tester */
        public String note;
        /** true if SVG rendering itself failed */
        public boolean renderError;
        /** true if Batik rasterisation failed */
        public boolean rasterError;
    }

    // ═══════════════════════════════════════════════════════════
    //  Spec for a single diagram to test
    // ═══════════════════════════════════════════════════════════

    private static final class DiagramSpec {
        final String id;
        final String title;
        final String expectedDescription;
        final String mermaidCode;

        DiagramSpec(String id, String title, String expectedDescription, String mermaidCode) {
            this.id = id;
            this.title = title;
            this.expectedDescription = expectedDescription;
            this.mermaidCode = mermaidCode;
        }
    }

    /** Rendered result kept in memory for the dialog. */
    private static final class RenderedCase {
        final DiagramSpec spec;
        final BufferedImage image;
        final String svg;
        final boolean renderError;
        final boolean rasterError;

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
    //  Test-case catalogue
    // ═══════════════════════════════════════════════════════════

    private static List<DiagramSpec> buildSpecs() {
        List<DiagramSpec> s = new ArrayList<DiagramSpec>();

        s.add(new DiagramSpec("simple-edge",
                "1 — Einfache Linie (A → B)",
                "Zwei Rechtecke \"A\" und \"B\", verbunden durch einen Pfeil. "
                        + "Die Buchstaben muessen INNERHALB der Bloecke stehen, nicht daneben.",
                "graph LR\n    A --> B"));

        s.add(new DiagramSpec("three-nodes",
                "2 — Drei Knoten linear",
                "Drei Rechtecke \"Start\" → \"Mitte\" → \"Ende\" in einer Reihe. "
                        + "Alle Texte innerhalb der Bloecke lesbar.",
                "graph LR\n    Start --> Mitte --> Ende"));

        s.add(new DiagramSpec("decision-diamond",
                "3 — Verzweigung (Raute)",
                "Oben ein Rechteck \"A\", darunter eine Raute mit \"Ja?\". "
                        + "Zwei Pfeile: \"ja\" nach links zu \"B\", \"nein\" nach rechts zu \"C\". "
                        + "Text \"Ja?\" muss IN der Raute stehen.",
                "graph TD\n    A --> D{Ja?}\n    D -->|ja| B\n    D -->|nein| C"));

        s.add(new DiagramSpec("stadium-node",
                "4 — Runder Knoten (Stadium)",
                "Links ein Knoten mit abgerundeten Seiten (Stadium) \"Rundlich\", "
                        + "rechts ein normales Rechteck \"Eckig\". Pfeil dazwischen. "
                        + "Text komplett sichtbar.",
                "graph LR\n    A([Rundlich]) --> B[Eckig]"));

        s.add(new DiagramSpec("edge-label",
                "5 — Pfeilbeschriftung",
                "Zwei Rechtecke \"X\" und \"Y\". AUF dem Pfeil steht der Text \"Label\".",
                "graph LR\n    X -->|Label| Y"));

        s.add(new DiagramSpec("sequence",
                "6 — Sequenzdiagramm",
                "Zwei senkrechte Lebenslinien \"Alice\" und \"Bob\". "
                        + "Pfeil Alice→Bob \"Hallo\", Antwortpfeil Bob→Alice \"Hi\". "
                        + "Nachrichtentexte neben den Pfeilen.",
                "sequenceDiagram\n    Alice->>Bob: Hallo\n    Bob-->>Alice: Hi"));

        s.add(new DiagramSpec("subgraph",
                "7 — Subgraph / Gruppierung",
                "Umrandeter Bereich mit Ueberschrift \"Gruppe\", darin \"G1\"→\"G2\". "
                        + "Ausserhalb ein Knoten \"Aussen\" mit Pfeil zu \"G1\".",
                "graph TD\n    subgraph Gruppe\n        G1 --> G2\n    end\n    Aussen --> G1"));

        return s;
    }

    // ═══════════════════════════════════════════════════════════
    //  main
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

            // Apply Batik-compatibility fixes (z-order, marker fills)
            svg = MermaidSvgFixup.fixForBatik(svg);

            // Save fixed SVG for inspection
            File svgFile = new File(System.getProperty("user.dir"),
                    "mermaid-test-" + spec.id + ".svg");
            Writer w = new OutputStreamWriter(new FileOutputStream(svgFile), "UTF-8");
            try { w.write(svg); } finally { w.close(); }

            // Rasterise via Batik
            byte[] svgBytes = svg.getBytes("UTF-8");
            BufferedImage img = SvgRenderer.renderToBufferedImage(svgBytes, 400f, 300f);
            rendered.add(new RenderedCase(spec, img, svg, false, img == null));
        }

        // Show Swing dialog
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                showDialog(rendered);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Swing dialog
    // ═══════════════════════════════════════════════════════════

    private static void showDialog(final List<RenderedCase> cases) {
        final JDialog dialog = new JDialog((Frame) null, "Mermaid Render Test", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));

        // Storage arrays — parallel to cases list
        final String[] ratings = new String[cases.size()];
        final JTextArea[] noteAreas = new JTextArea[cases.size()];
        for (int i = 0; i < ratings.length; i++) ratings[i] = "";

        // ── Scrollable list of test cards ───────────────────────
        JPanel grid = new JPanel();
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (int idx = 0; idx < cases.size(); idx++) {
            final int fi = idx;
            final RenderedCase rc = cases.get(idx);

            // --- card wrapper ---
            JPanel card = new JPanel(new BorderLayout(4, 4));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(Color.GRAY),
                            rc.spec.title,
                            TitledBorder.LEFT, TitledBorder.TOP,
                            new Font(Font.SANS_SERIF, Font.BOLD, 12)),
                    BorderFactory.createEmptyBorder(4, 6, 6, 6)));

            // --- expected description (top) ---
            JTextArea descArea = new JTextArea("Erwartet: " + rc.spec.expectedDescription);
            descArea.setEditable(false);
            descArea.setLineWrap(true);
            descArea.setWrapStyleWord(true);
            descArea.setRows(2);
            descArea.setBackground(new Color(255, 255, 230));
            descArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            descArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 150)),
                    BorderFactory.createEmptyBorder(3, 5, 3, 5)));
            card.add(descArea, BorderLayout.NORTH);

            // --- rendered image (centre) ---
            JPanel imgPanel;
            if (rc.image != null) {
                final BufferedImage img = rc.image;
                imgPanel = new JPanel() {
                    @Override protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        int pw = getWidth(), ph = getHeight();
                        int iw = img.getWidth(), ih = img.getHeight();
                        double scale = Math.min((double) pw / iw, (double) ph / ih);
                        if (scale > 1.0) scale = 1.0;
                        int dw = (int) (iw * scale), dh = (int) (ih * scale);
                        g.drawImage(img, (pw - dw) / 2, (ph - dh) / 2, dw, dh, null);
                    }
                };
                imgPanel.setBackground(Color.WHITE);
                imgPanel.setPreferredSize(new Dimension(460, Math.min(img.getHeight() + 10, 280)));
            } else {
                imgPanel = new JPanel(new BorderLayout());
                String errMsg = rc.renderError ? "SVG-Rendering fehlgeschlagen" : "Batik-Rasterisierung fehlgeschlagen";
                JLabel errLabel = new JLabel(errMsg, SwingConstants.CENTER);
                errLabel.setForeground(Color.RED);
                errLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                imgPanel.add(errLabel, BorderLayout.CENTER);
                imgPanel.setPreferredSize(new Dimension(460, 60));
                imgPanel.setBackground(new Color(255, 230, 230));
            }
            imgPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            card.add(imgPanel, BorderLayout.CENTER);

            // --- bottom area: rating buttons + notes field ---
            JPanel bottomBox = new JPanel();
            bottomBox.setLayout(new BoxLayout(bottomBox, BoxLayout.Y_AXIS));

            // rating buttons row
            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            final JLabel statusLabel = new JLabel("  \u2190 bitte bewerten");
            statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
            statusLabel.setForeground(Color.DARK_GRAY);

            JButton yesBtn = new JButton("\u2705 OK");
            JButton partBtn = new JButton("\u26A0 Teilweise");
            JButton noBtn = new JButton("\u274C Falsch");

            yesBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    ratings[fi] = "YES";
                    statusLabel.setText("  \u2705 OK");
                    statusLabel.setForeground(new Color(0, 128, 0));
                }
            });
            partBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    ratings[fi] = "PARTIAL";
                    statusLabel.setText("  \u26A0 Teilweise");
                    statusLabel.setForeground(new Color(180, 120, 0));
                }
            });
            noBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    ratings[fi] = "NO";
                    statusLabel.setText("  \u274C Falsch");
                    statusLabel.setForeground(Color.RED);
                }
            });

            btnRow.add(yesBtn);
            btnRow.add(partBtn);
            btnRow.add(noBtn);
            btnRow.add(statusLabel);
            bottomBox.add(btnRow);

            // per-case note field
            JTextArea noteArea = new JTextArea(2, 40);
            noteArea.setLineWrap(true);
            noteArea.setWrapStyleWord(true);
            noteArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            noteAreas[idx] = noteArea;
            JScrollPane noteScroll = new JScrollPane(noteArea);
            noteScroll.setBorder(BorderFactory.createTitledBorder("Anmerkung zu diesem Diagramm"));
            bottomBox.add(noteScroll);

            card.add(bottomBox, BorderLayout.SOUTH);

            grid.add(card);
            grid.add(Box.createVerticalStrut(12));
        }

        JScrollPane scrollPane = new JScrollPane(grid);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.setPreferredSize(new Dimension(540, 650));
        dialog.add(scrollPane, BorderLayout.CENTER);

        // ── Submit button at the very bottom ────────────────────
        JButton submitBtn = new JButton("   Ergebnisse absenden   ");
        submitBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        submitBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                List<TestCaseResult> results = new ArrayList<TestCaseResult>();
                for (int i = 0; i < cases.size(); i++) {
                    RenderedCase rc = cases.get(i);
                    TestCaseResult tcr = new TestCaseResult();
                    tcr.id = rc.spec.id;
                    tcr.title = rc.spec.title;
                    tcr.expectedDescription = rc.spec.expectedDescription;
                    tcr.mermaidCode = rc.spec.mermaidCode;
                    tcr.rating = ratings[i].isEmpty() ? "UNRATED" : ratings[i];
                    tcr.note = noteAreas[i].getText().trim();
                    tcr.renderError = rc.renderError;
                    tcr.rasterError = rc.rasterError;
                    results.add(tcr);
                }
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(results);
                System.out.println(json);

                // Also write to file so background callers can read it
                try {
                    File resultFile = new File(System.getProperty("user.dir"),
                            "mermaid-test-result.json");
                    Writer fw = new OutputStreamWriter(
                            new FileOutputStream(resultFile),
                            Charset.forName("UTF-8"));
                    try { fw.write(json); } finally { fw.close(); }
                    System.err.println("[MermaidRenderTest] Result written to " + resultFile.getAbsolutePath());
                } catch (Exception ex) {
                    System.err.println("[MermaidRenderTest] Failed to write result file: " + ex);
                }

                dialog.dispose();
            }
        });
        JPanel submitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        submitPanel.add(submitBtn);
        dialog.add(submitPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }
}
