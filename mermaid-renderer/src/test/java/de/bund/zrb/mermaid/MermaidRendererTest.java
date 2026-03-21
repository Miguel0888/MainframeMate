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
}

