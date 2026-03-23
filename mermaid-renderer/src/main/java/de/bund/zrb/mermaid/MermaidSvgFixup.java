package de.bund.zrb.mermaid;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Post-processes Mermaid-generated SVG so it renders correctly in
 * strict SVG rasterisers such as Apache Batik.
 * <p>
 * Mermaid emits SVG that relies on browser-specific CSS/z-index behaviour
 * which Batik does not replicate.  This class applies DOM-level fixes.
 */
public final class MermaidSvgFixup {

    private static final Logger LOG = Logger.getLogger(MermaidSvgFixup.class.getName());
    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    // ── Typographic constants ───────────────────────────────────
    /** Default SVG font size in px (matches Mermaid's {@code font-size:16px}). */
    private static final double DEFAULT_FONT_SIZE = 16.0;
    /** Line-height factor relative to font size (CSS normal ≈ 1.2). */
    private static final double LINE_HEIGHT_FACTOR = 1.2;
    /** Baseline shift as em-value for single-line vertical centering.
     *  0.35 em ≈ half the cap-height, placing the glyph centre at y=0. */
    private static final String BASELINE_SHIFT_EM = "0.35em";

    /** Regex for Mermaid mindmap rounded-rect path commands.
     *  Captures: (1) -halfW, (2) halfH, (3) -H, (4) -R, (5) contentW */
    private static final java.util.regex.Pattern MINDMAP_BOX_PATH = java.util.regex.Pattern.compile(
            "M\\s*(-?[\\d.]+)\\s+(-?[\\d.]+)"        // M x y  (x = -halfW, y = halfH)
            + "\\s+v\\s*(-?[\\d.]+)"                  // v -H
            + "\\s+q\\s*0\\s*,\\s*(-?[\\d.]+)"        // q 0,-R …
            + "\\s+[\\d.]+\\s*,\\s*-?[\\d.]+"         // … R,-R  (rest of first quadratic)
            + "\\s+h\\s*(-?[\\d.]+)"                  // h W  (content width)
    );

    private MermaidSvgFixup() {}

    /**
     * Apply all Batik-compatibility fixes to a Mermaid SVG string.
     *
     * @param svg raw SVG produced by {@link MermaidRenderer#renderToSvg(String)}
     * @return fixed SVG string, or the original string unchanged on error
     */
    public static String fixForBatik(String svg) {
        return fixForBatik(svg, null);
    }

    /**
     * Apply all Batik-compatibility fixes to a Mermaid SVG string.
     * When the original Mermaid source code is provided, additional
     * fixes can be applied such as injecting missing sequence diagram
     * overlays (loop/alt/note/activation boxes) that GraalJS may not
     * generate.
     *
     * @param svg           raw SVG produced by {@link MermaidRenderer#renderToSvg(String)}
     * @param mermaidSource original Mermaid diagram source (optional, may be null)
     * @return fixed SVG string, or the original string unchanged on error
     */
    public static String fixForBatik(String svg, String mermaidSource) {
        if (svg == null || svg.isEmpty()) return svg;

        // Pre-process: apply critical regex fixes that must work regardless
        // of whether DOM parsing succeeds (alignment-baseline, hsl, rgba).
        svg = regexSanitize(svg);

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(svg.getBytes("UTF-8")));

            moveMarkersToDefs(doc);
            fixMarkerFills(doc);
            fixMarkerViewBox(doc);
            fixMarkerOrient(doc);          // auto-start-reverse → auto (SVG 2→1.1)
            flattenSwitchElements(doc);    // <switch> → keep first child only
            fixGroupZOrder(doc);           // edges paint ON TOP of nodes
            fixNodeZOrder(doc);
            fixLabelCentering(doc);
            fixErEntityLabels(doc);        // reposition ER entity labels into correct cells
            fixMindmapMultilineBoxes(doc);  // expand boxes for multi-line text
            fixEdgeStrokes(doc);
            fixEdgeLabelBackground(doc);
            fixEdgeLabelRect(doc);
            fixStrokeNoneOnLines(doc);
            fixCssFillNone(doc);
            fixCssForBatik(doc);           // hsl()→hex, strip unsupported CSS
            fixAlignmentBaseline(doc);     // remove alignment-baseline AND dominant-baseline attrs
            fixSequenceText(doc);          // fix text positioning in seq diagrams
            fixSequenceLifelines(doc);     // extend lifelines to bottom actor boxes
            if (mermaidSource != null) {
                injectSequenceOverlays(doc, mermaidSource); // add missing loop/alt/note/activation boxes
            }
            fixViewBoxFromAttributes(doc);

