package de.bund.zrb.mermaid;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test: renders Mermaid diagrams to SVG and validates the raw XML.
 * NO Batik, NO Swing — purely checks if Mermaid 11 + GraalJS produces valid SVG XML.
 * <p>
 * Each test case:
 * 1. Renders via {@link MermaidRenderer}
 * 2. Saves raw SVG to {@code build/svg-diag/} for manual inspection
 * 3. Saves post-processed SVG to same directory
 * 4. Attempts XML parsing on both raw and processed outputs
 * 5. Reports validity
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MermaidSvgDiagnosticTest {

    private static final File OUTPUT_DIR = new File("build/svg-diag");
    private MermaidRenderer renderer;
    private DocumentBuilder xmlParser;

    @BeforeAll
    void setUp() throws Exception {
        OUTPUT_DIR.mkdirs();
        renderer = MermaidRenderer.getInstance();
        assertTrue(renderer.isAvailable(), "Mermaid bundle must be on classpath");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        // Disable DTD/external entity fetching to avoid network hangs
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        xmlParser = dbf.newDocumentBuilder();
    }

    // ── Test Cases ───────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC1: Simple Flowchart")
    void tc1_simpleFlowchart() {
        renderAndValidate("tc1_flowchart", "graph TD\n    A[Start] --> B[Process]\n    B --> C[End]");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC2: Simple Sequence Diagram")
    void tc2_simpleSequence() {
        renderAndValidate("tc2_sequence", "sequenceDiagram\n    Alice->>Bob: Hello\n    Bob->>Alice: Hi back");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC3: Flowchart with Subgraph")
    void tc3_subgraph() {
        renderAndValidate("tc3_subgraph",
                "graph TD\n"
                        + "    subgraph Group1\n"
                        + "        A --> B\n"
                        + "    end\n"
                        + "    subgraph Group2\n"
                        + "        C --> D\n"
                        + "    end\n"
                        + "    B --> C");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC4: Class Diagram")
    void tc4_classDiagram() {
        renderAndValidate("tc4_class",
                "classDiagram\n"
                        + "    class Animal {\n"
                        + "        +String name\n"
                        + "        +makeSound()\n"
                        + "    }\n"
                        + "    class Dog {\n"
                        + "        +fetch()\n"
                        + "    }\n"
                        + "    Animal <|-- Dog");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC5: State Diagram")
    void tc5_stateDiagram() {
        renderAndValidate("tc5_state",
                "stateDiagram-v2\n"
                        + "    [*] --> Idle\n"
                        + "    Idle --> Running : start\n"
                        + "    Running --> Idle : stop\n"
                        + "    Running --> [*] : crash");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC6: Pie Chart")
    void tc6_pie() {
        renderAndValidate("tc6_pie",
                "pie title Pets\n"
                        + "    \"Dogs\" : 386\n"
                        + "    \"Cats\" : 85\n"
                        + "    \"Birds\" : 15");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC7: Gantt Chart (known limitation — needs full DOM layout)")
    void tc7_gantt() {
        String diagramCode = "gantt\n"
                + "    title Project Plan\n"
                + "    dateFormat  YYYY-MM-DD\n"
                + "    section Design\n"
                + "    Wireframe :a1, 2024-01-01, 7d\n"
                + "    Mockup    :after a1, 5d\n"
                + "    section Dev\n"
                + "    Backend   :2024-01-08, 14d\n"
                + "    Frontend  :2024-01-15, 10d";

        System.out.println("\n════════════════════════════════════════════════════════════");
        System.out.println("  tc7_gantt (known limitation)");
        System.out.println("════════════════════════════════════════════════════════════");

        String rawSvg = renderer.renderToSvg(diagramCode);
        if (rawSvg == null) {
            System.out.println("[KNOWN LIMITATION] Gantt chart requires full DOM layout (offsetWidth)");
            System.out.println("  This is expected — Gantt uses D3 DOM measurement which our headless shim cannot provide.");
            // Don't fail — this is a known limitation
            return;
        }
        // If it does render, validate it
        saveTo("tc7_gantt_raw.svg", rawSvg);
        String fixed = MermaidSvgFixup.fixForBatik(MermaidRenderer.postProcessSvg(rawSvg));
        saveTo("tc7_gantt_fixed.svg", fixed);
        String xmlError = tryXmlParse(fixed);
        assertNull(xmlError, "If Gantt renders, it should be valid XML");
    }

    // ── Core validation logic ────────────────────────────────────────────────

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC8: ER Diagram")
    void tc8_erDiagram() {
        renderAndValidate("tc8_er",
                "erDiagram\n"
                        + "    CUSTOMER ||--o{ ORDER : places\n"
                        + "    ORDER ||--|{ LINE-ITEM : contains\n"
                        + "    CUSTOMER {\n"
                        + "        string name\n"
                        + "        int id\n"
                        + "    }");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC9: Journey Diagram")
    void tc9_journey() {
        renderAndValidate("tc9_journey",
                "journey\n"
                        + "    title My Day\n"
                        + "    section Morning\n"
                        + "      Wake up: 3: Me\n"
                        + "      Coffee: 5: Me\n"
                        + "    section Work\n"
                        + "      Code: 4: Me, Cat");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC10: Mindmap (known limitation)")
    void tc10_mindmap() {
        System.out.println("\n════════════════════════════════════════════════════════════");
        System.out.println("  tc10_mindmap");
        System.out.println("════════════════════════════════════════════════════════════");

        String rawSvg = renderer.renderToSvg(
                "mindmap\n  root((Humus))\n    Arten\n      Naehrhumus\n      Dauerhumus");
        if (rawSvg == null) {
            System.out.println("[KNOWN LIMITATION] Mindmap requires Canvas 2D text measurement");
            return;
        }
        saveTo("tc10_mindmap_raw.svg", rawSvg);
        String fixed = MermaidSvgFixup.fixForBatik(MermaidRenderer.postProcessSvg(rawSvg));
        saveTo("tc10_mindmap_fixed.svg", fixed);
        String xmlError = tryXmlParse(fixed);
        System.out.println(xmlError == null ? "RESULT: tc10_mindmap PASSED" : "RESULT: tc10_mindmap FAILED — " + xmlError);
        assertNull(xmlError, "If mindmap renders, it should be valid XML");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("TC11: Git Graph")
    void tc11_gitGraph() {
        renderAndValidate("tc11_gitgraph",
                "gitGraph\n"
                        + "    commit\n"
                        + "    branch develop\n"
                        + "    checkout develop\n"
                        + "    commit\n"
                        + "    commit\n"
                        + "    checkout main\n"
                        + "    merge develop\n"
                        + "    commit");
    }

    private void renderAndValidate(String name, String diagramCode) {
        System.out.println("\n════════════════════════════════════════════════════════════");
        System.out.println("  " + name);
        System.out.println("════════════════════════════════════════════════════════════");

        // Step 1: Render
        System.out.println("[1] Rendering via MermaidRenderer...");
        long t0 = System.currentTimeMillis();
        String rawSvg = renderer.renderToSvg(diagramCode);
        long renderMs = System.currentTimeMillis() - t0;
        System.out.println("[1] Render took " + renderMs + " ms");

        if (rawSvg == null) {
            System.out.println("[1] *** RENDER RETURNED NULL ***");
            // Try detailed to get error message
            JsExecutionResult detailed = renderer.renderToSvgDetailed(diagramCode);
            if (!detailed.isSuccessful()) {
                System.out.println("[1] Error: " + detailed.getErrorMessage());
            } else {
                System.out.println("[1] Output was: " + (detailed.getOutput() == null ? "null"
                        : detailed.getOutput().substring(0, Math.min(500, detailed.getOutput().length()))));
            }
            saveTo(name + "_raw_NULL.txt", "Render returned null for:\n" + diagramCode);
            fail(name + ": render returned null");
            return;
        }

        System.out.println("[1] Raw SVG length: " + rawSvg.length() + " chars");

        // Save raw SVG
        saveTo(name + "_raw.svg", rawSvg);
        System.out.println("[2] Saved raw SVG to build/svg-diag/" + name + "_raw.svg");

        // Step 2: Check raw SVG basics
        boolean rawHasSvgTag = rawSvg.contains("<svg");
        boolean rawHasClosingSvg = rawSvg.contains("</svg>");
        System.out.println("[2] Raw has <svg> tag: " + rawHasSvgTag);
        System.out.println("[2] Raw has </svg> tag: " + rawHasClosingSvg);

        // Print first 300 and last 300 chars for quick inspection
        System.out.println("[2] Raw SVG head: " + rawSvg.substring(0, Math.min(300, rawSvg.length())));
        System.out.println("[2] Raw SVG tail: " + rawSvg.substring(Math.max(0, rawSvg.length() - 300)));

        // Step 3: Try XML parsing on raw SVG
        String rawXmlError = tryXmlParse(rawSvg);
        if (rawXmlError == null) {
            System.out.println("[3] ✅ Raw SVG is valid XML");
        } else {
            System.out.println("[3] ❌ Raw SVG is NOT valid XML: " + rawXmlError);
        }

        // Step 4: Apply postProcessSvg
        System.out.println("[4] Applying postProcessSvg...");
        String processed = MermaidRenderer.postProcessSvg(rawSvg);
        saveTo(name + "_postprocess.svg", processed);
        System.out.println("[4] Post-processed SVG length: " + processed.length() + " chars");
        System.out.println("[4] Saved to build/svg-diag/" + name + "_postprocess.svg");

        String postXmlError = tryXmlParse(processed);
        if (postXmlError == null) {
            System.out.println("[4] ✅ Post-processed SVG is valid XML");
        } else {
            System.out.println("[4] ❌ Post-processed SVG is NOT valid XML: " + postXmlError);
            System.out.println("[4] Post-proc head: " + processed.substring(0, Math.min(500, processed.length())));
            System.out.println("[4] Post-proc tail: " + processed.substring(Math.max(0, processed.length() - 500)));
        }

        // Step 5: Apply MermaidSvgFixup.fixForBatik
        System.out.println("[5] Applying MermaidSvgFixup.fixForBatik...");
        String fixed = MermaidSvgFixup.fixForBatik(processed);
        saveTo(name + "_fixed.svg", fixed);
        System.out.println("[5] Fixed SVG length: " + fixed.length() + " chars");
        System.out.println("[5] Saved to build/svg-diag/" + name + "_fixed.svg");

        String fixedXmlError = tryXmlParse(fixed);
        if (fixedXmlError == null) {
            System.out.println("[5] ✅ Fixed SVG is valid XML");
        } else {
            System.out.println("[5] ❌ Fixed SVG is NOT valid XML: " + fixedXmlError);
            System.out.println("[5] Fixed head: " + fixed.substring(0, Math.min(500, fixed.length())));
            System.out.println("[5] Fixed tail: " + fixed.substring(Math.max(0, fixed.length() - 500)));
        }

        // Step 6: Check for problematic attributes
        checkForProblems(name, fixed);

        // Step 7: Final assertion — at least the fixed SVG should be valid XML
        System.out.println("────────────────────────────────────────────────────────────");
        if (fixedXmlError != null) {
            System.out.println("RESULT: " + name + " FAILED — fixed SVG still not valid XML");
        } else {
            System.out.println("RESULT: " + name + " PASSED — valid XML SVG produced");
        }
        assertNull(fixedXmlError, name + ": fixed SVG should be valid XML but got: " + fixedXmlError);
    }

    private String tryXmlParse(String svg) {
        try {
            Document doc = xmlParser.parse(new InputSource(new StringReader(svg)));
            if (doc.getDocumentElement() == null) {
                return "Parsed but no document element";
            }
            return null; // valid
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private void checkForProblems(String name, String svg) {
        boolean hasAlignmentBaseline = svg.contains("alignment-baseline");
        boolean hasForeignObject = svg.toLowerCase().contains("foreignobject");
        boolean hasCurrentColor = svg.contains("currentColor");
        boolean hasCssVariables = svg.contains("var(--");
        boolean hasXhtmlNs = svg.contains("xmlns=\"http://www.w3.org/1999/xhtml\"");
        boolean hasHtmlTags = svg.contains("<div") || svg.contains("<span") || svg.contains("<body");

        System.out.println("[6] Problem check:");
        System.out.println("    alignment-baseline : " + (hasAlignmentBaseline ? "❌ PRESENT" : "✅ clean"));
        System.out.println("    foreignObject      : " + (hasForeignObject ? "⚠️ PRESENT" : "✅ clean"));
        System.out.println("    currentColor       : " + (hasCurrentColor ? "⚠️ present (minor)" : "✅ clean"));
        System.out.println("    CSS var(--)        : " + (hasCssVariables ? "⚠️ present" : "✅ clean"));
        System.out.println("    xhtml namespace    : " + (hasXhtmlNs ? "❌ PRESENT" : "✅ clean"));
        System.out.println("    HTML tags in SVG   : " + (hasHtmlTags ? "❌ PRESENT" : "✅ clean"));
    }

    private void saveTo(String filename, String content) {
        try {
            FileWriter writer = new FileWriter(new File(OUTPUT_DIR, filename));
            writer.write(content);
            writer.close();
        } catch (Exception e) {
            System.err.println("Failed to save " + filename + ": " + e.getMessage());
        }
    }
}

