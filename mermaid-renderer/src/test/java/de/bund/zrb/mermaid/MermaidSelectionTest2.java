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
 * Interactive visual selection &amp; editing test for diagram types NOT covered
 * by {@link MermaidSelectionTest}.
 * <p>
 * Covers: User Journey, Gantt, Pie, Quadrant, Git Graph, Timeline, Sankey,
 * XY Chart, Block, Kanban, Architecture, Packet, Requirement, C4, ZenUML,
 * Radar, Treemap, Venn.
 * <p>
 * Each test case shows a rendered diagram that the tester can click on.
 * The system identifies the clicked element using {@link RenderedDiagram#findNodeAt}
 * and {@link DiagramEdge#containsApprox}, then displays its polymorphic type
 * and properties in a detail panel.
 */
public final class MermaidSelectionTest2 {

    private MermaidSelectionTest2() {}

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

        CardState(SelectionSpec spec) {
            this.spec = spec;
            this.currentSource = spec.mermaidCode;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Test-case catalogue — remaining diagram types
    // ═══════════════════════════════════════════════════════════

    private static List<SelectionSpec> buildSpecs() {
        List<SelectionSpec> s = new ArrayList<SelectionSpec>();

        // ── 1. User Journey ──
        s.add(new SelectionSpec("journey-sel",
                "1 — User Journey: Aufgaben selektieren",
                "Klicken Sie auf Aufgaben oder Abschnitte.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "journey\n"
                + "    title Einkaufserlebnis\n"
                + "    section Suche\n"
                + "        Produkt finden: 5: Kunde\n"
                + "        Bewertungen lesen: 3: Kunde\n"
                + "    section Kauf\n"
                + "        In den Warenkorb: 5: Kunde\n"
                + "        Bezahlen: 2: Kunde, System\n"
                + "    section Nachkauf\n"
                + "        Lieferstatus pruefen: 4: Kunde, System\n"
                + "        Bewerten: 3: Kunde"));

        // ── 2. Gantt Chart ──
        s.add(new SelectionSpec("gantt-sel",
                "2 — Gantt-Chart: Balken selektieren",
                "Klicken Sie auf Gantt-Balken.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "gantt\n"
                + "    title Hausrenovierung\n"
                + "    dateFormat YYYY-MM-DD\n"
                + "    section Planung\n"
                + "        Architektengespraech :a1, 2025-01-01, 3d\n"
                + "        Genehmigung          :a2, after a1, 5d\n"
                + "    section Bau\n"
                + "        Rohbau               :b1, after a2, 10d\n"
                + "        Elektrik             :b2, after b1, 7d\n"
                + "    section Finish\n"
                + "        Malern               :c1, after b2, 5d\n"
                + "        Einzug               :milestone, c2, after c1, 1d"));

        // ── 3. Pie Chart ──
        s.add(new SelectionSpec("pie-sel",
                "3 — Tortendiagramm: Segmente selektieren",
                "Klicken Sie auf Kreissegmente.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "pie title Lieblingshaustiere\n"
                + "    \"Hunde\" : 40\n"
                + "    \"Katzen\" : 35\n"
                + "    \"Voegel\" : 15\n"
                + "    \"Fische\" : 10"));

        // ── 4. Quadrant Chart ──
        s.add(new SelectionSpec("quadrant-sel",
                "4 — Quadrant-Chart: Datenpunkte selektieren",
                "Klicken Sie auf Datenpunkte.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "quadrantChart\n"
                + "    title Priorisierungsmatrix\n"
                + "    x-axis Wenig Aufwand --> Viel Aufwand\n"
                + "    y-axis Wenig Wirkung --> Viel Wirkung\n"
                + "    quadrant-1 Quick Wins\n"
                + "    quadrant-2 Grossprojekte\n"
                + "    quadrant-3 Nice-to-have\n"
                + "    quadrant-4 Zeitfresser\n"
                + "    Feature A: [0.2, 0.8]\n"
                + "    Feature B: [0.7, 0.9]\n"
                + "    Bug Fix: [0.1, 0.5]\n"
                + "    Refactoring: [0.8, 0.3]"));

        // ── 5. Git Graph ──
        s.add(new SelectionSpec("git-sel",
                "5 — Git Graph: Commits selektieren",
                "Klicken Sie auf Commit-Punkte.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "gitGraph\n"
                + "    commit id: \"init\"\n"
                + "    commit id: \"v0.1\"\n"
                + "    branch feature\n"
                + "    commit id: \"feat-1\"\n"
                + "    commit id: \"feat-2\"\n"
                + "    checkout main\n"
                + "    commit id: \"v0.2\"\n"
                + "    merge feature\n"
                + "    branch hotfix\n"
                + "    commit id: \"fix-1\"\n"
                + "    checkout main\n"
                + "    merge hotfix\n"
                + "    commit id: \"v1.0\""));

        // ── 6. Timeline ──
        s.add(new SelectionSpec("timeline-sel",
                "6 — Timeline: Ereignisse selektieren",
                "Klicken Sie auf Zeitpunkte oder Ereignisse.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "timeline\n"
                + "    title Meilensteine der Informatik\n"
                + "    1941 : Z3 Konrad Zuse\n"
                + "    1969 : ARPANET\n"
                + "         : Unix\n"
                + "    1991 : World Wide Web\n"
                + "         : Linux\n"
                + "    2007 : iPhone"));

        // ── 7. Sankey ──
        s.add(new SelectionSpec("sankey-sel",
                "7 — Sankey: Knoten und Fluesse selektieren",
                "Klicken Sie auf Knoten oder Flussbereiche.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "sankey-beta\n"
                + "\n"
                + "Kohle,Strom,30\n"
                + "Gas,Strom,20\n"
                + "Solar,Strom,15\n"
                + "Wind,Strom,10\n"
                + "Strom,Industrie,30\n"
                + "Strom,Haushalte,25\n"
                + "Strom,Verkehr,20"));

        // ── 8. XY Chart ──
        s.add(new SelectionSpec("xy-sel",
                "8 — XY-Chart: Balken und Linie selektieren",
                "Klicken Sie auf Balken oder die Linie.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "xychart-beta\n"
                + "    title \"Wetter Halbjahr\"\n"
                + "    x-axis [Jan, Feb, Mar, Apr, Mai, Jun]\n"
                + "    y-axis \"Werte\" 0 --> 100\n"
                + "    bar [50, 40, 55, 70, 85, 60]\n"
                + "    line [2, 4, 8, 14, 19, 22]"));

        // ── 9. Block Diagram ──
        s.add(new SelectionSpec("block-sel",
                "9 — Block-Diagramm: Bloecke selektieren",
                "Klicken Sie auf Bloecke.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "block-beta\n"
                + "    columns 3\n"
                + "    Frontend:1 space:1 API[\"API Gateway\"]:1\n"
                + "    ServiceA[\"Service A\"]:1 ServiceB[\"Service B\"]:1 DB[(\"Datenbank\")]:1\n"
                + "\n"
                + "    Frontend --> API\n"
                + "    API --> ServiceA\n"
                + "    API --> ServiceB\n"
                + "    ServiceA --> DB\n"
                + "    ServiceB --> DB"));

        // ── 10. Kanban ──
        s.add(new SelectionSpec("kanban-sel",
                "10 — Kanban: Karten selektieren",
                "Klicken Sie auf Karten oder Spalten.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "kanban\n"
                + "    Backlog\n"
                + "        task1[\"Login-Seite erstellen\"]\n"
                + "        task2[\"API Dokumentation\"]\n"
                + "    In-Arbeit\n"
                + "        task3[\"Datenbank-Migration\"]\n"
                + "    Fertig\n"
                + "        task4[\"CI/CD Pipeline\"]\n"
                + "        task5[\"Unit Tests\"]"));

        // ── 11. Architecture ──
        s.add(new SelectionSpec("arch-sel",
                "11 — Architecture: Services selektieren",
                "Klicken Sie auf Service-Knoten.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "architecture-beta\n"
                + "    group internet(cloud)[Internet]\n"
                + "    group cloud(cloud)[Cloud]\n"
                + "    group onprem(server)[OnPrem]\n"
                + "\n"
                + "    service user(cloud)[User] in internet\n"
                + "    service lb(server)[LB] in cloud\n"
                + "    service app(server)[App] in cloud\n"
                + "    service db(database)[DB] in onprem\n"
                + "\n"
                + "    user:R --> T:lb\n"
                + "    lb:R --> T:app\n"
                + "    app:R --> T:db"));

        // ── 12. Packet Diagram ──
        s.add(new SelectionSpec("packet-sel",
                "12 — Packet: Felder selektieren",
                "Klicken Sie auf Paketfelder.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "packet-beta\n"
                + "    0-15: \"Source Port\"\n"
                + "    16-31: \"Dest Port\"\n"
                + "    32-63: \"Sequence Number\"\n"
                + "    64-95: \"Acknowledgment Number\""));

        // ── 13. Requirement Diagram ──
        s.add(new SelectionSpec("req-sel",
                "13 — Requirement: Anforderungen selektieren",
                "Klicken Sie auf Requirement-Boxen.\n"
                + "Im Edit-Panel koennen Sie den Namen aendern.",
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
                + "    auth_service - satisfies -> user_login"));

        // ── 14. C4 Diagram ──
        s.add(new SelectionSpec("c4-sel",
                "14 — C4-Diagramm: Systeme selektieren",
                "Klicken Sie auf Personen oder Systeme.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "C4Context\n"
                + "    title System Context Diagramm\n"
                + "    Person(user, \"Benutzer\", \"Arbeitet mit dem System\")\n"
                + "    System(system, \"MainframeMate\", \"Verwaltet Mainframe-Wissen\")\n"
                + "    System_Ext(ldap, \"LDAP\", \"Authentifizierung\")\n"
                + "    System_Ext(mail, \"Mailserver\", \"Versendet E-Mails\")\n"
                + "\n"
                + "    Rel(user, system, \"nutzt\")\n"
                + "    Rel(system, ldap, \"authentifiziert\")\n"
                + "    Rel(system, mail, \"sendet Benachrichtigungen\")"));

        // ── 15. ZenUML ──
        s.add(new SelectionSpec("zenuml-sel",
                "15 — ZenUML: Teilnehmer selektieren",
                "Klicken Sie auf Teilnehmer oder Aufrufe.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
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
                + "    }"));

        // ── 16. Radar Chart ──
        s.add(new SelectionSpec("radar-sel",
                "16 — Radar-Chart: Achsen und Daten selektieren",
                "Klicken Sie auf Achsen oder Datenpunkte.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "radar-beta\n"
                + "    title Qualitaetsprofil\n"
                + "    axis Wartbarkeit, Performance, Sicherheit, Testbarkeit, Usability\n"
                + "    curve Version A{8, 6, 7, 9, 5}\n"
                + "    curve Version B{6, 8, 8, 6, 7}"));

        // ── 17. Treemap ──
        s.add(new SelectionSpec("treemap-sel",
                "17 — Treemap: Bereiche selektieren",
                "Klicken Sie auf Treemap-Bereiche.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "treemap-beta\n"
                + "    root System\n"
                + "    Backend: 40\n"
                + "    Frontend: 25\n"
                + "    Datenbank: 20\n"
                + "    Logging: 15"));

        // ── 18. Venn Diagram ──
        s.add(new SelectionSpec("venn-sel",
                "18 — Venn-Diagramm: Mengen selektieren",
                "Klicken Sie auf Mengen oder Ueberlappungen.\n"
                + "Im Detail-Panel sehen Sie den Elementtyp.",
                "venn\n"
                + "    \"Java\" : 10\n"
                + "    \"Spring\" : 8\n"
                + "    \"React\" : 7\n"
                + "    \"Java&Spring\" : 4\n"
                + "    \"Java&React\" : 2\n"
                + "    \"Spring&React\" : 2\n"
                + "    \"Java&Spring&React\" : 1"));

        return s;
    }

    // ═══════════════════════════════════════════════════════════
    //  main — render, then show interactive UI
    // ═══════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.err.println("[MermaidSelectionTest2] Initialising renderer...");
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
        System.err.println("[MermaidSelectionTest2] Rendering: " + card.spec.title);
        card.renderError = false;
        card.rasterError = false;
        card.selectedNode = null;
        card.selectedEdge = null;

        String svg = null;
        try {
            svg = renderer.renderToSvg(card.currentSource);
            card.renderError = (svg == null || !svg.contains("<svg"));
        } catch (Exception e) {
            System.err.println("[MermaidSelectionTest2] Render error: " + e);
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
            System.err.println("[MermaidSelectionTest2] Raster error: " + e);
            card.image = null;
        }
        if (card.image == null) card.rasterError = true;
    }

    // ═══════════════════════════════════════════════════════════
    //  Swing dialog — interactive selection + editing UI
    // ═══════════════════════════════════════════════════════════

    private static void showDialog(final List<CardState> cards, final MermaidRenderer renderer) {
        final JFrame frame = new JFrame("Mermaid Selection Test 2 \u2014 Weitere Diagrammtypen");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(0, 0));

        // ── TOP BAR ──
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        topBar.setBackground(new Color(40, 40, 40));

        JLabel titleLabel = new JLabel("  \u2699 Mermaid Diagramm-Editor 2  ");
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
            JLabel noSelLabel = new JLabel("Kein Element ausgew\u00e4hlt");
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
                                JLabel done = new JLabel("\u2705 \u00c4nderung angewendet \u2014 neu gerendert");
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
                                JLabel done = new JLabel("\u2705 \u00c4nderung angewendet \u2014 neu gerendert");
                                done.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                                done.setForeground(new Color(0, 120, 0));
                                editPanel.add(done);
                                editPanel.revalidate();
                                editPanel.repaint();
                            }
                        });
                    } else {
                        JLabel noSel = new JLabel("Kein Element ausgew\u00e4hlt");
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

                                // Draw drag line for edge creation
                                if (cs.dragging && cs.dragStart != null && cs.dragCurrent != null) {
                                    g2.setColor(new Color(0, 100, 255, 180));
                                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND,
                                            BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0));
                                    g2.drawLine(cs.dragStart.x, cs.dragStart.y,
                                            cs.dragCurrent.x, cs.dragCurrent.y);
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

                                DiagramNode nodeAtPress = diagram.findNodeAt(svgX, svgY);
                                if (nodeAtPress != null && !(nodeAtPress instanceof SequenceFragment)) {
                                    cs.dragSourceNode = nodeAtPress;
                                    cs.dragStart = new Point(e.getX(), e.getY());
                                    cs.dragging = false;
                                } else {
                                    cs.dragSourceNode = null;
                                    cs.dragStart = null;
                                    cs.dragging = false;
                                }
                            }

                            @Override public void mouseReleased(MouseEvent e) {
                                if (cs.dragging && cs.dragSourceNode != null) {
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
                                cs.dragStart = null;
                                cs.dragCurrent = null;
                                dPanel.repaint();
                            }

                            @Override public void mouseClicked(MouseEvent e) {
                                if (cs.dragging) return;
                                handleClick(e, dPanel, cs, diagram, imgW, imgH,
                                        highlightRect, detailArea, refreshEditPanel[0]);
                            }
                        });

                        // ── Mouse drag: draw rubber-band line ──
                        dPanel.addMouseMotionListener(new MouseMotionAdapter() {
                            @Override public void mouseDragged(MouseEvent e) {
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

            JButton addNodeBtn = new JButton("Hinzuf\u00fcgen");
            addNodeBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            addNodeBtn.setMargin(new Insets(1, 4, 1, 4));
            createBar.add(addNodeBtn);

            JLabel dragHint = new JLabel(" | Drag: Knoten \u2192 Knoten = Kante");
            dragHint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 9));
            dragHint.setForeground(new Color(80, 80, 130));
            createBar.add(dragHint);

            addNodeBtn.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) {
                    String name = newNodeName.getText().trim();
                    if (name.isEmpty()) return;
                    if (cs.diagram != null && cs.diagram.findNodeById(name) != null) {
                        name = name + "_" + (cs.diagram.getNodes().size() + 1);
                    }
                    cs.currentSource = addNodeToSource(cs.currentSource,
                            cs.diagram != null ? cs.diagram.getDiagramType() : "flowchart",
                            name);
                    renderCard(renderer, cs);
                    sourceArea.setText(cs.currentSource);
                    highlightRect[0] = null;
                    detailArea.setText("\u2705 Neuer Knoten hinzugef\u00fcgt: " + name);
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

            // If label differs from ID, offer separate label edit
            if (!node.getLabel().equals(node.getId()) && !node.getLabel().isEmpty()) {
                JPanel labelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
                labelRow.setOpaque(false);
                labelRow.add(label("Label:"));
                final JTextField labelField = new JTextField(node.getLabel(), 14);
                labelField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                labelRow.add(labelField);
                JButton labelBtn = new JButton("Label \u00e4ndern");
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

        // ── RequirementItemNode-specific: show requirement details ──
        if (node instanceof RequirementItemNode) {
            RequirementItemNode ri = (RequirementItemNode) node;
            panel.add(Box.createVerticalStrut(4));
            panel.add(separator("Requirement-Details"));
            panel.add(label("  Typ: " + ri.getReqNodeType()));
            panel.add(label("  Req-ID: " + ri.getReqId()));
            panel.add(label("  Risiko: " + ri.getRisk()));
        }

        // ── Delete node button ──
        panel.add(Box.createVerticalStrut(8));
        JPanel deleteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        deleteRow.setOpaque(false);
        deleteRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JButton deleteBtn = new JButton("\u2716 Knoten entfernen");
        deleteBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        deleteBtn.setForeground(new Color(180, 0, 0));
        deleteRow.add(deleteBtn);
        panel.add(deleteRow);

        deleteBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String id = node.getId();
                // Remove lines containing this node ID
                String[] lines = cs.currentSource.split("\n");
                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    // Keep lines that don't reference this node
                    if (!line.contains(id)) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(line);
                    }
                }
                cs.currentSource = sb.toString();
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

        // ── Label edit ──
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
                String oldLabel = edge.getLabel();
                if (!oldLabel.isEmpty()) {
                    cs.currentSource = cs.currentSource.replace(oldLabel, newLabel);
                }
                onApplied.run();
            }
        });

        // ── Endpoint editing ──
        panel.add(Box.createVerticalStrut(4));
        panel.add(separator("Endpunkte \u00e4ndern"));
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
        JButton reconnBtn = new JButton("Umh\u00e4ngen");
        reconnBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        reconnRow.add(reconnBtn);
        panel.add(reconnRow);

        reconnBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String newSrc = srcField.getText().trim();
                String newTgt = tgtField.getText().trim();
                if (newSrc.equals(edge.getSourceId()) && newTgt.equals(edge.getTargetId())) return;
                // Generic reconnect: find line with both old IDs and replace
                String[] lines = cs.currentSource.split("\n");
                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    if (line.contains(edge.getSourceId()) && line.contains(edge.getTargetId())) {
                        line = line.replace(edge.getSourceId(), newSrc)
                                   .replace(edge.getTargetId(), newTgt);
                    }
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
                cs.currentSource = sb.toString();
                onApplied.run();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Source manipulation helpers
    // ═══════════════════════════════════════════════════════════

    private static String renameNodeInSource(String source, String diagramType, String oldId,
                                              String oldLabel, String newName) {
        // General word-boundary replacement
        String escaped = Pattern.quote(oldId);
        source = source.replaceAll(
                "(?<=^|[\\s,;:{}()|<>\\[\\]])(" + escaped + ")(?=$|[\\s,;:{}()|<>\\[\\]])",
                Matcher.quoteReplacement(newName));
        // Fallback: \b replacement
        if (source.contains(oldId)) {
            source = source.replaceAll("\\b" + escaped + "\\b", Matcher.quoteReplacement(newName));
        }
        return source;
    }

    private static String addNodeToSource(String source, String diagramType, String nodeName) {
        // Generic: append a standalone node reference
        return source + "\n    " + nodeName;
    }

    private static String addEdgeToSource(String source, String diagramType,
                                           String sourceId, String targetId) {
        // Generic edge addition
        return source + "\n    " + sourceId + " --> " + targetId;
    }

    // ═══════════════════════════════════════════════════════════
    //  UI helper methods
    // ═══════════════════════════════════════════════════════════

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

        // ── Try to find a node (non-fragment first, then fragment) ──
        DiagramNode foundNode = null;
        for (DiagramNode n : diagram.getNodes()) {
            if (n.contains(svgX, svgY)) {
                if (foundNode == null || foundNode instanceof SequenceFragment) {
                    foundNode = n;
                }
            }
        }
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
