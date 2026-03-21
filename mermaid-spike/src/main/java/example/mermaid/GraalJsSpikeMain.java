https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.jspackage example.mermaid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Entry point for the GraalJS / Mermaid rendering spike.
 * <p>
 * Runs three probes in sequence:
 * <ol>
 *     <li>Basic JavaScript execution</li>
 *     <li>Pseudo browser environment setup</li>
 *     <li>Loading the actual mermaid.min.js bundle (if present on the classpath)</li>
 * </ol>
 */
public final class GraalJsSpikeMain {

    private static final String MERMAID_RESOURCE = "/mermaid/mermaid.min.js";

    public static void main(String[] args) {
        GraalJsExecutor executor = new GraalJsExecutor();
        MermaidRenderingProbe probe = new MermaidRenderingProbe(executor);

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   GraalJS / Mermaid Rendering Spike                 ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // Stage 1
        printResult("Stage 1: Basic JavaScript probe", probe.runBasicJavaScriptProbe());

        // Stage 2
        printResult("Stage 2: Pseudo browser environment probe", probe.runPseudoMermaidEnvironmentProbe());

        // Stage 3
        String mermaidSource = loadResource(MERMAID_RESOURCE);
        if (mermaidSource == null) {
            System.out.println("=== Stage 3: Mermaid source load probe ===");
            System.out.println("⚠  Mermaid source not found at classpath:" + MERMAID_RESOURCE);
            System.out.println("   Place mermaid.min.js into src/main/resources/mermaid/ and re-run.");
            System.out.println("   Download from: https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js");
            System.out.println();
            System.out.println("Spike finished (stages 1–2 only).");
            return;
        }

        System.out.println("   Mermaid source size: " + mermaidSource.length() + " chars");
        printResult("Stage 3: Mermaid source load probe", probe.runMermaidLoadProbe(mermaidSource));

        System.out.println("Spike finished.");
    }

    private static void printResult(String label, JavaScriptExecutionResult result) {
        System.out.println("=== " + label + " ===");

        if (result.isSuccessful()) {
            System.out.println("✔  Successful");
            System.out.println("   Output: " + result.getOutput());
        } else {
            System.out.println("✘  Failed");
            String error = result.getErrorMessage();
            // Truncate very long error messages for readability
            if (error != null && error.length() > 2000) {
                error = error.substring(0, 2000) + "\n   ... [truncated, " + result.getErrorMessage().length() + " chars total]";
            }
            System.out.println("   Error: " + error);
        }

        System.out.println();
    }

    private static String loadResource(String resourcePath) {
        InputStream inputStream = GraalJsSpikeMain.class.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            return null;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }

            return content.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read resource: " + resourcePath, exception);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // Ignore close exception
                }
            }
        }
    }
}

