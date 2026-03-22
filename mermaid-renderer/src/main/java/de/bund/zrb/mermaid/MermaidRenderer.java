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

        // Mermaid 11 API: render() returns a Promise that resolves to { svg: string }.
        // GraalJS resolves microtasks synchronously within a single context.eval(),
        // so the .then() callback fires before the script finishes.
        // Fallback: try the legacy v9 callback API first (for old bundles).
        String script = preamble + "\n"
                + "var __mermaid = window.mermaid;\n"
                + "var __svgResult = '';\n"
                + "var __renderError = '';\n"
                + "__mermaid.initialize({ startOnLoad: false, securityLevel: 'loose' });\n"
                + "\n"
                + "try {\n"
                + "  var __result = __mermaid.render('" + diagramId + "', '" + escapedDiagram + "');\n"
                + "  if (__result && typeof __result.then === 'function') {\n"
                + "    // Mermaid 11+: Promise<{svg: string}>\n"
                + "    __result.then(function(res) {\n"
                + "      __svgResult = (res && res.svg) ? res.svg : (typeof res === 'string' ? res : '');\n"
                + "    })['catch'](function(err) {\n"
                + "      __renderError = '' + err;\n"
                + "    });\n"
                + "  } else if (__result && __result.svg) {\n"
                + "    // Direct result object\n"
                + "    __svgResult = __result.svg;\n"
                + "  } else if (typeof __result === 'string') {\n"
                + "    // Legacy Mermaid 9.x sync return\n"
                + "    __svgResult = __result;\n"
                + "  }\n"
                + "} catch(renderErr) {\n"
                + "  __renderError = '' + renderErr;\n"
                + "}\n";

        // Use executeAsync: the setup script chains .then() on the Promise,
        // and the result expression is evaluated after microtask flush.
        String resultExpr = "__svgResult || (__renderError ? 'ERROR:' + __renderError : '')";

        JsExecutionResult result = executor.executeAsync(script, resultExpr);
        if (result.isSuccessful() && result.getOutput() != null && !result.getOutput().isEmpty()) {
            String output = result.getOutput();
            // Detect error sentinel from async fallback path
            if (output.startsWith("ERROR:")) {
                System.err.println("[MermaidRenderer] Render failed: " + output);
                return null;
            }
            return postProcessSvg(output);
        }
        System.err.println("[MermaidRenderer] Render failed for: "
                + diagramCode.substring(0, Math.min(80, diagramCode.length()))
                + (result.isSuccessful() ? " (empty output)" : " ERROR: " + result.getErrorMessage()));
        return null;
    }

    /**
     * Like {@link #renderToSvg(String)} but returns the full
     * {@link JsExecutionResult} so callers can inspect error details.
     */
    public JsExecutionResult renderToSvgDetailed(String diagramCode) {
        if (diagramCode == null || diagramCode.trim().isEmpty()) {
            return JsExecutionResult.failure("Empty diagram code");
        }

        String preamble = getPreamble();
        if (preamble == null) {
            return JsExecutionResult.failure("Mermaid bundle not available");
        }

        String diagramId = "mmd-" + diagramCounter.incrementAndGet();
        String escapedDiagram = diagramCode
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");

        // Same async-capable script as renderToSvg (Mermaid 11 API)
        String script = preamble + "\n"
                + "var __mermaid = window.mermaid;\n"
                + "var __svgResult = '';\n"
                + "var __renderError = '';\n"
                + "__mermaid.initialize({ startOnLoad: false, securityLevel: 'loose' });\n"
                + "try {\n"
                + "  var __result = __mermaid.render('" + diagramId + "', '" + escapedDiagram + "');\n"
                + "  if (__result && typeof __result.then === 'function') {\n"
                + "    __result.then(function(res) {\n"
                + "      __svgResult = (res && res.svg) ? res.svg : (typeof res === 'string' ? res : '');\n"
                + "    })['catch'](function(err) {\n"
                + "      __renderError = '' + err;\n"
                + "    });\n"
                + "  } else if (__result && __result.svg) {\n"
                + "    __svgResult = __result.svg;\n"
                + "  } else if (typeof __result === 'string') {\n"
                + "    __svgResult = __result;\n"
                + "  }\n"
                + "} catch(renderErr) {\n"
                + "  __renderError = '' + renderErr;\n"
                + "}\n";

        return executor.executeAsync(script,
                "__svgResult || (__renderError ? 'ERROR:' + __renderError : '')");
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

        // 0) Extract only <svg>...</svg> — strip wrapper divs or trailing markup
        //    (Mermaid 11 may emit <style> siblings after </svg>)
        int svgStart = svg.indexOf("<svg");
        int svgEnd = svg.lastIndexOf("</svg>");
        if (svgStart >= 0 && svgEnd > svgStart) {
            svg = svg.substring(svgStart, svgEnd + 6); // 6 = "</svg>".length()
        }

        // 1) Ensure root <svg> has xmlns declaration
        if (!svg.contains("xmlns=\"http://www.w3.org/2000/svg\"")
                && !svg.contains("xmlns='http://www.w3.org/2000/svg'")) {
            svg = svg.replaceFirst("<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"");
        }

        // 2) Remove xmlns="http://www.w3.org/1999/xhtml" from SVG child elements
        svg = svg.replace(" xmlns=\"http://www.w3.org/1999/xhtml\"", "");

        // 3) Convert <foreignObject> blocks to <text> elements for Batik
        svg = convertForeignObjectsToText(svg);

        // 3b) Aggressively remove any remaining <foreignObject> blocks
        //     that the conversion regex may have missed
        svg = removeRemainingForeignObjects(svg);

        // 3c) Remove stray HTML elements that don't belong in SVG
        //     (may remain after foreignObject removal or from shim serialisation)
        svg = removeStrayHtmlElements(svg);

        // 4) Fix invalid "translate(undefined, undefined)" transforms
        svg = svg.replaceAll("translate\\(undefined[^)]*\\)", "translate(0, 0)");
        svg = svg.replaceAll("translate\\(NaN[^)]*\\)", "translate(0, 0)");

        // 5) Recalculate viewBox from actual path coordinates
        svg = fixViewBox(svg);

        // 6) Replace width="100%" with a temporary pixel width
        //    (MermaidSvgFixup.fixViewBoxFromAttributes will set the final proportional width)
        svg = svg.replaceFirst("width=\"100%\"", "width=\"800\"");

        // 7) Remove max-width from inline style (confuses Batik)
        svg = svg.replaceAll("max-width:\\s*[^;\"]+;?\\s*", "");

        // 8) Replace fill="currentColor" with a concrete color (Batik doesn't support it)
        svg = svg.replace("fill=\"currentColor\"", "fill=\"#333333\"");

        // 9) Strip CSS :root rules with custom properties (Batik doesn't support CSS variables)
        svg = svg.replaceAll("#mmd-\\d+\\s*:root\\s*\\{[^}]*\\}", "");

        // 10) Remove alignment-baseline attributes (Batik chokes on value "central")
        svg = svg.replaceAll("\\s*alignment-baseline\\s*=\\s*\"[^\"]*\"", "");

        // 10b) Clean up style attributes containing serialized JavaScript functions
        //      (polyfill methods like Array.prototype.at may leak into attr values)
        svg = svg.replaceAll(" style=\"function\\([^\"]*\"", " style=\"\"");

        // 11) Fix HTML5 void elements that may remain (<br> → <br/>)
        svg = fixHtmlVoidElements(svg);

        // 12) Wrap <style> content in CDATA so CSS selectors with > don't break XML
        svg = wrapStylesInCdata(svg);

        return svg;
    }

    /**
     * Remove any remaining {@code <foreignObject>} blocks and clean up
     * orphaned closing tags left behind from nested foreignObject structures.
     * <p>
     * Mermaid 11 creates deeply nested {@code foreignObject → svg → g → ...}
     * structures.  The non-greedy regex in {@link #convertForeignObjectsToText}
     * matches only the innermost level, leaving orphaned closing tags from
     * outer nesting layers.  This method does a thorough cleanup:
     * <ol>
     *   <li>Remove remaining paired foreignObject blocks</li>
     *   <li>Remove ALL remaining foreignObject open/close tags</li>
     *   <li>Flatten nested SVGs down to the single root SVG</li>
     * </ol>
     */
    private static String removeRemainingForeignObjects(String svg) {
        // Remove paired foreignObject blocks (may miss nested ones)
        svg = Pattern.compile("<foreignObject[^>]*>.*?</foreignObject>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(svg).replaceAll("");
        // Remove self-closing foreignObject elements
        svg = Pattern.compile("<foreignObject[^>]*/\\s*>",
                Pattern.CASE_INSENSITIVE).matcher(svg).replaceAll("");
        // Remove ANY remaining foreignObject opening/closing tags (orphaned from nesting)
        svg = svg.replaceAll("(?i)<foreignobject[^>]*>", "");
        svg = svg.replaceAll("(?i)</foreignobject>", "");

        // Clean up orphaned nested SVG structures:
        // After foreignObject removal, nested <svg>...</svg> blocks are exposed
        // as siblings/children.  We keep only the root <svg> and its closing </svg>.
        svg = flattenNestedSvgs(svg);

        return svg;
    }

    /**
     * Flatten nested SVG elements: keep only the outermost (root) {@code <svg>}
     * opening tag and its matching (last) {@code </svg>} closing tag.
     * All inner {@code <svg>} and {@code </svg>} tags are removed, so their
     * children are promoted into the root SVG's content.
     * <p>
     * Also balances orphaned closing tags ({@code </g>}) that result from
     * the foreignObject/nested-SVG removal.
     */
    private static String flattenNestedSvgs(String svg) {
        int firstSvgStart = svg.indexOf("<svg");
        if (firstSvgStart < 0) return svg;
        int firstSvgEnd = svg.indexOf(">", firstSvgStart);
        if (firstSvgEnd < 0) return svg;

        String rootOpening = svg.substring(0, firstSvgEnd + 1);
        String inner = svg.substring(firstSvgEnd + 1);

        // Remove all nested <svg ...> opening tags
        inner = inner.replaceAll("<svg[^>]*>", "");

        // Remove all </svg> except the very last one (the root's closing tag)
        int lastCloseIdx = inner.lastIndexOf("</svg>");
        if (lastCloseIdx >= 0) {
            String beforeLast = inner.substring(0, lastCloseIdx);
            String theLastClose = inner.substring(lastCloseIdx);
            beforeLast = beforeLast.replace("</svg>", "");
            inner = beforeLast + theLastClose;
        }

        svg = rootOpening + inner;

        // Balance orphaned </g> closing tags — the nested foreignObject/SVG
        // removal may leave more </g> closings than <g> openings.
        svg = balanceGTags(svg);

        return svg;
    }

    /**
     * Remove orphaned {@code </g>} closing tags by walking left-to-right
     * with a depth counter.  Every {@code <g ...>} increments depth,
     * every {@code </g>} decrements it.  If depth would go negative,
     * the {@code </g>} is an orphan and is skipped.
     */
    private static String balanceGTags(String svg) {
        StringBuilder result = new StringBuilder(svg.length());
        int depth = 0;
        int i = 0;
        int len = svg.length();
        while (i < len) {
            // Check for <g> or <g ...>
            if (i + 2 < len && svg.charAt(i) == '<' && svg.charAt(i + 1) == 'g'
                    && (i + 2 == len || svg.charAt(i + 2) == ' ' || svg.charAt(i + 2) == '>' || svg.charAt(i + 2) == '\t')) {
                depth++;
                int tagEnd = svg.indexOf('>', i);
                if (tagEnd < 0) tagEnd = len - 1;
                result.append(svg, i, tagEnd + 1);
                i = tagEnd + 1;
            }
            // Check for </g>
            else if (i + 3 < len && svg.charAt(i) == '<' && svg.charAt(i + 1) == '/'
                    && svg.charAt(i + 2) == 'g' && svg.charAt(i + 3) == '>') {
                if (depth > 0) {
                    depth--;
                    result.append("</g>");
                }
                // else: orphaned closing tag — silently skip it
                i += 4;
            }
            else {
                result.append(svg.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /**
     * Remove HTML elements that don't belong in SVG (div, span, body, p, etc.).
     * These may be left behind from incomplete foreignObject conversion or
     * from the browser shim's serialisation using innerHTML.
     */
    private static String removeStrayHtmlElements(String svg) {
        // Remove open and close tags but keep text content between them
        // Note: <a> is intentionally excluded — SVG has its own <a> element
        String htmlTags = "div|span|p|body|section|i|b|em|strong|h[1-6]|ul|ol|li|table|tr|td|th|label";
        svg = svg.replaceAll("<(" + htmlTags + ")(\\s[^>]*)?>", "");
        svg = svg.replaceAll("</(" + htmlTags + ")>", "");
        return svg;
    }

    /**
     * Fix HTML5 void elements by ensuring they are self-closing (XHTML style).
     */
    private static String fixHtmlVoidElements(String svg) {
        return svg.replaceAll(
                "<(br|hr|wbr|img|input|meta|link|col|embed|area|base|source|track)(\\s[^>]*?)?\\s*(?<!/)>",
                "<$1$2/>");
    }

    /**
     * Wrap {@code <style>} content in {@code <![CDATA[...]]>} sections
     * so that CSS selectors containing {@code >} or {@code <} don't break XML parsing.
     */
    private static String wrapStylesInCdata(String svg) {
        Pattern p = Pattern.compile(
                "(<style[^>]*>)(.*?)(</style>)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(svg);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String content = m.group(2);
            // Only wrap if not already CDATA and content has XML-unsafe chars
            if (!content.contains("<![CDATA[") &&
                    (content.contains(">") || content.contains("<") || content.contains("&"))) {
                m.appendReplacement(sb, Matcher.quoteReplacement(
                        m.group(1) + "<![CDATA[" + content + "]]>" + m.group(3)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
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

            // Extract text content (strip all HTML/XML tags)
            String text = content.replaceAll("<[^>]+>", "").trim();

            // Mermaid 11 produces deeply nested foreignObjects containing entire
            // SVGs with <style> blocks.  The stripped "text" then contains the
            // full CSS, which is useless as a label.  Detect and skip.
            if (text.length() > 200 || text.contains("{font-family:") || text.startsWith("#mmd-")) {
                // CSS or other non-label content — just remove the foreignObject
                matcher.appendReplacement(sb, "");
                continue;
            }

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

