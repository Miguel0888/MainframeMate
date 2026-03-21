package de.bund.zrb.mermaid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            return postProcessSvg(result.getOutput());
        }
        return null;
    }

    /**
     * Post-process the raw SVG to make it compatible with Apache Batik:
     * <ul>
     *   <li>Ensure {@code xmlns="http://www.w3.org/2000/svg"} on root {@code <svg>} element</li>
     *   <li>Remove spurious {@code xmlns="http://www.w3.org/1999/xhtml"} from SVG child elements</li>
     *   <li>Convert {@code <foreignObject>} text labels to SVG {@code <text>} elements</li>
     * </ul>
     */
    static String postProcessSvg(String svg) {
        if (svg == null || svg.isEmpty()) return svg;

        // 1) Ensure root <svg> has xmlns declaration
        if (!svg.contains("xmlns=\"http://www.w3.org/2000/svg\"")
                && !svg.contains("xmlns='http://www.w3.org/2000/svg'")) {
            svg = svg.replaceFirst("<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"");
        }

        // 2) Remove xmlns="http://www.w3.org/1999/xhtml" from SVG child elements
        svg = svg.replace(" xmlns=\"http://www.w3.org/1999/xhtml\"", "");

        // 3) Convert <foreignObject> blocks to <text> elements for Batik
        svg = convertForeignObjectsToText(svg);

        // 4) Fix invalid "translate(undefined, undefined)" transforms
        svg = svg.replaceAll("translate\\(undefined[^)]*\\)", "translate(0, 0)");
        svg = svg.replaceAll("translate\\(NaN[^)]*\\)", "translate(0, 0)");

        // 5) Recalculate viewBox from actual path coordinates
        svg = fixViewBox(svg);

        // 6) Replace width="100%" with a concrete pixel width
        svg = svg.replaceFirst("width=\"100%\"", "width=\"800\"");

        // 7) Remove max-width from inline style (confuses Batik)
        svg = svg.replaceAll("max-width:\\s*[^;\"]+;?\\s*", "");

        // 8) Replace fill="currentColor" with a concrete color (Batik doesn't support it)
        svg = svg.replace("fill=\"currentColor\"", "fill=\"#333333\"");

        // 9) Strip CSS :root rules with custom properties (Batik doesn't support CSS variables)
        svg = svg.replaceAll("#mmd-\\d+\\s*:root\\s*\\{[^}]*\\}", "");

        return svg;
    }

    /**
     * Scan all numeric coordinates in the SVG and recalculate the viewBox
     * to encompass all content with some padding.
     */
    private static String fixViewBox(String svg) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        boolean found = false;

        // Scan translate transforms
        Pattern translatePattern = Pattern.compile("translate\\(\\s*(-?[\\d.]+)\\s*[,\\s]\\s*(-?[\\d.]+)\\s*\\)");
        Matcher tm = translatePattern.matcher(svg);
        while (tm.find()) {
            try {
                double tx = Double.parseDouble(tm.group(1));
                double ty = Double.parseDouble(tm.group(2));
                if (tx < minX) minX = tx;
                if (tx > maxX) maxX = tx;
                if (ty < minY) minY = ty;
                if (ty > maxY) maxY = ty;
                found = true;
            } catch (NumberFormatException ignored) {
            }
        }

        // Scan path data for coordinate pairs (M x,y  L x,y  C x,y,...)
        Pattern coordPattern = Pattern.compile(
                "[MLCSQTA]\\s*([\\d.,-]+(?:\\s+[\\d.,-]+)*)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher pm = coordPattern.matcher(svg);
        while (pm.find()) {
            String data = pm.group(1);
            String[] nums = data.split("[,\\s]+");
            for (int i = 0; i + 1 < nums.length; i += 2) {
                try {
                    double x = Double.parseDouble(nums[i]);
                    double y = Double.parseDouble(nums[i + 1]);
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    found = true;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (!found || maxX <= minX || maxY <= minY) {
            return svg;
        }

        // Add padding
        double pad = 50;
        int vbX = (int) (minX - pad);
        int vbY = (int) (minY - pad);
        int vbW = (int) (maxX - minX + 2 * pad);
        int vbH = (int) (maxY - minY + 2 * pad);

        // Replace existing viewBox
        svg = svg.replaceFirst(
                "viewBox\\s*=\\s*\"[^\"]*\"",
                "viewBox=\"" + vbX + " " + vbY + " " + vbW + " " + vbH + "\""
        );

        return svg;
    }

    /**
     * Replace {@code <foreignObject>...<span>Label</span>...</foreignObject>} blocks
     * with SVG {@code <text>} elements containing the extracted text.
     */
    private static String convertForeignObjectsToText(String svg) {
        Pattern foPattern = Pattern.compile(
                "<foreignobject([^>]*)>(.*?)</foreignobject>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = foPattern.matcher(svg);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String content = matcher.group(2);

            // Extract x, y, width, height from foreignObject attributes
            String x = extractAttr(attrs, "x");
            String y = extractAttr(attrs, "y");
            String width = extractAttr(attrs, "width");
            String height = extractAttr(attrs, "height");

            // Extract text content (strip all HTML tags)
            String text = content.replaceAll("<[^>]+>", "").trim();

            if (text.isEmpty()) {
                matcher.appendReplacement(sb, "");
                continue;
            }

            // Build a <text> element centered in the foreignObject area
            StringBuilder textEl = new StringBuilder();
            textEl.append("<text");
            if (x != null && width != null) {
                try {
                    double xd = Double.parseDouble(x);
                    double wd = Double.parseDouble(width);
                    textEl.append(" x=\"").append(String.valueOf(xd + wd / 2)).append("\"");
                } catch (NumberFormatException e) {
                    textEl.append(" x=\"").append(x).append("\"");
                }
            } else if (x != null) {
                textEl.append(" x=\"").append(x).append("\"");
            }
            if (y != null && height != null) {
                try {
                    double yd = Double.parseDouble(y);
                    double hd = Double.parseDouble(height);
                    textEl.append(" y=\"").append(String.valueOf(yd + hd / 2 + 5)).append("\"");
                } catch (NumberFormatException e) {
                    textEl.append(" y=\"").append(y).append("\"");
                }
            } else if (y != null) {
                textEl.append(" y=\"").append(y).append("\"");
            }
            textEl.append(" text-anchor=\"middle\"");
            textEl.append(" dominant-baseline=\"central\"");
            textEl.append(" font-family=\"sans-serif\"");
            textEl.append(" font-size=\"14\"");
            textEl.append(" fill=\"currentColor\"");
            textEl.append(">");
            textEl.append(escapeXml(text));
            textEl.append("</text>");

            matcher.appendReplacement(sb, Matcher.quoteReplacement(textEl.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String extractAttr(String attrs, String name) {
        Pattern p = Pattern.compile(name + "\\s*=\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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

