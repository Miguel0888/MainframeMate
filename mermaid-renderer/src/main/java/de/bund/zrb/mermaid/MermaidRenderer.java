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

    /**
     * Mermaid initialization config.  {@code htmlLabels: false} forces Mermaid
     * to use native SVG {@code <text>} elements instead of
     * {@code <foreignObject>} with HTML — the headless browser shim cannot
     * properly serialize foreignObject content (innerHTML explosion).
     */
    private static final String MERMAID_INIT_CONFIG =
            "{ startOnLoad: false, securityLevel: 'loose',"
            + " htmlLabels: false,"
            + " flowchart: { htmlLabels: false },"
            + " sequence: { htmlLabels: false } }";

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
                + "__mermaid.initialize(" + MERMAID_INIT_CONFIG + ");\n"
                + "\n"
                + "// Pre-create a container element in the DOM so diagram renderers\n"
                + "// (especially Gantt) can find it and measure offsetWidth/Height.\n"
                + "var __container = document.createElement('div');\n"
                + "__container.id = 'd' + '" + diagramId + "';\n"
                + "__container.setAttribute('id', 'd' + '" + diagramId + "');\n"
                + "document.body.appendChild(__container);\n"
                + "\n"
                + "try {\n"
                + "  var __result = __mermaid.render('" + diagramId + "', '" + escapedDiagram + "');\n"
                + "  if (__result && typeof __result.then === 'function') {\n"
                + "    // Mermaid 11+: Promise<{svg: string}>\n"
                + "    __result.then(function(res) {\n"
                + "      __svgResult = (res && res.svg) ? res.svg : (typeof res === 'string' ? res : '');\n"
                + "    })['catch'](function(err) {\n"
                + "      __renderError = '' + err + (err && err.stack ? '\\nSTACK: ' + err.stack : '');\n"
                + "    });\n"
                + "  } else if (__result && __result.svg) {\n"
                + "    // Direct result object\n"
                + "    __svgResult = __result.svg;\n"
                + "  } else if (typeof __result === 'string') {\n"
                + "    // Legacy Mermaid 9.x sync return\n"
                + "    __svgResult = __result;\n"
                + "  }\n"
                + "} catch(renderErr) {\n"
                + "  __renderError = '' + renderErr + (renderErr && renderErr.stack ? '\\nSTACK: ' + renderErr.stack : '');\n"
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
                + "__mermaid.initialize(" + MERMAID_INIT_CONFIG + ");\n"
                + "var __container = document.createElement('div');\n"
                + "__container.id = 'd' + '" + diagramId + "';\n"
                + "__container.setAttribute('id', 'd' + '" + diagramId + "');\n"
                + "document.body.appendChild(__container);\n"
                + "try {\n"
                + "  var __result = __mermaid.render('" + diagramId + "', '" + escapedDiagram + "');\n"
                + "  if (__result && typeof __result.then === 'function') {\n"
                + "    __result.then(function(res) {\n"
                + "      __svgResult = (res && res.svg) ? res.svg : (typeof res === 'string' ? res : '');\n"
                + "    })['catch'](function(err) {\n"
                + "      __renderError = '' + err + (err && err.stack ? '\\nSTACK: ' + err.stack : '');\n"
                + "    });\n"
                + "  } else if (__result && __result.svg) {\n"
                + "    __svgResult = __result.svg;\n"
                + "  } else if (typeof __result === 'string') {\n"
                + "    __svgResult = __result;\n"
                + "  }\n"
                + "} catch(renderErr) {\n"
                + "  __renderError = '' + renderErr + (renderErr && renderErr.stack ? '\\nSTACK: ' + renderErr.stack : '');\n"
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

        // 5) viewBox recalculation — handled by MermaidSvgFixup.fixViewBoxFromAttributes()
        //    which uses DOM-based scanning and is much more precise than regex scanning.
        //    Do NOT run the regex-based fixViewBox here — it can produce wrong results
        //    by scanning coordinates inside <style>, <defs>, and marker elements.

        // 6) Replace width="100%" with a temporary pixel width
        //    (MermaidSvgFixup.fixViewBoxFromAttributes will set the final proportional width)
        svg = svg.replaceFirst("width=\"100%\"", "width=\"1600\"");

        // 7) Remove max-width from inline style (confuses Batik)
        svg = svg.replaceAll("max-width:\\s*[^;\"]+;?\\s*", "");

        // 8) Replace fill="currentColor" with a concrete color (Batik doesn't support it)
        svg = svg.replace("fill=\"currentColor\"", "fill=\"#333333\"");

        // 9) Strip CSS :root rules with custom properties (Batik doesn't support CSS variables)
        svg = svg.replaceAll("#mmd-\\d+\\s*:root\\s*\\{[^}]*\\}", "");

        // 10) Remove alignment-baseline attributes (Batik chokes on value "central")
        //     Handle both double and single quoted attribute values
        svg = svg.replaceAll("\\s+alignment-baseline\\s*=\\s*\"[^\"]*\"", "");
        svg = svg.replaceAll("\\s+alignment-baseline\\s*=\\s*'[^']*'", "");

        // 10a) Remove alignment-baseline from CSS in <style> blocks AND inline styles
        svg = svg.replaceAll("alignment-baseline\\s*:\\s*[^;\"'}<]+[;]?", "");

        // 10aa) Remove dominant-baseline attributes (Batik chokes on "central", "middle")
        svg = svg.replaceAll("\\s+dominant-baseline\\s*=\\s*\"[^\"]*\"", "");
        svg = svg.replaceAll("\\s+dominant-baseline\\s*=\\s*'[^']*'", "");
        svg = svg.replaceAll("dominant-baseline\\s*:\\s*[^;\"'}<]+[;]?", "");

        // 10ab) Replace CSS var() references (Batik has no CSS variable support)
        svg = svg.replace("var(--mermaid-font-family)",
                "'trebuchet ms', verdana, arial, sans-serif");
        svg = svg.replaceAll("var\\(\\s*--[\\w-]+\\s*,\\s*([^)]+)\\)", "$1");

        // 10ac) Fix fractional rgb() values: e.g. rgb(48.833, 0, 146.5) → hex
        svg = fixFractionalRgbValues(svg);

        // 10ad) Clean up empty/broken style attributes: style=";;;" → remove
        svg = svg.replaceAll("\\s+style\\s*=\\s*\"[;\\s]*\"", "");

        // 10ae) Remove empty presentation attributes: font-weight="" / font-style=""
        //       Mermaid class diagrams emit <tspan font-weight=""> which crashes
        //       Batik's CSS parser (empty string is not a valid font-weight value).
        svg = svg.replaceAll("\\s+font-weight\\s*=\\s*\"\"", "");
        svg = svg.replaceAll("\\s+font-style\\s*=\\s*\"\"", "");

        // 10af) Replace hsl() with NaN values (browser shim artefact)
        svg = svg.replaceAll("hsl\\([^)]*NaN[^)]*\\)", "#888888");

        // 10b) Clean up style attributes containing serialized JavaScript functions
        //      (polyfill methods like Array.prototype.at may leak into attr values)
        svg = svg.replaceAll(" style=\"function\\([^\"]*\"", " style=\"\"");

        // 10c) Sanitise CSS for Batik: strip @keyframes, animation, hsl(), rgba(),
        //      stroke-linecap — these must be removed BEFORE CDATA wrapping
        svg = sanitiseCssForBatik(svg);

        // 10cf) Replace "transparent" as paint value with "none" (Batik NPE)
        svg = svg.replace("fill:transparent", "fill:none");
        svg = svg.replace("stroke:transparent", "stroke:none");
        svg = svg.replace("fill=\"transparent\"", "fill=\"none\"");
        svg = svg.replace("stroke=\"transparent\"", "stroke=\"none\"");

        // 10d) Fix negative width/height on <rect> elements (Gantt charts produce these
        //      when D3's time scale inverts coordinates).
        //      Convert negative width to absolute value and shift x accordingly.
        {
            java.util.regex.Pattern negWP = java.util.regex.Pattern.compile(
                    "<rect([^>]*)\\bwidth\\s*=\\s*\"(-[\\d.]+)\"([^>]*)>");
            java.util.regex.Matcher negWM = negWP.matcher(svg);
            StringBuffer negWSb = new StringBuffer(svg.length());
            while (negWM.find()) {
                String before = negWM.group(1);
                double negW = Double.parseDouble(negWM.group(2));
                String after = negWM.group(3);
                double posW = Math.abs(negW);

                // Detect and strip self-closing '/' that [^>]* consumed from '/>'
                boolean selfClosing = after.endsWith("/");
                if (selfClosing) {
                    after = after.substring(0, after.length() - 1);
                }
                String closeTag = selfClosing ? "/>" : ">";

                // Try to adjust x position: x = x + negW (shift left by absolute width)
                String combined = before + after;
                java.util.regex.Matcher xM = java.util.regex.Pattern.compile(
                        "\\bx\\s*=\\s*\"(-?[\\d.]+)\"").matcher(combined);
                if (xM.find()) {
                    double oldX = Double.parseDouble(xM.group(1));
                    double newX = oldX + negW; // negW is negative, so this shifts left
                    String fixedAttrs = combined.replaceFirst(
                            "\\bx\\s*=\\s*\"-?[\\d.]+\"",
                            "x=\"" + String.format(java.util.Locale.US, "%.1f", newX) + "\"");
                    negWM.appendReplacement(negWSb,
                            java.util.regex.Matcher.quoteReplacement(
                                    "<rect" + fixedAttrs + " width=\""
                                    + String.format(java.util.Locale.US, "%.1f", posW) + "\"" + closeTag));
                } else {
                    negWM.appendReplacement(negWSb,
                            java.util.regex.Matcher.quoteReplacement(
                                    "<rect" + before + "width=\""
                                    + String.format(java.util.Locale.US, "%.1f", posW) + "\"" + after + closeTag));
                }
            }
            negWM.appendTail(negWSb);
            svg = negWSb.toString();
        }
        // Also clamp any remaining negative height to 0 (not applicable to Gantt but safety net)
        svg = svg.replaceAll("height\\s*=\\s*\"(-[\\d.]+)\"", "height=\"0\"");

        // 10e) Fix SVG element case sensitivity: D3 creates lowercase element names
        //      (lineargradient) but SVG requires camelCase (linearGradient)
        svg = svg.replace("<lineargradient", "<linearGradient");
        svg = svg.replace("</lineargradient>", "</linearGradient>");
        svg = svg.replace("<radialgradient", "<radialGradient");
        svg = svg.replace("</radialgradient>", "</radialGradient>");
        svg = svg.replace("<clippath", "<clipPath");
        svg = svg.replace("</clippath>", "</clipPath>");

        // 11) Fix HTML5 void elements that may remain (<br> → <br/>)
        svg = fixHtmlVoidElements(svg);

        // 12) Wrap <style> content in CDATA so CSS selectors with > don't break XML
        svg = wrapStylesInCdata(svg);

        // 12b) Remove duplicated empty <g class="node"> groups.
        //      Mermaid 11's innerHTML serialisation emits the full nodes list
        //      inside each foreignObject's nested SVG.  After foreignObject/nested-SVG
        //      removal these duplicates get promoted to root level.
        //      Keep only <g class="node"> that have a transform with real coordinates.
        svg = deduplicateNodeGroups(svg);

        // 12c) Fix child order within node groups: shape elements must come before labels.
        //      Mermaid's stadium, pill, and rough shapes may place the label <g> before
        //      the shape <g> (containing filled paths), causing the filled shape to cover
        //      the text in SVG's painter's model.  Swap them so shapes paint first.
        svg = fixNodeShapeLabelOrder(svg);

        // 12c) Fix <rect> elements without width/height — Batik requires these attributes.
        //      Mermaid with htmlLabels:false emits <rect class="background" style="stroke: none"/>
        //      which are decorative background rects with no dimensions.
        //      Remove them entirely (they're invisible anyway) or add default values.
        svg = svg.replaceAll("<rect[^>]*class=\"background\"[^>]*style=\"stroke:\\s*none\"[^>]*/?>", "");
        // For any remaining <rect> without width, add width="0" height="0" so Batik doesn't crash
        svg = Pattern.compile("<rect(?![^>]*width=)([^>]*?)(/?)>").matcher(svg)
                .replaceAll("<rect width=\"0\" height=\"0\"$1$2>");

        // 13) Final safety: re-extract <svg>...</svg> to ensure no trailing content,
        //     then re-balance <g> tags one more time
        int finalSvgStart = svg.indexOf("<svg");
        int finalSvgEnd = svg.lastIndexOf("</svg>");
        if (finalSvgStart >= 0 && finalSvgEnd > finalSvgStart) {
            svg = svg.substring(finalSvgStart, finalSvgEnd + 6);
        }
        svg = balanceGTags(svg);

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
     * <p>
     * Also handles self-closing {@code <g ... />} (depth stays unchanged)
     * and appends missing closing tags at the end.
     */
    private static String balanceGTags(String svg) {
        StringBuilder result = new StringBuilder(svg.length());
        int depth = 0;
        int i = 0;
        int len = svg.length();
        while (i < len) {
            // Check for <g> or <g ...> (whitespace: space, tab, newline, CR)
            if (i + 2 < len && svg.charAt(i) == '<' && svg.charAt(i + 1) == 'g'
                    && (svg.charAt(i + 2) == ' ' || svg.charAt(i + 2) == '>'
                    || svg.charAt(i + 2) == '\t' || svg.charAt(i + 2) == '\n'
                    || svg.charAt(i + 2) == '\r')) {
                int tagEnd = svg.indexOf('>', i);
                if (tagEnd < 0) tagEnd = len - 1;
                // Check if self-closing <g ... />
                boolean selfClosing = (tagEnd > 0 && svg.charAt(tagEnd - 1) == '/');
                if (!selfClosing) {
                    depth++;
                }
                result.append(svg, i, tagEnd + 1);
                i = tagEnd + 1;
            }
            // Check for </g> (possibly with whitespace: </g > or </g\n>)
            else if (i + 3 < len && svg.charAt(i) == '<' && svg.charAt(i + 1) == '/'
                    && svg.charAt(i + 2) == 'g') {
                // Find the closing >
                int closeEnd = i + 3;
                while (closeEnd < len && svg.charAt(closeEnd) != '>') closeEnd++;
                if (closeEnd < len) closeEnd++; // include the '>'

                if (depth > 0) {
                    depth--;
                    result.append(svg, i, closeEnd);
                }
                // else: orphaned closing tag — silently skip it
                i = closeEnd;
            }
            else {
                result.append(svg.charAt(i));
                i++;
            }
        }
        // Append missing closing tags
        while (depth > 0) {
            result.append("</g>");
            depth--;
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
     * Sanitise CSS inside {@code <style>} blocks so Batik's CSS engine doesn't NPE.
     * <p>
     * Batik cannot parse:
     * <ul>
     *   <li>{@code @keyframes} blocks → NPE in CSSEngine.parseStyleSheet</li>
     *   <li>{@code animation} property → references non-existent keyframes</li>
     *   <li>{@code hsl()} / {@code rgba()} colour functions → unsupported</li>
     *   <li>{@code stroke-linecap} in certain contexts</li>
     *   <li>Various HTML-only CSS properties ({@code position}, {@code z-index}, etc.)</li>
     * </ul>
     * This must run BEFORE {@link #wrapStylesInCdata} because CDATA wrapping
     * makes the content opaque to regex processing.
     */
    private static String sanitiseCssForBatik(String svg) {
        // Phase 1: Extract each <style>...</style> block, clean its content, and put it back.
        // Use a simple approach: find <style> and </style>, process the content between them.
        StringBuilder result = new StringBuilder(svg.length());
        String lowerSvg = svg.toLowerCase();
        int lastEnd = 0;

        while (true) {
            int styleStart = lowerSvg.indexOf("<style", lastEnd);
            if (styleStart < 0) break;

            int styleTagEnd = lowerSvg.indexOf(">", styleStart);
            if (styleTagEnd < 0) break;
            styleTagEnd++; // include the '>'

            int closeStyleStart = lowerSvg.indexOf("</style>", styleTagEnd);
            if (closeStyleStart < 0) break;

            // Append everything before this <style> block
            result.append(svg, lastEnd, styleStart);

            // Extract the opening tag and CSS content
            String openTag = svg.substring(styleStart, styleTagEnd);
            String css = svg.substring(styleTagEnd, closeStyleStart);

            // Strip CDATA wrapper if present
            String cssContent = css;
            if (cssContent.contains("<![CDATA[")) {
                cssContent = cssContent.replace("<![CDATA[", "").replace("]]>", "");
            }

            // ── Clean the CSS content ──

            // 1) Remove @keyframes blocks (with nested braces)
            //    Must handle: @keyframes name { from { ... } to { ... } }
            //    Use iterative removal since regex can struggle with nested braces
            cssContent = removeKeyframesBlocks(cssContent);

            // 2) Remove animation properties
            cssContent = cssContent.replaceAll("animation\\s*:\\s*[^;}\"]+(;|(?=\\}))", "");

            // 3) Remove stroke-linecap
            cssContent = cssContent.replaceAll("stroke-linecap\\s*:\\s*[^;}\"]+(;|(?=\\}))", "");

            // 4) Convert hsl() → hex
            cssContent = MermaidSvgFixup.replaceHslValues(cssContent);

            // 5) Convert rgba()/rgb() → hex
            cssContent = MermaidSvgFixup.replaceRgbaValues(cssContent);

            // 6) Remove unsupported CSS properties
            String[] unsupported = {
                "position", "z-index", "pointer-events", "cursor",
                "text-align", "background-color", "box-shadow", "filter",
                "alignment-baseline", "dominant-baseline", "vertical-align", "display",
                "overflow", "padding", "margin"
            };
            for (String prop : unsupported) {
                cssContent = cssContent.replaceAll(
                    prop + "\\s*:\\s*[^;}\"]+(;|(?=\\}))", "");
            }

            // 6b) Fix negative stroke-width values (Mermaid mindmap generates them
            //     for deep edge-depth-N classes; Batik throws IllegalArgumentException)
            {
                java.util.regex.Pattern swp = java.util.regex.Pattern.compile(
                        "stroke-width\\s*:\\s*(-\\d+(?:\\.\\d+)?)");
                java.util.regex.Matcher swm = swp.matcher(cssContent);
                StringBuffer swsb = new StringBuffer(cssContent.length());
                while (swm.find()) {
                    swm.appendReplacement(swsb, "stroke-width:0");
                }
                swm.appendTail(swsb);
                cssContent = swsb.toString();
            }

            // 7) Replace currentColor with concrete value
            cssContent = cssContent.replace("currentColor", "#333333");

            // 7b) Replace "transparent" paint value with "none" — Batik's CSS parser
            //     throws NullPointerException when encountering "transparent" as a fill
            //     or stroke value (it's a CSS3 color, not SVG 1.1).
            cssContent = cssContent.replace("fill:transparent", "fill:none");
            cssContent = cssContent.replace("stroke:transparent", "stroke:none");

            // 8) Replace "revert" with safe defaults
            cssContent = cssContent.replaceAll("stroke\\s*:\\s*revert\\s*;?", "");
            cssContent = cssContent.replaceAll("stroke-width\\s*:\\s*revert\\s*;?", "");

            // 9) Strip :root rules with CSS custom properties
            cssContent = cssContent.replaceAll("#mmd-\\d+\\s*:root\\s*\\{[^}]*\\}", "");

            // 10) Replace CSS var() references (Batik has no CSS variable support)
            cssContent = cssContent.replace("var(--mermaid-font-family)",
                    "'trebuchet ms', verdana, arial, sans-serif");
            cssContent = cssContent.replaceAll("var\\(\\s*--[\\w-]+\\s*,\\s*([^)]+)\\)", "$1");
            // Remove remaining property declarations that still contain var()
            cssContent = cssContent.replaceAll("[\\w-]+\\s*:\\s*[^;{}]*var\\(--[^)]*\\)[^;{}]*(;|(?=\\}))", "");

            // 11) Remove CSS rules with bare SVG element type selectors that crash
            //     Batik's CSS parser.  Mermaid class diagrams emit:
            //       marker path, marker circle, marker polygon { fill:... !important; }
            //     Batik's CSSEngine throws NullPointerException when parsing these.
            cssContent = cssContent.replaceAll(
                    "marker\\s+path\\s*,\\s*marker\\s+circle\\s*,\\s*marker\\s+polygon\\s*\\{[^}]*\\}", "");
            // Also remove .arrowMarkerPath rules that reference markers
            cssContent = cssContent.replaceAll("\\.arrowMarkerPath\\s*\\{[^}]*\\}", "");

            // Rebuild the <style> block with cleaned content
            result.append(openTag).append(cssContent).append("</style>");

            lastEnd = closeStyleStart + "</style>".length();
        }

        // Append remainder of SVG
        result.append(svg, lastEnd, svg.length());

        // Phase 2: Also strip @keyframes and animation from outside <style> (inline styles)
        String output = result.toString();
        output = output.replaceAll("\\s+alignment-baseline\\s*=\\s*\"[^\"]*\"", "");
        // Remove style attributes containing "undefined" (browser shim artefact)
        output = output.replaceAll("\\s+style\\s*=\\s*\"[^\"]*undefined[^\"]*\"", "");
        // Fix negative stroke-width in inline styles
        output = output.replaceAll("stroke-width\\s*:\\s*-\\d+(?:\\.\\d+)?", "stroke-width:0");
        return output;
    }

    /**
     * Remove {@code @keyframes} blocks by iteratively scanning for them.
     * This is more reliable than regex for nested braces.
     */
    private static String removeKeyframesBlocks(String css) {
        StringBuilder sb = new StringBuilder(css.length());
        int i = 0;
        int len = css.length();
        while (i < len) {
            // Look for @keyframes
            int kfIdx = css.indexOf("@keyframes", i);
            if (kfIdx < 0) {
                sb.append(css, i, len);
                break;
            }
            // Append everything before @keyframes
            sb.append(css, i, kfIdx);

            // Find the opening brace of the @keyframes block
            int braceStart = css.indexOf('{', kfIdx);
            if (braceStart < 0) {
                // No opening brace found — keep the rest as-is
                sb.append(css, kfIdx, len);
                break;
            }

            // Count braces to find the matching closing brace
            int depth = 0;
            int j = braceStart;
            while (j < len) {
                char c = css.charAt(j);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        j++; // skip the final '}'
                        break;
                    }
                }
                j++;
            }
            // Skip the entire @keyframes block
            i = j;
        }
        return sb.toString();
    }

    /**
     * Fix fractional rgb() colour values that Batik cannot parse.
     * Mermaid's git-graph theme generates values like {@code rgb(48.833, 0, 146.5)}
     * which must be rounded to integer values.
     */
    private static String fixFractionalRgbValues(String svg) {
        java.util.regex.Pattern rgbP = Pattern.compile(
                "rgb\\(\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*\\)");
        java.util.regex.Matcher rgbM = rgbP.matcher(svg);
        StringBuffer rgbSb = new StringBuffer(svg.length());
        while (rgbM.find()) {
            try {
                int r = Math.max(0, Math.min(255, (int) Math.round(Double.parseDouble(rgbM.group(1)))));
                int g = Math.max(0, Math.min(255, (int) Math.round(Double.parseDouble(rgbM.group(2)))));
                int b = Math.max(0, Math.min(255, (int) Math.round(Double.parseDouble(rgbM.group(3)))));
                rgbM.appendReplacement(rgbSb, String.format("#%02x%02x%02x", r, g, b));
            } catch (NumberFormatException e) {
                rgbM.appendReplacement(rgbSb, "#888888");
            }
        }
        rgbM.appendTail(rgbSb);
        return rgbSb.toString();
    }

    /**
     * Fix the child order within node groups: shape elements must come before labels.
     * <p>
     * Mermaid's stadium/pill shapes (and others using "outer-path" groups) may
     * render the label {@code <g>} before the shape {@code <g>} when the
     * browser shim's {@code :first-child} selector doesn't work correctly.
     * Since SVG uses a painter's model (later elements cover earlier ones),
     * having the filled shape after the label text makes the text invisible.
     * <p>
     * This method detects node groups where a {@code <g class="label"...>}
     * precedes a {@code <g class="...outer-path"...>} and swaps them.
     */
    private static String fixNodeShapeLabelOrder(String svg) {
        StringBuilder result = new StringBuilder(svg.length());
        int pos = 0;

        while (pos < svg.length()) {
            int nodeStart = svg.indexOf("<g class=\"node ", pos);
            if (nodeStart < 0) {
                result.append(svg, pos, svg.length());
                break;
            }
            result.append(svg, pos, nodeStart);

            // Find end of the node's opening tag
            int nodeTagEnd = svg.indexOf('>', nodeStart);
            if (nodeTagEnd < 0) { result.append(svg, nodeStart, svg.length()); break; }

            // Find the matching </g> for this node group
            int nodeCloseStart = findMatchingGClose(svg, nodeTagEnd + 1);
            if (nodeCloseStart < 0) { result.append(svg, nodeStart, svg.length()); break; }

            String nodeOpenTag = svg.substring(nodeStart, nodeTagEnd + 1);
            String nodeInner = svg.substring(nodeTagEnd + 1, nodeCloseStart);

            // Check if this node has a label group followed by an outer-path group
            if (nodeInner.contains("outer-path")) {
                int labelStart = nodeInner.indexOf("<g class=\"label\"");
                if (labelStart < 0) labelStart = nodeInner.indexOf("<g class=\"label ");
                int outerIdx = nodeInner.indexOf("outer-path");
                int outerGStart = outerIdx >= 0 ? nodeInner.lastIndexOf("<g ", outerIdx) : -1;

                if (labelStart >= 0 && outerGStart >= 0 && labelStart < outerGStart) {
                    // Label comes before outer-path — need to swap
                    String labelGroup = extractGGroup(nodeInner, labelStart);
                    String outerGroup = extractGGroup(nodeInner, outerGStart);

                    if (labelGroup != null && outerGroup != null) {
                        String before = nodeInner.substring(0, labelStart);
                        int labelEnd = labelStart + labelGroup.length();
                        String between = nodeInner.substring(labelEnd, outerGStart);
                        int outerEnd = outerGStart + outerGroup.length();
                        String after = nodeInner.substring(outerEnd);

                        // Reassemble: outer-path first, then label (so text paints on top)
                        nodeInner = before + outerGroup + between + labelGroup + after;
                    }
                }
            }

            result.append(nodeOpenTag);
            result.append(nodeInner);
            result.append("</g>");

            pos = nodeCloseStart + 4; // skip past </g>
        }

        return result.toString();
    }

    /**
     * Find the position of the matching {@code </g>} closing tag, properly
     * tracking nested {@code <g>} elements.  Starts scanning from {@code startPos}
     * (which should be right after the opening tag's {@code >}).
     *
     * @return index of the {@code <} in {@code </g>}, or -1 if not found
     */
    private static int findMatchingGClose(String svg, int startPos) {
        int depth = 1;
        int scan = startPos;
        int len = svg.length();
        while (scan < len && depth > 0) {
            if (svg.charAt(scan) == '<') {
                if (scan + 3 < len && svg.charAt(scan + 1) == '/' && svg.charAt(scan + 2) == 'g'
                        && (svg.charAt(scan + 3) == '>' || svg.charAt(scan + 3) == ' ')) {
                    depth--;
                    if (depth == 0) return scan;
                    scan += 4;
                    continue;
                }
                if (scan + 2 < len && svg.charAt(scan + 1) == 'g'
                        && (svg.charAt(scan + 2) == ' ' || svg.charAt(scan + 2) == '>'
                        || svg.charAt(scan + 2) == '\t' || svg.charAt(scan + 2) == '\n')) {
                    int gEnd = svg.indexOf('>', scan);
                    if (gEnd >= 0 && gEnd > scan && svg.charAt(gEnd - 1) != '/') {
                        depth++;
                    }
                    scan = gEnd >= 0 ? gEnd + 1 : scan + 1;
                    continue;
                }
            }
            scan++;
        }
        return -1;
    }

    /**
     * Extract a complete {@code <g ...>...</g>} group starting at the given
     * position within a string.  Properly tracks nested {@code <g>} elements.
     *
     * @return the full group string including opening and closing tags, or null
     */
    private static String extractGGroup(String s, int start) {
        int tagEnd = s.indexOf('>', start);
        if (tagEnd < 0) return null;
        // Self-closing <g ... />
        if (s.charAt(tagEnd - 1) == '/') return s.substring(start, tagEnd + 1);

        int closePos = findMatchingGClose(s, tagEnd + 1);
        if (closePos < 0) return null;
        // Include the </g>
        int closeEnd = s.indexOf('>', closePos);
        return closeEnd >= 0 ? s.substring(start, closeEnd + 1) : null;
    }

    /**
     * Remove duplicated empty {@code <g class="node ...">} groups that arise
     * from Mermaid 11's DOM serialisation.
     * <p>
     * Mermaid creates {@code <foreignObject>} elements containing nested
     * {@code <svg>} with their own {@code <g class="nodes">} block — each
     * such block contains copies of ALL node groups, not just the current
     * label.  After foreignObject removal and SVG flattening, these copies
     * end up as siblings at the root level, producing massive duplication.
     * <p>
     * This method keeps only {@code <g class="node ...">} groups that:
     * <ul>
     *   <li>have a {@code transform} attribute with real coordinates, OR</li>
     *   <li>contain shape elements ({@code <rect>}, {@code <polygon>}, {@code <path>}), OR</li>
     *   <li>contain non-empty text content</li>
     * </ul>
     * All other "empty" node groups are removed as duplicates.
     */
    private static String deduplicateNodeGroups(String svg) {
        // Strategy: Find all <g class="node ..."> ... </g> groups.
        // If they don't have a transform attribute AND contain no shapes/text,
        // they're duplicates and should be removed.
        StringBuilder result = new StringBuilder(svg.length());
        int i = 0;
        int len = svg.length();

        while (i < len) {
            // Look for <g class="node
            int gStart = svg.indexOf("<g class=\"node ", i);
            if (gStart < 0) {
                result.append(svg, i, len);
                break;
            }

            // Append everything before this <g>
            result.append(svg, i, gStart);

            // Find the closing > of this <g> tag
            int tagEnd = svg.indexOf(">", gStart);
            if (tagEnd < 0) {
                result.append(svg, gStart, len);
                break;
            }

            String openTag = svg.substring(gStart, tagEnd + 1);

            // Find the matching </g> — need to count nested <g> tags
            int depth = 1;
            int scanPos = tagEnd + 1;
            while (scanPos < len && depth > 0) {
                // Look for next <g or </g
                int nextOpen = svg.indexOf("<g", scanPos);
                int nextClose = svg.indexOf("</g>", scanPos);

                if (nextClose < 0) {
                    scanPos = len;
                    break;
                }

                // Check if there's an opening <g before the next </g>
                if (nextOpen >= 0 && nextOpen < nextClose) {
                    // Verify it's actually <g> or <g ...> (not <g-something>)
                    int afterG = nextOpen + 2;
                    if (afterG < len && (svg.charAt(afterG) == '>' || svg.charAt(afterG) == ' '
                            || svg.charAt(afterG) == '\t' || svg.charAt(afterG) == '\n')) {
                        // Check if self-closing
                        int gEnd = svg.indexOf(">", nextOpen);
                        if (gEnd >= 0 && svg.charAt(gEnd - 1) != '/') {
                            depth++;
                        }
                    }
                    scanPos = nextOpen + 2;
                } else {
                    depth--;
                    if (depth == 0) {
                        scanPos = nextClose + 4; // "</g>".length()
                    } else {
                        scanPos = nextClose + 4;
                    }
                }
            }

            String fullGroup = svg.substring(gStart, scanPos);

            // Decide whether to keep this group:
            // KEEP if it has a transform attribute (= positioned real node)
            // KEEP if it contains shape elements (rect with width, polygon with points, path with d)
            // REMOVE if it's an empty duplicate
            boolean hasTransform = openTag.contains("transform=");
            boolean hasShapes = fullGroup.contains("<rect ") && fullGroup.contains("width=");
            boolean hasPolygon = fullGroup.contains("<polygon ");
            boolean hasPath = fullGroup.contains(" d=\"M");
            boolean hasText = fullGroup.contains("<text");

            if (hasTransform || hasShapes || hasPolygon || hasPath || hasText) {
                result.append(fullGroup);
            }
            // else: duplicate — skip it

            i = scanPos;
        }

        // Also remove orphaned empty <rect/> tags (artifacts from foreignObject removal)
        String output = result.toString();
        output = output.replace("<rect/>", "");

        return output;
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
     * <p>
     * Mermaid 11's foreignObject content may be bloated (containing entire nested SVGs
     * from the browser shim's innerHTML explosion).  This method uses a smart extraction
     * strategy:
     * <ol>
     *   <li>First, look for {@code <span class="nodeLabel">text</span>} or
     *       {@code <span class="edgeLabel">text</span>} — the actual label text</li>
     *   <li>If not found, strip all HTML/XML tags and use the plain text</li>
     *   <li>If the result is too long or contains CSS, discard the foreignObject</li>
     * </ol>
     */
    private static String convertForeignObjectsToText(String svg) {
        Pattern foPattern = Pattern.compile(
                "<foreignobject([^>]*)>(.*?)</foreignobject>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        // Pattern to extract label text from Mermaid's label spans
        Pattern labelSpanPattern = Pattern.compile(
                "<span[^>]*class\\s*=\\s*\"[^\"]*(?:nodeLabel|edgeLabel)[^\"]*\"[^>]*>([^<]*)</span>",
                Pattern.CASE_INSENSITIVE
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

            // Strategy 1: Look for Mermaid's label spans (nodeLabel, edgeLabel)
            String text = null;
            Matcher labelMatcher = labelSpanPattern.matcher(content);
            if (labelMatcher.find()) {
                text = labelMatcher.group(1).trim();
            }

            // Strategy 2: Strip all HTML/XML tags and use plain text
            if (text == null || text.isEmpty()) {
                text = content.replaceAll("<[^>]+>", "").trim();
            }

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
            textEl.append(" fill=\"#333333\"");
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
                        // DOMPurify (embedded in Mermaid) requires a full DOM implementation
                        // (Element.prototype with proper getters, createNodeIterator, etc.)
                        // that our headless browser shim cannot provide.  Replace the DOMPurify
                        // instance with a pass-through sanitizer.  This is safe because we
                        // control the diagram input — there is no user-supplied untrusted HTML.
                        mermaidBundle = mermaidBundle.replace(
                                "tl=nW()",
                                "tl={sanitize:function(t){return typeof t==='string'?t:''},isSupported:true,removed:[],version:'3.3.3'}");
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

