https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.jshttps://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.jspackage example.mermaid;

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

        // Simulate a tiny JS library that sets a global
        String fakeMermaid = "var mermaid = { version: '0.0.0-fake' };\n";
        JavaScriptExecutionResult result = probe.runMermaidLoadProbe(fakeMermaid);

        assertTrue(result.isSuccessful(), "Stage 3 with fake library should succeed: " + result.getErrorMessage());
        assertEquals("Loaded Mermaid source", result.getOutput());
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

