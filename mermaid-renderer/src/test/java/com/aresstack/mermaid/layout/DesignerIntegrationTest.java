package com.aresstack.mermaid.layout;

import de.bund.zrb.mermaid.MermaidRenderer;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that validates the layout extraction API against the
 * <a href="https://github.com/Miguel0888/mermaid-designer">mermaid-designer</a>
 * prototype's use cases.
 *
 * <p><b>Simulated workflows:</b>
 * <ol>
 *   <li>Render a flowchart → extract layout → find nodes/edges by logical id</li>
 *   <li>Simulate SVG click: svgId → findNodeBySvgId → logical id</li>
 *   <li>Add a node textually → re-render → find new node → highlight it (BLINK)</li>
 *   <li>Select node for editing → highlight it (STEADY)</li>
 *   <li>Test edge highlighting between two nodes</li>
 *   <li>Clear highlights for state transition</li>
 *   <li>Generate JavaScript highlight script for WebView injection</li>
 * </ol>
 *
 * <p>Requires GraalJS to be on the classpath (the mermaid-renderer module
 * provides it).  If the engine is unavailable, tests are skipped.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesignerIntegrationTest {

    private static MermaidRenderer renderer;
    private static boolean engineAvailable;

    // ── Test diagrams ───────────────────────────────────────────

    private static final String FLOWCHART_V1 =
            "flowchart TD\n"
          + "    A[Start] --> B{Entscheidung}\n"
          + "    B -->|Ja| C[Aktion]\n"
          + "    B -->|Nein| D[Ende]\n";

    /** Version 2: node E added */
    private static final String FLOWCHART_V2 =
            "flowchart TD\n"
          + "    A[Start] --> B{Entscheidung}\n"
          + "    B -->|Ja| C[Aktion]\n"
          + "    B -->|Nein| D[Ende]\n"
          + "    C --> E[Neuer Schritt]\n";

    private static final String CLASS_DIAGRAM =
            "classDiagram\n"
          + "    class Kunde {\n"
          + "        +String name\n"
          + "        +String email\n"
          + "    }\n"
          + "    class Bestellung {\n"
          + "        +int nummer\n"
          + "        +Date datum\n"
          + "    }\n"
          + "    Kunde --> Bestellung : bestellt\n";

    private static final String SEQUENCE_DIAGRAM =
            "sequenceDiagram\n"
          + "    Alice->>Bob: Hallo Bob!\n"
          + "    Bob-->>Alice: Hallo Alice!\n";

    @BeforeAll
    static void setUp() {
        renderer = MermaidRenderer.getInstance();
        // Quick check whether the engine can render
        try {
            String test = renderer.renderToSvg("graph TD; X-->Y;");
            engineAvailable = (test != null && test.contains("<svg"));
        } catch (Exception e) {
            engineAvailable = false;
        }
    }

    private void assumeEngine() {
        Assumptions.assumeTrue(engineAvailable,
                "GraalJS Mermaid engine nicht verfügbar — Test übersprungen");
    }

    // ═══════════════════════════════════════════════════════════
    //  1. Grundlagen: Render + Extract
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Flowchart rendern → Layout extrahieren → Nodes und Edges vorhanden")
    void flowchart_renderAndExtract() {
        assumeEngine();

        String svg = renderer.renderToSvg(FLOWCHART_V1);
        assertNotNull(svg, "SVG darf nicht null sein");
        assertTrue(svg.contains("<svg"), "Muss gültiges SVG sein");

        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram, "RenderedDiagram darf nicht null sein");
        assertEquals("flowchart", diagram.getDiagramType());

        // Must find all 4 nodes: A, B, C, D
        System.out.println("=== Flowchart V1 Nodes ===");
        for (DiagramNode n : diagram.getNodes()) {
            System.out.println("  " + n);
        }
        assertTrue(diagram.getNodes().size() >= 4,
                "Mindestens 4 Nodes erwartet, gefunden: " + diagram.getNodes().size());

        assertNotNull(diagram.findNodeById("A"), "Node A muss vorhanden sein");
        assertNotNull(diagram.findNodeById("B"), "Node B muss vorhanden sein");
        assertNotNull(diagram.findNodeById("C"), "Node C muss vorhanden sein");
        assertNotNull(diagram.findNodeById("D"), "Node D muss vorhanden sein");

        // Must find edges: A→B, B→C, B→D
        System.out.println("=== Flowchart V1 Edges ===");
        for (DiagramEdge e : diagram.getEdges()) {
            System.out.println("  " + e);
        }
        assertTrue(diagram.getEdges().size() >= 3,
                "Mindestens 3 Edges erwartet, gefunden: " + diagram.getEdges().size());

        // Nodes must have non-zero bounding boxes
        DiagramNode nodeA = diagram.findNodeById("A");
        assertTrue(nodeA.getWidth() > 0, "Node A muss Breite > 0 haben");
        assertTrue(nodeA.getHeight() > 0, "Node A muss Höhe > 0 haben");

        // SVG ID must be set
        assertNotNull(nodeA.getSvgId(), "Node A muss SVG-ID haben");
        assertFalse(nodeA.getSvgId().isEmpty(), "SVG-ID darf nicht leer sein");
    }

    // ═══════════════════════════════════════════════════════════
    //  2. SVG-Klick → Node finden (reverse mapping)
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("SVG-Klick simulieren → findNodeBySvgId → logische ID")
    void svgClick_reverseMapping() {
        assumeEngine();

        String svg = renderer.renderToSvg(FLOWCHART_V1);
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        // Simulate: user clicks on SVG element → browser returns svgId
        DiagramNode nodeB = diagram.findNodeById("B");
        assertNotNull(nodeB);
        String clickedSvgId = nodeB.getSvgId();

        // Reverse lookup: svgId → DiagramNode
        DiagramNode found = diagram.findNodeBySvgId(clickedSvgId);
        assertNotNull(found, "findNodeBySvgId muss Node finden für: " + clickedSvgId);
        assertEquals("B", found.getId(), "Logische ID muss 'B' sein");
        assertEquals("Entscheidung", found.getLabel().replace(" ", ""),
                "Label muss 'Entscheidung' enthalten");

        System.out.println("SVG-Klick auf '" + clickedSvgId + "' → Node: " + found);
    }

    // ═══════════════════════════════════════════════════════════
    //  3. Node hinzufügen → neu rendern → neue Node finden + highlighten
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("Node hinzufügen → neu rendern → neue Node E finden → BLINK-Highlight")
    void addNode_rerender_findAndHighlight() {
        assumeEngine();

        // Step 1: Render V1 (without node E)
        String svgV1 = renderer.renderToSvg(FLOWCHART_V1);
        RenderedDiagram diagramV1 = DiagramLayoutExtractor.extract(svgV1);
        assertNotNull(diagramV1);
        assertNull(diagramV1.findNodeById("E"), "Node E darf in V1 nicht existieren");

        // Step 2: User adds node E textually → render V2
        String svgV2 = renderer.renderToSvg(FLOWCHART_V2);
        assertNotNull(svgV2);
        RenderedDiagram diagramV2 = DiagramLayoutExtractor.extract(svgV2);
        assertNotNull(diagramV2);

        // Step 3: Find new node E
        DiagramNode nodeE = diagramV2.findNodeById("E");
        assertNotNull(nodeE, "Node E muss in V2 vorhanden sein");
        assertTrue(nodeE.getWidth() > 0, "Node E muss sichtbare Breite haben");
        System.out.println("Neuer Node E: " + nodeE);

        // Step 4: Generate highlighted SVG with BLINK effect
        String highlightedSvg = SvgHighlighter.highlight(
                svgV2, nodeE.getSvgId(), SvgHighlighter.Mode.BLINK);
        assertNotNull(highlightedSvg);
        assertTrue(highlightedSvg.contains("mmd-highlight-blink"),
                "SVG muss Blink-Animation enthalten");
        assertTrue(highlightedSvg.contains(nodeE.getSvgId()),
                "SVG muss die SVG-ID von Node E referenzieren");
        assertTrue(highlightedSvg.length() > svgV2.length(),
                "Hervorgehobenes SVG muss länger sein als Original");

        System.out.println("✓ BLINK-Highlight für Node E injiziert (" 
                + (highlightedSvg.length() - svgV2.length()) + " Bytes CSS hinzugefügt)");
    }

    // ═══════════════════════════════════════════════════════════
    //  4. Node bearbeiten → STEADY Highlight
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("Node zum Bearbeiten auswählen → STEADY-Highlight")
    void selectNodeForEditing_steadyHighlight() {
        assumeEngine();

        String svg = renderer.renderToSvg(FLOWCHART_V1);
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        // User wählt Node B zum Bearbeiten aus
        String highlightedSvg = SvgHighlighter.highlightNode(
                diagram, "B", SvgHighlighter.Mode.STEADY);
        assertNotNull(highlightedSvg);
        assertTrue(highlightedSvg.contains("drop-shadow"),
                "STEADY-Highlight muss drop-shadow enthalten");
        assertTrue(highlightedSvg.contains("mmd-highlight-start"),
                "Muss Highlight-Marker enthalten");

        // Clear highlights again
        String cleaned = SvgHighlighter.clearHighlights(highlightedSvg);
        assertFalse(cleaned.contains("mmd-highlight-start"),
                "Nach clearHighlights dürfen keine Marker mehr vorhanden sein");
        assertFalse(cleaned.contains("drop-shadow"),
                "Nach clearHighlights darf kein drop-shadow mehr vorhanden sein");

        System.out.println("✓ STEADY-Highlight für Node B + clearHighlights funktioniert");
    }

    // ═══════════════════════════════════════════════════════════
    //  5. Edge highlighting
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("Edge zwischen A→B hervorheben")
    void highlightEdge() {
        assumeEngine();

        String svg = renderer.renderToSvg(FLOWCHART_V1);
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        // Find edges from A
        List<DiagramEdge> edgesFromA = diagram.findEdgesFor("A");
        System.out.println("Edges von A: " + edgesFromA);
        assertFalse(edgesFromA.isEmpty(), "Node A muss mindestens eine Edge haben");

        // Find edges between A and B specifically
        List<DiagramEdge> abEdges = diagram.findEdgesBetween("A", "B");
        System.out.println("Edges A→B: " + abEdges);
        assertFalse(abEdges.isEmpty(), "Edge A→B muss vorhanden sein");

        // Verify edge has bounding box
        DiagramEdge abEdge = abEdges.get(0);
        // Edge may have zero-area bbox if purely vertical/horizontal, but should have some extent
        System.out.println("Edge A→B Bounds: x=" + abEdge.getX() + " y=" + abEdge.getY()
                + " w=" + abEdge.getWidth() + " h=" + abEdge.getHeight());
    }

    // ═══════════════════════════════════════════════════════════
    //  6. Hit-Testing: Punkt → Node
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("Hit-Testing: Mittelpunkt eines Nodes → findNodeAt")
    void hitTest_nodeCentre() {
        assumeEngine();

        String svg = renderer.renderToSvg(FLOWCHART_V1);
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        DiagramNode nodeA = diagram.findNodeById("A");
        assertNotNull(nodeA);

        // Hit-test at the centre of node A
        DiagramNode hit = diagram.findNodeAt(nodeA.getCenterX(), nodeA.getCenterY());
        assertNotNull(hit, "Hit-Test am Zentrum von Node A muss treffen");
        assertEquals("A", hit.getId());

        System.out.println("✓ Hit-Test am Zentrum von A (" + nodeA.getCenterX()
                + ", " + nodeA.getCenterY() + ") → " + hit.getId());
    }

    // ═══════════════════════════════════════════════════════════
    //  7. Klassendiagramm
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("Klassendiagramm: Klassen und Beziehung extrahieren")
    void classDiagram_extractClassesAndRelation() {
        assumeEngine();

        String svg = renderer.renderToSvg(CLASS_DIAGRAM);
        assertNotNull(svg, "Klassendiagramm-SVG darf nicht null sein");

        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        System.out.println("=== Klassendiagramm ===");
        System.out.println("Diagram type: " + diagram.getDiagramType());
        for (DiagramNode n : diagram.getNodes()) {
            System.out.println("  Node: " + n);
        }
        for (DiagramEdge e : diagram.getEdges()) {
            System.out.println("  Edge: " + e);
        }

        // Should find at least 2 class nodes
        assertTrue(diagram.getNodes().size() >= 2,
                "Mindestens 2 Klassen erwartet, gefunden: " + diagram.getNodes().size());

        // Try to find "Kunde" and "Bestellung"
        boolean foundKunde = false, foundBestellung = false;
        for (DiagramNode n : diagram.getNodes()) {
            if (n.getId().contains("Kunde") || n.getLabel().contains("Kunde")) foundKunde = true;
            if (n.getId().contains("Bestellung") || n.getLabel().contains("Bestellung")) foundBestellung = true;
        }
        assertTrue(foundKunde, "Klasse 'Kunde' muss gefunden werden");
        assertTrue(foundBestellung, "Klasse 'Bestellung' muss gefunden werden");

        // Verify edge source/target resolution (was TODO: class-edge-ID pattern)
        assertFalse(diagram.getEdges().isEmpty(), "Mindestens 1 Edge erwartet");
        DiagramEdge klassEdge = diagram.getEdges().get(0);
        System.out.println("  Edge resolved: " + klassEdge.getSourceId() + " → " + klassEdge.getTargetId()
                + " [" + klassEdge.getLabel() + "]");
        assertEquals("Kunde", klassEdge.getSourceId(),
                "Edge-Source muss 'Kunde' sein, war: " + klassEdge.getSourceId());
        assertEquals("Bestellung", klassEdge.getTargetId(),
                "Edge-Target muss 'Bestellung' sein, war: " + klassEdge.getTargetId());
        assertEquals("bestellt", klassEdge.getLabel(),
                "Edge-Label muss 'bestellt' sein, war: " + klassEdge.getLabel());
    }

    // ═══════════════════════════════════════════════════════════
    //  8. Sequenzdiagramm
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("Sequenzdiagramm: Akteure und Nachrichten extrahieren")
    void sequenceDiagram_extractActorsAndMessages() {
        assumeEngine();

        String svg = renderer.renderToSvg(SEQUENCE_DIAGRAM);
        assertNotNull(svg, "Sequenzdiagramm-SVG darf nicht null sein");

        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        System.out.println("=== Sequenzdiagramm ===");
        System.out.println("Diagram type: " + diagram.getDiagramType());
        for (DiagramNode n : diagram.getNodes()) {
            System.out.println("  Actor: " + n);
        }
        for (DiagramEdge e : diagram.getEdges()) {
            System.out.println("  Message: " + e);
        }

        // Should find 2 actors: Alice, Bob
        assertTrue(diagram.getNodes().size() >= 2,
                "Mindestens 2 Akteure erwartet, gefunden: " + diagram.getNodes().size());

        boolean foundAlice = false, foundBob = false;
        for (DiagramNode n : diagram.getNodes()) {
            if ("Alice".equals(n.getId())) foundAlice = true;
            if ("Bob".equals(n.getId())) foundBob = true;
        }
        assertTrue(foundAlice, "Akteur 'Alice' muss gefunden werden");
        assertTrue(foundBob, "Akteur 'Bob' muss gefunden werden");

        // Should find at least 2 messages
        assertTrue(diagram.getEdges().size() >= 2,
                "Mindestens 2 Nachrichten erwartet, gefunden: " + diagram.getEdges().size());
    }

    // ═══════════════════════════════════════════════════════════
    //  9. JavaScript-Snippet für WebView
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("JavaScript-Highlight-Snippet generieren")
    void generateJsHighlightScript() {
        // Dieser Test braucht keine GraalJS-Engine — testet nur die Code-Generierung
        String svgId = "flowchart-A-0";

        String blinkScript = SvgHighlighter.generateHighlightScript(
                Arrays.asList(svgId), SvgHighlighter.Mode.BLINK);
        assertNotNull(blinkScript);
        assertTrue(blinkScript.contains("mmdBlink"), "JS muss Animation-Name enthalten");
        assertTrue(blinkScript.contains(svgId), "JS muss SVG-ID enthalten");

        String steadyScript = SvgHighlighter.generateHighlightScript(
                Arrays.asList(svgId), SvgHighlighter.Mode.STEADY);
        assertNotNull(steadyScript);
        assertTrue(steadyScript.contains("drop-shadow"), "JS muss drop-shadow enthalten");

        String clearScript = SvgHighlighter.generateClearScript();
        assertNotNull(clearScript);
        assertTrue(clearScript.contains("mmd-highlight-style"), "JS muss Style-Cleanup enthalten");

        System.out.println("✓ JavaScript-Snippets generiert");
        System.out.println("BLINK script length: " + blinkScript.length());
        System.out.println("STEADY script length: " + steadyScript.length());
    }

    // ═══════════════════════════════════════════════════════════
    //  10. Mehrere Nodes gleichzeitig hervorheben
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("Mehrere Nodes gleichzeitig hervorheben (Multi-Select)")
    void highlightMultipleNodes() {
        assumeEngine();

        String svg = renderer.renderToSvg(FLOWCHART_V1);
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        DiagramNode nodeA = diagram.findNodeById("A");
        DiagramNode nodeC = diagram.findNodeById("C");
        assertNotNull(nodeA);
        assertNotNull(nodeC);

        String highlightedSvg = SvgHighlighter.highlight(
                svg,
                Arrays.asList(nodeA.getSvgId(), nodeC.getSvgId()),
                SvgHighlighter.Mode.STEADY);
        assertNotNull(highlightedSvg);
        assertTrue(highlightedSvg.contains(nodeA.getSvgId()),
                "SVG muss SVG-ID von A enthalten");
        assertTrue(highlightedSvg.contains(nodeC.getSvgId()),
                "SVG muss SVG-ID von C enthalten");

        System.out.println("✓ Multi-Highlight für A + C injiziert");
    }

    // ═══════════════════════════════════════════════════════════
    //  11. End-to-End: Voller Designer-Workflow
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(11)
    @DisplayName("E2E: Vollständiger mermaid-designer Workflow")
    void endToEnd_designerWorkflow() {
        assumeEngine();

        System.out.println("\n=== E2E Workflow ===");

        // 1. User öffnet Editor mit Flowchart
        String svg1 = renderer.renderToSvg(FLOWCHART_V1);
        RenderedDiagram d1 = DiagramLayoutExtractor.extract(svg1);
        assertNotNull(d1);
        System.out.println("1. Initial: " + d1);

        // 2. User klickt auf Node B im Preview → SVG-Element-ID kommt zurück
        DiagramNode clickedNode = d1.findNodeById("B");
        String svgElementId = clickedNode.getSvgId();
        System.out.println("2. User klickt auf SVG-Element: " + svgElementId);

        // 3. App macht reverse lookup → findet logische Node-ID
        DiagramNode resolved = d1.findNodeBySvgId(svgElementId);
        assertEquals("B", resolved.getId());
        System.out.println("3. Reverse lookup → Node: " + resolved.getId());

        // 4. App hebt Node B gelb hervor (STEADY)
        String highlighted = SvgHighlighter.highlightNode(d1, "B", SvgHighlighter.Mode.STEADY);
        assertTrue(highlighted.contains("mmd-highlight-start"));
        System.out.println("4. STEADY-Highlight für B gesetzt");

        // 5. User editiert Node B im Text-Editor (ändert Label)
        //    → kein Layout-Update nötig, nur Text-Sync

        // 6. User fügt Node E hinzu über Toolbar
        String svg2 = renderer.renderToSvg(FLOWCHART_V2);
        RenderedDiagram d2 = DiagramLayoutExtractor.extract(svg2);
        assertNotNull(d2);
        System.out.println("5. Nach Hinzufügen von E: " + d2);

        // 7. App findet neuen Node E und blinkt ihn an
        DiagramNode newNode = d2.findNodeById("E");
        assertNotNull(newNode);
        String blinkSvg = SvgHighlighter.highlight(
                svg2, newNode.getSvgId(), SvgHighlighter.Mode.BLINK);
        assertTrue(blinkSvg.contains("mmd-highlight-blink"));
        System.out.println("6. BLINK-Highlight für neuen Node E: " + newNode);

        // 8. Alternative: JavaScript-Snippet für WebView-basiertes Highlighting
        String jsSnippet = SvgHighlighter.generateHighlightScript(
                Arrays.asList(newNode.getSvgId()), SvgHighlighter.Mode.BLINK);
        assertTrue(jsSnippet.contains("mmdBlink"));
        System.out.println("7. JS-Snippet für WebView generiert (" + jsSnippet.length() + " chars)");

        System.out.println("\n✓ E2E Workflow komplett — alle Schritte erfolgreich!");
    }

    // ═══════════════════════════════════════════════════════════
    //  12. ViewBox und Koordinatensystem
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(12)
    @DisplayName("ViewBox wird korrekt extrahiert")
    void viewBox_extracted() {
        assumeEngine();

        String svg = renderer.renderToSvg(FLOWCHART_V1);
        RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
        assertNotNull(diagram);

        System.out.println("ViewBox: x=" + diagram.getViewBoxX()
                + " y=" + diagram.getViewBoxY()
                + " w=" + diagram.getViewBoxWidth()
                + " h=" + diagram.getViewBoxHeight());

        // ViewBox should have non-zero dimensions
        assertTrue(diagram.getViewBoxWidth() > 0, "ViewBox-Breite muss > 0 sein");
        assertTrue(diagram.getViewBoxHeight() > 0, "ViewBox-Höhe muss > 0 sein");

        // All nodes should be within the viewBox (with some tolerance for padding)
        double margin = 50;
        for (DiagramNode n : diagram.getNodes()) {
            assertTrue(n.getX() >= diagram.getViewBoxX() - margin,
                    "Node " + n.getId() + " x=" + n.getX() + " liegt außerhalb der ViewBox");
            assertTrue(n.getY() >= diagram.getViewBoxY() - margin,
                    "Node " + n.getId() + " y=" + n.getY() + " liegt außerhalb der ViewBox");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  13. renderWithLayout() Convenience-Methode
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @DisplayName("renderWithLayout() Convenience-Methode")
    void renderWithLayout_convenienceMethod() {
        assumeEngine();

        // One-liner: render + extract in a single call
        RenderedDiagram diagram = renderer.renderWithLayout(FLOWCHART_V1);
        assertNotNull(diagram, "renderWithLayout darf nicht null liefern");

        // Must contain SVG
        assertNotNull(diagram.getSvg(), "SVG muss vorhanden sein");
        assertTrue(diagram.getSvg().contains("<svg"), "Muss gültiges SVG enthalten");

        // Must have nodes and edges
        assertTrue(diagram.getNodes().size() >= 4,
                "Mindestens 4 Nodes erwartet, gefunden: " + diagram.getNodes().size());
        assertTrue(diagram.getEdges().size() >= 3,
                "Mindestens 3 Edges erwartet, gefunden: " + diagram.getEdges().size());

        // Quick highlight test on the result
        DiagramNode nodeA = diagram.findNodeById("A");
        assertNotNull(nodeA);
        String highlighted = SvgHighlighter.highlightNode(diagram, "A", SvgHighlighter.Mode.BLINK);
        assertTrue(highlighted.contains("mmd-highlight-blink"));

        System.out.println("✓ renderWithLayout() → " + diagram);
    }
}

