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

    private MermaidSvgFixup() {}

    /**
     * Apply all Batik-compatibility fixes to a Mermaid SVG string.
     *
     * @param svg raw SVG produced by {@link MermaidRenderer#renderToSvg(String)}
     * @return fixed SVG string, or the original string unchanged on error
     */
    public static String fixForBatik(String svg) {
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
            fixGroupZOrder(doc);           // edges paint ON TOP of nodes
            fixNodeZOrder(doc);
            fixLabelCentering(doc);
            fixEdgeStrokes(doc);
            fixEdgeLabelBackground(doc);
            fixEdgeLabelRect(doc);
            fixStrokeNoneOnLines(doc);
            fixCssFillNone(doc);
            fixCssForBatik(doc);           // hsl()→hex, strip unsupported CSS
            fixAlignmentBaseline(doc);     // remove alignment-baseline attrs
            fixSequenceText(doc);          // fix text positioning in seq diagrams
            fixSequenceLifelines(doc);     // extend lifelines to bottom actor boxes
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
     */
    private static void resetTextCentering(Element container) {
        NodeList texts = container.getElementsByTagNameNS("*", "text");
        for (int k = 0; k < texts.getLength(); k++) {
            Element text = (Element) texts.item(k);
            // Remove dominant-baseline (Batik doesn't handle "central")
            text.removeAttribute("dominant-baseline");
            // Position text baseline at y=0, then shift down 0.35em
            // so the visual centre of the glyphs aligns with y=0
            text.setAttribute("y", "0");
            text.setAttribute("dy", "0.35em");
            // Ensure horizontal centering
            text.setAttribute("text-anchor", "middle");

            // Clear y/dy on inner <tspan> elements — Mermaid sets these
            // for browser layout but they cause wrong offsets in Batik
            NodeList tspans = text.getElementsByTagNameNS("*", "tspan");
            for (int t = 0; t < tspans.getLength(); t++) {
                Element tspan = (Element) tspans.item(t);
                tspan.removeAttribute("y");
                tspan.removeAttribute("dy");
            }
        }
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

            // Ensure marker is large enough
            String mw = marker.getAttribute("markerWidth");
            String mh = marker.getAttribute("markerHeight");
            try {
                double w = mw.isEmpty() ? 0 : Double.parseDouble(mw);
                double h = mh.isEmpty() ? 0 : Double.parseDouble(mh);
                if (w < 12) marker.setAttribute("markerWidth", "12");
                if (h < 12) marker.setAttribute("markerHeight", "12");
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
                "alignment-baseline", "animation", "stroke-linecap",
                "vertical-align", "display", "overflow", "padding", "margin"
            };
            for (String prop : unsupported) {
                css = css.replaceAll(prop + "\\s*:\\s*[^;}\"]+[;]?", "");
            }

            // 5) Replace currentColor
            css = css.replace("currentColor", "#333333");

            // 6) Replace "revert" values
            css = css.replaceAll("stroke\\s*:\\s*revert\\s*;?", "");
            css = css.replaceAll("stroke-width\\s*:\\s*revert\\s*;?", "");

            // 7) Strip :root rules with CSS custom properties
            css = css.replaceAll("#mmd-\\d+\\s*:root\\s*\\{[^}]*\\}", "");

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
                String fixed = replaceHslValues(style);
                fixed = replaceRgbaValues(fixed);
                fixed = fixed.replace("currentColor", "#333333");
                if (!fixed.equals(style)) {
                    el.setAttribute("style", fixed);
                }
            }
            // Fix fill/stroke attributes with unsupported values
            String fill = el.getAttribute("fill");
            if ("currentColor".equals(fill)) {
                el.setAttribute("fill", "#333333");
            }
        }
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
     */
    static String replaceHslValues(String css) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "hsl\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)%\\s*,\\s*([\\d.]+)%\\s*\\)");
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
     * Replace all {@code rgba(r, g, b, a)} and {@code rgb(r g b / a)} occurrences with hex.
     */
    static String replaceRgbaValues(String css) {
        // rgba(r, g, b, a)
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "rgba?\\(\\s*([\\d.]+)\\s*[,\\s]\\s*([\\d.]+)\\s*[,\\s]\\s*([\\d.]+)\\s*[,/]\\s*([\\d.]+)\\s*\\)");
        java.util.regex.Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int r = (int) Double.parseDouble(m.group(1));
                int g = (int) Double.parseDouble(m.group(2));
                int b = (int) Double.parseDouble(m.group(3));
                String hex = String.format("#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
                m.appendReplacement(sb, hex);
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, "#888888");
            }
        }
        m.appendTail(sb);
        return sb.toString();
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

        // Initialise bounds accumulators
        _minX = Double.MAX_VALUE;  _minY = Double.MAX_VALUE;
        _maxX = -Double.MAX_VALUE; _maxY = -Double.MAX_VALUE;
        boolean found = false;

        // Scan all elements
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
                if (w > 0 || h > 0) {
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
            // text: x, y
            else if ("text".equals(tag)) {
                double x = parseDouble(el, "x", 0);
                double y = parseDouble(el, "y", 0);
                double[] abs = resolveAbsolutePosition(el, x, y);
                // Estimate text extent
                String content = el.getTextContent();
                int len = (content != null) ? content.trim().length() : 0;
                double tw = len * 8;
                updateBounds(abs[0] - tw / 2, abs[1] - 10, abs[0] + tw / 2, abs[1] + 10);
                found = true;
            }
        }

        if (!found || _maxX <= _minX || _maxY <= _minY) {
            // Element scanning failed — still set dimensions from existing viewBox
            setDimensions(svgRoot);
            return;
        }

        // Parse Mermaid's original viewBox (our safety boundary)
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
                    hasMermaidVb = true;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Build a viewBox that encompasses ALL scanned content.
        // If Mermaid had an original viewBox, take the UNION of both
        // so we never clip content that either source found.
        double pad = 20;
        double tightLeft  = _minX - pad;
        double tightRight = _maxX + pad;
        double tightTop   = _minY - pad;
        double tightBot   = _maxY + pad;

        double newLeft, newRight, newTop, newBot;
        if (hasMermaidVb) {
            double mRight = cvbX + cvbW;
            double mBot   = cvbY + cvbH;
            // Union: take the wider extent on each side
            newLeft  = Math.min(tightLeft,  cvbX);
            newTop   = Math.min(tightTop,   cvbY);
            newRight = Math.max(tightRight, mRight);
            newBot   = Math.max(tightBot,   mBot);
        } else {
            newLeft  = tightLeft;
            newTop   = tightTop;
            newRight = tightRight;
            newBot   = tightBot;
        }

        int vbX = (int) Math.floor(newLeft);
        int vbY = (int) Math.floor(newTop);
        int vbW = (int) Math.ceil(newRight - newLeft);
        int vbH = (int) Math.ceil(newBot - newTop);
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
     * Also strips {@code alignment-baseline} from inline {@code style} attributes.
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
            // Remove from inline style
            String style = el.getAttribute("style");
            if (style != null && style.contains("alignment-baseline")) {
                style = style.replaceAll("alignment-baseline\\s*:\\s*[^;]+;?\\s*", "");
                if (style.trim().isEmpty()) {
                    el.removeAttribute("style");
                } else {
                    el.setAttribute("style", style.trim());
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
