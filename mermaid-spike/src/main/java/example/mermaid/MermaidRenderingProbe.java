package example.mermaid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Three-stage probe for Mermaid rendering feasibility inside GraalJS:
 * <ol>
 *     <li>Basic JS execution</li>
 *     <li>Pseudo browser globals (window, document, navigator)</li>
 *     <li>Loading the real mermaid.min.js bundle</li>
 * </ol>
 */
public final class MermaidRenderingProbe {

    private static final String BROWSER_SHIM_RESOURCE = "/mermaid/browser-shim.js";

    private final GraalJsExecutor graalJsExecutor;

    public MermaidRenderingProbe(GraalJsExecutor graalJsExecutor) {
        this.graalJsExecutor = graalJsExecutor;
    }

    /**
     * Stage 1 — verify that GraalJS can execute trivial JavaScript.
     */
    public JavaScriptExecutionResult runBasicJavaScriptProbe() {
        String script =
                "var message = 'GraalJS works';" +
                "javaBridge.log(message);" +
                "message;";

        return graalJsExecutor.execute(script);
    }

    /**
     * Stage 2 — install minimal browser-like globals and verify they survive.
     */
    public JavaScriptExecutionResult runPseudoMermaidEnvironmentProbe() {
        String shimSource = loadShim();
        if (shimSource == null) {
            return JavaScriptExecutionResult.failure("browser-shim.js not found on classpath");
        }

        String script = shimSource + "\n'Pseudo browser globals installed';";
        return graalJsExecutor.execute(script);
    }

    /**
     * Stage 3 — load the real Mermaid JS bundle and see what breaks.
     *
     * @param mermaidSource the full content of mermaid.min.js
     */
    public JavaScriptExecutionResult runMermaidLoadProbe(String mermaidSource) {
        String shimSource = loadShim();
        if (shimSource == null) {
            return JavaScriptExecutionResult.failure("browser-shim.js not found on classpath");
        }

        String script = shimSource + "\n" +
                "var module = undefined; var exports = undefined; var define = undefined;\n" +
                mermaidSource + "\n" +
                "typeof window.mermaid !== 'undefined' ? 'Loaded Mermaid source (window.mermaid available)' : 'Loaded but mermaid not on window';";
        return graalJsExecutor.execute(script);
    }

    /**
     * Stage 4 — attempt to render a simple Mermaid diagram to SVG.
     *
     * @param mermaidSource the full content of mermaid.min.js
     * @param diagramCode   Mermaid diagram definition, e.g. {@code "graph TD; A-->B;"}
     */
    public JavaScriptExecutionResult runMermaidRenderProbe(String mermaidSource, String diagramCode) {
        String shimSource = loadShim();
        if (shimSource == null) {
            return JavaScriptExecutionResult.failure("browser-shim.js not found on classpath");
        }

        String escapedDiagram = diagramCode
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");

        String renderScript =
                shimSource + "\n" +
                "var module = undefined; var exports = undefined; var define = undefined;\n" +
                mermaidSource + "\n" +
                "var __mermaid = window.mermaid || (typeof mermaid !== 'undefined' ? mermaid : null);\n" +
                "var __svgResult = '';\n" +
                "if (!__mermaid) { throw new Error('mermaid not found after loading bundle'); }\n" +
                "javaBridge.log('Mermaid version: ' + (__mermaid.version || 'unknown'));\n" +
                "__mermaid.initialize({ startOnLoad: false, securityLevel: 'loose' });\n" +
                // Try callback-style first
                "__mermaid.render('spike-diagram', '" + escapedDiagram + "', function(svgCode, bindFn) {\n" +
                "  javaBridge.log('Callback received SVG: ' + (svgCode ? svgCode.length + ' chars' : 'null'));\n" +
                "  __svgResult = svgCode;\n" +
                "});\n" +
                // If callback didn't fire, try to extract SVG from the DOM
                "if (!__svgResult) {\n" +
                "  javaBridge.log('Callback SVG empty, trying DOM extraction...');\n" +
                "  var __container = document.querySelector('[id=\"dspike-diagram\"]') || document.getElementById('dspike-diagram');\n" +
                "  if (__container) {\n" +
                "    javaBridge.log('Found container: ' + __container.tagName + ', children: ' + __container.childNodes.length);\n" +
                "    var __svgEl = __container.querySelector('svg') || (__container.childNodes.length > 0 ? __container.childNodes[0] : null);\n" +
                "    if (__svgEl) {\n" +
                "      javaBridge.log('Found SVG element: ' + __svgEl.tagName + ', innerHTML length: ' + (__svgEl.innerHTML || '').length);\n" +
                "      var ser = new XMLSerializer();\n" +
                "      __svgResult = ser.serializeToString(__svgEl);\n" +
                "    } else {\n" +
                "      javaBridge.log('No SVG element in container');\n" +
                "    }\n" +
                "  } else {\n" +
                "    javaBridge.log('Container dspike-diagram not found in DOM');\n" +
                "  }\n" +
                "}\n" +
                "__svgResult;\n";

        return graalJsExecutor.execute(renderScript);
    }

    private String loadShim() {
        return loadResource(BROWSER_SHIM_RESOURCE);
    }

    static String loadResource(String resourcePath) {
        InputStream inputStream = MermaidRenderingProbe.class.getResourceAsStream(resourcePath);
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
