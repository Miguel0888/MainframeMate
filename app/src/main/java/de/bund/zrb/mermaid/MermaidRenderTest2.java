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
public final class MermaidRenderTest2 {

    private static final int COUNTDOWN_SECONDS = 15 * 60;   // 15 minutes (more test cases)

    private MermaidRenderTest2() {}

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

        // ── 1. CLASS DIAGRAM ────────────────────────────────────
        s.add(new DiagramSpec("class-diagram",
                "1 \u2014 Klassendiagramm (UML)",
                "Ein UML-Klassendiagramm mit drei Klassen:\n"
                        + "\u2022 \"Animal\" (abstrakt) mit Attributen: +name: String, +age: int und Methode: +speak(): void\n"
                        + "\u2022 \"Dog\" erbt von Animal, mit eigener Methode: +fetch(): void\n"
                        + "\u2022 \"Cat\" erbt von Animal, mit eigener Methode: +purr(): void\n"
                        + "Vererbungspfeile (hohle Dreiecke) von Dog und Cat zu Animal.\n"
                        + "Attribute und Methoden m\u00fcssen in den Klassenboxen sichtbar sein.",
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
                        {"renders", "Wird \u00fcberhaupt ein Diagramm angezeigt (kein Fehler)?"},
                        {"three-boxes", "Sind drei Klassenboxen (Animal, Dog, Cat) sichtbar?"},
                        {"attributes", "Sind Attribute (name, age) in der Animal-Box lesbar?"},
                        {"methods", "Sind Methoden (speak, fetch, purr) in den Boxen sichtbar?"},
                        {"inheritance", "Gibt es Vererbungspfeile von Dog und Cat zu Animal?"},
                        {"abstract-label", "Ist der Stereotyp \u00ababstract\u00bb bei Animal sichtbar?"}
                }));

        // ── 2. STATE DIAGRAM ────────────────────────────────────
        s.add(new DiagramSpec("state-diagram",
                "2 \u2014 Zustandsdiagramm (Ampel)",
                "Ein Zustandsdiagramm mit Anfangs- und Endzustand:\n"
                        + "\u2022 [*] (Start, schwarzer Punkt) \u2192 Rot\n"
                        + "\u2022 Rot \u2192 Rot_Gelb (Label: \"warten\")\n"
                        + "\u2022 Rot_Gelb \u2192 Gr\u00fcn\n"
                        + "\u2022 Gr\u00fcn \u2192 Gelb\n"
                        + "\u2022 Gelb \u2192 Rot\n"
                        + "Die Zust\u00e4nde sollen als abgerundete Rechtecke dargestellt werden.",
                "stateDiagram-v2\n"
                        + "    [*] --> Rot\n"
                        + "    Rot --> Rot_Gelb : warten\n"
                        + "    Rot_Gelb --> Gruen\n"
                        + "    Gruen --> Gelb\n"
                        + "    Gelb --> Rot",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt (kein Fehler)?"},
                        {"start-dot", "Gibt es einen Startpunkt (schwarzer Punkt oder [*])?"},
                        {"four-states", "Sind 4 Zust\u00e4nde (Rot, Rot_Gelb, Gruen, Gelb) sichtbar?"},
                        {"transitions", "Sind die \u00dcberg\u00e4nge als Pfeile zwischen den Zust\u00e4nden sichtbar?"},
                        {"label-warten", "Ist das Label \"warten\" an einer Transition lesbar?"},
                        {"rounded-boxes", "Haben die Zust\u00e4nde abgerundete Formen (nicht eckig)?"}
                }));

        // ── 3. ENTITY-RELATIONSHIP ──────────────────────────────
        s.add(new DiagramSpec("er-diagram",
                "3 \u2014 Entity-Relationship (Bibliothek)",
                "Ein ER-Diagramm mit drei Entit\u00e4ten:\n"
                        + "\u2022 BUCH hat: isbn (PK), titel, jahr\n"
                        + "\u2022 AUTOR hat: id (PK), name\n"
                        + "\u2022 VERLAG hat: id (PK), name, stadt\n"
                        + "Beziehungen:\n"
                        + "\u2022 AUTOR ||--o{ BUCH : \"schreibt\" (ein Autor, viele B\u00fccher)\n"
                        + "\u2022 VERLAG ||--o{ BUCH : \"verlegt\" (ein Verlag, viele B\u00fccher)\n"
                        + "Entit\u00e4ten sollen als Boxen mit Attributen dargestellt werden.",
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
                        + "        string stadt\n"
                        + "    }\n"
                        + "    AUTOR ||--o{ BUCH : schreibt\n"
                        + "    VERLAG ||--o{ BUCH : verlegt",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"three-entities", "Sind drei Entit\u00e4ten-Boxen (BUCH, AUTOR, VERLAG) sichtbar?"},
                        {"attributes", "Sind Attribute (isbn, titel, name, stadt) in den Boxen lesbar?"},
                        {"relationships", "Gibt es Verbindungslinien zwischen den Entit\u00e4ten?"},
                        {"cardinality", "Sind Kardinalit\u00e4tssymbole (||, o{) an den Beziehungen erkennbar?"},
                        {"labels", "Sind die Beziehungslabels (\"schreibt\", \"verlegt\") lesbar?"}
                }));

        // ── 4. USER JOURNEY ─────────────────────────────────────
        s.add(new DiagramSpec("user-journey",
                "4 \u2014 User Journey (Online-Shop)",
                "Eine User Journey mit dem Titel \"Einkaufserlebnis\".\n"
                        + "Drei Abschnitte (sections):\n"
                        + "\u2022 \"Suche\": Produkt finden (5), Bewertungen lesen (3)\n"
                        + "\u2022 \"Kauf\": In den Warenkorb (5), Bezahlen (2)\n"
                        + "\u2022 \"Nachkauf\": Lieferstatus pr\u00fcfen (4), Bewerten (3)\n"
                        + "Die Zahlen (1-5) geben die Zufriedenheit an.\n"
                        + "Akteure: Kunde, System.",
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
                        + "        Bewerten: 3: Kunde",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"title", "Ist der Titel \"Einkaufserlebnis\" sichtbar?"},
                        {"sections", "Sind die drei Abschnitte (Suche, Kauf, Nachkauf) erkennbar?"},
                        {"tasks", "Sind die einzelnen Aufgaben (Produkt finden, Bezahlen etc.) lesbar?"},
                        {"scores", "Sind Zufriedenheitswerte oder farbliche Abstufungen erkennbar?"},
                        {"actors", "Sind Akteure (Kunde, System) zugeordnet oder sichtbar?"}
                }));

        // ── 5. GANTT CHART ──────────────────────────────────────
        s.add(new DiagramSpec("gantt-chart",
                "5 \u2014 Gantt-Chart (Projekt)",
                "Ein Gantt-Diagramm mit Titel \"Hausrenovierung\":\n"
                        + "\u2022 Section \"Planung\": Architektengespr\u00e4ch (3d), Genehmigung (5d, nach Gespr\u00e4ch)\n"
                        + "\u2022 Section \"Bau\": Rohbau (10d, nach Genehmigung), Elektrik (7d, nach Rohbau)\n"
                        + "\u2022 Section \"Finish\": Malern (5d, nach Elektrik), Einzug (1d Milestone, nach Malern)\n"
                        + "Balken sollen auf einer Zeitachse liegen, Abh\u00e4ngigkeiten als Abfolge.",
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
                        + "        Einzug               :milestone, c2, after c1, 1d",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"title", "Ist \"Hausrenovierung\" als Titel sichtbar?"},
                        {"bars", "Sind horizontale Balken f\u00fcr die Aufgaben sichtbar?"},
                        {"sections", "Sind die drei Sektionen (Planung, Bau, Finish) erkennbar?"},
                        {"time-axis", "Gibt es eine Zeitachse (Datumsangaben oben oder unten)?"},
                        {"sequence", "Sind die Balken zeitlich sequenziell angeordnet (nicht \u00fcbereinander)?"}
                }));

        // ── 6. PIE CHART ────────────────────────────────────────
        s.add(new DiagramSpec("pie-chart",
                "6 \u2014 Tortendiagramm (Haustiere)",
                "Ein Kreisdiagramm (Pie Chart) mit Titel \"Lieblingshaustiere\":\n"
                        + "\u2022 Hunde: 40%\n"
                        + "\u2022 Katzen: 35%\n"
                        + "\u2022 V\u00f6gel: 15%\n"
                        + "\u2022 Fische: 10%\n"
                        + "Kreissegmente sollen farblich unterschiedlich sein. Prozentangaben oder Labels sichtbar.",
                "pie title Lieblingshaustiere\n"
                        + "    \"Hunde\" : 40\n"
                        + "    \"Katzen\" : 35\n"
                        + "    \"Voegel\" : 15\n"
                        + "    \"Fische\" : 10",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"circle", "Ist ein Kreisdiagramm (runde Form) sichtbar?"},
                        {"segments", "Sind mindestens 4 verschiedenfarbige Segmente erkennbar?"},
                        {"labels", "Sind die Labels (Hunde, Katzen, V\u00f6gel, Fische) lesbar?"},
                        {"title", "Ist der Titel \"Lieblingshaustiere\" sichtbar?"},
                        {"proportions", "Ist das Hunde-Segment deutlich gr\u00f6\u00dfer als das Fische-Segment?"}
                }));

        // ── 7. QUADRANT CHART ───────────────────────────────────
        s.add(new DiagramSpec("quadrant-chart",
                "7 \u2014 Quadrant-Chart (Priorisierung)",
                "Ein Quadrant-Chart mit 4 beschrifteten Quadranten:\n"
                        + "\u2022 x-Achse: \"Aufwand\" (links niedrig, rechts hoch)\n"
                        + "\u2022 y-Achse: \"Wirkung\" (unten niedrig, oben hoch)\n"
                        + "Quadranten: \"Quick Wins\" (oben-links), \"Gro\u00dfprojekte\" (oben-rechts),\n"
                        + "\"Nice-to-have\" (unten-links), \"Zeitfresser\" (unten-rechts).\n"
                        + "Punkte: Feature A (0.2, 0.8), Feature B (0.7, 0.9), Bug Fix (0.1, 0.5), Refactoring (0.8, 0.3).",
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
                        + "    Refactoring: [0.8, 0.3]",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"four-quadrants", "Sind 4 Quadranten-Bereiche erkennbar (z.B. durch Farben oder Linien)?"},
                        {"axes", "Sind x- und y-Achse mit Beschriftungen sichtbar?"},
                        {"points", "Sind Datenpunkte (Feature A, B, Bug Fix, Refactoring) sichtbar?"},
                        {"labels", "Sind die Quadranten-Beschriftungen (Quick Wins etc.) lesbar?"},
                        {"title", "Ist der Titel \"Priorisierungsmatrix\" sichtbar?"}
                }));

        // ── 8. GIT GRAPH ────────────────────────────────────────
        s.add(new DiagramSpec("git-graph",
                "8 \u2014 Git Graph (Branching)",
                "Ein Git-Graph mit Commits auf mehreren Branches:\n"
                        + "\u2022 3 Commits auf main\n"
                        + "\u2022 Branch \"feature\" abzweigt, 2 Commits, merge zur\u00fcck in main\n"
                        + "\u2022 Branch \"hotfix\" abzweigt, 1 Commit, merge zur\u00fcck in main\n"
                        + "Commits sollen als Punkte/Kreise auf Linien sichtbar sein.",
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
                        + "    commit id: \"v1.0\"",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"commits", "Sind mehrere Commit-Punkte (Kreise/Punkte) sichtbar?"},
                        {"branches", "Sind mindestens 2 verschiedene Branch-Linien erkennbar?"},
                        {"merge", "Sind Merge-Punkte erkennbar (wo Linien zusammenlaufen)?"},
                        {"ids", "Sind Commit-IDs (init, v0.1, feat-1 etc.) lesbar?"},
                        {"colors", "Sind verschiedene Branches farblich unterschieden?"}
                }));

        // ── 9. TIMELINE ─────────────────────────────────────────
        s.add(new DiagramSpec("timeline",
                "9 \u2014 Timeline (Computergeschichte)",
                "Eine Timeline mit dem Titel \"Meilensteine der Informatik\":\n"
                        + "\u2022 1941: Z3 Konrad Zuse\n"
                        + "\u2022 1969: ARPANET : Unix\n"
                        + "\u2022 1991: World Wide Web : Linux\n"
                        + "\u2022 2007: iPhone\n"
                        + "Zeitpunkte sollen chronologisch angeordnet sein.",
                "timeline\n"
                        + "    title Meilensteine der Informatik\n"
                        + "    1941 : Z3 Konrad Zuse\n"
                        + "    1969 : ARPANET\n"
                        + "         : Unix\n"
                        + "    1991 : World Wide Web\n"
                        + "         : Linux\n"
                        + "    2007 : iPhone",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"title", "Ist der Titel sichtbar?"},
                        {"years", "Sind Jahreszahlen (1941, 1969, 1991, 2007) lesbar?"},
                        {"events", "Sind die Ereignisse (Z3, ARPANET, WWW etc.) lesbar?"},
                        {"chronological", "Sind die Ereignisse chronologisch (links-nach-rechts oder oben-nach-unten) angeordnet?"},
                        {"multiple-per-year", "Sind bei 1969 und 1991 jeweils zwei Eintr\u00e4ge sichtbar?"}
                }));

        // ── 10. SANKEY ──────────────────────────────────────────
        s.add(new DiagramSpec("sankey",
                "10 \u2014 Sankey-Diagramm (Energie)",
                "Ein Sankey-Diagramm das Energiefl\u00fcsse zeigt:\n"
                        + "\u2022 Kohle (30) und Gas (20) flie\u00dfen in Strom\n"
                        + "\u2022 Solar (15) und Wind (10) flie\u00dfen in Strom\n"
                        + "\u2022 Strom (50) flie\u00dft in Industrie, Haushalte, Verkehr\n"
                        + "Die Breite der Fl\u00fcsse soll proportional zum Wert sein.",
                "sankey-beta\n"
                        + "\n"
                        + "Kohle,Strom,30\n"
                        + "Gas,Strom,20\n"
                        + "Solar,Strom,15\n"
                        + "Wind,Strom,10\n"
                        + "Strom,Industrie,30\n"
                        + "Strom,Haushalte,25\n"
                        + "Strom,Verkehr,20",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"flows", "Sind flie\u00dfende Verbindungen (Sankey-B\u00e4nder) sichtbar?"},
                        {"sources", "Sind die Quellen (Kohle, Gas, Solar, Wind) lesbar?"},
                        {"targets", "Sind die Ziele (Industrie, Haushalte, Verkehr) lesbar?"},
                        {"widths", "Sind die Fl\u00fcsse unterschiedlich breit (Kohle breiter als Wind)?"},
                        {"strom-node", "Gibt es einen mittleren Knoten \"Strom\" wo Fl\u00fcsse zusammenlaufen?"}
                }));

        // ── 11. XY CHART ────────────────────────────────────────
        s.add(new DiagramSpec("xy-chart",
                "11 \u2014 XY-Chart (Temperatur + Regen)",
                "Ein XY-Diagramm mit Balken und Linie:\n"
                        + "\u2022 x-Achse: Monate (Jan, Feb, Mär, Apr, Mai, Jun)\n"
                        + "\u2022 Balken: Niederschlag in mm (50, 40, 55, 70, 85, 60)\n"
                        + "\u2022 Linie: Temperatur in \u00b0C (2, 4, 8, 14, 19, 22)\n"
                        + "Sowohl Balken als auch Linie sollen sichtbar sein.",
                "xychart-beta\n"
                        + "    title \"Wetter Halbjahr\"\n"
                        + "    x-axis [Jan, Feb, Mar, Apr, Mai, Jun]\n"
                        + "    y-axis \"Werte\" 0 --> 100\n"
                        + "    bar [50, 40, 55, 70, 85, 60]\n"
                        + "    line [2, 4, 8, 14, 19, 22]",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"bars", "Sind vertikale Balken sichtbar?"},
                        {"line", "Ist eine Linienkurve \u00fcber den Balken sichtbar?"},
                        {"x-labels", "Sind die Monatsnamen (Jan, Feb, ...) auf der x-Achse lesbar?"},
                        {"y-axis", "Ist eine y-Achse mit Skalierung sichtbar?"},
                        {"title", "Ist der Titel \"Wetter Halbjahr\" sichtbar?"}
                }));

        // ── 12. BLOCK DIAGRAM ───────────────────────────────────
        s.add(new DiagramSpec("block-diagram",
                "12 \u2014 Block-Diagramm (Systemarchitektur)",
                "Ein Block-Diagramm mit verschachtelten Bl\u00f6cken:\n"
                        + "\u2022 Obere Reihe: \"Frontend\" und \"API Gateway\" nebeneinander\n"
                        + "\u2022 Untere Reihe: \"Service A\", \"Service B\", \"Datenbank\"\n"
                        + "\u2022 Pfeile von Frontend zu API Gateway, von Gateway zu Services, von Services zu DB.\n"
                        + "Bl\u00f6cke sollen als Rechtecke mit Text sichtbar sein.",
                "block-beta\n"
                        + "    columns 3\n"
                        + "    Frontend:1 space:1 API[\"API Gateway\"]:1\n"
                        + "    ServiceA[\"Service A\"]:1 ServiceB[\"Service B\"]:1 DB[(\"Datenbank\")]:1\n"
                        + "\n"
                        + "    Frontend --> API\n"
                        + "    API --> ServiceA\n"
                        + "    API --> ServiceB\n"
                        + "    ServiceA --> DB\n"
                        + "    ServiceB --> DB",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"blocks", "Sind Bl\u00f6cke (Frontend, API Gateway, Service A/B, Datenbank) sichtbar?"},
                        {"layout", "Sind die Bl\u00f6cke in einer Rasterstruktur angeordnet (nicht zuf\u00e4llig)?"},
                        {"arrows", "Sind Verbindungspfeile zwischen den Bl\u00f6cken sichtbar?"},
                        {"labels", "Sind alle Block-Beschriftungen lesbar?"},
                        {"db-shape", "Hat \"Datenbank\" eine besondere Form (Zylinder oder abgerundet)?"}
                }));

        // ── 13. KANBAN ──────────────────────────────────────────
        s.add(new DiagramSpec("kanban",
                "13 \u2014 Kanban-Board",
                "Ein Kanban-Board mit drei Spalten:\n"
                        + "\u2022 \"Backlog\": Ticket-1, Ticket-2\n"
                        + "\u2022 \"In Arbeit\": Ticket-3\n"
                        + "\u2022 \"Fertig\": Ticket-4, Ticket-5\n"
                        + "Karten sollen in ihren Spalten sichtbar sein.",
                "kanban\n"
                        + "    Backlog\n"
                        + "        task1[\"Login-Seite erstellen\"]\n"
                        + "        task2[\"API Dokumentation\"]\n"
                        + "    In-Arbeit\n"
                        + "        task3[\"Datenbank-Migration\"]\n"
                        + "    Fertig\n"
                        + "        task4[\"CI/CD Pipeline\"]\n"
                        + "        task5[\"Unit Tests\"]",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"columns", "Sind drei Spalten (Backlog, In-Arbeit, Fertig) erkennbar?"},
                        {"cards", "Sind die einzelnen Tasks/Karten in den Spalten sichtbar?"},
                        {"labels", "Sind die Task-Texte (Login-Seite, API Doku etc.) lesbar?"},
                        {"column-headers", "Sind die Spalten\u00fcberschriften lesbar?"}
                }));

        // ── 14. ARCHITECTURE ────────────────────────────────────
        s.add(new DiagramSpec("architecture",
                "14 \u2014 Architecture-Diagramm (Cloud)",
                "Ein Architecture-Diagramm (Mermaid v11.1.0+):\n"
                        + "\u2022 Drei Gruppen: \"Internet\", \"Cloud\", \"OnPrem\"\n"
                        + "\u2022 Internet enth\u00e4lt: \"User\" (als cloud Icon)\n"
                        + "\u2022 Cloud enth\u00e4lt: \"LB\" (Load Balancer), \"App\" (Server)\n"
                        + "\u2022 OnPrem enth\u00e4lt: \"DB\" (Datenbank)\n"
                        + "\u2022 Verbindungen: User -- LB -- App -- DB",
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
                        + "    app:R --> T:db",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt (kein Syntax-Fehler)?"},
                        {"groups", "Sind Gruppen-Bereiche (Internet, Cloud, OnPrem) erkennbar?"},
                        {"services", "Sind Service-Knoten (User, LB, App, DB) sichtbar?"},
                        {"connections", "Sind Verbindungslinien zwischen den Services sichtbar?"},
                        {"labels", "Sind die Service-Namen lesbar?"},
                        {"icons", "Sind Icons oder symbolische Darstellungen bei den Services sichtbar?"}
                }));

        // ── 15. PACKET DIAGRAM ──────────────────────────────────
        s.add(new DiagramSpec("packet-diagram",
                "15 \u2014 Packet-Diagramm (TCP Header)",
                "Ein Paketdiagramm das einen vereinfachten TCP-Header zeigt:\n"
                        + "\u2022 Erste Zeile (0-31): Source Port (0-15), Destination Port (16-31)\n"
                        + "\u2022 Zweite Zeile: Sequence Number (0-31)\n"
                        + "\u2022 Dritte Zeile: Acknowledgment Number (0-31)\n"
                        + "Felder sollen als nummerierte Bl\u00f6cke nebeneinander sichtbar sein.",
                "packet-beta\n"
                        + "    0-15: \"Source Port\"\n"
                        + "    16-31: \"Dest Port\"\n"
                        + "    0-31: \"Sequence Number\"\n"
                        + "    0-31: \"Acknowledgment Number\"",
                new String[][] {
                        {"renders", "Wird ein Diagramm angezeigt?"},
                        {"fields", "Sind die Felder (Source Port, Dest Port, Seq, Ack) sichtbar?"},
                        {"layout", "Sind Source Port und Dest Port nebeneinander (nicht untereinander)?"},
                        {"full-width", "Spannen Sequence und Ack Number jeweils die volle Breite?"},
                        {"bit-numbers", "Sind Bit-Nummern (0, 15, 16, 31) irgendwo sichtbar?"}
                }));

        return s;
    }

    // ═══════════════════════════════════════════════════════════
    //  main — render, then show UI
    // ═══════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.err.println("[MermaidRenderTest2] Initialising renderer...");
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            System.out.println("{\"error\":\"mermaid.min.js not found on classpath\"}");
            System.exit(1);
            return;
        }

        List<DiagramSpec> specs = buildSpecs();
        final List<RenderedCase> rendered = new ArrayList<RenderedCase>();

        for (DiagramSpec spec : specs) {
            System.err.println("[MermaidRenderTest2] Rendering: " + spec.title);
            String svg = null;
            boolean renderErr = false;
            try {
                svg = renderer.renderToSvg(spec.mermaidCode);
                renderErr = (svg == null || !svg.contains("<svg"));
            } catch (Exception e) {
                System.err.println("[MermaidRenderTest2] Render exception for " + spec.id + ": " + e);
                renderErr = true;
            }
            if (renderErr) {
                rendered.add(new RenderedCase(spec, null, null, true, false));
                continue;
            }
            svg = MermaidSvgFixup.fixForBatik(svg);

            // Save SVG for manual inspection
            File svgFile = new File(System.getProperty("user.dir"),
                    "mermaid-test2-" + spec.id + ".svg");
            Writer w = new OutputStreamWriter(new FileOutputStream(svgFile), "UTF-8");
            try { w.write(svg); } finally { w.close(); }

            byte[] svgBytes = svg.getBytes("UTF-8");
            BufferedImage img = null;
            boolean rasterErr = false;
            try {
                img = SvgRenderer.renderToBufferedImage(svgBytes);
                img = autoCrop(img);
            } catch (Exception e) {
                System.err.println("[MermaidRenderTest2] Batik error for " + spec.id + ": " + e);
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
        final JFrame frame = new JFrame("Mermaid Render Test 2 \u2014 Advanced Diagram Types");
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
                            "mermaid-test2-result.json");
                    Writer fw = new OutputStreamWriter(
                            new FileOutputStream(resultFile), Charset.forName("UTF-8"));
                    try { fw.write(json); } finally { fw.close(); }
                    System.err.println("[MermaidRenderTest2] Written: " + resultFile.getAbsolutePath());
                } catch (Exception ex) {
                    System.err.println("[MermaidRenderTest2] File write error: " + ex);
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