            String result = serialise(doc);
            // Final safety pass: catch anything that leaked through DOM serialisation
            result = regexSanitize(result);
            return result;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MermaidSvgFixup] DOM processing failed, applying regex fallback", e);
            return regexFallbackFix(svg);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Regex-based Batik sanitisation (fallback + safety net)
    // ═══════════════════════════════════════════════════════════

    /**
     * Apply essential Batik-compatibility fixes using regex only.
     * This is used as:
     * <ul>
     *   <li><b>Pre-processing</b> before DOM parsing (to help the parser succeed)</li>
     *   <li><b>Post-processing</b> after DOM serialisation (safety net)</li>
     *   <li><b>Fallback</b> when DOM parsing fails entirely</li>
     * </ul>
     */
    static String regexSanitize(String svg) {
        if (svg == null || svg.isEmpty()) return svg;

        // Fix locale-dependent decimal separators in ALL SVG transform functions.
        // German locale produces "translate(10,5 -18,9)" instead of "translate(10.5 -18.9)"
        // where commas are used as decimal separators WITH spaces separating values.
        // IMPORTANT: We must NOT replace commas that are value separators (e.g. translate(225,225)).
        // GraalJS always produces proper dots for decimals, so the only locale issue comes
        // from Java code that formats numbers without Locale.US.
        // We only fix commas that are clearly decimal: digit,digit NOT followed by space/comma
        // which would indicate a 2nd value.  The safest heuristic: only fix if the Java bridge
        // injected locale-formatted numbers, which would be "translate(10,5 -18,9)" pattern
        // (space-separated, not comma-separated values).
        // For safety, skip this fix entirely — GraalJS and our Java code use Locale.US.
        // If locale issues resurface, fix them at the source (Java String.format with Locale.US).

        // Fix transform attributes containing invalid values that aren't covered
        // by the NaN/Infinity removal above.  Batik requires well-formed SVG 1.1
        // transform syntax.  Strip any transform whose value doesn't start with
        // a known SVG transform function.
        {
            java.util.regex.Pattern badTransformP = java.util.regex.Pattern.compile(
                    "\\s+transform\\s*=\\s*\"([^\"]*)\"");
            java.util.regex.Matcher badTransformM = badTransformP.matcher(svg);
            StringBuffer badTransformSb = new StringBuffer(svg.length());
            while (badTransformM.find()) {
                String val = badTransformM.group(1).trim();
                // A valid SVG transform must contain at least one known function
                if (!val.isEmpty() && !val.matches(".*\\b(translate|rotate|scale|matrix|skewX|skewY)\\s*\\(.*")) {
                    // Not a valid transform → remove the entire attribute
                    badTransformM.appendReplacement(badTransformSb, "");
                } else {
                    badTransformM.appendReplacement(badTransformSb,
                            java.util.regex.Matcher.quoteReplacement(badTransformM.group(0)));
                }
            }
            badTransformM.appendTail(badTransformSb);
            svg = badTransformSb.toString();
        }

        // Remove alignment-baseline XML attributes (double or single quotes)
        svg = svg.replaceAll("\\s+alignment-baseline\\s*=\\s*\"[^\"]*\"", "");
        svg = svg.replaceAll("\\s+alignment-baseline\\s*=\\s*'[^']*'", "");

        // Remove alignment-baseline from CSS (in <style> blocks and inline styles)
        svg = svg.replaceAll("alignment-baseline\\s*:\\s*[^;\"'}<]+[;]?", "");

        // Remove @keyframes blocks (Batik CSS engine cannot parse them → NPE)
        // Handles nested braces: @keyframes name { from { ... } to { ... } }
        svg = svg.replaceAll("@keyframes\\s+[^{]+\\{[^}]*\\{[^}]*\\}[^}]*\\{[^}]*\\}[^}]*\\}", "");
        svg = svg.replaceAll("@keyframes\\s+[^{]+\\{[^}]*\\{[^}]*\\}[^}]*\\}", "");
        svg = svg.replaceAll("@keyframes\\s+[^{]+\\{[^}]*\\}", "");

        // Remove animation CSS property (depends on @keyframes which is stripped)
        svg = svg.replaceAll("animation\\s*:\\s*[^;\"'}<]+[;]?", "");

        // Remove stroke-linecap (Batik may choke on certain contexts)
        svg = svg.replaceAll("stroke-linecap\\s*:\\s*[^;\"'}<]+[;]?", "");

        // Replace orient="auto-start-reverse" with orient="auto" (SVG 2 → 1.1)
        svg = svg.replace("orient=\"auto-start-reverse\"", "orient=\"auto\"");

        // Replace "transparent" paint value with "none" — Batik's CSS parser throws
        // NullPointerException on "transparent" (CSS3 color, not SVG 1.1).
        svg = svg.replace("fill:transparent", "fill:none");
        svg = svg.replace("stroke:transparent", "stroke:none");
        svg = svg.replace("fill=\"transparent\"", "fill=\"none\"");
        svg = svg.replace("stroke=\"transparent\"", "stroke=\"none\"");

        // Fix negative stroke-width values in CSS (Mermaid mindmap generates
        // edge-depth-N classes with negative stroke-widths for deep nesting levels,
        // e.g. stroke-width:-1.  Batik's BasicStroke throws IllegalArgumentException
        // for negative widths).  Replace any negative value with 0.
        {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "stroke-width\\s*:\\s*(-\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher m = p.matcher(svg);
            StringBuffer sb = new StringBuffer(svg.length());
            while (m.find()) {
                m.appendReplacement(sb, "stroke-width:0");
            }
            m.appendTail(sb);
            svg = sb.toString();
        }

        // Remove style attributes containing "undefined" (browser shim artefact:
        // Mermaid mindmap edges get style="undefined;;;undefined" when the shim
        // returns undefined for CSS property lookups)
        svg = svg.replaceAll("\\s+style\\s*=\\s*\"[^\"]*undefined[^\"]*\"", "");
        svg = svg.replaceAll("\\s+style\\s*=\\s*'[^']*undefined[^']*'", "");

        // Remove dominant-baseline from CSS and inline styles
        // (Batik may choke on certain values like "middle", "central")
        svg = svg.replaceAll("dominant-baseline\\s*:\\s*[^;\"'}<]+[;]?", "");

        // Remove dominant-baseline XML attributes (Batik doesn't support values
        // like "central" which Mermaid journey/gitGraph diagrams emit)
        svg = svg.replaceAll("\\s+dominant-baseline\\s*=\\s*\"[^\"]*\"", "");
        svg = svg.replaceAll("\\s+dominant-baseline\\s*=\\s*'[^']*'", "");

        // Replace CSS var() references — Batik has no CSS variable support
        svg = svg.replace("var(--mermaid-font-family)",
                "'trebuchet ms', verdana, arial, sans-serif");
        // Remove remaining var(--name, fallback) → use fallback
        svg = svg.replaceAll("var\\(\\s*--[\\w-]+\\s*,\\s*([^)]+)\\)", "$1");

        // Fix fractional rgb() values: Mermaid git-graph theme emits values like
        // rgb(48.8333, 0, 146.5) which Batik cannot parse.  Round to integers.
        {
            java.util.regex.Pattern rgbP = java.util.regex.Pattern.compile(
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
            svg = rgbSb.toString();
        }

        // Clean up empty/broken style attributes: style=";;;" → remove
        svg = svg.replaceAll("\\s+style\\s*=\\s*\"[;\\s]*\"", "");
        svg = svg.replaceAll("\\s+style\\s*=\\s*'[;\\s]*'", "");

        // Remove empty presentation attributes: font-weight="" / font-style=""
        // Mermaid class diagrams emit <tspan font-weight=""> which crashes Batik's
        // CSS parser (empty string is not a valid font-weight value).
        svg = svg.replaceAll("\\s+font-weight\\s*=\\s*\"\"", "");
        svg = svg.replaceAll("\\s+font-weight\\s*=\\s*''", "");
        svg = svg.replaceAll("\\s+font-style\\s*=\\s*\"\"", "");
        svg = svg.replaceAll("\\s+font-style\\s*=\\s*''", "");

        // Convert hsl() in fill/stroke attributes (e.g. ER diagram row backgrounds)
        // Pattern: fill="hsl(240, 100%, 97%)"
        {
            java.util.regex.Pattern hslAttrP = java.util.regex.Pattern.compile(
                    "(fill|stroke)\\s*=\\s*\"(hsl\\([^)]+\\))\"");
            java.util.regex.Matcher hslAttrM = hslAttrP.matcher(svg);
            StringBuffer hslAttrSb = new StringBuffer(svg.length());
            while (hslAttrM.find()) {
                String attrName = hslAttrM.group(1);
                String hslVal = hslAttrM.group(2);
                String converted = replaceHslValues(hslVal);
                hslAttrM.appendReplacement(hslAttrSb, attrName + "=\"" + converted + "\"");
            }
            hslAttrM.appendTail(hslAttrSb);
            svg = hslAttrSb.toString();
        }

        // Fix negative width on <rect> elements — Batik throws
        // "The attribute 'width' of the element <rect> cannot be negative".
        // Gantt charts produce these when D3's time scale inverts coordinates.
        // Convert negative width to absolute value and shift x position accordingly.
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
                                    + String.format(java.util.Locale.US, "%.1f", posW)
                                    + "\"" + closeTag));
                } else {
                    negWM.appendReplacement(negWSb,
                            java.util.regex.Matcher.quoteReplacement(
                                    "<rect" + before + "width=\""
                                    + String.format(java.util.Locale.US, "%.1f", posW)
                                    + "\"" + after + closeTag));
                }
            }
            negWM.appendTail(negWSb);
            svg = negWSb.toString();
        }
        // Clamp any remaining negative height to 0
        svg = svg.replaceAll("height\\s*=\\s*\"(-[\\d.]+)\"", "height=\"0\"");

        // Remove empty presentation attributes: fill="" / stroke="" / transform=""
        // Batik's CSS parser crashes on empty string values for paint/transform.
        svg = svg.replaceAll("\\s+fill\\s*=\\s*\"\"", "");
        svg = svg.replaceAll("\\s+fill\\s*=\\s*''", "");
        svg = svg.replaceAll("\\s+stroke\\s*=\\s*\"\"", "");
        svg = svg.replaceAll("\\s+stroke\\s*=\\s*''", "");
        svg = svg.replaceAll("\\s+transform\\s*=\\s*\"\"", "");
        svg = svg.replaceAll("\\s+transform\\s*=\\s*''", "");

        // Remove transform attributes containing NaN or Infinity values
        // (browser shim may produce these when dimension computation fails)
        svg = svg.replaceAll("\\s+transform\\s*=\\s*\"[^\"]*NaN[^\"]*\"", "");
        svg = svg.replaceAll("\\s+transform\\s*=\\s*\"[^\"]*Infinity[^\"]*\"", "");
        svg = svg.replaceAll("\\s+transform\\s*=\\s*\"[^\"]*undefined[^\"]*\"", "");

        // Fix transforms that contain only whitespace or are syntactically invalid:
        // e.g. transform="translate(, )" or transform="rotate()" with no args
        svg = svg.replaceAll("\\s+transform\\s*=\\s*\"\\s*\"", "");
        svg = svg.replaceAll("\\s+transform\\s*=\\s*\"[a-z]+\\(\\s*\\)\"", "");
        svg = svg.replaceAll("\\s+transform\\s*=\\s*\"[a-z]+\\(\\s*,\\s*\\)\"", "");

        // Fix SVG element case sensitivity: D3 creates <lineargradient> (lowercase)
        // but SVG requires <linearGradient> (camelCase).  Same for other elements.
        svg = svg.replace("<lineargradient", "<linearGradient");
        svg = svg.replace("</lineargradient>", "</linearGradient>");
        svg = svg.replace("<radialgradient", "<radialGradient");
        svg = svg.replace("</radialgradient>", "</radialGradient>");
        svg = svg.replace("<clippath", "<clipPath");
        svg = svg.replace("</clippath>", "</clipPath>");
        svg = svg.replace("<textpath", "<textPath");
        svg = svg.replace("</textpath>", "</textPath>");
        svg = svg.replace("<foreignobject", "<foreignObject");
        svg = svg.replace("</foreignobject>", "</foreignObject>");

        // Also remove CSS rules with bare 'marker' type selectors that crash
        // Batik's CSSEngine (NullPointerException in parseStyleSheet).
        // Mermaid class diagrams emit: marker path, marker circle, marker polygon {...}
        svg = svg.replaceAll(
                "marker\\s+path\\s*,\\s*marker\\s+circle\\s*,\\s*marker\\s+polygon\\s*\\{[^}]*\\}", "");
        svg = svg.replaceAll("\\.arrowMarkerPath\\s*\\{[^}]*\\}", "");

        return svg;
    }

    /**
     * Comprehensive regex-based fallback when DOM parsing fails.
     * Applies all critical Batik fixes that would normally be done via DOM.
     */
    private static String regexFallbackFix(String svg) {
        if (svg == null || svg.isEmpty()) return svg;

        // alignment-baseline (already done by regexSanitize, but be safe)
        svg = regexSanitize(svg);

        // Convert hsl() → hex in all contexts
        svg = replaceHslValues(svg);

        // Convert rgba() → hex
        svg = replaceRgbaValues(svg);

        // Strip unsupported CSS properties
        svg = svg.replaceAll("position\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("box-shadow\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("filter\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("z-index\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("pointer-events\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("cursor\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("text-align\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("background-color\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("animation\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("stroke-linecap\\s*:\\s*[^;\"]+;?", "");

        // Replace fill="currentColor" with concrete color
        svg = svg.replace("fill=\"currentColor\"", "fill=\"#333333\"");

        // Strip CSS :root rules with custom properties
        svg = svg.replaceAll("#mmd-\\d+\\s*:root\\s*\\{[^}]*\\}", "");

        return svg;
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 1 — move <marker> elements into <defs>
    // ═══════════════════════════════════════════════════════════

    /**
     * Batik only resolves {@code url(#id)} marker references when the
     * {@code <marker>} lives inside a {@code <defs>} block.  Mermaid places
     * them as direct children of a {@code <g>}.  This method collects all
     * markers and moves them into a single {@code <defs>} element at the
     * start of the SVG root.
     */
    private static void moveMarkersToDefs(Document doc) {
        NodeList markers = doc.getElementsByTagNameNS("*", "marker");
        if (markers.getLength() == 0) return;

        // Collect markers (snapshot, because we'll be moving nodes)
        List<Element> markerList = new ArrayList<Element>();
        for (int i = 0; i < markers.getLength(); i++) {
            Node n = markers.item(i);
            if (n instanceof Element) markerList.add((Element) n);
        }

        // Find or create <defs> as first child of <svg>
        Element svgRoot = doc.getDocumentElement();
        Element defs = null;
        NodeList defsList = svgRoot.getElementsByTagNameNS("*", "defs");
        if (defsList.getLength() > 0) {
            defs = (Element) defsList.item(0);
        } else {
            defs = doc.createElementNS(SVG_NS, "defs");
            svgRoot.insertBefore(defs, svgRoot.getFirstChild());
        }

        // Move each marker into <defs>
        for (Element marker : markerList) {
            marker.getParentNode().removeChild(marker);
            defs.appendChild(marker);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 2 — marker arrow fills
    // ═══════════════════════════════════════════════════════════

    /**
     * Inside every {@code <marker>} element, ensure child shapes
     * ({@code <path>}, {@code <circle>}) have an explicit
     * {@code fill="#333333"} attribute so Batik renders them visibly.
     */
    private static void fixMarkerFills(Document doc) {
        NodeList markers = doc.getElementsByTagNameNS("*", "marker");
        for (int i = 0; i < markers.getLength(); i++) {
            Node m = markers.item(i);
            if (m instanceof Element) addFillToDescendants((Element) m, "#333333");
        }
    }

    private static void addFillToDescendants(Element parent, String fill) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            Element el = (Element) child;
            String tag = el.getLocalName();
            if ("path".equals(tag) || "circle".equals(tag) || "polygon".equals(tag)) {
                // Set fill as both attribute AND in style for highest specificity
                // This prevents fill:none inheritance from referencing elements
                el.setAttribute("fill", fill);
                String existingStyle = el.getAttribute("style");
                if (existingStyle == null) existingStyle = "";
                if (!existingStyle.contains("fill:") && !existingStyle.contains("fill :")) {
                    el.setAttribute("style", "fill:" + fill + ";" + existingStyle);
                }
            }
            addFillToDescendants(el, fill);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — flatten <switch> elements for Batik
    // ═══════════════════════════════════════════════════════════

    /**
     * Batik has issues with SVG {@code <switch>} elements when the children
     * lack proper {@code requiredFeatures} or {@code systemLanguage}
     * attributes.  Mermaid's journey diagram emits {@code <switch>} blocks
     * with two {@code <text>} alternatives but no feature-gating, which can
     * cause Batik to render both or fail entirely.
     * <p>
     * This fix replaces each {@code <switch>} with its first element child,
     * discarding the rest.
     */
    private static void flattenSwitchElements(Document doc) {
        NodeList switches = doc.getElementsByTagNameNS("*", "switch");
        // Snapshot into list because we'll be modifying the DOM
        List<Element> switchList = new ArrayList<Element>();
        for (int i = 0; i < switches.getLength(); i++) {
            Node n = switches.item(i);
            if (n instanceof Element) switchList.add((Element) n);
        }

        for (Element sw : switchList) {
            Node parent = sw.getParentNode();
            if (parent == null) continue;

            // Find first element child
            Element firstChild = null;
            Node child = sw.getFirstChild();
            while (child != null) {
                if (child instanceof Element) {
                    firstChild = (Element) child;
                    break;
                }
                child = child.getNextSibling();
            }

            if (firstChild != null) {
                // Replace <switch> with its first child
                parent.insertBefore(firstChild, sw);
            }
            parent.removeChild(sw);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — reorder top-level groups: nodes BEFORE edges
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid's SVG group order is {@code clusters → edgePaths → edgeLabels → nodes}.
     * This means node rectangles paint ON TOP of edge arrows, hiding arrowheads.
     * <p>
     * We reorder to: {@code clusters → nodes → edgePaths → edgeLabels}
     * so that edges (with their marker arrowheads) paint on top of nodes.
     */
    private static void fixGroupZOrder(Document doc) {
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = attr(g, "class");
            if (!"root".equals(cls)) continue;

            // Collect the known child groups
            Element clusters = null, edgePaths = null, edgeLabels = null, nodes = null;
            List<Node> otherChildren = new ArrayList<Node>();

            Node child = g.getFirstChild();
            while (child != null) {
                Node next = child.getNextSibling();
                if (child instanceof Element) {
                    String childCls = attr((Element) child, "class");
                    if ("clusters".equals(childCls)) clusters = (Element) child;
                    else if ("edgePaths".equals(childCls)) edgePaths = (Element) child;
                    else if ("edgeLabels".equals(childCls)) edgeLabels = (Element) child;
                    else if ("nodes".equals(childCls)) nodes = (Element) child;
                    else otherChildren.add(child);
                } else {
                    otherChildren.add(child);
                }
                child = next;
            }

            // Only reorder if we found the expected groups
            if (edgePaths == null || nodes == null) continue;

            // Remove all children
            while (g.getFirstChild() != null) g.removeChild(g.getFirstChild());

            // Re-add in correct order: clusters → nodes → edgePaths → edgeLabels → others
            if (clusters != null) g.appendChild(clusters);
            g.appendChild(nodes);
            g.appendChild(edgePaths);
            if (edgeLabels != null) g.appendChild(edgeLabels);
            for (Node other : otherChildren) g.appendChild(other);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 3 — node z-order: shape before label
    // ═══════════════════════════════════════════════════════════

    /**
     * Inside every {@code <g class="node ...">} element, reorder children
     * so that shape elements ({@code rect, circle, ellipse, polygon, path})
     * are painted first (bottom), and label groups on top.
     */
    private static void fixNodeZOrder(Document doc) {
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = attr(g, "class");
            if (!cls.contains("node")) continue;

            List<Node> shapes = new ArrayList<Node>();
            List<Node> others = new ArrayList<Node>();

            Node child = g.getFirstChild();
            while (child != null) {
                Node next = child.getNextSibling();
                if (child instanceof Element && isShapeTag(child.getLocalName())) {
                    shapes.add(child);
                } else {
                    others.add(child);
                }
                g.removeChild(child);
                child = next;
            }
            for (Node s : shapes) g.appendChild(s);
            for (Node o : others) g.appendChild(o);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 4 — recenter labels inside nodes
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid positions label groups with a {@code translate()} that
     * places text near the top-left of the text bounding box, relying on
     * {@code dominant-baseline:central} + {@code text-anchor:middle} in
     * browsers.  Batik does not replicate this, so we:
     * <ol>
     *   <li>Reset the label group's transform to {@code translate(0,0)}
     *       (= node centre, matching the shape centre).</li>
     *   <li>Reset the {@code <text>} element's {@code y} to {@code "0"}
     *       and set {@code dy="0.35em"} for portable vertical centering.</li>
     *   <li>Clear any {@code y}/{@code dy} attributes on inner {@code <tspan>}
     *       elements that would shift text away from centre.</li>
     * </ol>
     */
    private static void fixLabelCentering(Document doc) {
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = attr(g, "class");
            if (!cls.contains("node")) continue;

            // Skip ER entity nodes — their labels are positioned in cells,
            // not centered.  fixErEntityLabels() handles them separately.
            String id = attr(g, "id");
            if (id.startsWith("entity-")) continue;

            // Skip class diagram nodes — they have specific annotation/member/method
            // positioning that should not be overridden.
            if (id.startsWith("classId-")) continue;

            // Find child <g class="label"> groups
            NodeList children = g.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (!(child instanceof Element)) continue;
                Element childEl = (Element) child;
                if (!"g".equals(childEl.getLocalName())) continue;
                if (!attr(childEl, "class").contains("label")) continue;

                // Reset label group transform to node centre
                childEl.setAttribute("transform", "translate(0, 0)");

                // Fix text elements inside
                resetTextCentering(childEl);
            }
        }
    }

    /**
     * Reset text centering within a container element.  Sets the
     * {@code <text>} y to 0, dy to 0.35em, and clears inner tspan
     * y/dy attributes so the text is visually centred at the container
     * origin.
     * <p>
     * For multi-line text (multiple {@code <tspan class="text-outer-tspan row">}
     * elements), adds {@code dy="1.2em"} to the 2nd+ row tspans for proper
     * line breaks, and shifts the text {@code y} upward to keep the block
     * vertically centred.
     */
    private static void resetTextCentering(Element container) {
        NodeList texts = container.getElementsByTagNameNS("*", "text");
        for (int k = 0; k < texts.getLength(); k++) {
            Element text = (Element) texts.item(k);
            // Remove dominant-baseline (Batik doesn't handle "central")
            text.removeAttribute("dominant-baseline");
            // Ensure horizontal centering
            text.setAttribute("text-anchor", "middle");

            // Collect outer row tspans (each represents one line of text)
            List<Element> rowTspans = new ArrayList<Element>();
            NodeList tspans = text.getElementsByTagNameNS("*", "tspan");
            for (int t = 0; t < tspans.getLength(); t++) {
                Element tspan = (Element) tspans.item(t);
                String cls = attr(tspan, "class");
                if (cls.contains("text-outer-tspan")) {
                    rowTspans.add(tspan);
                }
            }

            // Clear y/dy on ALL inner <tspan> elements — Mermaid sets these
            // for browser layout but they cause wrong offsets in Batik
            for (int t = 0; t < tspans.getLength(); t++) {
                Element tspan = (Element) tspans.item(t);
                tspan.removeAttribute("y");
                tspan.removeAttribute("dy");
            }

            int numRows = rowTspans.size();
            if (numRows > 1) {
                // Multi-line text: position rows with dy offsets
                double fontSize = parseFontSizeFromStyle(text);
                if (fontSize <= 0) fontSize = DEFAULT_FONT_SIZE;
                double lineHeight = fontSize * LINE_HEIGHT_FACTOR;

                // Shift text y up so the multi-line block is centred at y=0
                // Centre of N rows = first baseline + (N-1) * lineHeight / 2
                // We want that centre at dy=BASELINE_SHIFT_EM, so:
                // y = -(N-1) * lineHeight / 2
                double centerOffset = (numRows - 1) * lineHeight / 2.0;
                text.setAttribute("y", String.valueOf(Math.round(-centerOffset)));
                text.setAttribute("dy", BASELINE_SHIFT_EM);

                // Add dy on 2nd+ row tspans for line breaks
                String lineDy = LINE_HEIGHT_FACTOR + "em";
                for (int r = 1; r < numRows; r++) {
                    rowTspans.get(r).setAttribute("dy", lineDy);
                }
            } else {
                // Single-line: simple centering at y=0
                text.setAttribute("y", "0");
                text.setAttribute("dy", BASELINE_SHIFT_EM);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 4a — reposition ER entity labels into correct cells
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid's ER diagram renderer positions entity attribute labels using
     * {@code getBBox()} measurements which return wrong values in our
     * headless browser shim.  As a result, all label {@code <g>} elements
     * end up at {@code translate(0, 0)}, overlapping each other.
     * <p>
     * This fix parses the entity box structure (outer path + horizontal/vertical
     * dividers) to determine cell boundaries, then repositions each label
     * {@code <g>} to the centre of its correct cell.
     * <p>
     * Label order per entity:
     * <ol>
     *   <li>{@code <g class="label name">} — entity name, centered in the header row</li>
     *   <li>For each attribute row: type, name, keys, comment (4 labels per row)</li>
     * </ol>
     */
    private static void fixErEntityLabels(Document doc) {
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String id = attr(g, "id");
            if (!id.startsWith("entity-")) continue;

            // ── Parse entity box bounds from the first <path> child ──
            double boxMinX = 0, boxMaxX = 0, boxMinY = 0, boxMaxY = 0;
            boolean foundBox = false;
            NodeList children = g.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (!(child instanceof Element)) continue;
                Element ce = (Element) child;
                if (!"g".equals(ce.getLocalName()) && !"path".equals(ce.getLocalName())) continue;
                // First child <g> contains the entity box path
                if ("g".equals(ce.getLocalName()) && !foundBox) {
                    NodeList paths = ce.getElementsByTagNameNS("*", "path");
                    if (paths.getLength() > 0) {
                        Element firstPath = (Element) paths.item(0);
                        String d = firstPath.getAttribute("d");
                        if (d != null && !d.isEmpty()) {
                            // Extract bounds from M commands: M-81 -75.5 L81 -75.5 L81 75.5 L-81 75.5
                            double[] bounds = parsePathBounds(d);
                            if (bounds != null) {
                                boxMinX = bounds[0]; boxMinY = bounds[1];
                                boxMaxX = bounds[2]; boxMaxY = bounds[3];
                                foundBox = true;
                            }
                        }
                    }
                    break;
                }
            }
            if (!foundBox) continue;

            // ── Parse dividers to find row and column boundaries ──
            List<Double> hDividers = new ArrayList<Double>(); // horizontal y values
            List<Double> vDividers = new ArrayList<Double>(); // vertical x values
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (!(child instanceof Element)) continue;
                Element ce = (Element) child;
                if (!attr(ce, "class").contains("divider")) continue;
                NodeList divPaths = ce.getElementsByTagNameNS("*", "path");
                if (divPaths.getLength() == 0) continue;
                String d = ((Element) divPaths.item(0)).getAttribute("d");
                if (d == null || d.isEmpty()) continue;
                double[] db = parsePathBounds(d);
                if (db == null) continue;
                double dw = db[2] - db[0]; // width of divider path
                double dh = db[3] - db[1]; // height of divider path
                if (dw > dh * 3) {
                    // Horizontal divider — spans most of the box width
                    double y = (db[1] + db[3]) / 2.0;
                    if (!containsApprox(hDividers, y, 2)) hDividers.add(y);
                } else if (dh > dw * 3) {
                    // Vertical divider — spans most of the box height
                    double x = (db[0] + db[2]) / 2.0;
                    if (!containsApprox(vDividers, x, 2)) vDividers.add(x);
                }
            }
            java.util.Collections.sort(hDividers);
            java.util.Collections.sort(vDividers);

            // If no horizontal divider found, there's only the name row and no attributes
            if (hDividers.isEmpty()) continue;

            // ── Build cell grid ──
            // Rows: [boxMinY, hDiv1] = name row, then attribute rows below.
            double nameRowTop = boxMinY;
            double nameRowBottom = hDividers.get(0);
            // Column boundaries for attributes: [boxMinX, vDiv1, vDiv2, ..., boxMaxX]
            List<Double> colBounds = new ArrayList<Double>();
            colBounds.add(boxMinX);
            for (Double vd : vDividers) colBounds.add(vd);
            colBounds.add(boxMaxX);
            int visibleCols = colBounds.size() - 1;

            // ── Collect label <g> elements in order ──
            List<Element> labels = new ArrayList<Element>();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (!(child instanceof Element)) continue;
                Element ce = (Element) child;
                if (!"g".equals(ce.getLocalName())) continue;
                String cls = attr(ce, "class");
                if (cls.contains("label")) labels.add(ce);
            }
            if (labels.isEmpty()) continue;

            // ── Position entity name ──
            // First label is the entity name — center it in the name row
            Element nameLabel = labels.get(0);
            double nameCenterX = 0; // already centered horizontally
            double nameCenterY = (nameRowTop + nameRowBottom) / 2.0;
            nameLabel.setAttribute("transform",
                    "translate(" + fmt(nameCenterX) + ", " + fmt(nameCenterY) + ")");

            // ── Position attribute labels ──
            // Mermaid ER uses exactly 4 labels per attribute row:
            //   attribute-type, attribute-name, attribute-keys, attribute-comment
            // But only 3 visible columns exist (comment shares the keys column).
            int LABELS_PER_ROW = 4;
            int attrLabels = labels.size() - 1;
            int numAttrRows = attrLabels / LABELS_PER_ROW;
            if (numAttrRows <= 0) continue;

            double attrTop = hDividers.get(0);
            double attrBottom = boxMaxY;
            double rowHeight = (attrBottom - attrTop) / numAttrRows;

            for (int a = 0; a < attrLabels; a++) {
                int row = a / LABELS_PER_ROW;
                int col = a % LABELS_PER_ROW;
                if (row >= numAttrRows) break;

                double cellTop = attrTop + row * rowHeight;
                double cellBottom = cellTop + rowHeight;

                // Map 4-column label index to visible column bounds (3 columns)
                // col 0 = type, col 1 = name, col 2 = keys, col 3 = comment
                // Comment (col 3) shares the keys column (col 2)
                int visCol = Math.min(col, visibleCols - 1);
                double cellLeft = colBounds.get(visCol);
                double cellRight = colBounds.get(visCol + 1);

                double cx = (cellLeft + cellRight) / 2.0;
                double cy = (cellTop + cellBottom) / 2.0;

                Element label = labels.get(1 + a);

                // For comment labels (col 3) that share the keys column,
                // hide them to avoid overlap with the keys label
                if (col == 3) {
                    label.setAttribute("transform", "translate(" + fmt(cx) + ", " + fmt(cy) + ")");
                    label.setAttribute("visibility", "hidden");
                } else {
                    label.setAttribute("transform", "translate(" + fmt(cx) + ", " + fmt(cy) + ")");
                }
                // Set text-anchor to middle for proper centering
                NodeList texts = label.getElementsByTagNameNS("*", "text");
                for (int t = 0; t < texts.getLength(); t++) {
                    ((Element) texts.item(t)).setAttribute("text-anchor", "middle");
                }
            }
        }
    }

    /** Format a double as a compact string (no trailing zeros).
     *  MUST use Locale.US — SVG requires '.' as decimal separator.
     *  Using the default locale on German systems would produce ',' which
     *  makes the SVG invalid (Batik rejects transform="translate(0, -56,6)"). */
    private static String fmt(double v) {
        if (v == Math.floor(v) && Math.abs(v) < Long.MAX_VALUE) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    /** Check if a list already contains a value within tolerance. */
    private static boolean containsApprox(List<Double> list, double value, double tolerance) {
        for (Double d : list) {
            if (Math.abs(d - value) < tolerance) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 4b — expand mindmap node boxes for multi-line text
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid mindmap nodes use a fixed-height box ({@code <path>} with a
     * rounded-rectangle {@code d} attribute) sized for one line of text.
     * When the text wraps to multiple lines, the box must be expanded so
     * the text doesn't overflow.
     * <p>
     * The path pattern is:
     * {@code M-{halfW} {halfH} v-{H} q0,-R R,-R h{W} qR,0 R,R v{H} q0,R -R,R h-{W} q-R,0 -R,-R Z}
     * <p>
     * We only modify the height-related values (halfH, H) and the underline
     * position.  The width ({@code h{W}}) and corner radius are preserved
     * exactly from the original path.
     * <p>
     * The companion {@code <line>} (underline) at y={halfH+R} is also moved.
     */
    private static void fixMindmapMultilineBoxes(Document doc) {
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = attr(g, "class");
            if (!cls.contains("mindmap-node")) continue;

            // Count row tspans in this node's text
            int numRows = countRowTspans(g);
            if (numRows <= 1) continue;

            // Find the <path class="node-bkg ..."> and <line class="node-line..."> children
            Element pathEl = null;
            Element lineEl = null;
            Element textEl = null;
            NodeList children = g.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (!(child instanceof Element)) continue;
                Element ce = (Element) child;
                String tag = ce.getLocalName();
                if ("path".equals(tag) && attr(ce, "class").contains("node-bkg")) {
                    pathEl = ce;
                } else if ("line".equals(tag) && attr(ce, "class").contains("node-line")) {
                    lineEl = ce;
                }
            }
            // Also find the text element for font-size lookup
            NodeList textNodes = g.getElementsByTagNameNS("*", "text");
            if (textNodes.getLength() > 0) {
                textEl = (Element) textNodes.item(0);
            }
            if (pathEl == null) continue;

            // Parse path d attribute
            String d = pathEl.getAttribute("d");
            if (d == null || d.isEmpty()) continue;

            java.util.regex.Matcher pm = MINDMAP_BOX_PATH.matcher(d);
            if (!pm.find()) continue;

            try {
                double halfW    = Math.abs(Double.parseDouble(pm.group(1)));
                // group(2) = oldHalfH — not needed, we compute the new one
                double oldH     = Math.abs(Double.parseDouble(pm.group(3)));
                double radius   = Math.abs(Double.parseDouble(pm.group(4)));
                double contentW = Math.abs(Double.parseDouble(pm.group(5)));

                // Resolve actual font-size from the text element
                double fontSize = DEFAULT_FONT_SIZE;
                if (textEl != null) {
                    double parsed = parseFontSizeFromStyle(textEl);
                    if (parsed > 0) fontSize = parsed;
                }
                double lineHeight = fontSize * LINE_HEIGHT_FACTOR;

                // Expand height by (numRows - 1) line-heights
                double newH     = oldH + (numRows - 1) * lineHeight;
                double newHalfH = newH / 2.0;

                // Rebuild path — only height changes; width and radii are preserved
                long hw = Math.round(halfW);
                long hh = Math.round(newHalfH);
                long h  = Math.round(newH);
                long r  = Math.round(radius);
                long cw = Math.round(contentW);
                String newD = "M-" + hw + " " + hh
                        + " v-" + h
                        + " q0,-" + r + " " + r + ",-" + r
                        + " h" + cw
                        + " q" + r + ",0 " + r + "," + r
                        + " v" + h
                        + " q0," + r + " -" + r + "," + r
                        + " h-" + cw
                        + " q-" + r + ",0 -" + r + ",-" + r
                        + " Z";
                pathEl.setAttribute("d", newD);

                // Move the underline to new bottom
                if (lineEl != null) {
                    long newLineY = hh + r;
                    lineEl.setAttribute("y1", String.valueOf(newLineY));
                    lineEl.setAttribute("y2", String.valueOf(newLineY));
                }
            } catch (NumberFormatException ignored) {
                // Path doesn't match expected number format — skip silently
            }
        }
    }

    /**
     * Count the number of {@code <tspan class="text-outer-tspan ...">}
     * elements inside the given element (recursively).
     */
    private static int countRowTspans(Element container) {
        int count = 0;
        NodeList tspans = container.getElementsByTagNameNS("*", "tspan");
        for (int t = 0; t < tspans.getLength(); t++) {
            Element tspan = (Element) tspans.item(t);
            String cls = attr(tspan, "class");
            if (cls.contains("text-outer-tspan")) {
                count++;
            }
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 5 — explicit stroke on edge paths
    // ═══════════════════════════════════════════════════════════

    /**
     * Edge paths ({@code <path class="flowchart-link ...">}) rely on CSS
     * for their stroke colour/width.  Batik does not always resolve
     * Mermaid's id-scoped CSS selectors.  Without a visible stroke the
     * {@code marker-end} is also invisible.  We add explicit attributes.
     */
    private static void fixEdgeStrokes(Document doc) {
        NodeList paths = doc.getElementsByTagNameNS("*", "path");
        for (int i = 0; i < paths.getLength(); i++) {
            Node n = paths.item(i);
            if (!(n instanceof Element)) continue;
            Element path = (Element) n;
            String cls = attr(path, "class");
            if (!cls.contains("flowchart-link") && !cls.contains("messageLine")) continue;

            // Add explicit stroke so Batik renders line + markers
            if (!path.hasAttribute("stroke")) {
                path.setAttribute("stroke", "#333333");
            }
            if (!path.hasAttribute("stroke-width")) {
                path.setAttribute("stroke-width", "2");
            }
            // Move fill:none from style to attribute to prevent
            // style-level inheritance into marker content
            String style = path.getAttribute("style");
            if (style != null && (style.contains("fill:none") || style.contains("fill: none"))) {
                style = style.replace("fill:none;", "").replace("fill:none", "")
                             .replace("fill: none;", "").replace("fill: none", "").trim();
                if (style.isEmpty()) {
                    path.removeAttribute("style");
                } else {
                    path.setAttribute("style", style);
                }
                path.setAttribute("fill", "none");
            }
        }

        // Also handle <line> elements used in sequence diagrams
        NodeList lines = doc.getElementsByTagNameNS("*", "line");
        for (int i = 0; i < lines.getLength(); i++) {
            Node n = lines.item(i);
            if (!(n instanceof Element)) continue;
            Element line = (Element) n;
            String cls = attr(line, "class");
            if (cls.contains("messageLine") || cls.contains("actor-line")) {
                if (!line.hasAttribute("stroke") || "none".equals(line.getAttribute("stroke"))) {
                    line.setAttribute("stroke", "#333");
                }
                if (!line.hasAttribute("stroke-width")) {
                    line.setAttribute("stroke-width", "1.5");
                }
                // Move fill:none from style to attribute to prevent
                // style-level inheritance into marker content
                String lineStyle = line.getAttribute("style");
                if (lineStyle != null && (lineStyle.contains("fill:") || lineStyle.contains("fill "))) {
                    lineStyle = lineStyle.replaceAll("fill\\s*:\\s*none\\s*;?", "").trim();
                    if (lineStyle.isEmpty()) {
                        line.removeAttribute("style");
                    } else {
                        line.setAttribute("style", lineStyle);
                    }
                    line.setAttribute("fill", "none");
                }
            }
        }

        // Ensure markers have overflow:visible
        NodeList markers = doc.getElementsByTagNameNS("*", "marker");
        for (int i = 0; i < markers.getLength(); i++) {
            Node m = markers.item(i);
            if (m instanceof Element) {
                ((Element) m).setAttribute("overflow", "visible");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 6 — edge label backgrounds
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid's edge labels use {@code <rect>} with CSS-based opacity.
     * Ensure the rect has an explicit {@code fill} so the background
     * is visible in Batik.  Also recenter text and background rects
     * since Mermaid's positioning relies on browser font metrics.
     */
    private static void fixEdgeLabelBackground(Document doc) {
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = attr(g, "class");
            // Match "edgeLabel" but NOT "edgeLabels" (the container)
            if (!cls.contains("edgeLabel") || cls.contains("edgeLabels")) continue;

            // Also recenter the label text inside edgeLabels
            NodeList labelGs = g.getElementsByTagNameNS("*", "g");
            for (int j = 0; j < labelGs.getLength(); j++) {
                Element lg = (Element) labelGs.item(j);
                if (attr(lg, "class").contains("label")) {
                    lg.setAttribute("transform", "translate(0, 0)");
                }
            }

            // Fix text centering (same as node labels)
            resetTextCentering(g);

            // Fix background rects: ensure fill and re-center around (0,0)
            NodeList rects = g.getElementsByTagNameNS("*", "rect");
            for (int j = 0; j < rects.getLength(); j++) {
                Element rect = (Element) rects.item(j);
                if (!rect.hasAttribute("fill")) {
                    rect.setAttribute("fill", "#e8e8e8");
                }
                // Almost-opaque so the line is mostly hidden but line style
                // (dashed, thick) remains faintly visible
                rect.setAttribute("style", "opacity:0.85");

                // Re-center the rect around (0,0) — Mermaid positions it
                // based on browser font metrics which don't match Batik
                double w = parseDouble(rect, "width", 0);
                double h = parseDouble(rect, "height", 0);
                if (w > 0 && h > 0) {
                    rect.setAttribute("x", String.valueOf(-w / 2.0));
                    rect.setAttribute("y", String.valueOf(-h / 2.0));
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 7 — stroke="none" on sequence-diagram lines
    // ═══════════════════════════════════════════════════════════

    private static void fixStrokeNoneOnLines(Document doc) {
        // Already handled in fixEdgeStrokes — this is kept for
        // any remaining lines not caught there.
        NodeList lines = doc.getElementsByTagNameNS("*", "line");
        for (int i = 0; i < lines.getLength(); i++) {
            Node n = lines.item(i);
            if (!(n instanceof Element)) continue;
            Element line = (Element) n;
            if ("none".equals(line.getAttribute("stroke"))) {
                String cls = attr(line, "class");
                if (cls.contains("messageLine") || cls.contains("actor-line")) {
                    line.setAttribute("stroke", "#333");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — remove viewBox from <marker> elements
    // ═══════════════════════════════════════════════════════════

    /**
     * Batik has known issues rendering marker content when the marker's
     * {@code viewBox} aspect ratio doesn't match {@code markerWidth/markerHeight}.
     * Mermaid emits markers with {@code viewBox="0 0 12 20"} but actual arrow
     * shapes that fit in a 10×10 box.  Removing the viewBox lets Batik render
     * the marker content directly in the marker coordinate system.
     */
    private static void fixMarkerViewBox(Document doc) {
        NodeList markers = doc.getElementsByTagNameNS("*", "marker");
        for (int i = 0; i < markers.getLength(); i++) {
            Node n = markers.item(i);
            if (!(n instanceof Element)) continue;
            Element marker = (Element) n;

            // Remove viewBox — let marker content use markerWidth/Height directly
            marker.removeAttribute("viewBox");

            // Ensure marker is reasonably sized — not too small and not absurdly large.
            // Mermaid class-diagram emits markerHeight="240" markerWidth="190" for
            // start markers, which creates a huge coordinate space that can confuse Batik.
            String mw = marker.getAttribute("markerWidth");
            String mh = marker.getAttribute("markerHeight");
            try {
                double w = mw.isEmpty() ? 0 : Double.parseDouble(mw);
                double h = mh.isEmpty() ? 0 : Double.parseDouble(mh);
                if (w < 12) marker.setAttribute("markerWidth", "12");
                else if (w > 30) marker.setAttribute("markerWidth", "30");
                if (h < 12) marker.setAttribute("markerHeight", "12");
                else if (h > 30) marker.setAttribute("markerHeight", "30");
            } catch (NumberFormatException ignored) {}

            // Ensure overflow is visible
            marker.setAttribute("overflow", "visible");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — insert background rect behind edge labels
    // ═══════════════════════════════════════════════════════════

    /**
     * Replace {@code orient="auto-start-reverse"} on {@code <marker>}
     * elements with {@code orient="auto"}.
     * <p>
     * {@code auto-start-reverse} is an SVG 2.0 feature that Batik (SVG 1.1)
     * does not understand.  Without this fix, sequence diagrams (which use
     * markers with this orient value) fail to rasterise entirely.
     */
    private static void fixMarkerOrient(Document doc) {
        NodeList markers = doc.getElementsByTagNameNS("*", "marker");
        for (int i = 0; i < markers.getLength(); i++) {
            Node n = markers.item(i);
            if (!(n instanceof Element)) continue;
            Element marker = (Element) n;
            String orient = marker.getAttribute("orient");
            if ("auto-start-reverse".equals(orient)) {
                marker.setAttribute("orient", "auto");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — fix text in sequence diagrams
    // ═══════════════════════════════════════════════════════════

    /**
     * Fix text positioning in sequence diagrams.  Mermaid's sequence
     * diagrams use {@code dominant-baseline="central"} on actor text
     * and {@code dominant-baseline="middle"} + {@code dy="1em"} on
     * message text.  After removing these for Batik, text appears in
     * wrong positions.  This method adds proper {@code dy} offsets.
     */
    private static void fixSequenceText(Document doc) {
        NodeList texts = doc.getElementsByTagNameNS("*", "text");
        for (int i = 0; i < texts.getLength(); i++) {
            Node n = texts.item(i);
            if (!(n instanceof Element)) continue;
            Element text = (Element) n;
            String cls = attr(text, "class");

            // Actor labels: class="actor actor-box" — need dy for centering
            if (cls.contains("actor")) {
                text.removeAttribute("dominant-baseline");
                // Set dy for vertical centering (replaces dominant-baseline:central)
                text.setAttribute("dy", "0.35em");
                // Also fix inner tspans
                NodeList tspans = text.getElementsByTagNameNS("*", "tspan");
                for (int t = 0; t < tspans.getLength(); t++) {
                    Element tspan = (Element) tspans.item(t);
                    // Keep the x attribute (absolute horizontal position)
                    // but clear dy since we set it on the <text>
                    tspan.removeAttribute("dy");
                }
            }
            // Message labels: class="messageText" — have dy="1em" which is too much
            else if (cls.contains("messageText")) {
                text.removeAttribute("dominant-baseline");
                // Mermaid sets dy="1em" assuming dominant-baseline:middle.
                // Without dominant-baseline, the text baseline is "alphabetic"
                // (at bottom of letters). We need a smaller dy.
                text.setAttribute("dy", "0.35em");

                // ── Move text closer to the corresponding arrow line ────────
                // In the headless shim the text bbox is over-estimated, causing
                // Mermaid to place message labels ~40 px above the arrow instead
                // of the expected ~5 px.  Walk forward through siblings to find
                // the next <line class="messageLine…"> and re-position the text
                // so that the visual baseline sits a small gap above the line.
                Element nextLine = findNextMessageLine(text);
                if (nextLine != null) {
                    String lineYStr = attr(nextLine, "y1");
                    String textYStr = attr(text, "y");
                    if (!lineYStr.isEmpty() && !textYStr.isEmpty()) {
                        try {
                            double lineY = Double.parseDouble(lineYStr);
                            double textY = Double.parseDouble(textYStr);
                            // Resolve font-size from style (default 16 px)
                            double fontSize = parseFontSizeFromStyle(text);
                            // dy=0.35em shifts visual baseline down by 0.35·fontSize
                            double dyShift = 0.35 * fontSize;
                            // We want the visual text bottom (baseline + descent)
                            // to be ~4 px above the arrow line.
                            // descent ≈ 0.25·fontSize
                            double desiredGap = 4;
                            double descent = fontSize * 0.25;
                            // visual bottom = newY + dyShift + descent
                            // visual bottom = lineY - desiredGap
                            double newY = lineY - desiredGap - descent - dyShift;
                            // Only move DOWN (towards line) — never push text further away
                            if (newY > textY) {
                                text.setAttribute("y", String.valueOf(Math.round(newY)));
                            }
                        } catch (NumberFormatException ignored) {
                            // keep original position
                        }
                    }
                }
            }
            // Loop/note labels: class contains "loopText" or "noteText" or "labelText"
            else if (cls.contains("loopText") || cls.contains("noteText") || cls.contains("labelText")) {
                text.removeAttribute("dominant-baseline");
                text.setAttribute("dy", "0.35em");
                // Fix inner tspans
                NodeList tspans = text.getElementsByTagNameNS("*", "tspan");
                for (int t = 0; t < tspans.getLength(); t++) {
                    ((Element) tspans.item(t)).removeAttribute("dy");
                }
            }
        }
    }

    /**
     * Walk forward through element siblings of {@code text} and return the
     * first {@code <line>} whose class starts with {@code "messageLine"}.
     */
    private static Element findNextMessageLine(Element text) {
        Node sib = text.getNextSibling();
        while (sib != null) {
            if (sib instanceof Element) {
                Element el = (Element) sib;
                if ("line".equalsIgnoreCase(el.getLocalName())) {
                    String cls = attr(el, "class");
                    if (cls.contains("messageLine")) {
                        return el;
                    }
                }
                // Stop if we hit the next messageText — no matching line found
                if ("text".equalsIgnoreCase(el.getLocalName())) {
                    String cls = attr(el, "class");
                    if (cls.contains("messageText")) break;
                }
            }
            sib = sib.getNextSibling();
        }
        return null;
    }

    /**
     * Extract the CSS {@code font-size} value from an element's inline style.
     * Returns 16 as default if not found.
     */
    private static double parseFontSizeFromStyle(Element el) {
        String style = attr(el, "style");
        if (!style.isEmpty()) {
            // Match "font-size: 16px" or "font-size:14px"
            int idx = style.indexOf("font-size");
            if (idx >= 0) {
                String rest = style.substring(idx + 9).trim();
                if (rest.startsWith(":")) rest = rest.substring(1).trim();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rest.length(); i++) {
                    char c = rest.charAt(i);
                    if (c == '.' || (c >= '0' && c <= '9')) sb.append(c);
                    else if (sb.length() > 0) break;
                }
                if (sb.length() > 0) {
                    try { return Double.parseDouble(sb.toString()); }
                    catch (NumberFormatException ignored) { }
                }
            }
        }
        return 16.0;
    }

    // ═══════════════════════════════════════════════════════════

    /**
     * If an {@code <g class="edgeLabel">} has text but no {@code <rect>}
     * background, insert one so the label has a visible background and
     * the edge line doesn't draw through the text.
     */
    private static void fixEdgeLabelRect(Document doc) {
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = attr(g, "class");
            // Match "edgeLabel" but NOT "edgeLabels" (the container)
            if (!cls.contains("edgeLabel") || cls.contains("edgeLabels")) continue;

            // Check if there's already a rect
            NodeList rects = g.getElementsByTagNameNS("*", "rect");
            if (rects.getLength() > 0) continue;

            // Check if there's text content
            NodeList texts = g.getElementsByTagNameNS("*", "text");
            if (texts.getLength() == 0) continue;

            // Estimate text size and insert a background rect
            Element text = (Element) texts.item(0);
            String textContent = text.getTextContent();
            if (textContent == null || textContent.trim().isEmpty()) continue;

            // Approximate: each char ~ 8px wide, height ~ 20px, with padding
            int charCount = textContent.trim().length();
            int width = charCount * 9 + 16;
            int height = 24;

            Element rect = doc.createElementNS(SVG_NS, "rect");
            rect.setAttribute("x", String.valueOf(-width / 2));
            rect.setAttribute("y", String.valueOf(-height / 2));
            rect.setAttribute("width", String.valueOf(width));
            rect.setAttribute("height", String.valueOf(height));
            rect.setAttribute("fill", "#e8e8e8");
            rect.setAttribute("style", "opacity:0.85");
            rect.setAttribute("rx", "3");
            rect.setAttribute("ry", "3");

            // Insert rect as first child (behind text)
            g.insertBefore(rect, g.getFirstChild());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — patch CSS fill:none to prevent marker inheritance
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid's embedded {@code <style>} block contains rules like
     * {@code .flowchart-link{fill:none}} which in Batik can cascade
     * into marker content via property inheritance.  This fix adds an
     * explicit CSS rule for marker child elements.
     */
    private static void fixCssFillNone(Document doc) {
        NodeList styles = doc.getElementsByTagNameNS("*", "style");
        for (int i = 0; i < styles.getLength(); i++) {
            Node styleNode = styles.item(i);
            String css = styleNode.getTextContent();
            if (css == null || css.isEmpty()) continue;

            // Add marker content override — ensures arrow fills are not overridden
            if (!css.contains(".arrowMarkerPath")) {
                css = css + "\n.arrowMarkerPath{fill:#333333 !important;}\n"
                        + "marker path, marker circle, marker polygon{fill:#333333 !important;}\n";
                styleNode.setTextContent(css);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — clean CSS for Batik compatibility
    // ═══════════════════════════════════════════════════════════

    /**
     * Batik's CSS parser does not support:
     * <ul>
     *   <li>{@code hsl()} colour functions → convert to hex</li>
     *   <li>{@code position}, {@code box-shadow}, {@code filter},
     *       {@code z-index}, {@code pointer-events}, {@code cursor}
     *       → strip them</li>
     *   <li>{@code rgba()} colour functions → convert to hex (drop alpha)</li>
     * </ul>
     * Without this fix, sequence diagrams fail to rasterise entirely.
     */
    private static void fixCssForBatik(Document doc) {
        NodeList styles = doc.getElementsByTagNameNS("*", "style");
        for (int i = 0; i < styles.getLength(); i++) {
            Node styleNode = styles.item(i);
            String css = styleNode.getTextContent();
            if (css == null || css.isEmpty()) continue;

            // 1) Remove @keyframes blocks using iterative brace counting
            css = removeKeyframesBlocks(css);

            // 2) Convert hsl(...) → hex
            css = replaceHslValues(css);

            // 3) Convert rgba(...) → hex (drop alpha)
            css = replaceRgbaValues(css);

            // 4) Remove unsupported CSS properties
            String[] unsupported = {
                "position", "box-shadow", "filter", "z-index",
                "pointer-events", "cursor", "text-align", "background-color",
                "alignment-baseline", "dominant-baseline", "animation", "stroke-linecap",
                "vertical-align", "display", "overflow", "padding", "margin"
            };
            for (String prop : unsupported) {
                css = css.replaceAll(prop + "\\s*:\\s*[^;}\"]+[;]?", "");
            }

            // 4b) Fix negative stroke-width values (Mermaid mindmap generates them
            //     for deep edge-depth-N classes; Batik throws IllegalArgumentException)
            {
                java.util.regex.Pattern swp = java.util.regex.Pattern.compile(
                        "stroke-width\\s*:\\s*(-\\d+(?:\\.\\d+)?)");
                java.util.regex.Matcher swm = swp.matcher(css);
                StringBuffer swsb = new StringBuffer(css.length());
                while (swm.find()) {
                    swm.appendReplacement(swsb, "stroke-width:0");
                }
                swm.appendTail(swsb);
                css = swsb.toString();
            }

            // 5) Replace currentColor
            css = css.replace("currentColor", "#333333");

            // 6) Replace "revert" values
            css = css.replaceAll("stroke\\s*:\\s*revert\\s*;?", "");
            css = css.replaceAll("stroke-width\\s*:\\s*revert\\s*;?", "");

            // 7) Strip :root rules with CSS custom properties
            css = css.replaceAll("#mmd-\\d+\\s*:root\\s*\\{[^}]*\\}", "");

            // 8) Replace CSS var() references with their fallback or a safe default.
            //    Mermaid git-graph emits font-family:var(--mermaid-font-family);
            //    Batik's CSS engine cannot handle var() at all.
            //    Pattern: property:...var(--name)... → strip the var() call
            css = replaceCssVarReferences(css);

            styleNode.setTextContent(css);
        }

        // Also fix hsl/rgba in inline style attributes throughout the document
        NodeList all = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (!(n instanceof Element)) continue;
            Element el = (Element) n;
            String style = el.getAttribute("style");
            if (style != null && !style.isEmpty()) {
                // Remove style attributes containing "undefined" (browser shim artefact)
                if (style.contains("undefined")) {
                    el.removeAttribute("style");
                } else {
                    String fixed = replaceHslValues(style);
                    fixed = replaceRgbaValues(fixed);
                    fixed = fixed.replace("currentColor", "#333333");
                    // Fix negative stroke-width in inline styles
                    fixed = fixed.replaceAll("stroke-width\\s*:\\s*-\\d+(?:\\.\\d+)?", "stroke-width:0");
                    if (!fixed.equals(style)) {
                        el.setAttribute("style", fixed);
                    }
                }
            }
            // Fix fill/stroke attributes with unsupported values
            String fill = el.getAttribute("fill");
            if ("currentColor".equals(fill)) {
                el.setAttribute("fill", "#333333");
            } else if (fill != null && !fill.isEmpty()) {
                // Convert hsl/rgba/rgb in fill attributes (e.g. ER diagram rows)
                String fixedFill = replaceHslValues(fill);
                fixedFill = replaceRgbaValues(fixedFill);
                if (!fixedFill.equals(fill)) {
                    el.setAttribute("fill", fixedFill);
                }
            }
            String stroke = el.getAttribute("stroke");
            if (stroke != null && !stroke.isEmpty()) {
                String fixedStroke = replaceHslValues(stroke);
                fixedStroke = replaceRgbaValues(fixedStroke);
                fixedStroke = fixedStroke.replace("currentColor", "#333333");
                if (!fixedStroke.equals(stroke)) {
                    el.setAttribute("stroke", fixedStroke);
                }
            }
        }
    }

    /**
     * Replace CSS {@code var(--name)} references with safe fallbacks.
     * Batik's CSS engine does not support CSS custom properties / variables.
     * <p>
     * Strategy:
     * <ul>
     *   <li>{@code var(--name, fallback)} → use the fallback value</li>
     *   <li>{@code var(--mermaid-font-family)} → replace with default font stack</li>
     *   <li>Any remaining {@code var(--...)} → remove the entire property declaration</li>
     * </ul>
     */
    private static String replaceCssVarReferences(String css) {
        // Step 1: var(--name, fallback) → use fallback
        css = css.replaceAll("var\\(\\s*--[\\w-]+\\s*,\\s*([^)]+)\\)", "$1");

        // Step 2: known Mermaid variables with concrete replacements
        css = css.replace("var(--mermaid-font-family)",
                "'trebuchet ms', verdana, arial, sans-serif");

        // Step 3: remove any remaining property declarations that contain var()
        // e.g. "font-family:var(--foo);" → ""
        css = css.replaceAll("[\\w-]+\\s*:\\s*[^;{}]*var\\(--[^)]*\\)[^;{}]*(;|(?=\\}))", "");

        return css;
    }

    /**
     * Remove {@code @keyframes} blocks by iteratively scanning for them.
     * More reliable than regex for nested braces.
     */
    private static String removeKeyframesBlocks(String css) {
        StringBuilder sb = new StringBuilder(css.length());
        int i = 0;
        int len = css.length();
        while (i < len) {
            int kfIdx = css.indexOf("@keyframes", i);
            if (kfIdx < 0) {
                sb.append(css, i, len);
                break;
            }
            sb.append(css, i, kfIdx);
            int braceStart = css.indexOf('{', kfIdx);
            if (braceStart < 0) {
                sb.append(css, kfIdx, len);
                break;
            }
            int depth = 0;
            int j = braceStart;
            while (j < len) {
                char c = css.charAt(j);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { j++; break; }
                }
                j++;
            }
            i = j;
        }
        return sb.toString();
    }

    /**
     * Replace all {@code hsl(h, s%, l%)} occurrences with hex colour values.
     * Supports negative hue values (e.g. {@code hsl(-4, 100%, 93%)}) which
     * Mermaid emits for some diagram themes.
     */
    static String replaceHslValues(String css) {
        // First: handle hsl() with NaN values (browser shim returns NaN for
        // computed styles it cannot resolve, e.g. quadrant-chart point colors)
        css = css.replaceAll(
                "hsl\\([^)]*NaN[^)]*\\)", "#888888");

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "hsl\\(\\s*(-?[\\d.]+)\\s*,\\s*([\\d.]+)%\\s*,\\s*([\\d.]+)%\\s*\\)");
        java.util.regex.Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                double h = Double.parseDouble(m.group(1));
                double s = Double.parseDouble(m.group(2)) / 100.0;
                double l = Double.parseDouble(m.group(3)) / 100.0;
                String hex = hslToHex(h, s, l);
                m.appendReplacement(sb, hex);
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, "#888888");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Replace all {@code rgba(r, g, b, a)} and {@code rgb(r, g, b)} occurrences with hex.
     * Handles fractional values like {@code rgb(48.833, 0, 146.5)} which Mermaid's
     * git-graph theme generates and Batik cannot parse.
     */
    static String replaceRgbaValues(String css) {
        // First pass: rgba(r, g, b, a) with 4 components
        java.util.regex.Pattern p4 = java.util.regex.Pattern.compile(
                "rgba\\(\\s*(-?[\\d.]+)\\s*[,\\s]\\s*(-?[\\d.]+)\\s*[,\\s]\\s*(-?[\\d.]+)\\s*[,/]\\s*([\\d.]+)\\s*\\)");
        java.util.regex.Matcher m4 = p4.matcher(css);
        StringBuffer sb4 = new StringBuffer();
        while (m4.find()) {
            try {
                int r = clamp((int) Math.round(Double.parseDouble(m4.group(1))));
                int g = clamp((int) Math.round(Double.parseDouble(m4.group(2))));
                int b = clamp((int) Math.round(Double.parseDouble(m4.group(3))));
                String hex = String.format("#%02x%02x%02x", r, g, b);
                m4.appendReplacement(sb4, hex);
            } catch (NumberFormatException e) {
                m4.appendReplacement(sb4, "#888888");
            }
        }
        m4.appendTail(sb4);
        css = sb4.toString();

        // Second pass: rgb(r, g, b) with 3 components (including fractional values)
        java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(
                "rgb\\(\\s*(-?[\\d.]+)\\s*[,\\s]\\s*(-?[\\d.]+)\\s*[,\\s]\\s*(-?[\\d.]+)\\s*\\)");
        java.util.regex.Matcher m3 = p3.matcher(css);
        StringBuffer sb3 = new StringBuffer();
        while (m3.find()) {
            try {
                int r = clamp((int) Math.round(Double.parseDouble(m3.group(1))));
                int g = clamp((int) Math.round(Double.parseDouble(m3.group(2))));
                int b = clamp((int) Math.round(Double.parseDouble(m3.group(3))));
                String hex = String.format("#%02x%02x%02x", r, g, b);
                m3.appendReplacement(sb3, hex);
            } catch (NumberFormatException e) {
                m3.appendReplacement(sb3, "#888888");
            }
        }
        m3.appendTail(sb3);
        return sb3.toString();
    }

    private static String hslToHex(double h, double s, double l) {
        h = ((h % 360) + 360) % 360;
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double r, g, b;
        if (h < 60)      { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else               { r = c; g = 0; b = x; }
        int ri = clamp((int) Math.round((r + m) * 255));
        int gi = clamp((int) Math.round((g + m) * 255));
        int bi = clamp((int) Math.round((b + m) * 255));
        return String.format("#%02x%02x%02x", ri, gi, bi);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — recalculate viewBox from element attributes
    // ═══════════════════════════════════════════════════════════

    /**
     * Scans all SVG elements for positional attributes (x, y, width, height,
     * x1, y1, x2, y2, cx, cy, r, rx, ry) and recalculates the root SVG
     * viewBox to encompass all content.
     * <p>
     * The new viewBox is the <b>union</b> of the scanned element bounds
     * (with padding) and Mermaid's original viewBox — this ensures content
     * is never clipped, even when elements extend beyond Mermaid's own
     * viewBox calculation.
     */
    private static void fixViewBoxFromAttributes(Document doc) {
        Element svgRoot = doc.getDocumentElement();
        if (svgRoot == null) return;

        // Parse Mermaid's original viewBox — it is always our baseline.
        // Mermaid computes its viewBox from its own layout engine, which has
        // much better knowledge of the diagram geometry than our post-processor.
        String currentVb = svgRoot.getAttribute("viewBox");
        double cvbX = 0, cvbY = 0, cvbW = 0, cvbH = 0;
        boolean hasMermaidVb = false;
        if (currentVb != null && !currentVb.isEmpty()) {
            String[] parts = currentVb.trim().split("\\s+");
            if (parts.length == 4) {
                try {
                    cvbX = Double.parseDouble(parts[0]);
                    cvbY = Double.parseDouble(parts[1]);
                    cvbW = Double.parseDouble(parts[2]);
                    cvbH = Double.parseDouble(parts[3]);
                    hasMermaidVb = cvbW > 0 && cvbH > 0;
                } catch (NumberFormatException ignored) {}
            }
        }

        if (hasMermaidVb) {
            // Mermaid set a viewBox — TRUST it.
            // Only apply setDimensions() to scale to our target resolution.
            // Do NOT re-scan elements with rough heuristics (len*9 for text etc.)
            // which would inflate the viewBox and make the actual content appear tiny.
            setDimensions(svgRoot);
            return;
        }

        // No Mermaid viewBox — compute bounds from scratch by scanning all elements
        _minX = Double.MAX_VALUE;  _minY = Double.MAX_VALUE;
        _maxX = -Double.MAX_VALUE; _maxY = -Double.MAX_VALUE;
        boolean found = false;

        // Scan all elements to find content bounds
        NodeList all = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (!(n instanceof Element)) continue;
            Element el = (Element) n;
            String tag = el.getLocalName();

            // Skip markers and defs children — their coords are local
            if ("marker".equals(tag) || "defs".equals(tag) || "symbol".equals(tag)) continue;
            if (isInsideTag(el, "marker") || isInsideTag(el, "defs")) continue;

            // rect: x, y, width, height
            if ("rect".equals(tag)) {
                double x = parseDouble(el, "x", 0);
                double y = parseDouble(el, "y", 0);
                double w = parseDouble(el, "width", 0);
                double h = parseDouble(el, "height", 0);
                if (w > 0 && h > 0) {
                    double[] abs = resolveAbsolutePosition(el, x, y);
                    updateBounds(abs[0], abs[1], abs[0] + w, abs[1] + h);
                    found = true;
                }
            }
            // polygon: parse points, apply own transform, resolve parents
            else if ("polygon".equals(tag)) {
                String points = el.getAttribute("points");
                if (points != null && !points.isEmpty()) {
                    double[] localOff = parseElementTranslate(el);
                    String[] pairs = points.trim().split("\\s+");
                    for (String pair : pairs) {
                        String[] xy = pair.split(",");
                        if (xy.length == 2) {
                            try {
                                double px = Double.parseDouble(xy[0].trim()) + localOff[0];
                                double py = Double.parseDouble(xy[1].trim()) + localOff[1];
                                double[] abs = resolveAbsolutePosition(el, px, py);
                                updateBounds(abs[0], abs[1], abs[0], abs[1]);
                                found = true;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
            // line: x1, y1, x2, y2
            else if ("line".equals(tag)) {
                double x1 = parseDouble(el, "x1", 0);
                double y1 = parseDouble(el, "y1", 0);
                double x2 = parseDouble(el, "x2", 0);
                double y2 = parseDouble(el, "y2", 0);
                double[] abs1 = resolveAbsolutePosition(el, Math.min(x1, x2), Math.min(y1, y2));
                double[] abs2 = resolveAbsolutePosition(el, Math.max(x1, x2), Math.max(y1, y2));
                updateBounds(abs1[0], abs1[1], abs2[0], abs2[1]);
                found = true;
            }
            // circle: cx, cy, r
            else if ("circle".equals(tag)) {
                double cx = parseDouble(el, "cx", 0);
                double cy = parseDouble(el, "cy", 0);
                double r = parseDouble(el, "r", 0);
                if (r > 0) {
                    double[] abs = resolveAbsolutePosition(el, cx, cy);
                    updateBounds(abs[0] - r, abs[1] - r, abs[0] + r, abs[1] + r);
                    found = true;
                }
            }
            // ellipse: cx, cy, rx, ry
            else if ("ellipse".equals(tag)) {
                double cx = parseDouble(el, "cx", 0);
                double cy = parseDouble(el, "cy", 0);
                double rx = parseDouble(el, "rx", 0);
                double ry = parseDouble(el, "ry", 0);
                if (rx > 0 || ry > 0) {
                    double[] abs = resolveAbsolutePosition(el, cx, cy);
                    updateBounds(abs[0] - rx, abs[1] - ry, abs[0] + rx, abs[1] + ry);
                    found = true;
                }
            }
            // text: x, y — use wider estimate (avg char width ~9px)
            else if ("text".equals(tag)) {
                double x = parseDouble(el, "x", 0);
                double y = parseDouble(el, "y", 0);
                double[] abs = resolveAbsolutePosition(el, x, y);
                String content = el.getTextContent();
                int len = (content != null) ? content.trim().length() : 0;
                double tw = len * 9;  // 9px per char is closer to real rendering
                double th = 16;       // typical font height
                updateBounds(abs[0] - tw / 2, abs[1] - th, abs[0] + tw / 2, abs[1] + 4);
                found = true;
            }
            // path: parse d attribute bounding box
            else if ("path".equals(tag)) {
                String d = el.getAttribute("d");
                if (d != null && !d.isEmpty()) {
                    try {
                        double[] pathBounds = parsePathBounds(d);
                        if (pathBounds != null) {
                            double[] localOff = parseElementTranslate(el);
                            double px = pathBounds[0] + localOff[0];
                            double py = pathBounds[1] + localOff[1];
                            double px2 = pathBounds[2] + localOff[0];
                            double py2 = pathBounds[3] + localOff[1];
                            double[] abs1 = resolveAbsolutePosition(el, px, py);
                            double[] abs2 = resolveAbsolutePosition(el, px2, py2);
                            updateBounds(abs1[0], abs1[1], abs2[0], abs2[1]);
                            found = true;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (!found || _maxX <= _minX || _maxY <= _minY) {
            setDimensions(svgRoot);
            return;
        }


        // Apply padding and set the new viewBox
        double pad = 20;
        int vbX = (int) Math.floor(_minX - pad);
        int vbY = (int) Math.floor(_minY - pad);
        int vbW = (int) Math.ceil((_maxX + pad) - (_minX - pad));
        int vbH = (int) Math.ceil((_maxY + pad) - (_minY - pad));
        if (vbW < 50) vbW = 50;
        if (vbH < 50) vbH = 50;

        svgRoot.setAttribute("viewBox", vbX + " " + vbY + " " + vbW + " " + vbH);
        setDimensions(svgRoot);
    }

    /**
     * Target pixel count for the SVG's <b>larger</b> dimension.
     * The smaller dimension is calculated proportionally from the viewBox
     * aspect ratio.  2 000 px gives crisp rendering even when zoomed in,
     * while still being reasonable for rasterisation performance.
     */
    private static final double TARGET_MAX_DIMENSION_PX = 2000.0;

    /**
     * Set both {@code width} and {@code height} on the root {@code <svg>}
     * element so that the <b>larger</b> viewBox dimension maps to
     * {@link #TARGET_MAX_DIMENSION_PX} pixels, and the smaller dimension
     * is scaled proportionally.
     * <p>
     * This ensures that Batik (and any other SVG rasteriser) renders the
     * image at a consistently high resolution regardless of whether the
     * diagram is landscape or portrait.
     */
    private static void setDimensions(Element svgRoot) {
        String vb = svgRoot.getAttribute("viewBox");
        if (vb == null || vb.isEmpty()) return;
        String[] parts = vb.trim().split("\\s+");
        if (parts.length < 4) return;
        try {
            double vbW = Double.parseDouble(parts[2]);
            double vbH = Double.parseDouble(parts[3]);
            if (vbW <= 0 || vbH <= 0) return;

            double maxDim = Math.max(vbW, vbH);
            double scale = TARGET_MAX_DIMENSION_PX / maxDim;

            int pixelWidth  = Math.max((int) Math.ceil(vbW * scale), 200);
            int pixelHeight = Math.max((int) Math.ceil(vbH * scale), 200);

            svgRoot.setAttribute("width",  String.valueOf(pixelWidth));
            svgRoot.setAttribute("height", String.valueOf(pixelHeight));
        } catch (NumberFormatException ignored) {}
    }

    // Thread-local bounds accumulators (avoid passing state through every call)
    private static double _minX, _minY, _maxX, _maxY;

    private static void updateBounds(double x1, double y1, double x2, double y2) {
        if (x1 < _minX) _minX = x1;
        if (y1 < _minY) _minY = y1;
        if (x2 > _maxX) _maxX = x2;
        if (y2 > _maxY) _maxY = y2;
    }

    private static double parseDouble(Element el, String attrName, double defaultVal) {
        String v = el.getAttribute(attrName);
        if (v == null || v.isEmpty()) return defaultVal;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    /**
     * Resolve an element's (x,y) to absolute coordinates by walking up
     * the parent chain and accumulating translate transforms.
     */
    private static double[] resolveAbsolutePosition(Element el, double x, double y) {
        Node parent = el.getParentNode();
        while (parent instanceof Element) {
            Element pe = (Element) parent;
            String transform = pe.getAttribute("transform");
            if (transform != null && transform.contains("translate")) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("translate\\(\\s*(-?[\\d.]+)\\s*[,\\s]\\s*(-?[\\d.]+)\\s*\\)")
                        .matcher(transform);
                if (m.find()) {
                    try {
                        x += Double.parseDouble(m.group(1));
                        y += Double.parseDouble(m.group(2));
                    } catch (NumberFormatException ignored) {}
                }
            }
            parent = pe.getParentNode();
        }
        return new double[]{x, y};
    }

    /**
     * Parse a {@code translate(dx, dy)} from an element's <b>own</b>
     * {@code transform} attribute.  Returns {@code {dx, dy}} or
     * {@code {0, 0}} if no translate is found.
     * <p>
     * This is needed for elements like {@code <polygon>} where Mermaid
     * places a {@code transform} directly on the shape element, not only
     * on the parent {@code <g>}.
     */
    private static double[] parseElementTranslate(Element el) {
        String transform = el.getAttribute("transform");
        if (transform != null && transform.contains("translate")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("translate\\(\\s*(-?[\\d.]+)\\s*[,\\s]\\s*(-?[\\d.]+)\\s*\\)")
                    .matcher(transform);
            if (m.find()) {
                try {
                    return new double[]{
                            Double.parseDouble(m.group(1)),
                            Double.parseDouble(m.group(2))
                    };
                } catch (NumberFormatException ignored) {}
            }
        }
        return new double[]{0, 0};
    }

    private static boolean isInsideTag(Element el, String tagName) {
        Node parent = el.getParentNode();
        while (parent instanceof Element) {
            if (tagName.equals(((Element) parent).getLocalName())) return true;
            parent = parent.getParentNode();
        }
        return false;
    }

    /**
     * Parse an SVG path "d" attribute and return its bounding box as
     * {@code [minX, minY, maxX, maxY]}, or {@code null} if parsing fails.
     * Handles M, L, H, V, C, S, Q, T, A, Z commands (absolute and relative).
     */
    private static double[] parsePathBounds(String d) {
        if (d == null || d.isEmpty()) return null;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double curX = 0, curY = 0, startX = 0, startY = 0;
        boolean found = false;

        java.util.regex.Matcher tokenizer = java.util.regex.Pattern
                .compile("[MmLlHhVvCcSsQqTtAaZz][^MmLlHhVvCcSsQqTtAaZz]*")
                .matcher(d);
        while (tokenizer.find()) {
            String token = tokenizer.group().trim();
            char cmd = token.charAt(0);
            boolean isRel = Character.isLowerCase(cmd);
            char CMD = Character.toUpperCase(cmd);
            java.util.regex.Matcher numMatcher = java.util.regex.Pattern
                    .compile("-?[\\d]+(?:\\.[\\d]*)?(?:[eE][+-]?[\\d]+)?")
                    .matcher(token.substring(1));
            java.util.List<Double> vals = new java.util.ArrayList<Double>();
            while (numMatcher.find()) vals.add(Double.parseDouble(numMatcher.group()));

            switch (CMD) {
                case 'M':
                    for (int k = 0; k + 1 < vals.size(); k += 2) {
                        double mx = isRel ? curX + vals.get(k) : vals.get(k);
                        double my = isRel ? curY + vals.get(k + 1) : vals.get(k + 1);
                        if (mx < minX) minX = mx; if (mx > maxX) maxX = mx;
                        if (my < minY) minY = my; if (my > maxY) maxY = my;
                        curX = mx; curY = my; found = true;
                        if (k == 0) { startX = mx; startY = my; }
                    }
                    break;
                case 'L': case 'T':
                    for (int k = 0; k + 1 < vals.size(); k += 2) {
                        double lx = isRel ? curX + vals.get(k) : vals.get(k);
                        double ly = isRel ? curY + vals.get(k + 1) : vals.get(k + 1);
                        if (lx < minX) minX = lx; if (lx > maxX) maxX = lx;
                        if (ly < minY) minY = ly; if (ly > maxY) maxY = ly;
                        curX = lx; curY = ly; found = true;
                    }
                    break;
                case 'H':
                    for (int k = 0; k < vals.size(); k++) {
                        double hx = isRel ? curX + vals.get(k) : vals.get(k);
                        if (hx < minX) minX = hx; if (hx > maxX) maxX = hx;
                        if (curY < minY) minY = curY; if (curY > maxY) maxY = curY;
                        curX = hx; found = true;
                    }
                    break;
                case 'V':
                    for (int k = 0; k < vals.size(); k++) {
                        double vy = isRel ? curY + vals.get(k) : vals.get(k);
                        if (curX < minX) minX = curX; if (curX > maxX) maxX = curX;
                        if (vy < minY) minY = vy; if (vy > maxY) maxY = vy;
                        curY = vy; found = true;
                    }
                    break;
                case 'C':
                    for (int k = 0; k + 5 < vals.size(); k += 6) {
                        for (int p = 0; p < 3; p++) {
                            double cx = isRel ? curX + vals.get(k + p * 2) : vals.get(k + p * 2);
                            double cy = isRel ? curY + vals.get(k + p * 2 + 1) : vals.get(k + p * 2 + 1);
                            if (cx < minX) minX = cx; if (cx > maxX) maxX = cx;
                            if (cy < minY) minY = cy; if (cy > maxY) maxY = cy;
                        }
                        curX = isRel ? curX + vals.get(k + 4) : vals.get(k + 4);
                        curY = isRel ? curY + vals.get(k + 5) : vals.get(k + 5);
                        found = true;
                    }
                    break;
                case 'A':
                    for (int k = 0; k + 6 < vals.size(); k += 7) {
                        double ax = isRel ? curX + vals.get(k + 5) : vals.get(k + 5);
                        double ay = isRel ? curY + vals.get(k + 6) : vals.get(k + 6);
                        if (ax < minX) minX = ax; if (ax > maxX) maxX = ax;
                        if (ay < minY) minY = ay; if (ay > maxY) maxY = ay;
                        curX = ax; curY = ay; found = true;
                    }
                    break;
                case 'Z':
                    curX = startX; curY = startY;
                    break;
            }
        }
        if (!found || minX > maxX) return null;
        return new double[]{minX, minY, maxX, maxY};
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — remove alignment-baseline (Batik incompatible)
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid 11 uses {@code alignment-baseline="central"} on text elements.
     * Batik's CSS engine rejects "central" as an invalid value for this
     * property.  Remove the attribute entirely (Batik handles text
     * centering via {@code dy} and {@code dominant-baseline} which we set
     * in other fix methods).
     * <p>
     * Also strips {@code alignment-baseline} and {@code dominant-baseline}
     * from inline {@code style} attributes and XML attributes.
     * <p>
     * Additionally cleans up empty/broken {@code style} attributes
     * (e.g. {@code style=";;;"}) that confuse Batik's CSS parser.
     */
    private static void fixAlignmentBaseline(Document doc) {
        NodeList all = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (!(n instanceof Element)) continue;
            Element el = (Element) n;
            // Remove alignment-baseline XML attribute
            if (el.hasAttribute("alignment-baseline")) {
                el.removeAttribute("alignment-baseline");
            }
            // Remove dominant-baseline XML attribute — Batik may choke on
            // values like "central", "middle" that are CSS Inline Layout 3 values
            if (el.hasAttribute("dominant-baseline")) {
                el.removeAttribute("dominant-baseline");
                // Add dy="0.35em" to text elements as replacement for
                // dominant-baseline:central vertical centering
                if ("text".equals(el.getLocalName()) && !el.hasAttribute("dy")) {
                    el.setAttribute("dy", "0.35em");
                }
            }
            // Remove from inline style and clean up empty styles
            String style = el.getAttribute("style");
            if (style != null && !style.isEmpty()) {
                if (style.contains("alignment-baseline") || style.contains("dominant-baseline")) {
                    style = style.replaceAll("alignment-baseline\\s*:\\s*[^;]+;?\\s*", "");
                    style = style.replaceAll("dominant-baseline\\s*:\\s*[^;]+;?\\s*", "");
                }
                // Clean up empty/broken styles: remove sequences of only semicolons/whitespace
                style = style.replaceAll("^[;\\s]+$", "").trim();
                if (style.isEmpty()) {
                    el.removeAttribute("style");
                } else {
                    el.setAttribute("style", style);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — extend sequence-diagram lifelines to bottom actor boxes
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid's sequence-diagram lifelines may be too short when the
     * browser shim's {@code getBBox()} returns approximate values.
     * This fix finds the bottom actor boxes and extends lifeline
     * {@code y2} so they reach the top of those boxes.
     * It also adjusts {@code y1} to start at the bottom of the top boxes.
     */
    private static void fixSequenceLifelines(Document doc) {
        // Detect sequence diagram by looking for actor lines
        NodeList lines = doc.getElementsByTagNameNS("*", "line");
        List<Element> lifelines = new ArrayList<Element>();
        for (int i = 0; i < lines.getLength(); i++) {
            Node n = lines.item(i);
            if (!(n instanceof Element)) continue;
            Element line = (Element) n;
            String id = attr(line, "id");
            // Mermaid sequence diagrams use id="actor0", "actor1", etc.
            if (id.matches("actor\\d+")) {
                lifelines.add(line);
            }
        }
        if (lifelines.isEmpty()) return;

        // Collect all actor rects — find the highest y (bottom actor boxes)
        // and the lowest y (top actor boxes)
        NodeList rects = doc.getElementsByTagNameNS("*", "rect");
        double topBoxBottom = 0;
        double bottomBoxTop = Double.MAX_VALUE;
        boolean foundBottom = false;

        // First pass: find all actor rects and their y positions
        List<double[]> actorRectYH = new ArrayList<double[]>();
        for (int i = 0; i < rects.getLength(); i++) {
            Node n = rects.item(i);
            if (!(n instanceof Element)) continue;
            Element rect = (Element) n;
            if (!attr(rect, "class").contains("actor")) continue;

            try {
                double y = Double.parseDouble(rect.getAttribute("y"));
                double h = Double.parseDouble(rect.getAttribute("height"));
                actorRectYH.add(new double[]{y, h});
            } catch (NumberFormatException ignored) {}
        }
        if (actorRectYH.size() < 2) return;

        // Determine top-box bottom and bottom-box top
        // Top boxes have the lowest y, bottom boxes have the highest y
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        for (double[] yh : actorRectYH) {
            if (yh[0] < minY) minY = yh[0];
            if (yh[0] > maxY) maxY = yh[0];
        }
        // If all boxes are at the same y, nothing to fix
        if (Math.abs(maxY - minY) < 1.0) return;

        // Top box bottom = minY + height of a top box
        for (double[] yh : actorRectYH) {
            if (Math.abs(yh[0] - minY) < 1.0) {
                topBoxBottom = Math.max(topBoxBottom, yh[0] + yh[1]);
            }
            if (Math.abs(yh[0] - maxY) < 1.0) {
                bottomBoxTop = Math.min(bottomBoxTop, yh[0]);
            }
        }

        // Extend all lifelines
        for (Element line : lifelines) {
            try {
                double y1 = Double.parseDouble(line.getAttribute("y1"));
                double y2 = Double.parseDouble(line.getAttribute("y2"));
                // Extend y2 to reach the top of the bottom actor box
                if (y2 < bottomBoxTop) {
                    line.setAttribute("y2", String.valueOf(bottomBoxTop));
                }
                // Optionally adjust y1 to start at the bottom of the top box
                if (y1 < topBoxBottom) {
                    line.setAttribute("y1", String.valueOf(topBoxBottom));
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix — inject missing sequence diagram overlays
    // ═══════════════════════════════════════════════════════════

    /**
     * GraalJS's headless Mermaid rendering may not produce structural overlay
     * elements for sequence diagrams: loop boxes, alt/else blocks, notes, and
     * activation boxes.  This method parses the original Mermaid source to
     * identify these constructs and injects the appropriate SVG elements
     * (rect + text) at the correct positions derived from the existing
     * message line coordinates.
     *
     * @param doc           the SVG DOM
     * @param mermaidSource the original Mermaid diagram source
     */
    private static void injectSequenceOverlays(Document doc, String mermaidSource) {
        if (mermaidSource == null || mermaidSource.isEmpty()) return;

        // Only apply to sequence diagrams
        String trimmed = mermaidSource.trim();
        if (!trimmed.startsWith("sequenceDiagram")) return;

        // Check if overlays already exist (Mermaid generated them)
        NodeList allElements = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element el = (Element) allElements.item(i);
            String cls = attr(el, "class");
            if (cls.contains("loopLine") || cls.contains("loopText")
                    || cls.contains("labelBox") || cls.contains("note ")) {
                return; // Overlays already present — don't double-inject
            }
        }

        // Collect message line elements in document order
        // Each message is a <line class="messageLine0|messageLine1"> element
        NodeList lines = doc.getElementsByTagNameNS("*", "line");
        List<Element> messageLines = new ArrayList<Element>();
        for (int i = 0; i < lines.getLength(); i++) {
            Element line = (Element) lines.item(i);
            String cls = attr(line, "class");
            if (cls.contains("messageLine")) {
                messageLines.add(line);
            }
        }
        if (messageLines.isEmpty()) return;

        // Collect actor lifeline positions (x-coordinates per actor name)
        // From <rect class="actor" name="ActorName">
        java.util.Map<String, Double> actorX = new java.util.LinkedHashMap<String, Double>();
        NodeList rects = doc.getElementsByTagNameNS("*", "rect");
        for (int i = 0; i < rects.getLength(); i++) {
            Element rect = (Element) rects.item(i);
            String cls = attr(rect, "class");
            if (!cls.contains("actor")) continue;
            String name = attr(rect, "name");
            if (name.isEmpty()) continue;
            if (actorX.containsKey(name)) continue; // keep first occurrence
            double x = parseDouble(rect, "x", 0);
            double w = parseDouble(rect, "width", 150);
            actorX.put(name, x + w / 2.0); // center of actor box
        }

        // Determine leftmost and rightmost actor positions
        double leftMostX = Double.MAX_VALUE, rightMostX = -Double.MAX_VALUE;
        for (double ax : actorX.values()) {
            if (ax < leftMostX) leftMostX = ax;
            if (ax > rightMostX) rightMostX = ax;
        }
        // Fallback if no actors found
        if (leftMostX == Double.MAX_VALUE) {
            leftMostX = 50;
            rightMostX = 500;
        }

        // Parse the Mermaid source to find overlay constructs and map them
        // to message indices
        String[] srcLines = mermaidSource.split("\\n");
        int messageIdx = 0;

        // Find the SVG root to append overlay elements
        Element svgRoot = doc.getDocumentElement();

        // Overlay style constants
        String overlayStroke = "#dacef3";
        String overlayFill = "#dacef3";
        String noteFill = "#fff5ad";
        String noteStroke = "#aaaa33";
        String activationFill = "#f4f4f4";
        String activationStroke = "#666";
        String textFill = "black";
        String fontStyle = "font-family: 'trebuchet ms', verdana, arial, sans-serif; font-size: 12px;";

        // Track overlay block nesting (loop, alt, else, opt, par, critical, break, rect)
        List<int[]> overlayBlocks = new ArrayList<int[]>(); // {startMsgIdx, endMsgIdx, type}
        // type: 0=loop, 1=alt, 2=opt/par/critical/break/rect
        List<String> overlayLabels = new ArrayList<String>();
        List<int[]> elsePositions = new ArrayList<int[]>(); // {blockIndex, messageIdx}
        List<String> elseLabels = new ArrayList<String>();
        List<Object[]> notes = new ArrayList<Object[]>(); // {messageIdx, side, actorName, text}
        List<Object[]> activations = new ArrayList<Object[]>(); // {startMsgIdx, endMsgIdx, actorName}

        // Stack for tracking nested blocks: {startMsgIdx, type, label}
        List<Object[]> blockStack = new ArrayList<Object[]>();

        // Track activations
        java.util.Map<String, Integer> activeActors = new java.util.LinkedHashMap<String, Integer>(); // actor→startMsgIdx

        for (String srcLine : srcLines) {
            String trimLine = srcLine.trim();

            // Skip empty lines and participant declarations
            if (trimLine.isEmpty() || trimLine.startsWith("sequenceDiagram")
                    || trimLine.startsWith("participant") || trimLine.startsWith("actor")
                    || trimLine.startsWith("autonumber") || trimLine.startsWith("%%")) {
                continue;
            }

            // Loop/Alt/Opt/Par/Critical/Break/Rect blocks
            if (trimLine.matches("(?i)loop\\s+.*")) {
                String label = trimLine.substring(4).trim();
                blockStack.add(new Object[]{messageIdx, 0, label});
                continue;
            }
            if (trimLine.matches("(?i)alt\\s+.*")) {
                String label = trimLine.substring(3).trim();
                blockStack.add(new Object[]{messageIdx, 1, label});
                continue;
            }
            if (trimLine.matches("(?i)(?:opt|par|critical|break|rect)\\s+.*")) {
                int spaceIdx = trimLine.indexOf(' ');
                String label = trimLine.substring(spaceIdx + 1).trim();
                blockStack.add(new Object[]{messageIdx, 2, label});
                continue;
            }
            if (trimLine.matches("(?i)else\\s*.*")) {
                // Record else position within the current alt block
                if (!blockStack.isEmpty()) {
                    int blockIdx = overlayBlocks.size() + blockStack.size() - 1;
                    elsePositions.add(new int[]{blockIdx, messageIdx});
                    String elseLabel = trimLine.length() > 4 ? trimLine.substring(4).trim() : "";
                    elseLabels.add(elseLabel);
                }
                continue;
            }
            if (trimLine.equalsIgnoreCase("end")) {
                if (!blockStack.isEmpty()) {
                    Object[] block = blockStack.remove(blockStack.size() - 1);
                    int startIdx = (Integer) block[0];
                    int type = (Integer) block[1];
                    String label = (String) block[2];
                    overlayBlocks.add(new int[]{startIdx, messageIdx - 1, type});
                    overlayLabels.add(label);
                }
                continue;
            }

            // Note
            if (trimLine.matches("(?i)Note\\s+(?:right|left)\\s+of\\s+.*")) {
                java.util.regex.Matcher nm = java.util.regex.Pattern
                        .compile("(?i)Note\\s+(right|left)\\s+of\\s+(\\w+)\\s*:\\s*(.*)")
                        .matcher(trimLine);
                if (nm.find()) {
                    String side = nm.group(1).toLowerCase();
                    String actorName = nm.group(2);
                    String noteText = nm.group(3).trim();
                    notes.add(new Object[]{messageIdx, side, actorName, noteText});
                }
                continue;
            }
            if (trimLine.matches("(?i)Note\\s+over\\s+.*")) {
                // Note over Actor1, Actor2: text
                java.util.regex.Matcher nm = java.util.regex.Pattern
                        .compile("(?i)Note\\s+over\\s+([^:]+):\\s*(.*)")
                        .matcher(trimLine);
                if (nm.find()) {
                    String actors = nm.group(1).trim();
                    String noteText = nm.group(2).trim();
                    String firstActor = actors.contains(",") ? actors.split(",")[0].trim() : actors;
                    notes.add(new Object[]{messageIdx, "over", firstActor, noteText});
                }
                continue;
            }

            // Message lines (arrows): detect activation markers (+/-)
            if (trimLine.contains("->>") || trimLine.contains("-->>")
                    || trimLine.contains("-x") || trimLine.contains("-)")) {
                // Check for activation: ->>+Actor or -->>-Actor
                if (trimLine.contains("->>+") || trimLine.contains("-->>+")) {
                    // Find target actor (after : or after the arrow)
                    String target = extractTargetActor(trimLine);
                    if (target != null && !activeActors.containsKey(target)) {
                        activeActors.put(target, messageIdx);
                    }
                }
                if (trimLine.contains("->>-") || trimLine.contains("-->>-")) {
                    String target = extractTargetActor(trimLine);
                    if (target != null && activeActors.containsKey(target)) {
                        int startIdx = activeActors.remove(target);
                        activations.add(new Object[]{startIdx, messageIdx, target});
                    }
                }

                // Count this as a message
                if (messageIdx < messageLines.size()) {
                    messageIdx++;
                }
            }
        }

        // Now inject the overlay elements

        // Helper: get Y position of a message line (use y1 attribute)
        // If msgIdx is out of bounds, extrapolate from last known position
        double lastLineY = 0;
        for (int i = 0; i < messageLines.size(); i++) {
            double y = parseDouble(messageLines.get(i), "y1", 0);
            if (y > lastLineY) lastLineY = y;
        }

        // 1) Inject loop/alt/opt blocks
        for (int b = 0; b < overlayBlocks.size(); b++) {
            int[] block = overlayBlocks.get(b);
            int startMsgIdx = block[0];
            int endMsgIdx = block[1];
            int type = block[2];
            String label = overlayLabels.get(b);

            // Determine Y coordinates from message lines
            double topY;
            if (startMsgIdx > 0 && startMsgIdx - 1 < messageLines.size()) {
                topY = parseDouble(messageLines.get(startMsgIdx - 1), "y1", 0) + 10;
            } else if (startMsgIdx < messageLines.size()) {
                topY = parseDouble(messageLines.get(startMsgIdx), "y1", 0) - 30;
            } else {
                topY = 100;
            }
            double bottomY;
            if (endMsgIdx >= 0 && endMsgIdx < messageLines.size()) {
                bottomY = parseDouble(messageLines.get(endMsgIdx), "y1", 0) + 20;
            } else {
                bottomY = lastLineY + 20;
            }

            // Determine X coordinates: span from leftmost to rightmost actor
            double boxLeft = leftMostX - 80;
            double boxRight = rightMostX + 80;
            double boxWidth = boxRight - boxLeft;
            double boxHeight = bottomY - topY;
            if (boxHeight < 30) boxHeight = 30;

            // Create the overlay rectangle
            Element overlayRect = doc.createElementNS(SVG_NS, "rect");
            overlayRect.setAttribute("x", String.valueOf(Math.round(boxLeft)));
            overlayRect.setAttribute("y", String.valueOf(Math.round(topY)));
            overlayRect.setAttribute("width", String.valueOf(Math.round(boxWidth)));
            overlayRect.setAttribute("height", String.valueOf(Math.round(boxHeight)));
            overlayRect.setAttribute("fill", overlayFill);
            overlayRect.setAttribute("fill-opacity", "0.15");
            overlayRect.setAttribute("stroke", overlayStroke);
            overlayRect.setAttribute("stroke-width", "2");
            overlayRect.setAttribute("stroke-dasharray", "2,2");

            // Insert before the first message text (so it's behind messages)
            Element firstChild = null;
            NodeList svgChildren = svgRoot.getChildNodes();
            for (int i = 0; i < svgChildren.getLength(); i++) {
                if (svgChildren.item(i) instanceof Element) {
                    Element ce = (Element) svgChildren.item(i);
                    if ("text".equals(ce.getLocalName()) && attr(ce, "class").contains("messageText")) {
                        firstChild = ce;
                        break;
                    }
                }
            }
            if (firstChild != null) {
                svgRoot.insertBefore(overlayRect, firstChild);
            } else {
                svgRoot.appendChild(overlayRect);
            }

            // Create label text (type name + label) at top-left of box
            String typeLabel;
            if (type == 0) typeLabel = "loop";
            else if (type == 1) typeLabel = "alt";
            else typeLabel = "opt";
            String fullLabel = typeLabel + " [" + label + "]";

            // Label background rect
            int labelWidth = fullLabel.length() * 8 + 12;
            int labelHeight = 20;
            Element labelBg = doc.createElementNS(SVG_NS, "rect");
            labelBg.setAttribute("x", String.valueOf(Math.round(boxLeft)));
            labelBg.setAttribute("y", String.valueOf(Math.round(topY)));
            labelBg.setAttribute("width", String.valueOf(labelWidth));
            labelBg.setAttribute("height", String.valueOf(labelHeight));
            labelBg.setAttribute("fill", overlayStroke);
            labelBg.setAttribute("rx", "3");
            labelBg.setAttribute("ry", "3");
            svgRoot.appendChild(labelBg);

            // Label text
            Element labelText = doc.createElementNS(SVG_NS, "text");
            labelText.setAttribute("x", String.valueOf(Math.round(boxLeft + 6)));
            labelText.setAttribute("y", String.valueOf(Math.round(topY + labelHeight / 2.0)));
            labelText.setAttribute("dy", "0.35em");
            labelText.setAttribute("fill", textFill);
            labelText.setAttribute("style", fontStyle + " font-weight: bold;");
            labelText.setTextContent(fullLabel);
            svgRoot.appendChild(labelText);

            // Inject else dividers for alt blocks
            for (int e = 0; e < elsePositions.size(); e++) {
                int[] elsePosn = elsePositions.get(e);
                // The blockIdx in elsePositions might not directly map after
                // blocks are fully closed; use a heuristic: if this else
                // position is between the block's start and end messages
                int elseMsgIdx = elsePosn[1];
                if (elseMsgIdx >= block[0] && elseMsgIdx <= block[1] + 1) {
                    double elseY;
                    if (elseMsgIdx > 0 && elseMsgIdx - 1 < messageLines.size()) {
                        elseY = parseDouble(messageLines.get(elseMsgIdx - 1), "y1", 0) + 15;
                    } else if (elseMsgIdx < messageLines.size()) {
                        elseY = parseDouble(messageLines.get(elseMsgIdx), "y1", 0) - 15;
                    } else {
                        continue;
                    }

                    // Dashed line across the box
                    Element elseLine = doc.createElementNS(SVG_NS, "line");
                    elseLine.setAttribute("x1", String.valueOf(Math.round(boxLeft)));
                    elseLine.setAttribute("y1", String.valueOf(Math.round(elseY)));
                    elseLine.setAttribute("x2", String.valueOf(Math.round(boxRight)));
                    elseLine.setAttribute("y2", String.valueOf(Math.round(elseY)));
                    elseLine.setAttribute("stroke", overlayStroke);
                    elseLine.setAttribute("stroke-width", "1.5");
                    elseLine.setAttribute("stroke-dasharray", "3,3");
                    svgRoot.appendChild(elseLine);

                    // Else label
                    if (e < elseLabels.size() && !elseLabels.get(e).isEmpty()) {
                        Element elseText = doc.createElementNS(SVG_NS, "text");
                        elseText.setAttribute("x", String.valueOf(Math.round(boxLeft + 6)));
                        elseText.setAttribute("y", String.valueOf(Math.round(elseY + 14)));
                        elseText.setAttribute("fill", textFill);
                        elseText.setAttribute("style", fontStyle + " font-style: italic;");
                        elseText.setTextContent("[" + elseLabels.get(e) + "]");
                        svgRoot.appendChild(elseText);
                    }
                }
            }
        }

        // 2) Inject notes
        for (Object[] note : notes) {
            int msgIdx = (Integer) note[0];
            String side = (String) note[1];
            String actorName = (String) note[2];
            String noteText = (String) note[3];

            Double actX = actorX.get(actorName);
            if (actX == null) actX = rightMostX;

            // Y position: after the previous message
            double noteY;
            if (msgIdx > 0 && msgIdx - 1 < messageLines.size()) {
                noteY = parseDouble(messageLines.get(msgIdx - 1), "y1", 0) + 15;
            } else if (msgIdx < messageLines.size()) {
                noteY = parseDouble(messageLines.get(msgIdx), "y1", 0) - 25;
            } else {
                noteY = lastLineY + 15;
            }

            // X position based on side
            double noteX;
            double noteWidth = noteText.length() * 8 + 20;
            if (noteWidth < 80) noteWidth = 80;
            double noteHeight = 30;

            if ("right".equals(side)) {
                noteX = actX + 15;
            } else if ("left".equals(side)) {
                noteX = actX - noteWidth - 15;
            } else {
                noteX = actX - noteWidth / 2;
            }

            // Note background
            Element noteRect = doc.createElementNS(SVG_NS, "rect");
            noteRect.setAttribute("x", String.valueOf(Math.round(noteX)));
            noteRect.setAttribute("y", String.valueOf(Math.round(noteY)));
            noteRect.setAttribute("width", String.valueOf(Math.round(noteWidth)));
            noteRect.setAttribute("height", String.valueOf(Math.round(noteHeight)));
            noteRect.setAttribute("fill", noteFill);
            noteRect.setAttribute("stroke", noteStroke);
            noteRect.setAttribute("stroke-width", "1");
            svgRoot.appendChild(noteRect);

            // Note text
            Element noteTextEl = doc.createElementNS(SVG_NS, "text");
            noteTextEl.setAttribute("x", String.valueOf(Math.round(noteX + noteWidth / 2)));
            noteTextEl.setAttribute("y", String.valueOf(Math.round(noteY + noteHeight / 2)));
            noteTextEl.setAttribute("dy", "0.35em");
            noteTextEl.setAttribute("text-anchor", "middle");
            noteTextEl.setAttribute("fill", textFill);
            noteTextEl.setAttribute("style", fontStyle);
            noteTextEl.setTextContent(noteText);
            svgRoot.appendChild(noteTextEl);
        }

        // 3) Inject activation boxes
        for (Object[] activation : activations) {
            int startIdx = (Integer) activation[0];
            int endIdx = (Integer) activation[1];
            String actorName = (String) activation[2];

            Double actX = actorX.get(actorName);
            if (actX == null) continue;

            double startY;
            if (startIdx < messageLines.size()) {
                startY = parseDouble(messageLines.get(startIdx), "y1", 0) - 5;
            } else {
                continue;
            }
            double endY;
            if (endIdx < messageLines.size()) {
                endY = parseDouble(messageLines.get(endIdx), "y1", 0) + 5;
            } else {
                endY = lastLineY + 5;
            }

            double actWidth = 12;
            double actHeight = endY - startY;
            if (actHeight < 10) actHeight = 10;

            // Activation rectangle on the lifeline
            Element actRect = doc.createElementNS(SVG_NS, "rect");
            actRect.setAttribute("x", String.valueOf(Math.round(actX - actWidth / 2)));
            actRect.setAttribute("y", String.valueOf(Math.round(startY)));
            actRect.setAttribute("width", String.valueOf(Math.round(actWidth)));
            actRect.setAttribute("height", String.valueOf(Math.round(actHeight)));
            actRect.setAttribute("fill", activationFill);
            actRect.setAttribute("stroke", activationStroke);
            actRect.setAttribute("stroke-width", "1");

            // Insert activation rect before message texts (so messages render on top)
            Element firstMsgText = null;
            NodeList children = svgRoot.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element) {
                    Element ce = (Element) children.item(i);
                    if ("text".equals(ce.getLocalName()) && attr(ce, "class").contains("messageText")) {
                        firstMsgText = ce;
                        break;
                    }
                }
            }
            if (firstMsgText != null) {
                svgRoot.insertBefore(actRect, firstMsgText);
            } else {
                svgRoot.appendChild(actRect);
            }
        }

        LOG.info("[MermaidSvgFixup] Injected " + overlayBlocks.size() + " overlay block(s), "
                + notes.size() + " note(s), " + activations.size() + " activation(s) into sequence diagram");
    }

    /**
     * Extract the target actor name from a Mermaid sequence diagram message line.
     * Handles patterns like: {@code Alice->>+Server: text} → "Server"
     */
    private static String extractTargetActor(String line) {
        // Remove activation markers from arrow
        String normalized = line.replace("->>+", "->>").replace("->>-", "->>")
                .replace("-->>+", "-->>").replace("-->>-", "-->>");
        // Pattern: Source->>Target: text  or  Source-->>Target: text
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\w+\\s*-+>+\\s*(\\w+)")
                .matcher(normalized);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private static boolean isShapeTag(String localName) {
        return "rect".equals(localName) || "circle".equals(localName)
                || "ellipse".equals(localName) || "polygon".equals(localName)
                || "path".equals(localName);
    }

    private static String attr(Element el, String name) {
        String v = el.getAttribute(name);
        return v != null ? v : "";
    }

    private static String serialise(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.setOutputProperty(OutputKeys.INDENT, "no");
        // Tell the serialiser to wrap <style> content in CDATA sections
        // so CSS selectors containing > don't break downstream XML parsing
        t.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "style");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
