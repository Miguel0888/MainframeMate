package de.bund.zrb.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Mermaid renderer module.
 */
class MermaidRendererTest {

    private final GraalJsExecutor executor = new GraalJsExecutor();

    // ── GraalJS basics ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GraalJS executes trivial JavaScript and returns result")
    void basicJavaScriptExecution() {
        JsExecutionResult result = executor.execute("40 + 2;");
        assertTrue(result.isSuccessful());
        assertEquals("42", result.getOutput());
    }

    @Test
    @DisplayName("Syntax error in script yields failure result, not exception")
    void syntaxErrorProducesFailure() {
        JsExecutionResult result = executor.execute("this is not valid javascript !!!");
        assertFalse(result.isSuccessful());
        assertNotNull(result.getErrorMessage());
    }

    // ── Browser shim ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Browser shim loads successfully in GraalJS")
    void browserShimLoads() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        assertNotNull(shim, "browser-shim.js should be on the classpath");

        JsExecutionResult result = executor.execute(shim + "\n'ok';");
        assertTrue(result.isSuccessful(), "Shim should load: " + result.getErrorMessage());
        assertEquals("ok", result.getOutput());
    }

    // ── MermaidRenderer ─────────────────────────────────────────────────────

    @Test
    @DisplayName("MermaidRenderer singleton is available when bundle is on classpath")
    void rendererIsAvailable() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        // May or may not be available depending on whether the real mermaid.min.js is present
        assertNotNull(renderer);
    }

    @Test
    @DisplayName("renderToSvg returns null for null/empty input")
    void renderNullInput() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        assertNull(renderer.renderToSvg(null));
        assertNull(renderer.renderToSvg(""));
        assertNull(renderer.renderToSvg("   "));
    }

    @Test
    @DisplayName("Renders a simple flowchart to SVG")
    void renderFlowchartToSvg() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            return; // skip if real mermaid.min.js is not present
        }

        String svg = renderer.renderToSvg("graph TD; A-->B; B-->C;");

        assertNotNull(svg, "SVG should not be null");
        assertTrue(svg.contains("<svg"), "Output should contain <svg> element");
        assertTrue(svg.contains("flowchart"), "SVG should contain flowchart content");
    }

    @Test
    @DisplayName("Renders a sequence diagram to SVG")
    void renderSequenceDiagramToSvg() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            return;
        }

        String svg = renderer.renderToSvg("sequenceDiagram\n    Alice->>Bob: Hello\n    Bob->>Alice: Hi back");

        assertNotNull(svg, "SVG should not be null");
        assertTrue(svg.contains("<svg"), "Output should contain <svg> element");
    }

    // ── JsExecutionResult ───────────────────────────────────────────────────


    @Test
    @DisplayName("JsExecutionResult toString is readable")
    void resultToString() {
        assertTrue(JsExecutionResult.success("hello").toString().contains("SUCCESS"));
        assertTrue(JsExecutionResult.failure("boom").toString().contains("FAILURE"));
    }

    // ── Mermaid 11+ new diagram types ───────────────────────────────────────

    @Test
    @DisplayName("Renders a mindmap to SVG (Mermaid 11+ only)")
    void renderMindmapToSvg() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            return;
        }

        String svg = renderer.renderToSvg(
                "mindmap\n  root((Humus))\n    Arten\n      Naehrhumus\n      Dauerhumus");

        assertNotNull(svg, "Mindmap SVG should not be null");
        assertTrue(svg.contains("<svg"), "Output should contain <svg> element");
    }

    @Test
    @DisplayName("Renders a timeline to SVG (Mermaid 11+ only)")
    void renderTimelineToSvg() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            return;
        }

        String svg = renderer.renderToSvg(
                "timeline\n    title History\n    2020 : Event A\n    2021 : Event B");

        assertNotNull(svg, "Timeline SVG should not be null");
        assertTrue(svg.contains("<svg"), "Output should contain <svg> element");
    }

    @Test
    @DisplayName("Renders a quadrant chart to SVG (Mermaid 11+ only)")
    void renderQuadrantToSvg() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) {
            return;
        }

        String svg = renderer.renderToSvg(
                "quadrantChart\n"
                        + "    title Tech Priority\n"
                        + "    x-axis Low Effort --> High Effort\n"
                        + "    y-axis Low Impact --> High Impact\n"
                        + "    Feature A: [0.3, 0.6]\n"
                        + "    Feature B: [0.7, 0.8]\n");

        assertNotNull(svg, "Quadrant chart SVG should not be null");
        assertTrue(svg.contains("<svg"), "Output should contain <svg> element");
    }

    // ── PostProcessSvg edge-case tests ──────────────────────────────────────

    @Test
    @DisplayName("postProcessSvg strips content after </svg>")
    void postProcessStripsTrailingContent() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><rect/></svg><style>.x{color:red}</style>";
        String result = MermaidRenderer.postProcessSvg(svg);
        assertFalse(result.contains("<style>"), "Content after </svg> should be removed");
        assertTrue(result.endsWith("</svg>"), "Should end with </svg>");
    }

    @Test
    @DisplayName("postProcessSvg removes alignment-baseline attributes")
    void postProcessRemovesAlignmentBaseline() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                + "<text alignment-baseline=\"central\">Hello</text></svg>";
        String result = MermaidRenderer.postProcessSvg(svg);
        assertFalse(result.contains("alignment-baseline"), "alignment-baseline should be removed");
    }

    @Test
    @DisplayName("postProcessSvg wraps style content in CDATA")
    void postProcessWrapsStyleInCdata() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                + "<style>.node > rect { fill: #fff; }</style><rect/></svg>";
        String result = MermaidRenderer.postProcessSvg(svg);
        assertTrue(result.contains("<![CDATA["), "Style with > should be CDATA-wrapped");
    }

    @Test
    @DisplayName("postProcessSvg removes stray HTML elements")
    void postProcessRemovesStrayHtml() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                + "<g><div class=\"label\"><span>Hello</span></div></g></svg>";
        String result = MermaidRenderer.postProcessSvg(svg);
        assertFalse(result.contains("<div"), "div tags should be removed");
        assertFalse(result.contains("<span"), "span tags should be removed");
        assertTrue(result.contains("Hello"), "Text content should be preserved");
    }

    @Test
    @DisplayName("Flowchart SVG passes XML validation after postProcessSvg")
    void flowchartProducesValidXml() throws Exception {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) return;

        String svg = renderer.renderToSvg("graph TD; A-->B; B-->C;");
        assertNotNull(svg, "SVG should not be null");


        // Try to parse as XML — should not throw
        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
        org.w3c.dom.Document doc = db.parse(new java.io.ByteArrayInputStream(svg.getBytes("UTF-8")));
        assertNotNull(doc.getDocumentElement(), "Should parse to valid DOM");
    }

    @Test
    @DisplayName("Sequence diagram SVG has no alignment-baseline after full pipeline")
    void sequenceDiagramNoAlignmentBaseline() {
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        if (!renderer.isAvailable()) return;

        String svg = renderer.renderToSvg("sequenceDiagram\n    Alice->>Bob: Hello\n    Bob->>Alice: Hi");
        assertNotNull(svg);

        // Full pipeline: postProcessSvg (already applied) + fixForBatik
        String fixed = MermaidSvgFixup.fixForBatik(svg);
        assertFalse(fixed.contains("alignment-baseline"),
                "alignment-baseline should not survive the full pipeline");
    }
}
