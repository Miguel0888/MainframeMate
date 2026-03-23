package de.bund.zrb.mermaid;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import java.awt.geom.Rectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Computes accurate SVG bounding boxes using Apache Batik's GVT (Graphics Vector Toolkit) tree.
 * <p>
 * This replaces the heuristic JavaScript-side {@code _computeElementDims()} for complex
 * SVG elements (especially {@code <text>} with {@code <tspan>} children) where browser-like
 * text layout is critical for correct Mermaid diagram rendering.
 * <p>
 * <b>How it works:</b>
 * <ol>
 *   <li>The SVG fragment (e.g. a {@code <text>} element) is wrapped in a minimal
 *       {@code <svg>} document.</li>
 *   <li>Batik's DOM + Bridge infrastructure parses and builds a GVT tree with
 *       accurate text layout (using Java2D fonts).</li>
 *   <li>The root {@link GraphicsNode}'s bounds give us the accurate bounding box.</li>
 * </ol>
 * <p>
 * Results are cached (LRU, keyed by SVG fragment string) to avoid redundant Batik
 * parsing when Mermaid calls {@code getBBox()} multiple times on the same element state.
 * <p>
 * Thread safety: This class is designed to be used from a single thread
 * (the GraalJS executor thread). Not thread-safe.
 */
final class BatikBBoxService {

    private static final Logger LOG = Logger.getLogger(BatikBBoxService.class.getName());

    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    /** SVG wrapper template. The fragment is inserted at the placeholder. */
    private static final String SVG_WRAPPER_PREFIX =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\""
            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\""
            + " width=\"4000\" height=\"4000\">";
    private static final String SVG_WRAPPER_SUFFIX = "</svg>";

    /** Maximum number of cached BBox results (LRU eviction). */
    private static final int CACHE_MAX_SIZE = 512;

    /** LRU cache: SVG fragment → "x,y,w,h" result string. */
    @SuppressWarnings("serial")
    private final Map<String, String> cache = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_MAX_SIZE;
        }
    };

    /** Batik's SVG document factory (thread-confined). */
    private org.apache.batik.anim.dom.SAXSVGDocumentFactory documentFactory;

    BatikBBoxService() {
        try {
            String parser = org.apache.batik.util.XMLResourceDescriptor.getXMLParserClassName();
            documentFactory = new org.apache.batik.anim.dom.SAXSVGDocumentFactory(parser);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[BatikBBoxService] Failed to create SAXSVGDocumentFactory", e);
            documentFactory = null;
        }
    }

    /**
     * Compute the bounding box of an SVG fragment using Batik's GVT tree.
     *
     * @param svgFragment  well-formed SVG markup (e.g. {@code <text x="10" y="20">Hello</text>})
     * @return comma-separated "x,y,width,height" string, or {@code null} if computation fails
     */
    String computeBBox(String svgFragment) {
        if (svgFragment == null || svgFragment.isEmpty() || documentFactory == null) {
            return null;
        }

        // Check cache
        String cached = cache.get(svgFragment);
        if (cached != null) {
            return cached;
        }

        try {
            String result = doComputeBBox(svgFragment);
            if (result != null) {
                cache.put(svgFragment, result);
            }
            return result;
        } catch (Exception e) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[BatikBBoxService] BBox computation failed for fragment ("
                        + svgFragment.length() + " chars)", e);
            }
            return null;
        }
    }

    /**
     * Clear the BBox cache. Call this between diagram renders to free memory.
     */
    void clearCache() {
        cache.clear();
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal
    // ═══════════════════════════════════════════════════════════

    private String doComputeBBox(String svgFragment) throws Exception {
        // Sanitize the fragment for Batik compatibility
        String sanitized = sanitizeForBatik(svgFragment);

        // Wrap in a minimal SVG document
        String svgDoc = SVG_WRAPPER_PREFIX + sanitized + SVG_WRAPPER_SUFFIX;

        // Parse with namespace-aware XML parser into a Batik-compatible SVG DOM.
        // We use Batik's own DOMImplementation so that the resulting Document
        // is an SVGDocument that BridgeContext can work with.
        DOMImplementation domImpl = org.apache.batik.anim.dom.SVGDOMImplementation.getDOMImplementation();
        org.apache.batik.anim.dom.SAXSVGDocumentFactory factory =
                new org.apache.batik.anim.dom.SAXSVGDocumentFactory(
                        org.apache.batik.util.XMLResourceDescriptor.getXMLParserClassName());
        Document document = factory.createDocument(
                "http://localhost/bbox.svg",
                new java.io.StringReader(svgDoc));

        // Build GVT tree using Batik's bridge
        UserAgentAdapter userAgent = new UserAgentAdapter();
        BridgeContext bridgeContext = new BridgeContext(userAgent);
        bridgeContext.setDynamic(false);

        try {
            GVTBuilder builder = new GVTBuilder();
            GraphicsNode gvtRoot = builder.build(bridgeContext, document);

            if (gvtRoot == null) {
                return null;
            }

            // Get the geometry bounds (tight fit around actual painted content)
            Rectangle2D bounds = gvtRoot.getGeometryBounds();
            if (bounds == null) {
                // Try sensitive bounds as fallback (includes stroke width)
                bounds = gvtRoot.getSensitiveBounds();
            }
            if (bounds == null) {
                return null;
            }

            double x = bounds.getX();
            double y = bounds.getY();
            double w = bounds.getWidth();
            double h = bounds.getHeight();

            // Sanity check: ignore clearly broken results
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h)) {
                return null;
            }
            if (w <= 0 && h <= 0) {
                return null;
            }

            return formatResult(x, y, w, h);
        } finally {
            bridgeContext.dispose();
        }
    }

    /**
     * Sanitize SVG fragment for Batik's strict parser.
     * Removes/replaces CSS properties and attribute values that crash Batik.
     */
    private static String sanitizeForBatik(String svg) {
        // Remove alignment-baseline (Batik doesn't support SVG 2 value "central")
        svg = svg.replaceAll("\\s*alignment-baseline\\s*=\\s*\"[^\"]*\"", "");

        // Remove dominant-baseline attribute (can conflict with Batik)
        svg = svg.replaceAll("\\s*dominant-baseline\\s*=\\s*\"[^\"]*\"", "");

        // Strip alignment-baseline and dominant-baseline from inline styles
        svg = svg.replaceAll("alignment-baseline\\s*:\\s*[^;\"]+;?", "");
        svg = svg.replaceAll("dominant-baseline\\s*:\\s*[^;\"]+;?", "");

        // Replace hsl() color values with hex (Batik doesn't support hsl)
        svg = svg.replaceAll("hsl\\([^)]*\\)", "#333333");

        // Replace rgba() with hex
        svg = svg.replaceAll("rgba\\([^)]*\\)", "#333333");

        // Remove filter references (Batik may crash on inline filters)
        svg = svg.replaceAll("\\s*filter\\s*=\\s*\"[^\"]*\"", "");

        // Remove any empty style attributes
        svg = svg.replaceAll("\\s*style\\s*=\\s*\"\\s*\"", "");

        // Replace orient="auto-start-reverse" with orient="auto"
        svg = svg.replace("orient=\"auto-start-reverse\"", "orient=\"auto\"");

        // Replace "transparent" with "none"
        svg = svg.replace("fill:transparent", "fill:none");
        svg = svg.replace("fill=\"transparent\"", "fill=\"none\"");

        return svg;
    }

    private static String formatResult(double x, double y, double w, double h) {
        // Two decimal places — sufficient for SVG layout
        return round2(x) + "," + round2(y) + "," + round2(w) + "," + round2(h);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

