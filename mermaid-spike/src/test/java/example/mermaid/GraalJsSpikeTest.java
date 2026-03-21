package example.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the GraalJS spike — validates that the embedded JS engine works
 * and that the probe stages execute correctly.
 */
class GraalJsSpikeTest {

    private final GraalJsExecutor executor = new GraalJsExecutor();

    @Test
    @DisplayName("GraalJS executes trivial JavaScript and returns result")
    void basicJavaScriptExecution() {
        JavaScriptExecutionResult result = executor.execute("40 + 2;");

        assertTrue(result.isSuccessful(), "Expected successful execution");
        assertEquals("42", result.getOutput());
    }

    @Test
    @DisplayName("GraalJS exposes javaBridge to scripts")
    void javaBridgeIsAccessible() {
        JavaScriptExecutionResult result = executor.execute(
                "javaBridge.log('hello from test'); 'ok';"
        );

        assertTrue(result.isSuccessful(), "Expected successful execution");
        assertEquals("ok", result.getOutput());
    }

    @Test
    @DisplayName("Syntax error in script yields failure result, not exception")
    void syntaxErrorProducesFailure() {
        JavaScriptExecutionResult result = executor.execute("this is not valid javascript !!!");

        assertFalse(result.isSuccessful(), "Expected failure for invalid JS");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("Stage 1 probe succeeds")
    void stage1BasicProbe() {
        MermaidRenderingProbe probe = new MermaidRenderingProbe(executor);
        JavaScriptExecutionResult result = probe.runBasicJavaScriptProbe();

        assertTrue(result.isSuccessful(), "Stage 1 should succeed: " + result.getErrorMessage());
        assertEquals("GraalJS works", result.getOutput());
    }

    @Test
    @DisplayName("Stage 2 probe installs pseudo browser globals")
    void stage2PseudoBrowserProbe() {
        MermaidRenderingProbe probe = new MermaidRenderingProbe(executor);
        JavaScriptExecutionResult result = probe.runPseudoMermaidEnvironmentProbe();

        assertTrue(result.isSuccessful(), "Stage 2 should succeed: " + result.getErrorMessage());
        assertEquals("Pseudo browser globals installed", result.getOutput());
    }

    @Test
    @DisplayName("Stage 3 probe handles a trivial JS 'library' without error")
    void stage3TrivialLibraryLoad() {
        MermaidRenderingProbe probe = new MermaidRenderingProbe(executor);

        // Simulate a tiny JS library that sets a global via UMD-like pattern
        String fakeMermaid = "window.mermaid = { version: '0.0.0-fake' };\n";
        JavaScriptExecutionResult result = probe.runMermaidLoadProbe(fakeMermaid);

        assertTrue(result.isSuccessful(), "Stage 3 with fake library should succeed: " + result.getErrorMessage());
        assertTrue(result.getOutput().contains("Loaded Mermaid source"), "Expected loaded confirmation");
    }

    @Test
    @DisplayName("Stage 3 loads the real mermaid.min.js bundle successfully")
    void stage3RealMermaidLoad() {
        String mermaidSource = MermaidRenderingProbe.loadResource("/mermaid/mermaid.min.js");
        if (mermaidSource == null || mermaidSource.length() < 10000) {
            // Placeholder is present, not the real bundle — skip
            return;
        }

        MermaidRenderingProbe probe = new MermaidRenderingProbe(executor);
        JavaScriptExecutionResult result = probe.runMermaidLoadProbe(mermaidSource);

        assertTrue(result.isSuccessful(), "Real mermaid.min.js should load: " + result.getErrorMessage());
        assertTrue(result.getOutput().contains("window.mermaid available"), "mermaid should be on window");
    }

    @Test
    @DisplayName("Stage 4 renders a simple flowchart to SVG")
    void stage4RenderFlowchartToSvg() {
        String mermaidSource = MermaidRenderingProbe.loadResource("/mermaid/mermaid.min.js");
        if (mermaidSource == null || mermaidSource.length() < 10000) {
            // Placeholder is present, not the real bundle — skip
            return;
        }

        MermaidRenderingProbe probe = new MermaidRenderingProbe(executor);
        JavaScriptExecutionResult result = probe.runMermaidRenderProbe(mermaidSource, "graph TD; A-->B; B-->C;");

        assertTrue(result.isSuccessful(), "Rendering should succeed: " + result.getErrorMessage());
        assertNotNull(result.getOutput(), "SVG output should not be null");
        assertTrue(result.getOutput().contains("<svg"), "Output should contain SVG element");
        assertTrue(result.getOutput().contains("flowchart"), "SVG should contain flowchart content");
    }

    @Test
    @DisplayName("JavaScriptExecutionResult toString is readable")
    void resultToString() {
        JavaScriptExecutionResult success = JavaScriptExecutionResult.success("hello");
        assertTrue(success.toString().contains("SUCCESS"));

        JavaScriptExecutionResult failure = JavaScriptExecutionResult.failure("boom");
        assertTrue(failure.toString().contains("FAILURE"));
    }
}

