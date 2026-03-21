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
 * causes two problems:
 * <ol>
 *   <li><b>Text behind shapes</b> — inside each {@code <g class="node">}
 *       the label ({@code <text>}) is emitted <em>before</em> the shape
 *       ({@code <rect>}, {@code <polygon>}, …), so the filled shape covers
 *       the text.</li>
 *   <li><b>Invisible arrowheads</b> — {@code <path>} elements inside
 *       {@code <marker>} definitions have no explicit {@code fill},
 *       relying on CSS class inheritance that Batik does not resolve
 *       inside marker contexts.</li>
 * </ol>
 * Call {@link #fixForBatik(String)} to obtain a corrected SVG string.
 */
public final class MermaidSvgFixup {

    private static final Logger LOG = Logger.getLogger(MermaidSvgFixup.class.getName());

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

            fixNodeZOrder(doc);
            fixMarkerFills(doc);
            fixEdgeLabelBackground(doc);

            return serialise(doc);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MermaidSvgFixup] DOM processing failed, returning original SVG", e);
            return svg;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 1 — node z-order: move shape elements before labels
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

            // Collect children into shapes vs. others
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
                    others.add(child); // whitespace text nodes etc.
                }
                g.removeChild(child);
                child = next;
            }

            // Re-append: shapes first, then labels/text on top
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
            // recurse
            addFillToDescendants(el, fill);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Fix 3 — edge label backgrounds
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

            // Find rect children and ensure fill
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

