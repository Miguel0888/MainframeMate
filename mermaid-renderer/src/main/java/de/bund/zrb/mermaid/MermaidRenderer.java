package de.bund.zrb.mermaid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders Mermaid diagram code to SVG using GraalJS with an embedded browser shim.
 * <p>
 * Thread-safe: each {@link #renderToSvg(String)} call creates a fresh GraalJS context.
 * The browser shim and Mermaid bundle are loaded once and cached.
 * <p>
 * Usage:
 * <pre>
 *   MermaidRenderer renderer = MermaidRenderer.getInstance();
 *   String svg = renderer.renderToSvg("graph TD; A--&gt;B;");
 * </pre>
 */
public final class MermaidRenderer {

    private static final String BROWSER_SHIM_RESOURCE = "/mermaid/browser-shim.js";
    private static final String MERMAID_BUNDLE_RESOURCE = "/mermaid/mermaid.min.js";

    private static final MermaidRenderer INSTANCE = new MermaidRenderer();

    private final GraalJsExecutor executor = new GraalJsExecutor();
    private final AtomicInteger diagramCounter = new AtomicInteger(0);

    /** Lazily loaded and cached shim + mermaid bundle. */
    private volatile String cachedPreamble;

    private MermaidRenderer() {
    }

    public static MermaidRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Render a Mermaid diagram definition to SVG markup.
     *
     * @param diagramCode Mermaid definition, e.g. {@code "graph TD; A-->B;"}
     * @return SVG string, or {@code null} if rendering failed
     */
    public String renderToSvg(String diagramCode) {
        if (diagramCode == null || diagramCode.trim().isEmpty()) {
            return null;
        }

        String preamble = getPreamble();
        if (preamble == null) {
            return null;
        }

        String diagramId = "mmd-" + diagramCounter.incrementAndGet();
        String escapedDiagram = diagramCode
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");

        String script = preamble + "\n" +
                "var __mermaid = window.mermaid;\n" +
                "var __svgResult = '';\n" +
                "__mermaid.initialize({ startOnLoad: false, securityLevel: 'loose' });\n" +
                "__mermaid.render('" + diagramId + "', '" + escapedDiagram + "', function(svgCode, bindFn) {\n" +
                "  __svgResult = svgCode;\n" +
                "});\n" +
                "if (!__svgResult) {\n" +
                "  var __container = document.querySelector('[id=\"d" + diagramId + "\"]');\n" +
                "  if (__container) {\n" +
                "    var __svgEl = __container.querySelector('svg') || (__container.childNodes.length > 0 ? __container.childNodes[0] : null);\n" +
                "    if (__svgEl) {\n" +
                "      var ser = new XMLSerializer();\n" +
                "      __svgResult = ser.serializeToString(__svgEl);\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "__svgResult;\n";

        JsExecutionResult result = executor.execute(script);
        if (result.isSuccessful() && result.getOutput() != null && !result.getOutput().isEmpty()) {
            return result.getOutput();
        }
        return null;
    }

    /**
     * Check whether the Mermaid bundle is available on the classpath.
     */
    public boolean isAvailable() {
        return getPreamble() != null;
    }

    private String getPreamble() {
        if (cachedPreamble == null) {
            synchronized (this) {
                if (cachedPreamble == null) {
                    String shim = loadResource(BROWSER_SHIM_RESOURCE);
                    String mermaidBundle = loadResource(MERMAID_BUNDLE_RESOURCE);
                    if (shim != null && mermaidBundle != null && mermaidBundle.length() > 1000) {
                        cachedPreamble = shim + "\n" +
                                "var module = undefined; var exports = undefined; var define = undefined;\n" +
                                mermaidBundle;
                    }
                }
            }
        }
        return cachedPreamble;
    }

    static String loadResource(String resourcePath) {
        InputStream inputStream = MermaidRenderer.class.getResourceAsStream(resourcePath);
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
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}

