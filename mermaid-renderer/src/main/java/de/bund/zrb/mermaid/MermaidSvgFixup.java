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
 * Mermaid emits SVG that relies on browser-specific CSS/z-index behaviour.
 * Batik honours the SVG paint order (later siblings drawn on top), which
 * causes several problems:
 * <ol>
 *   <li><b>Text behind shapes</b> — inside each {@code <g class="node">}
 *       the label ({@code <text>}) is emitted <em>before</em> the shape
 *       ({@code <rect>}, {@code <polygon>}, …), so the filled shape covers
 *       the text.</li>
 *   <li><b>Invisible arrowheads</b> — {@code <marker>} elements are placed
 *       inside a regular {@code <g>} instead of {@code <defs>}, and their
 *       child paths lack an explicit {@code fill}.</li>
 *   <li><b>Invisible sequence-diagram lines</b> — message lines have an
 *       inline {@code stroke="none"} that overrides CSS.</li>
 * </ol>
 * Call {@link #fixForBatik(String)} to obtain a corrected SVG string.
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
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(svg.getBytes("UTF-8")));

            moveMarkersToDefs(doc);
            fixMarkerFills(doc);
            fixNodeZOrder(doc);
            fixEdgeLabelBackground(doc);
            fixStrokeNoneOnLines(doc);

            return serialise(doc);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MermaidSvgFixup] DOM processing failed, returning original SVG", e);
            return svg;
        }
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
            if (!(m instanceof Element)) continue;
            addFillToDescendants((Element) m, "#333333");
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
                if (!el.hasAttribute("fill")) {
                    el.setAttribute("fill", fill);
                }
            }
            addFillToDescendants(el, fill);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 3 — node z-order: move shape elements before labels
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
            String cls = g.getAttribute("class");
            if (cls == null || !cls.contains("node")) continue;

            List<Node> shapes = new ArrayList<Node>();
            List<Node> others = new ArrayList<Node>();

            Node child = g.getFirstChild();
            while (child != null) {
                Node next = child.getNextSibling();
                if (child instanceof Element) {
                    String tag = child.getLocalName();
                    if (isShapeElement(tag)) {
                        shapes.add(child);
                    } else {
                        others.add(child);
                    }
                } else {
                    others.add(child);
                }
                g.removeChild(child);
                child = next;
            }

            // Re-append: shapes first (bottom), then labels on top
            for (Node s : shapes) g.appendChild(s);
            for (Node o : others) g.appendChild(o);
        }
    }

    private static boolean isShapeElement(String localName) {
        return "rect".equals(localName)
                || "circle".equals(localName)
                || "ellipse".equals(localName)
                || "polygon".equals(localName)
                || "path".equals(localName);
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 4 — edge label backgrounds
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid's edge labels use {@code <rect>} with CSS-based opacity.
     * Ensure the rect has an explicit {@code fill} so the background
     * is visible in Batik.
     */
    private static void fixEdgeLabelBackground(Document doc) {
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = g.getAttribute("class");
            if (cls == null || !cls.contains("edgeLabel")) continue;

            NodeList rects = g.getElementsByTagNameNS("*", "rect");
            for (int j = 0; j < rects.getLength(); j++) {
                Element rect = (Element) rects.item(j);
                if (!rect.hasAttribute("fill")) {
                    rect.setAttribute("fill", "#e8e8e8");
                    rect.setAttribute("opacity", "0.5");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 5 — stroke="none" on sequence-diagram lines
    // ═══════════════════════════════════════════════════════════

    /**
     * Mermaid sequence diagrams emit {@code <line stroke="none">} on
     * message lines, relying on CSS to override.  Batik treats the
     * attribute as authoritative.  Replace with the correct stroke.
     */
    private static void fixStrokeNoneOnLines(Document doc) {
        NodeList lines = doc.getElementsByTagNameNS("*", "line");
        for (int i = 0; i < lines.getLength(); i++) {
            Node n = lines.item(i);
            if (!(n instanceof Element)) continue;
            Element line = (Element) n;

            String cls = line.getAttribute("class");
            String stroke = line.getAttribute("stroke");

            if ("none".equals(stroke)) {
                // messageLine0 = solid, messageLine1 = dashed
                if (cls != null && (cls.contains("messageLine") || cls.contains("actor-line"))) {
                    line.setAttribute("stroke", "#333");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Serialisation
    // ═══════════════════════════════════════════════════════════

    private static String serialise(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}

