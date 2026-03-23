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

    /**
     * Trim transparent / white pixels from the edges of an image.
     * Returns a cropped copy, or the original if no trimming is needed.
     */
    private static BufferedImage autoCrop(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        int top = 0, bottom = h - 1, left = 0, right = w - 1;

        // Scan top
        outer_top:
        for (; top < h; top++)
            for (int x = 0; x < w; x++)
                if (isContentPixel(src.getRGB(x, top))) break outer_top;

        // Scan bottom
        outer_bottom:
        for (; bottom > top; bottom--)
            for (int x = 0; x < w; x++)
                if (isContentPixel(src.getRGB(x, bottom))) break outer_bottom;

        // Scan left
        outer_left:
        for (; left < w; left++)
            for (int y = top; y <= bottom; y++)
                if (isContentPixel(src.getRGB(left, y))) break outer_left;

        // Scan right
        outer_right:
        for (; right > left; right--)
            for (int y = top; y <= bottom; y++)
                if (isContentPixel(src.getRGB(right, y))) break outer_right;

        // Add small margin (8px)
        int margin = 8;
        top = Math.max(0, top - margin);
        bottom = Math.min(h - 1, bottom + margin);
        left = Math.max(0, left - margin);
        right = Math.min(w - 1, right + margin);

        int cw = right - left + 1;
        int ch = bottom - top + 1;
        if (cw >= w - 2 && ch >= h - 2) return src; // no significant crop

        return src.getSubimage(left, top, cw, ch);
    }

    /** Returns true if the pixel is NOT fully transparent and NOT white/near-white. */
    private static boolean isContentPixel(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a < 10) return false; // fully transparent
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return r < 250 || g < 250 || b < 250; // not near-white
    }

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

        // ═══════════════════════════════════════════════════════
        //  RUNDE 6 — Komplexe Diagramme
        // ═══════════════════════════════════════════════════════

        // 1 — Pizzabestellung: Komplexer Entscheidungsfluss mit vielen Knotentypen
        s.add(new DiagramSpec("pizza-flow",
                "1 \u2014 Pizzabestellung",
                "Ein Flussdiagramm von oben nach unten:\n"
                        + "Oben: Stadion-Knoten \"Hunger\" (abgerundete Seiten).\n"
                        + "Darunter: Raute \"Geld?\" mit zwei Ausg\u00e4ngen: links \"Nein\" zu \"Nudeln\" (Rechteck), "
                        + "rechts \"Ja\" zu Raute \"Lust auf Pizza?\".\n"
                        + "\"Lust auf Pizza?\" hat zwei Ausg\u00e4nge: \"Ja\" zu \"Mario\" (Stadion) und \"Nein\" zu \"Sushi\" (Rechteck).\n"
                        + "Sowohl \"Mario\" als auch \"Sushi\" f\u00fchren zu \"Satt\" (Stadion) ganz unten.\n"
                        + "ALLE Kanten haben beschriftete Labels (Ja/Nein).",
                "graph TD\n"
                        + "    Hunger([Hunger]) -->|Check| Geld{Geld?}\n"
                        + "    Geld -->|Nein| Nudeln[Nudeln kochen]\n"
                        + "    Geld -->|Ja| Pizza{Lust auf Pizza?}\n"
                        + "    Pizza -->|Ja| Mario([Mario anrufen])\n"
                        + "    Pizza -->|Nein| Sushi[Sushi bestellen]\n"
                        + "    Mario --> Satt([Satt])\n"
                        + "    Sushi --> Satt\n"
                        + "    Nudeln --> Satt",
                new String[][] {
                        {"hunger-shape", "Hat \"Hunger\" ganz oben abgerundete Seiten (Stadion-Form)?"},
                        {"geld-diamond", "Ist \"Geld?\" eine Raute (auf der Spitze stehendes Quadrat)?"},
                        {"pizza-diamond", "Ist \"Lust auf Pizza?\" ebenfalls eine Raute?"},
                        {"labels-visible", "Sind die Kantenlabels \"Ja\", \"Nein\" und \"Check\" lesbar?"},
                        {"satt-reached", "F\u00fchren alle drei Wege (Nudeln, Mario, Sushi) am Ende zu \"Satt\"?"},
                        {"no-overlap", "Sind Knoten und Labels \u00fcberschneidungsfrei lesbar?"}
                }));

        // 2 — B\u00fcro-Meeting: Sequenzdiagramm mit Loop, Alt und Note
        s.add(new DiagramSpec("meeting-seq",
                "2 \u2014 B\u00fcro-Meeting (Sequenz)",
                "Drei Akteure: \"Chef\", \"Alice\", \"Bob\" (in dieser Reihenfolge von links nach rechts).\n"
                        + "1) Chef sendet \"Agenda\" an Alice und Bob (je ein Pfeil).\n"
                        + "2) Ein LOOP-Block (beschriftet \"Diskussion\") enth\u00e4lt: Alice sendet \"Vorschlag\" an Bob, "
                        + "Bob antwortet gestrichelt \"Feedback\" an Alice.\n"
                        + "3) Eine NOTE rechts neben Bob mit dem Text \"Deadline Fr\".\n"
                        + "4) Alice sendet \"Ergebnis\" an Chef.\n"
                        + "Lebenslinien m\u00fcssen bis zu den unteren Actor-Boxen reichen.",
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
                        + "    Note right of Bob: Deadline Fr\n"
                        + "    Alice->>Chef: Ergebnis",
                new String[][] {
                        {"three-actors", "Sind drei Akteure (Chef, Alice, Bob) mit lesbaren Namen sichtbar?"},
                        {"loop-box", "Gibt es einen umrandeten Block mit der Beschriftung \"Diskussion\" (Loop)?"},
                        {"note-visible", "Ist eine gelbe/helle Notiz mit \"Deadline Fr\" rechts neben Bob sichtbar?"},
                        {"all-arrows", "Sind mindestens 5 Pfeile sichtbar (Agenda x2, Vorschlag, Feedback, Ergebnis)?"},
                        {"lifelines-ok", "Reichen die Lebenslinien bis zu den unteren Actor-Boxen?"}
                }));

        // 3 — Verschachtelte Subgraphs: Subgraph in Subgraph
        s.add(new DiagramSpec("nested-sub",
                "3 \u2014 Verschachtelte Teams",
                "Drei verschachtelte Ebenen:\n"
                        + "- \u00c4u\u00dferer Rahmen \"Firma\" enth\u00e4lt:\n"
                        + "  - Innerer Rahmen \"Dev\" mit Knoten \"Anna\" und \"Tom\" (Pfeil Anna\u2192Tom)\n"
                        + "  - Innerer Rahmen \"Ops\" mit Knoten \"Lisa\" und \"Max\" (Pfeil Lisa\u2192Max)\n"
                        + "- Au\u00dferhalb der Firma: Knoten \"Kunde\" mit Pfeil zu Anna.\n"
                        + "- Pfeil von Tom zu Lisa (Verbindung zwischen den inneren Subgraphs).\n"
                        + "Die Rahmen sollen beschriftet sein und sich NICHT \u00fcberlappen.",
                "graph TD\n"
                        + "    subgraph Firma\n"
                        + "        subgraph Dev\n"
                        + "            Anna --> Tom\n"
                        + "        end\n"
                        + "        subgraph Ops\n"
                        + "            Lisa --> Max\n"
                        + "        end\n"
                        + "        Tom --> Lisa\n"
                        + "    end\n"
                        + "    Kunde --> Anna",
                new String[][] {
                        {"firma-border", "Gibt es einen \u00e4u\u00dferen Rahmen \"Firma\"?"},
                        {"dev-inside", "Ist ein Rahmen \"Dev\" mit \"Anna\" und \"Tom\" INNERHALB von Firma?"},
                        {"ops-inside", "Ist ein Rahmen \"Ops\" mit \"Lisa\" und \"Max\" INNERHALB von Firma?"},
                        {"kunde-outside", "Ist \"Kunde\" AUSSERHALB des Firma-Rahmens?"},
                        {"cross-link", "Gibt es eine sichtbare Verbindung von Tom zu Lisa (zwischen Dev und Ops)?"}
                }));

        // 4 — Viele Pfeiltypen: durchgezogen, gestrichelt, dick, mit/ohne Label
        s.add(new DiagramSpec("arrow-types",
                "4 \u2014 Pfeiltypen-Sammlung",
                "Sechs Knoten in zwei Spalten (links A-C, rechts D-F):\n"
                        + "- \"Alpha\" ---> \"Delta\" : DURCHGEZOGENE Linie mit Pfeilspitze\n"
                        + "- \"Beta\" -.-> \"Echo\" : GESTRICHELTE Linie mit Pfeilspitze, Label \"dashed\"\n"
                        + "- \"Gamma\" ==> \"Foxtrot\" : DICKE Linie mit Pfeilspitze, Label \"thick\"\n"
                        + "Alle sechs Knoten-Texte und beide Labels m\u00fcssen lesbar sein.",
                "graph LR\n"
                        + "    Alpha --> Delta\n"
                        + "    Beta -.->|dashed| Echo\n"
                        + "    Gamma ==>|thick| Foxtrot",
                new String[][] {
                        {"solid-arrow", "Hat die Linie Alpha\u2192Delta eine durchgezogene Linie mit Pfeilspitze?"},
                        {"dashed-arrow", "Hat die Linie Beta\u2192Echo eine GESTRICHELTE Linie (keine durchgezogene)?"},
                        {"thick-arrow", "Ist die Linie Gamma\u2192Foxtrot DICKER als die anderen beiden?"},
                        {"labels-read", "Sind die Labels \"dashed\" und \"thick\" lesbar?"},
                        {"six-nodes", "Sind alle 6 Knoten-Texte (Alpha, Beta, Gamma, Delta, Echo, Foxtrot) lesbar?"}
                }));

        // 5 — Gro\u00dfer Flowchart mit vielen Knotenformen
        s.add(new DiagramSpec("shape-zoo",
                "5 \u2014 Formen-Zoo",
                "Ein Diagramm mit 6 verschiedenen Knotenformen, alle mit Namen beschriftet:\n"
                        + "- \"Rechteck\" = normales Rechteck (eckig)\n"
                        + "- \"Rund\" = Stadion (abgerundete Seiten)\n"
                        + "- \"Kreis\" = Kreis\n"
                        + "- \"Raute\" = Raute/Diamant\n"
                        + "- \"Sechseck\" = Sechseck (hexagonal)\n"
                        + "- \"Trapez\" = Trapezoid\n"
                        + "Alle durch Pfeile verbunden: Rechteck\u2192Rund\u2192Kreis\u2192Raute\u2192Sechseck\u2192Trapez.",
                "graph LR\n"
                        + "    Rechteck[Rechteck] --> Rund([Rund])\n"
                        + "    Rund --> Kreis((Kreis))\n"
                        + "    Kreis --> Raute{Raute}\n"
                        + "    Raute --> Sechseck{{Sechseck}}\n"
                        + "    Sechseck --> Trapez[/Trapez/]",
                new String[][] {
                        {"rect-shape", "Ist \"Rechteck\" ein normales Rechteck mit Ecken?"},
                        {"stadium-shape", "Hat \"Rund\" abgerundete Seiten (Stadion-Form)?"},
                        {"circle-shape", "Ist \"Kreis\" ein Kreis (nicht Rechteck)?"},
                        {"diamond-shape", "Ist \"Raute\" rautenf\u00f6rmig (auf Spitze stehendes Quadrat)?"},
                        {"hexagon-shape", "Hat \"Sechseck\" eine hexagonale/sechseckige Form (mehr als 4 Ecken)?"},
                        {"trapez-shape", "Hat \"Trapez\" eine Trapezoid-Form (schr\u00e4ge Seiten)?"},
                        {"all-connected", "Sind alle 6 Formen durch Pfeile verbunden?"}
                }));

        // 6 — Komplexe Sequenz: Activation Boxes + Parallele Nachrichten
        s.add(new DiagramSpec("seq-activate",
                "6 \u2014 Server-Anfrage (Sequenz mit Aktivierung)",
                "Drei Akteure: \"Browser\", \"Server\", \"DB\".\n"
                        + "1) Browser sendet \"GET /api\" an Server. Server wird AKTIVIERT (schmales Rechteck auf Lebenslinie).\n"
                        + "2) Server sendet \"SELECT\" an DB. DB wird AKTIVIERT.\n"
                        + "3) DB antwortet gestrichelt \"Rows\" an Server. DB wird DEAKTIVIERT.\n"
                        + "4) Server antwortet gestrichelt \"JSON\" an Browser. Server wird DEAKTIVIERT.\n"
                        + "Aktivierte Bereiche sollen als schmale Rechtecke auf den Lebenslinien sichtbar sein.",
                "sequenceDiagram\n"
                        + "    participant Browser\n"
                        + "    participant Server\n"
                        + "    participant DB\n"
                        + "    Browser->>+Server: GET /api\n"
                        + "    Server->>+DB: SELECT\n"
                        + "    DB-->>-Server: Rows\n"
                        + "    Server-->>-Browser: JSON",
                new String[][] {
                        {"three-actors", "Sind drei Akteure (Browser, Server, DB) mit lesbaren Namen sichtbar?"},
                        {"arrows-visible", "Sind die 4 Pfeile (GET, SELECT, Rows, JSON) mit lesbarem Text sichtbar?"},
                        {"activation-boxes", "Sind schmale Aktivierungs-Rechtecke auf den Lebenslinien von Server und/oder DB sichtbar?"},
                        {"lifelines-ok", "Reichen die Lebenslinien bis zu den unteren Actor-Boxen?"}
                }));

        // 7 — Bottom-Up Flowchart mit R\u00fcckw\u00e4rtspfeil (Zyklus)
        s.add(new DiagramSpec("cycle-flow",
                "7 \u2014 Endlosschleife (Zyklus)",
                "Ein Bottom-Up-Flowchart (BT = unten nach oben):\n"
                        + "Unten: \"Start\" (Stadion). Dar\u00fcber \"Arbeiten\" (Rechteck). Dar\u00fcber Raute \"Fertig?\".\n"
                        + "Von \"Fertig?\" geht \"Ja\" nach oben zu \"Ende\" (Stadion).\n"
                        + "Von \"Fertig?\" geht \"Nein\" ZUR\u00dcCK nach unten zu \"Arbeiten\" \u2014 das ist ein R\u00fcckw\u00e4rtspfeil!\n"
                        + "Der R\u00fcckpfeil zu \"Arbeiten\" muss als GEBOGENE oder UMGELEITETE Linie sichtbar sein (kein gerader \u00dcberlapp).",
                "graph BT\n"
                        + "    Start([Start]) --> Arbeiten[Arbeiten]\n"
                        + "    Arbeiten --> Fertig{Fertig?}\n"
                        + "    Fertig -->|Ja| Ende([Ende])\n"
                        + "    Fertig -->|Nein| Arbeiten",
                new String[][] {
                        {"bottom-up", "Flie\u00dft das Diagramm grob von UNTEN nach OBEN (Start unten, Ende oben)?"},
                        {"fertig-diamond", "Ist \"Fertig?\" eine Raute?"},
                        {"cycle-arrow", "Gibt es einen R\u00fcckw\u00e4rtspfeil von \"Fertig?\" ZUR\u00dcCK zu \"Arbeiten\" (Zyklus)?"},
                        {"labels-ja-nein", "Sind die Labels \"Ja\" und \"Nein\" an der Raute lesbar?"},
                        {"no-overlap", "Sind alle Knoten und Pfeile \u00fcberschneidungsfrei lesbar?"}
                }));

        // 8 — Sequenzdiagramm mit Alt-Block (if/else)
        s.add(new DiagramSpec("seq-alt",
                "8 \u2014 Wetter-Check (Sequenz mit Alt)",
                "Zwei Akteure: \"Mensch\" und \"App\".\n"
                        + "1) Mensch sendet \"Wie wird das Wetter?\" an App.\n"
                        + "2) Ein ALT-Block (beschriftet \"Sonne\" oben) zeigt: App antwortet \"Ab an den See!\".\n"
                        + "   Darunter ein ELSE-Abschnitt: App antwortet \"Regenschirm mitnehmen\".\n"
                        + "3) Mensch sendet \"Danke\" an App.\n"
                        + "Der Alt/Else-Block soll als umrandeter Bereich mit den Beschriftungen sichtbar sein.",
                "sequenceDiagram\n"
                        + "    participant Mensch\n"
                        + "    participant App\n"
                        + "    Mensch->>App: Wie wird das Wetter?\n"
                        + "    alt Sonne\n"
                        + "        App-->>Mensch: Ab an den See!\n"
                        + "    else Regen\n"
                        + "        App-->>Mensch: Regenschirm mitnehmen\n"
                        + "    end\n"
                        + "    Mensch->>App: Danke",
                new String[][] {
                        {"two-actors", "Sind zwei Akteure (Mensch, App) mit lesbaren Namen sichtbar?"},
                        {"alt-box", "Gibt es einen umrandeten Block f\u00fcr den Alt-Bereich (m\u00f6glicherweise mit \"Sonne\" beschriftet)?"},
                        {"else-section", "Gibt es eine Trennlinie oder Markierung f\u00fcr den Else-Bereich (\"Regen\")?"},
                        {"messages-readable", "Sind die Nachrichtentexte (Wetter?, See!, Regenschirm, Danke) lesbar?"},
                        {"lifelines-ok", "Reichen die Lebenslinien bis zu den unteren Actor-Boxen?"}
                }));

        // 9 — Mindmap: Humus
        s.add(new DiagramSpec("mindmap-humus",
                "9 \u2014 Mindmap: Humus",
                "Eine Mindmap mit zentralem Knoten \"Humus\" (doppelt umrundet = Kreis).\n"
                        + "Acht Hauptzweige strahlen vom Zentrum ab:\n"
                        + "  \u2022 Arten (mit Unterpunkten: N\u00e4hrhumus, Dauerhumus)\n"
                        + "  \u2022 Entstehung (mit Unterpunkten: Zersetzung, Bodenorganismen inkl. Regenw\u00fcrmer/Bakterien/Pilze, Laub)\n"
                        + "  \u2022 Bestandteile (Organische Substanz, Mineralstoffe, Wasser, Luft)\n"
                        + "  \u2022 Funktionen (N\u00e4hrstoffspeicher, Wasserspeicherung, Bodenlockerung, Bodenleben, Kohlenstoffspeicher)\n"
                        + "  \u2022 Bedeutung f\u00fcr Pflanzen (Wurzelbildung, N\u00e4hrstoffversorgung, Austrocknung)\n"
                        + "  \u2022 Einflussfaktoren (Klima, Feuchtigkeit, pH-Wert, Bodenart, Bewirtschaftung)\n"
                        + "  \u2022 Gefahren (Erosion, \u00dcberd\u00fcngung, Verdichtung, Austrocknung)\n"
                        + "  \u2022 F\u00f6rderung (Kompost, Mulchen, Fruchtfolge, Gr\u00fcnd\u00fcngung, Bodenbearbeitung)\n"
                        + "Zweige sollten farbig und radial um die Mitte angeordnet sein.",
                "mindmap\n"
                        + "  root((Humus))\n"
                        + "    Arten\n"
                        + "      N\u00e4hrhumus\n"
                        + "      Dauerhumus\n"
                        + "    Entstehung\n"
                        + "      Zersetzung organischer Stoffe\n"
                        + "      Bodenorganismen\n"
                        + "        Regenw\u00fcrmer\n"
                        + "        Bakterien\n"
                        + "        Pilze\n"
                        + "      Laub und Pflanzenreste\n"
                        + "    Bestandteile\n"
                        + "      Organische Substanz\n"
                        + "      Mineralstoffe\n"
                        + "      Wasser\n"
                        + "      Luft\n"
                        + "    Funktionen\n"
                        + "      N\u00e4hrstoffspeicher\n"
                        + "      Wasserspeicherung\n"
                        + "      Bodenlockerung\n"
                        + "      F\u00f6rderung des Bodenlebens\n"
                        + "      Kohlenstoffspeicher\n"
                        + "    Bedeutung f\u00fcr Pflanzen\n"
                        + "      Bessere Wurzelbildung\n"
                        + "      N\u00e4hrstoffversorgung\n"
                        + "      Schutz vor Austrocknung\n"
                        + "    Einflussfaktoren\n"
                        + "      Klima\n"
                        + "      Feuchtigkeit\n"
                        + "      pH-Wert\n"
                        + "      Bodenart\n"
                        + "      Bewirtschaftung\n"
                        + "    Gefahren\n"
                        + "      Erosion\n"
                        + "      \u00dcberd\u00fcngung\n"
                        + "      Verdichtung\n"
                        + "      Austrocknung\n"
                        + "    F\u00f6rderung\n"
                        + "      Kompost\n"
                        + "      Mulchen\n"
                        + "      Fruchtfolge\n"
                        + "      Gr\u00fcnd\u00fcngung\n"
                        + "      Schonende Bodenbearbeitung",
                new String[][] {
                        {"renders-at-all", "Wird \u00fcberhaupt ein Diagramm angezeigt (kein Render-Fehler)?"},
                        {"center-node", "Gibt es einen zentralen Knoten \"Humus\" in der Mitte?"},
                        {"branches-visible", "Sind mehrere Hauptzweige vom Zentrum abgehend sichtbar?"},
                        {"sub-nodes", "Haben die Hauptzweige sichtbare Unterpunkte (z.B. unter Arten: N\u00e4hrhumus, Dauerhumus)?"},
                        {"text-readable", "Sind die Texte auf den Knoten/Zweigen lesbar?"},
                        {"tree-structure", "Ist eine baumartige oder radiale Struktur erkennbar (nicht alles auf einer Linie)?"}
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
            svg = MermaidSvgFixup.fixForBatik(svg, spec.mermaidCode);

            // Save for manual inspection
            File svgFile = new File(System.getProperty("user.dir"),
                    "mermaid-test-" + spec.id + ".svg");
            Writer w = new OutputStreamWriter(new FileOutputStream(svgFile), "UTF-8");
            try { w.write(svg); } finally { w.close(); }

            byte[] svgBytes = svg.getBytes("UTF-8");

            // MermaidSvgFixup.setDimensions() already set width/height on the
            // SVG root so the larger dimension ≈ 2000px.  Batik renders at
            // these intrinsic dimensions → crisp, properly-sized image.
            BufferedImage img = SvgRenderer.renderToBufferedImage(svgBytes);
            // Auto-crop residual whitespace
            img = autoCrop(img);
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
            descArea.setRows(6);
            descArea.setBackground(new Color(255, 255, 220));
            descArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            descArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 150)),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            descArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
            card.add(descArea);
            card.add(Box.createVerticalStrut(4));

            // ── Rendered image — zoomable + pannable with dynamic SVG re-rendering ──
            JPanel imgContainer;
            if (rc.image != null) {
                // Base dimensions (from initial render) — used as the
                // stable reference frame for zoom/pan calculations.
                final int baseW = rc.image.getWidth();
                final int baseH = rc.image.getHeight();

                // Mutable image holder — replaced when SVG is re-rendered
                // at higher resolution for crisp zoom.
                final BufferedImage[] currentImg = {rc.image};

                // SVG bytes for on-demand re-rendering via Batik
                byte[] svgTmp = null;
                if (rc.svg != null) {
                    try { svgTmp = rc.svg.getBytes("UTF-8"); }
                    catch (Exception ignored) {}
                }
                final byte[] svgData = svgTmp;

                // Zoomable panel with drag-pan + wheel-zoom
                final double[] zoom = {1.0};
                final double[] offX = {0}, offY = {0};
                final Point[] dragStart = {null};

                final JPanel zoomPanel = new JPanel() {
                    @Override protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        // Display size is ALWAYS baseSize * zoom — this stays
                        // visually stable even when currentImg is swapped for a
                        // higher-resolution render.
                        int dw = (int) Math.round(baseW * zoom[0]);
                        int dh = (int) Math.round(baseH * zoom[0]);
                        g2.drawImage(currentImg[0],
                                (int) Math.round(offX[0]), (int) Math.round(offY[0]),
                                dw, dh, null);
                        g2.dispose();
                    }
                };
                zoomPanel.setBackground(Color.WHITE);

                // ── Dynamic SVG re-rendering on zoom ──
                // When the user zooms in far enough that the cached raster
                // image would be visibly upscaled, we re-render the SVG at
                // the needed resolution via Batik.  A debounce timer avoids
                // re-rendering on every intermediate scroll tick.
                final javax.swing.Timer[] rerenderTimer = {null};
                final Runnable scheduleRerender = new Runnable() {
                    @Override public void run() {
                        if (svgData == null) return;
                        // Check: would the display upscale beyond the cached image?
                        double displayW = baseW * zoom[0];
                        int cachedW = currentImg[0].getWidth();
                        if (displayW <= cachedW * 1.2) return; // still crisp enough

                        // (Re-)start debounce timer
                        if (rerenderTimer[0] != null) rerenderTimer[0].stop();
                        rerenderTimer[0] = new javax.swing.Timer(350, new ActionListener() {
                            @Override public void actionPerformed(ActionEvent e) {
                                double neededW = baseW * zoom[0] * 1.3; // 30 % headroom
                                neededW = Math.min(neededW, 8000);      // memory cap
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

                // Fit-to-panel on first layout
                zoomPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
                    boolean first = true;
                    @Override public void componentResized(java.awt.event.ComponentEvent e) {
                        if (first && zoomPanel.getWidth() > 0 && zoomPanel.getHeight() > 0) {
                            first = false;
                            double sx = (double) zoomPanel.getWidth() / baseW;
                            double sy = (double) zoomPanel.getHeight() / baseH;
                            zoom[0] = Math.min(sx, sy);
                            double dw = baseW * zoom[0];
                            double dh = baseH * zoom[0];
                            offX[0] = (zoomPanel.getWidth() - dw) / 2.0;
                            offY[0] = (zoomPanel.getHeight() - dh) / 2.0;
                            zoomPanel.repaint();
                        }
                    }
                });

                // Mouse-wheel zoom (centered on cursor)
                zoomPanel.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
                    @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                        double factor = e.getWheelRotation() < 0 ? 1.25 : 1.0 / 1.25;
                        double oldZ = zoom[0];
                        zoom[0] *= factor;
                        zoom[0] = Math.max(0.05, Math.min(zoom[0], 30.0));
                        double px = e.getX(), py = e.getY();
                        offX[0] = px - (px - offX[0]) * (zoom[0] / oldZ);
                        offY[0] = py - (py - offY[0]) * (zoom[0] / oldZ);
                        zoomPanel.repaint();
                        scheduleRerender.run();
                    }
                });

                // Drag to pan
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

                // Zoom buttons bar
                JPanel zoomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
                zoomBar.setBackground(new Color(240, 240, 240));
                JButton btnZoomIn = new JButton("\u2795");
                JButton btnZoomOut = new JButton("\u2796");
                JButton btnFit = new JButton("\uD83D\uDD04 Einpassen");
                btnZoomIn.setToolTipText("Vergr\u00f6\u00dfern (+)");
                btnZoomOut.setToolTipText("Verkleinern (-)");
                btnFit.setToolTipText("An Panel anpassen (0)");
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
                            double dw = baseW * zoom[0];
                            double dh = baseH * zoom[0];
                            offX[0] = (zoomPanel.getWidth() - dw) / 2.0;
                            offY[0] = (zoomPanel.getHeight() - dh) / 2.0;
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

            questionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50 * rc.spec.questions.size() + 40));
            card.add(questionsPanel);

            // Fixed card width — sized for 5120×1440 ultrawide
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
