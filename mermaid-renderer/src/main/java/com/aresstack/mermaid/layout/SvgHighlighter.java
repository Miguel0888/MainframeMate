package com.aresstack.mermaid.layout;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Utility for injecting highlight overlays into rendered Mermaid SVG.
 *
 * <p>This is the bridge between the layout data ({@link RenderedDiagram}) and
 * a visual editor like <em>mermaid-designer</em>.  Instead of manipulating the
 * live DOM in a {@code WebView}, this class produces a <b>new SVG string</b>
 * with CSS-based highlight styling injected for the requested elements.
 *
 * <h3>Supported highlight modes</h3>
 * <ul>
 *   <li><b>STEADY</b> — static yellow background (for "currently editing" state)</li>
 *   <li><b>BLINK</b> — CSS animation that fades yellow→transparent over ~1.5 s
 *       (for "just added" / "just changed" feedback)</li>
 * </ul>
 *
 * <h3>Usage example (JavaFX WebView)</h3>
 * <pre>
 *   RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
 *   DiagramNode node = diagram.findNodeById("A");
 *
 *   // Highlight node A with a blinking yellow overlay
 *   String highlightedSvg = SvgHighlighter.highlight(svg, node.getSvgId(), Mode.BLINK);
 *   webEngine.loadContent(wrapInHtml(highlightedSvg));
 * </pre>
 *
 * <h3>How it works</h3>
 * The highlighter injects a {@code <style>} block into the SVG's {@code <defs>}
 * section (or creates one) that targets elements by their SVG {@code id} attribute.
 * This approach is non-destructive — the SVG structure stays intact, so
 * subsequent {@link DiagramLayoutExtractor#extract(String)} calls still work.
 */
public final class SvgHighlighter {

    /** Highlight colour — translucent yellow (like a text marker). */
    private static final String HIGHLIGHT_COLOR = "rgba(255, 255, 0, 0.45)";
    private static final String HIGHLIGHT_COLOR_STRONG = "rgba(255, 255, 0, 0.7)";

    /** Highlight mode. */
    public enum Mode {
        /** Static yellow background. */
        STEADY,
        /** Animated blink that fades out after ~1.5 s. */
        BLINK
    }

    private SvgHighlighter() {}

    // ═══════════════════════════════════════════════════════════
    //  Public API — single element
    // ═══════════════════════════════════════════════════════════

    /**
     * Highlight a single SVG element by its {@code id} attribute.
     *
     * @param svg   the original SVG markup
     * @param svgId the {@code id} attribute of the element to highlight
     *              (e.g. {@code "flowchart-A-0"} from {@link DiagramNode#getSvgId()})
     * @param mode  highlight style
     * @return modified SVG with highlight, or the original SVG if {@code svgId} is null/empty
     */
    public static String highlight(String svg, String svgId, Mode mode) {
        if (svg == null || svgId == null || svgId.isEmpty()) return svg;
        return highlight(svg, Collections.singletonList(svgId), mode);
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API — multiple elements
    // ═══════════════════════════════════════════════════════════

    /**
     * Highlight multiple SVG elements by their {@code id} attributes.
     *
     * @param svg    the original SVG markup
     * @param svgIds collection of element ids to highlight
     * @param mode   highlight style
     * @return modified SVG with highlights
     */
    public static String highlight(String svg, Collection<String> svgIds, Mode mode) {
        if (svg == null || svgIds == null || svgIds.isEmpty()) return svg;

        StringBuilder css = new StringBuilder();

        // Keyframes for blink mode
        if (mode == Mode.BLINK) {
            css.append("@keyframes mmd-highlight-blink {\n");
            css.append("  0%   { filter: drop-shadow(0 0 8px ").append(HIGHLIGHT_COLOR_STRONG).append("); }\n");
            css.append("  15%  { filter: drop-shadow(0 0 12px ").append(HIGHLIGHT_COLOR_STRONG).append("); }\n");
            css.append("  100% { filter: none; }\n");
            css.append("}\n");
            css.append("@keyframes mmd-highlight-bg-blink {\n");
            css.append("  0%   { fill: ").append(HIGHLIGHT_COLOR_STRONG).append("; opacity: 1; }\n");
            css.append("  15%  { fill: ").append(HIGHLIGHT_COLOR_STRONG).append("; opacity: 1; }\n");
            css.append("  100% { fill: transparent; opacity: 0; }\n");
            css.append("}\n");
        }

        for (String id : svgIds) {
            if (id == null || id.isEmpty()) continue;
            String escaped = escapeCssId(id);

            if (mode == Mode.BLINK) {
                // Group element: apply glow animation
                css.append("#").append(escaped).append(" {\n");
                css.append("  animation: mmd-highlight-blink 1.5s ease-out forwards;\n");
                css.append("}\n");
                // First shape child: animated yellow fill
                css.append("#").append(escaped).append(" > rect,\n");
                css.append("#").append(escaped).append(" > circle,\n");
                css.append("#").append(escaped).append(" > ellipse,\n");
                css.append("#").append(escaped).append(" > polygon,\n");
                css.append("#").append(escaped).append(" > path {\n");
                css.append("  animation: mmd-highlight-bg-blink 1.5s ease-out forwards;\n");
                css.append("}\n");
            } else {
                // STEADY: persistent yellow background
                css.append("#").append(escaped).append(" {\n");
                css.append("  filter: drop-shadow(0 0 6px ").append(HIGHLIGHT_COLOR).append(");\n");
                css.append("}\n");
                css.append("#").append(escaped).append(" > rect,\n");
                css.append("#").append(escaped).append(" > circle,\n");
                css.append("#").append(escaped).append(" > ellipse,\n");
                css.append("#").append(escaped).append(" > polygon {\n");
                css.append("  fill: ").append(HIGHLIGHT_COLOR).append(" !important;\n");
                css.append("}\n");
            }
        }

        return injectStyle(svg, css.toString());
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API — highlight from RenderedDiagram
    // ═══════════════════════════════════════════════════════════

    /**
     * Convenience: highlight a node by its logical id within a {@link RenderedDiagram}.
     *
     * @param diagram the rendered diagram (provides SVG + node lookup)
     * @param nodeId  logical node id (e.g. "A", "Customer")
     * @param mode    highlight style
     * @return highlighted SVG, or original SVG if node not found
     */
    public static String highlightNode(RenderedDiagram diagram, String nodeId, Mode mode) {
        if (diagram == null || nodeId == null) return diagram != null ? diagram.getSvg() : null;
        DiagramNode node = diagram.findNodeById(nodeId);
        if (node == null) return diagram.getSvg();
        return highlight(diagram.getSvg(), node.getSvgId(), mode);
    }

    /**
     * Convenience: highlight an edge by source and target node ids.
     *
     * @param diagram  the rendered diagram
     * @param sourceId source node logical id
     * @param targetId target node logical id
     * @param mode     highlight style
     * @return highlighted SVG, or original SVG if edge not found
     */
    public static String highlightEdge(RenderedDiagram diagram,
                                       String sourceId, String targetId,
                                       Mode mode) {
        if (diagram == null) return null;
        java.util.List<DiagramEdge> edgesBetween = diagram.findEdgesBetween(sourceId, targetId);
        if (edgesBetween.isEmpty()) return diagram.getSvg();

        java.util.List<String> ids = new java.util.ArrayList<String>();
        for (DiagramEdge e : edgesBetween) {
            // Edge paths have SVG ids in the edgePath group
            if (e.getId() != null && !e.getId().isEmpty()) {
                // The SVG id of edge groups is typically "L-Source-Target-0"
                // We need to find it in the SVG
                ids.add(e.getId());
            }
        }
        // Fallback: try to match by edge path pattern L-src-tgt
        if (ids.isEmpty()) return diagram.getSvg();

        return highlight(diagram.getSvg(), ids, mode);
    }

    /**
     * Remove all previously injected highlight styles from the SVG.
     * This is useful when transitioning from one highlighted state to another.
     *
     * @param svg the SVG (possibly with injected styles)
     * @return clean SVG without highlight styles
     */
    public static String clearHighlights(String svg) {
        if (svg == null) return null;
        // Remove our injected style block (identified by the marker comment)
        // Use [\s\S] instead of . to match across newlines
        return svg.replaceAll(
                "<!--mmd-highlight-start-->[\\s\\S]*?<!--mmd-highlight-end-->",
                ""
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  JavaScript snippet generators (for WebView integration)
    // ═══════════════════════════════════════════════════════════

    /**
     * Generate a JavaScript snippet that highlights elements in a live SVG DOM.
     * This is an alternative to SVG-string manipulation — inject this script
     * into a {@code WebView} / {@code WebEngine} after the SVG is loaded.
     *
     * @param svgIds element ids to highlight
     * @param mode   highlight style
     * @return JavaScript code string (ready for {@code WebEngine.executeScript()})
     */
    public static String generateHighlightScript(Collection<String> svgIds, Mode mode) {
        StringBuilder js = new StringBuilder();

        if (mode == Mode.BLINK) {
            // Inject keyframes + apply
            js.append("(function() {\n");
            js.append("  var style = document.createElement('style');\n");
            js.append("  style.id = 'mmd-highlight-style';\n");
            js.append("  style.textContent = '");
            js.append("@keyframes mmdBlink { ");
            js.append("0% { filter: drop-shadow(0 0 8px rgba(255,255,0,0.7)); } ");
            js.append("15% { filter: drop-shadow(0 0 12px rgba(255,255,0,0.7)); } ");
            js.append("100% { filter: none; } ");
            js.append("}';\n");
            js.append("  var old = document.getElementById('mmd-highlight-style');\n");
            js.append("  if (old) old.remove();\n");
            js.append("  document.head.appendChild(style);\n");
            for (String id : svgIds) {
                js.append("  var el = document.getElementById('").append(escapeJs(id)).append("');\n");
                js.append("  if (el) el.style.animation = 'mmdBlink 1.5s ease-out forwards';\n");
            }
            js.append("})();\n");
        } else {
            js.append("(function() {\n");
            for (String id : svgIds) {
                js.append("  var el = document.getElementById('").append(escapeJs(id)).append("');\n");
                js.append("  if (el) el.style.filter = 'drop-shadow(0 0 6px rgba(255,255,0,0.45))';\n");
            }
            js.append("})();\n");
        }

        return js.toString();
    }

    /**
     * Generate a JavaScript snippet that clears all highlights.
     */
    public static String generateClearScript() {
        return "(function() {\n"
                + "  var style = document.getElementById('mmd-highlight-style');\n"
                + "  if (style) style.remove();\n"
                + "  document.querySelectorAll('[style*=\"animation\"]').forEach(function(el) {\n"
                + "    el.style.animation = '';\n"
                + "    el.style.filter = '';\n"
                + "  });\n"
                + "})();\n";
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Inject a {@code <style>} block into the SVG, wrapped in marker comments
     * so it can be cleanly removed by {@link #clearHighlights(String)}.
     */
    private static String injectStyle(String svg, String css) {
        String styleBlock = "<!--mmd-highlight-start--><style type=\"text/css\">\n"
                + css
                + "</style><!--mmd-highlight-end-->";

        // First: try to insert inside existing <defs>...</defs>
        int defsEnd = svg.indexOf("</defs>");
        if (defsEnd >= 0) {
            return svg.substring(0, defsEnd) + styleBlock + svg.substring(defsEnd);
        }

        // Second: try to insert right after <svg ...>
        int svgClose = svg.indexOf('>');
        if (svgClose >= 0) {
            // Make sure it's the root <svg> tag, not some other tag
            int svgStart = svg.indexOf("<svg");
            if (svgStart >= 0) {
                svgClose = svg.indexOf('>', svgStart);
                if (svgClose >= 0) {
                    return svg.substring(0, svgClose + 1)
                            + "<defs>" + styleBlock + "</defs>"
                            + svg.substring(svgClose + 1);
                }
            }
        }

        // Fallback: prepend (shouldn't happen with valid SVG)
        return styleBlock + svg;
    }

    /** Escape a CSS id selector (handles dots, colons, slashes). */
    private static String escapeCssId(String id) {
        // CSS.escape() equivalent: prefix special chars with backslash
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '.' || c == ':' || c == '[' || c == ']'
                    || c == '/' || c == '(' || c == ')' || c == '#') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Escape a string for embedding in a JS string literal. */
    private static String escapeJs(String s) {
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}

