package com.aresstack.mermaid.layout;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts layout metadata (node positions, edge geometry) from a rendered
 * Mermaid SVG document.
 *
 * <p>This extractor analyses the SVG DOM structure that Mermaid produces
 * and builds a {@link RenderedDiagram} with precise bounding boxes for
 * every node and edge.  It supports all major Mermaid diagram types:
 * flowchart, class, ER, sequence, mindmap, state, requirement.
 *
 * <h3>SVG structure patterns</h3>
 * <ul>
 *   <li><b>Flowchart nodes:</b> {@code <g class="node" id="flowchart-A-0" transform="translate(x,y)">}</li>
 *   <li><b>Class nodes:</b> {@code <g class="node" id="classId-Name-0" transform="translate(x,y)">}</li>
 *   <li><b>ER entities:</b> {@code <g class="node" id="entity-Order" transform="translate(x,y)">}</li>
 *   <li><b>Mindmap nodes:</b> {@code <g class="mindmap-node" transform="translate(x,y)">}</li>
 *   <li><b>Edges:</b> {@code <g class="edgePath" id="L-A-B-0">} with {@code <path class="flowchart-link">}</li>
 *   <li><b>Sequence messages:</b> {@code <line class="messageLine0|1">}</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   String svg = Mermaid.render("graph TD; A--&gt;B;");
 *   RenderedDiagram diagram = DiagramLayoutExtractor.extract(svg);
 * </pre>
 */
public final class DiagramLayoutExtractor {

    private static final Logger LOG = Logger.getLogger(DiagramLayoutExtractor.class.getName());

    // ── Regex patterns ──────────────────────────────────────────
    private static final Pattern TRANSLATE_PATTERN = Pattern.compile(
            "translate\\(\\s*(-?[\\d.]+)\\s*[,\\s]\\s*(-?[\\d.]+)\\s*\\)");

    /** Flowchart node id: {@code flowchart-NodeName-123} → NodeName */
    private static final Pattern FLOWCHART_ID = Pattern.compile("^flowchart-(.+?)(?:-\\d+)?$");

    /** Class diagram node id: {@code classId-ClassName-123} → ClassName */
    private static final Pattern CLASS_ID = Pattern.compile("^classId-(.+?)(?:-\\d+)?$");

    /** ER entity node id: {@code entity-EntityName} or {@code entity-EntityName-123} */
    private static final Pattern ENTITY_ID = Pattern.compile("^entity-(.+?)(?:-\\d+)?$");

    /** Edge path id: {@code L-Source-Target-0} or {@code L_Source_Target_0} (Mermaid 11+) */
    private static final Pattern EDGE_PATH_ID = Pattern.compile("^L[-_](.+?)[-_](.+?)(?:[-_]\\d+)?$");

    /** Class diagram edge id: {@code id_Source_Target_N} (Mermaid 11+).
     *  Captures the combined source_target part and the trailing index. */
    private static final Pattern CLASS_EDGE_ID = Pattern.compile("^id_(.+)_(\\d+)$");

    /** State diagram node id: {@code state-StateName-123} */
    private static final Pattern STATE_ID = Pattern.compile("^state-(.+?)(?:-\\d+)?$");

    private DiagramLayoutExtractor() {}

    // ═══════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════

    /**
     * Extract layout metadata from a rendered SVG string.
     *
     * @param svg  the SVG markup (as produced by {@code Mermaid.render()})
     * @return a {@link RenderedDiagram} with nodes, edges, and viewBox info,
     *         or {@code null} if the SVG cannot be parsed
     */
    public static RenderedDiagram extract(String svg) {
        if (svg == null || svg.isEmpty()) return null;

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(svg.getBytes("UTF-8")));

            String diagramType = detectDiagramType(doc, svg);
            double[] viewBox = parseViewBox(doc);

            List<DiagramNode> nodes;
            List<DiagramEdge> edges;

            if ("sequence".equals(diagramType)) {
                nodes = extractSequenceActors(doc);
                edges = extractSequenceMessages(doc, nodes);
            } else {
                nodes = extractNodes(doc);
                edges = extractEdges(doc, nodes);
            }

            return new RenderedDiagram(svg, diagramType, nodes, edges,
                    viewBox[0], viewBox[1], viewBox[2], viewBox[3]);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[DiagramLayoutExtractor] SVG parsing failed", e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Diagram type detection
    // ═══════════════════════════════════════════════════════════

    private static String detectDiagramType(Document doc, String svg) {
        Element root = doc.getDocumentElement();
        if (root == null) return "unknown";

        // aria-roledescription is the most reliable signal
        String roleDesc = attr(root, "aria-roledescription");
        if (!roleDesc.isEmpty()) {
            if (roleDesc.contains("flowchart")) return "flowchart";
            if (roleDesc.contains("sequence")) return "sequence";
            if (roleDesc.contains("class")) return "classDiagram";
            if (roleDesc.contains("er")) return "erDiagram";
            if (roleDesc.contains("mindmap")) return "mindmap";
            if (roleDesc.contains("state")) return "stateDiagram";
            if (roleDesc.contains("gantt")) return "gantt";
            if (roleDesc.contains("pie")) return "pie";
            if (roleDesc.contains("requirement")) return "requirement";
            if (roleDesc.contains("journey")) return "journey";
            if (roleDesc.contains("git")) return "gitGraph";
            if (roleDesc.contains("c4")) return "c4";
            if (roleDesc.contains("quadrant")) return "quadrant";
            if (roleDesc.contains("sankey")) return "sankey";
            if (roleDesc.contains("xy")) return "xyChart";
            return roleDesc;
        }

        // Fallback: heuristic detection from SVG structure
        if (svg.contains("class=\"actor\"")) return "sequence";
        if (svg.contains("id=\"classId-")) return "classDiagram";
        if (svg.contains("id=\"entity-")) return "erDiagram";
        if (svg.contains("class=\"mindmap-node")) return "mindmap";
        if (svg.contains("id=\"state-")) return "stateDiagram";
        if (svg.contains("class=\"edgePath")) return "flowchart";
        return "unknown";
    }

    // ═══════════════════════════════════════════════════════════
    //  ViewBox parsing
    // ═══════════════════════════════════════════════════════════

    private static double[] parseViewBox(Document doc) {
        Element root = doc.getDocumentElement();
        if (root == null) return new double[]{0, 0, 800, 600};

        String vb = root.getAttribute("viewBox");
        if (vb != null && !vb.isEmpty()) {
            String[] parts = vb.trim().split("\\s+");
            if (parts.length == 4) {
                try {
                    return new double[]{
                            Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3])
                    };
                } catch (NumberFormatException ignored) {}
            }
        }
        return new double[]{0, 0, 800, 600};
    }

    // ═══════════════════════════════════════════════════════════
    //  Node extraction (flowchart, class, ER, mindmap, state)
    // ═══════════════════════════════════════════════════════════

    private static List<DiagramNode> extractNodes(Document doc) {
        List<DiagramNode> result = new ArrayList<DiagramNode>();
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");

        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = attr(g, "class");
            String svgId = attr(g, "id");

            // Skip containers: "nodes", "edgePaths", "edgeLabels", "clusters", "root"
            if ("nodes".equals(cls) || "edgePaths".equals(cls) || "edgeLabels".equals(cls)
                    || "clusters".equals(cls) || "root".equals(cls)) continue;

            // ── Standard nodes: <g class="node ..."> ──
            if (cls.contains("node") && !cls.contains("mindmap-node")) {
                DiagramNode node = extractStandardNode(g, svgId, cls);
                if (node != null) result.add(node);
                continue;
            }

            // ── Mindmap nodes: <g class="mindmap-node"> ──
            if (cls.contains("mindmap-node")) {
                DiagramNode node = extractMindmapNode(g, svgId, result.size());
                if (node != null) result.add(node);
            }
        }
        return result;
    }

    private static DiagramNode extractStandardNode(Element g, String svgId, String cls) {
        // Determine kind and logical id from the SVG id
        String logicalId = svgId;
        String kind = "node";

        Matcher fm = FLOWCHART_ID.matcher(svgId);
        Matcher cm = CLASS_ID.matcher(svgId);
        Matcher em = ENTITY_ID.matcher(svgId);
        Matcher sm = STATE_ID.matcher(svgId);

        if (fm.matches()) {
            logicalId = fm.group(1);
            kind = "node";
        } else if (cm.matches()) {
            logicalId = cm.group(1);
            kind = "class";
        } else if (em.matches()) {
            logicalId = em.group(1);
            kind = "entity";
        } else if (sm.matches()) {
            logicalId = sm.group(1);
            kind = "state";
        } else if (cls.contains("requirement")) {
            kind = "requirement";
        }

        // Get the transform position (center of the node)
        double[] center = parseTranslate(g);

        // Also accumulate parent transforms
        double[] parentOffset = resolveParentTranslates(g);
        double cx = center[0] + parentOffset[0];
        double cy = center[1] + parentOffset[1];

        // Find the first shape child to determine the bounding box
        double[] shapeBounds = findShapeBounds(g);
        double hw = shapeBounds[0]; // half-width
        double hh = shapeBounds[1]; // half-height

        // If no shape found, estimate from text length
        if (hw == 0 && hh == 0) {
            String label = extractTextContent(g);
            hw = Math.max(40, label.length() * 5);
            hh = 20;
        }

        String label = extractTextContent(g);

        return new DiagramNode(
                logicalId, label, kind,
                cx - hw, cy - hh, hw * 2, hh * 2,
                svgId
        );
    }

    private static DiagramNode extractMindmapNode(Element g, String svgId, int index) {
        double[] center = parseTranslate(g);
        double[] parentOffset = resolveParentTranslates(g);
        double cx = center[0] + parentOffset[0];
        double cy = center[1] + parentOffset[1];

        double[] shapeBounds = findShapeBounds(g);
        double hw = shapeBounds[0];
        double hh = shapeBounds[1];

        String label = extractTextContent(g);
        String logicalId = label.isEmpty() ? ("mindmap-" + index) : label;

        return new DiagramNode(
                logicalId, label, "mindmap-node",
                cx - hw, cy - hh, hw * 2, hh * 2,
                svgId
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  Sequence diagram extraction
    // ═══════════════════════════════════════════════════════════

    private static List<DiagramNode> extractSequenceActors(Document doc) {
        List<DiagramNode> result = new ArrayList<DiagramNode>();

        // Actors are <rect class="actor" name="ActorName">
        // We group by name to get one DiagramNode per actor
        Map<String, double[]> actorBounds = new LinkedHashMap<String, double[]>();

        NodeList rects = doc.getElementsByTagNameNS("*", "rect");
        for (int i = 0; i < rects.getLength(); i++) {
            Node n = rects.item(i);
            if (!(n instanceof Element)) continue;
            Element rect = (Element) n;
            if (!attr(rect, "class").contains("actor")) continue;

            String name = attr(rect, "name");
            if (name.isEmpty()) continue;

            double x = parseDoubleAttr(rect, "x", 0);
            double y = parseDoubleAttr(rect, "y", 0);
            double w = parseDoubleAttr(rect, "width", 150);
            double h = parseDoubleAttr(rect, "height", 65);

            if (!actorBounds.containsKey(name)) {
                actorBounds.put(name, new double[]{x, y, w, h});
            }
            // Merge top & bottom actor boxes
        }

        for (Map.Entry<String, double[]> entry : actorBounds.entrySet()) {
            double[] b = entry.getValue();
            result.add(new DiagramNode(
                    entry.getKey(), entry.getKey(), "actor",
                    b[0], b[1], b[2], b[3],
                    "actor-" + entry.getKey()
            ));
        }
        return result;
    }

    private static List<DiagramEdge> extractSequenceMessages(Document doc, List<DiagramNode> actors) {
        List<DiagramEdge> result = new ArrayList<DiagramEdge>();

        // Build actor-name→centerX map for matching lines to actors
        Map<String, Double> actorCenterX = new LinkedHashMap<String, Double>();
        for (DiagramNode actor : actors) {
            actorCenterX.put(actor.getId(), actor.getCenterX());
        }

        // Messages are <line class="messageLine0|messageLine1">
        // Paired with <text class="messageText"> elements
        NodeList lines = doc.getElementsByTagNameNS("*", "line");
        NodeList texts = doc.getElementsByTagNameNS("*", "text");

        // Collect message texts in order
        List<String> messageTexts = new ArrayList<String>();
        for (int i = 0; i < texts.getLength(); i++) {
            Element t = (Element) texts.item(i);
            if (attr(t, "class").contains("messageText")) {
                messageTexts.add(t.getTextContent().trim());
            }
        }

        int msgIdx = 0;
        for (int i = 0; i < lines.getLength(); i++) {
            Node n = lines.item(i);
            if (!(n instanceof Element)) continue;
            Element line = (Element) n;
            if (!attr(line, "class").contains("messageLine")) continue;

            double x1 = parseDoubleAttr(line, "x1", 0);
            double y1 = parseDoubleAttr(line, "y1", 0);
            double x2 = parseDoubleAttr(line, "x2", 0);
            double y2 = parseDoubleAttr(line, "y2", 0);

            // Match source/target by closest actor center x
            String sourceId = findClosestActor(actorCenterX, x1);
            String targetId = findClosestActor(actorCenterX, x2);

            String label = (msgIdx < messageTexts.size()) ? messageTexts.get(msgIdx) : "";
            String edgeId = sourceId + "->" + targetId + (msgIdx > 0 ? "-" + msgIdx : "");

            double bx = Math.min(x1, x2);
            double by = Math.min(y1, y2);
            double bw = Math.abs(x2 - x1);
            double bh = Math.max(Math.abs(y2 - y1), 2);

            result.add(new DiagramEdge(
                    edgeId, sourceId, targetId, label,
                    "messageLine", null,
                    bx, by, bw, bh
            ));
            msgIdx++;
        }
        return result;
    }

    private static String findClosestActor(Map<String, Double> actorCenterX, double x) {
        String closest = "";
        double minDist = Double.MAX_VALUE;
        for (Map.Entry<String, Double> entry : actorCenterX.entrySet()) {
            double dist = Math.abs(entry.getValue() - x);
            if (dist < minDist) {
                minDist = dist;
                closest = entry.getKey();
            }
        }
        return closest;
    }

    // ═══════════════════════════════════════════════════════════
    //  Edge extraction (flowchart, class, ER, etc.)
    // ═══════════════════════════════════════════════════════════

    private static List<DiagramEdge> extractEdges(Document doc, List<DiagramNode> nodes) {
        List<DiagramEdge> result = new ArrayList<DiagramEdge>();

        // Build svgId → logicalId mapping for endpoint resolution
        Map<String, String> svgIdToLogicalId = new LinkedHashMap<String, String>();
        for (DiagramNode n : nodes) {
            svgIdToLogicalId.put(n.getSvgId(), n.getId());
        }

        // Collect edge labels from <g class="edgeLabels"> → <g class="edgeLabel">
        // matched by data-id attribute
        Map<String, String> edgeLabelsByDataId = collectEdgeLabels(doc);

        // ═══ Strategy 1: Mermaid 11+ flat paths with data-edge="true" ═══
        // In Mermaid 11, edges are <path> elements directly inside <g class="edgePaths">
        // with id="L_A_B_0", data-edge="true", class="... flowchart-link ..."
        NodeList allPaths = doc.getElementsByTagNameNS("*", "path");
        for (int i = 0; i < allPaths.getLength(); i++) {
            Node n = allPaths.item(i);
            if (!(n instanceof Element)) continue;
            Element path = (Element) n;

            String dataEdge = attr(path, "data-edge");
            String cls = attr(path, "class");
            String pathId = attr(path, "id");

            // Match: data-edge="true" OR (class contains "flowchart-link" AND id matches L_..._...)
            boolean isFlowchartEdge = "true".equals(dataEdge)
                    || (cls.contains("flowchart-link") && pathId.startsWith("L"));

            if (!isFlowchartEdge) continue;

            // Use data-id if available, otherwise fall back to id
            String edgeDataId = attr(path, "data-id");
            if (edgeDataId.isEmpty()) edgeDataId = pathId;

            // Parse source/target from the edge id
            String sourceId = "";
            String targetId = "";
            Matcher edgeMatcher = EDGE_PATH_ID.matcher(edgeDataId);
            if (edgeMatcher.matches()) {
                String rawSource = edgeMatcher.group(1);
                String rawTarget = edgeMatcher.group(2);
                sourceId = resolveNodeId(rawSource, svgIdToLogicalId);
                targetId = resolveNodeId(rawTarget, svgIdToLogicalId);
            } else {
                // Try class diagram pattern: id_Source_Target_N
                Matcher classEdgeMatcher = CLASS_EDGE_ID.matcher(edgeDataId);
                if (classEdgeMatcher.matches()) {
                    String combined = classEdgeMatcher.group(1); // e.g. "Kunde_Bestellung"
                    String[] resolved = resolveClassEdgeEndpoints(combined, svgIdToLogicalId);
                    if (resolved != null) {
                        sourceId = resolved[0];
                        targetId = resolved[1];
                    }
                }
            }

            // Parse path geometry
            String d = path.getAttribute("d");
            double[] pathBounds = (d != null && !d.isEmpty()) ? parsePathBounds(d) : null;

            // Apply parent transforms
            if (pathBounds != null) {
                double[] parentOffset = resolveParentTranslates(path);
                pathBounds[0] += parentOffset[0];
                pathBounds[1] += parentOffset[1];
                pathBounds[2] += parentOffset[0];
                pathBounds[3] += parentOffset[1];
            }
            if (pathBounds == null) pathBounds = new double[]{0, 0, 0, 0};

            // Look up label from edgeLabels
            String label = edgeLabelsByDataId.containsKey(edgeDataId)
                    ? edgeLabelsByDataId.get(edgeDataId)
                    : "";

            String edgeId = sourceId.isEmpty() ? edgeDataId : (sourceId + "->" + targetId);

            result.add(new DiagramEdge(
                    edgeId, sourceId, targetId,
                    label, "flowchart-link", d,
                    pathBounds[0], pathBounds[1],
                    pathBounds[2] - pathBounds[0],
                    pathBounds[3] - pathBounds[1]
            ));
        }

        // ═══ Strategy 2: Legacy <g class="edgePath"> groups ═══
        // Only if Strategy 1 found nothing (older Mermaid versions)
        if (result.isEmpty()) {
            NodeList allGs = doc.getElementsByTagNameNS("*", "g");
            for (int i = 0; i < allGs.getLength(); i++) {
                Node n = allGs.item(i);
                if (!(n instanceof Element)) continue;
                Element g = (Element) n;
                String cls = attr(g, "class");
                if (!cls.contains("edgePath")) continue;
                // Skip the container "edgePaths"
                if ("edgePaths".equals(cls.trim())) continue;

                DiagramEdge edge = extractEdgePathGroup(g, svgIdToLogicalId, edgeLabelsByDataId);
                if (edge != null) result.add(edge);
            }
        }

        // ═══ ER relationship lines ═══
        for (int i = 0; i < allPaths.getLength(); i++) {
            Node n = allPaths.item(i);
            if (!(n instanceof Element)) continue;
            Element path = (Element) n;
            String cls = attr(path, "class");
            if (!cls.contains("er") || !cls.contains("relationshipLine")) continue;

            String d = path.getAttribute("d");
            double[] bounds = (d != null && !d.isEmpty()) ? parsePathBounds(d) : null;
            if (bounds == null) bounds = new double[]{0, 0, 0, 0};

            result.add(new DiagramEdge(
                    "er-relation-" + i, "", "", "",
                    "er-link", d,
                    bounds[0], bounds[1],
                    bounds[2] - bounds[0], bounds[3] - bounds[1]
            ));
        }

        return result;
    }

    /**
     * Collect edge labels from {@code <g class="edgeLabels">} groups.
     * Returns a map from data-id → label text.
     */
    private static Map<String, String> collectEdgeLabels(Document doc) {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        NodeList allGs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < allGs.getLength(); i++) {
            Node n = allGs.item(i);
            if (!(n instanceof Element)) continue;
            Element g = (Element) n;
            String cls = attr(g, "class");
            if (!"edgeLabel".equals(cls.trim()) && !cls.startsWith("edgeLabel ")) continue;

            // Look for a child <g class="label" data-id="...">
            NodeList children = g.getElementsByTagNameNS("*", "g");
            for (int j = 0; j < children.getLength(); j++) {
                Element child = (Element) children.item(j);
                String dataId = attr(child, "data-id");
                if (!dataId.isEmpty()) {
                    String text = extractTextContent(child);
                    if (!text.isEmpty()) {
                        labels.put(dataId, text);
                    }
                }
            }
        }
        return labels;
    }

    private static DiagramEdge extractEdgePathGroup(Element g,
                                                     Map<String, String> svgIdToLogicalId,
                                                     Map<String, String> edgeLabelsByDataId) {
        String svgId = attr(g, "id");

        // Try to extract source/target from the edge id: L-Source-Target-0
        String sourceId = "";
        String targetId = "";
        Matcher edgeMatcher = EDGE_PATH_ID.matcher(svgId);
        if (edgeMatcher.matches()) {
            String rawSource = edgeMatcher.group(1);
            String rawTarget = edgeMatcher.group(2);
            // The raw values might be the full SVG ids or just the node names
            sourceId = resolveNodeId(rawSource, svgIdToLogicalId);
            targetId = resolveNodeId(rawTarget, svgIdToLogicalId);
        }

        // Find the <path> child with the edge line
        String pathData = null;
        double[] pathBounds = null;
        NodeList paths = g.getElementsByTagNameNS("*", "path");
        for (int i = 0; i < paths.getLength(); i++) {
            Element path = (Element) paths.item(i);
            String d = path.getAttribute("d");
            if (d != null && !d.isEmpty()) {
                pathData = d;
                pathBounds = parsePathBounds(d);
                break;
            }
        }

        // Find edge label from sibling edgeLabel group or inline text
        String label = extractEdgeLabel(g);
        if ((label == null || label.isEmpty()) && edgeLabelsByDataId.containsKey(svgId)) {
            label = edgeLabelsByDataId.get(svgId);
        }

        // Apply parent transforms to the path bounds
        if (pathBounds != null) {
            double[] parentOffset = resolveParentTranslates(g);
            double[] selfOffset = parseTranslate(g);
            double ox = parentOffset[0] + selfOffset[0];
            double oy = parentOffset[1] + selfOffset[1];
            pathBounds[0] += ox;
            pathBounds[1] += oy;
            pathBounds[2] += ox;
            pathBounds[3] += oy;
        }

        if (pathBounds == null) {
            pathBounds = new double[]{0, 0, 0, 0};
        }

        String edgeId = sourceId.isEmpty() ? svgId : (sourceId + "->" + targetId);
        String kind = "flowchart-link";

        return new DiagramEdge(
                edgeId, sourceId, targetId,
                label != null ? label : "",
                kind, pathData,
                pathBounds[0], pathBounds[1],
                pathBounds[2] - pathBounds[0],
                pathBounds[3] - pathBounds[1]
        );
    }

    /**
     * Resolve class diagram edge endpoints from a combined string like
     * {@code "Kunde_Bestellung"}.  Tries all underscore split positions and
     * checks whether both halves are known node IDs.
     *
     * @param combined  the combined source/target string (e.g. {@code "Kunde_Bestellung"})
     * @param svgIdToLogicalId  map from SVG id → logical node id
     * @return {@code [sourceId, targetId]} or {@code null} if unresolvable
     */
    private static String[] resolveClassEdgeEndpoints(String combined,
                                                       Map<String, String> svgIdToLogicalId) {
        // Collect all known logical node IDs
        java.util.Set<String> knownIds = new java.util.HashSet<String>(svgIdToLogicalId.values());

        // Try all underscore split positions
        for (int i = 1; i < combined.length(); i++) {
            if (combined.charAt(i) == '_') {
                String left = combined.substring(0, i);
                String right = combined.substring(i + 1);
                if (knownIds.contains(left) && knownIds.contains(right)) {
                    return new String[]{left, right};
                }
            }
        }

        // Fallback: simple split at first underscore
        int sep = combined.indexOf('_');
        if (sep > 0 && sep < combined.length() - 1) {
            return new String[]{combined.substring(0, sep), combined.substring(sep + 1)};
        }
        return null;
    }

    /**
     * Try to resolve a raw edge endpoint id (from the SVG id attribute)
     * to a logical node id.  Falls back to the raw value.
     */
    private static String resolveNodeId(String raw, Map<String, String> svgIdToLogicalId) {
        // Direct lookup
        if (svgIdToLogicalId.containsKey(raw)) return svgIdToLogicalId.get(raw);

        // Try with common prefixes
        for (Map.Entry<String, String> entry : svgIdToLogicalId.entrySet()) {
            String svgIdKey = entry.getKey();
            // Match if the SVG id contains the raw value
            if (svgIdKey.contains("-" + raw + "-") || svgIdKey.endsWith("-" + raw)) {
                return entry.getValue();
            }
        }

        // Strip flowchart- prefix if present
        if (raw.startsWith("flowchart-")) {
            return raw.substring("flowchart-".length()).replaceAll("-\\d+$", "");
        }
        return raw;
    }

    private static String extractEdgeLabel(Element edgePathG) {
        // Edge labels are typically in <g class="edgeLabels"> → <g class="edgeLabel">
        // at the same index as the edgePath.  We search for text within the edgePath
        // group first, then look at sibling edgeLabel groups.
        NodeList texts = edgePathG.getElementsByTagNameNS("*", "text");
        if (texts.getLength() > 0) {
            String content = texts.item(0).getTextContent();
            if (content != null && !content.trim().isEmpty()) {
                return content.trim();
            }
        }
        return "";
    }

    // ═══════════════════════════════════════════════════════════
    //  Geometry helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse {@code translate(dx, dy)} from an element's own transform attribute.
     * Returns {dx, dy} or {0, 0} if none found.
     */
    private static double[] parseTranslate(Element el) {
        String transform = el.getAttribute("transform");
        if (transform != null) {
            Matcher m = TRANSLATE_PATTERN.matcher(transform);
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

    /**
     * Walk up the parent chain and sum all {@code translate()} offsets.
     * Stops at the SVG root.
     */
    private static double[] resolveParentTranslates(Element el) {
        double tx = 0, ty = 0;
        Node parent = el.getParentNode();
        while (parent instanceof Element) {
            Element pe = (Element) parent;
            if ("svg".equals(pe.getLocalName())) break;
            double[] pt = parseTranslate(pe);
            tx += pt[0];
            ty += pt[1];
            parent = pe.getParentNode();
        }
        return new double[]{tx, ty};
    }

    /**
     * Find the bounding box of the first shape child (rect, circle, ellipse,
     * polygon, path).  Returns {halfWidth, halfHeight} relative to the
     * parent group's origin.
     */
    private static double[] findShapeBounds(Element g) {
        NodeList children = g.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            Element el = (Element) child;
            String tag = el.getLocalName();

            if ("rect".equals(tag)) {
                double x = parseDoubleAttr(el, "x", 0);
                double y = parseDoubleAttr(el, "y", 0);
                double w = parseDoubleAttr(el, "width", 0);
                double h = parseDoubleAttr(el, "height", 0);
                if (w > 0 && h > 0) {
                    // Rect may be centered or positioned at (x,y)
                    return new double[]{w / 2.0, h / 2.0};
                }
            } else if ("circle".equals(tag)) {
                double r = parseDoubleAttr(el, "r", 0);
                if (r > 0) return new double[]{r, r};
            } else if ("ellipse".equals(tag)) {
                double rx = parseDoubleAttr(el, "rx", 0);
                double ry = parseDoubleAttr(el, "ry", 0);
                if (rx > 0 || ry > 0) return new double[]{rx, ry};
            } else if ("polygon".equals(tag)) {
                return parsePolygonBounds(el);
            } else if ("path".equals(tag)) {
                String d = el.getAttribute("d");
                if (d != null && !d.isEmpty()) {
                    double[] bounds = parsePathBounds(d);
                    if (bounds != null) {
                        double hw = (bounds[2] - bounds[0]) / 2.0;
                        double hh = (bounds[3] - bounds[1]) / 2.0;
                        if (hw > 0 || hh > 0) return new double[]{hw, hh};
                    }
                }
            } else if ("g".equals(tag)) {
                // Recurse into child groups (Mermaid wraps shapes in <g>)
                double[] inner = findShapeBounds(el);
                if (inner[0] > 0 || inner[1] > 0) return inner;
            }
        }
        return new double[]{0, 0};
    }

    private static double[] parsePolygonBounds(Element polygon) {
        String points = polygon.getAttribute("points");
        if (points == null || points.isEmpty()) return new double[]{0, 0};

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        String[] pairs = points.trim().split("[\\s,]+");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            try {
                double x = Double.parseDouble(pairs[i]);
                double y = Double.parseDouble(pairs[i + 1]);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            } catch (NumberFormatException ignored) {}
        }

        if (minX > maxX) return new double[]{0, 0};
        return new double[]{(maxX - minX) / 2.0, (maxY - minY) / 2.0};
    }

    /** Extract the visible text content of all {@code <text>} elements inside a group. */
    private static String extractTextContent(Element container) {
        NodeList texts = container.getElementsByTagNameNS("*", "text");
        if (texts.getLength() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texts.getLength(); i++) {
            String content = texts.item(i).getTextContent();
            if (content != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(content.trim());
            }
        }
        return sb.toString().trim();
    }

    private static double parseDoubleAttr(Element el, String attrName, double defaultVal) {
        String v = el.getAttribute(attrName);
        if (v == null || v.isEmpty()) return defaultVal;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static String attr(Element el, String name) {
        String v = el.getAttribute(name);
        return v != null ? v : "";
    }

    // ═══════════════════════════════════════════════════════════
    //  SVG path bounding box parser (same logic as MermaidSvgFixup)
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse an SVG path {@code d} attribute and return its bounding box
     * as {@code [minX, minY, maxX, maxY]}, or {@code null} if parsing fails.
     * Handles M, L, H, V, C, S, Q, T, A, Z commands (absolute and relative).
     */
    static double[] parsePathBounds(String d) {
        if (d == null || d.isEmpty()) return null;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double curX = 0, curY = 0, startX = 0, startY = 0;
        boolean found = false;

        Matcher tokenizer = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz][^MmLlHhVvCcSsQqTtAaZz]*")
                .matcher(d);
        while (tokenizer.find()) {
            String token = tokenizer.group().trim();
            char cmd = token.charAt(0);
            boolean isRel = Character.isLowerCase(cmd);
            char CMD = Character.toUpperCase(cmd);
            Matcher numMatcher = Pattern.compile("-?[\\d]+(?:\\.[\\d]*)?(?:[eE][+-]?[\\d]+)?")
                    .matcher(token.substring(1));
            List<Double> vals = new ArrayList<Double>();
            while (numMatcher.find()) vals.add(Double.parseDouble(numMatcher.group()));

            switch (CMD) {
                case 'M':
                    for (int k = 0; k + 1 < vals.size(); k += 2) {
                        double mx = isRel ? curX + vals.get(k) : vals.get(k);
                        double my = isRel ? curY + vals.get(k + 1) : vals.get(k + 1);
                        minX = Math.min(minX, mx); maxX = Math.max(maxX, mx);
                        minY = Math.min(minY, my); maxY = Math.max(maxY, my);
                        curX = mx; curY = my; found = true;
                        if (k == 0) { startX = mx; startY = my; }
                    }
                    break;
                case 'L': case 'T':
                    for (int k = 0; k + 1 < vals.size(); k += 2) {
                        double lx = isRel ? curX + vals.get(k) : vals.get(k);
                        double ly = isRel ? curY + vals.get(k + 1) : vals.get(k + 1);
                        minX = Math.min(minX, lx); maxX = Math.max(maxX, lx);
                        minY = Math.min(minY, ly); maxY = Math.max(maxY, ly);
                        curX = lx; curY = ly; found = true;
                    }
                    break;
                case 'H':
                    for (int k = 0; k < vals.size(); k++) {
                        double hx = isRel ? curX + vals.get(k) : vals.get(k);
                        minX = Math.min(minX, hx); maxX = Math.max(maxX, hx);
                        minY = Math.min(minY, curY); maxY = Math.max(maxY, curY);
                        curX = hx; found = true;
                    }
                    break;
                case 'V':
                    for (int k = 0; k < vals.size(); k++) {
                        double vy = isRel ? curY + vals.get(k) : vals.get(k);
                        minX = Math.min(minX, curX); maxX = Math.max(maxX, curX);
                        minY = Math.min(minY, vy); maxY = Math.max(maxY, vy);
                        curY = vy; found = true;
                    }
                    break;
                case 'C':
                    for (int k = 0; k + 5 < vals.size(); k += 6) {
                        for (int p = 0; p < 3; p++) {
                            double cx = isRel ? curX + vals.get(k + p * 2) : vals.get(k + p * 2);
                            double cy = isRel ? curY + vals.get(k + p * 2 + 1) : vals.get(k + p * 2 + 1);
                            minX = Math.min(minX, cx); maxX = Math.max(maxX, cx);
                            minY = Math.min(minY, cy); maxY = Math.max(maxY, cy);
                        }
                        curX = isRel ? curX + vals.get(k + 4) : vals.get(k + 4);
                        curY = isRel ? curY + vals.get(k + 5) : vals.get(k + 5);
                        found = true;
                    }
                    break;
                case 'S':
                    for (int k = 0; k + 3 < vals.size(); k += 4) {
                        for (int p = 0; p < 2; p++) {
                            double sx = isRel ? curX + vals.get(k + p * 2) : vals.get(k + p * 2);
                            double sy = isRel ? curY + vals.get(k + p * 2 + 1) : vals.get(k + p * 2 + 1);
                            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
                        }
                        curX = isRel ? curX + vals.get(k + 2) : vals.get(k + 2);
                        curY = isRel ? curY + vals.get(k + 3) : vals.get(k + 3);
                        found = true;
                    }
                    break;
                case 'Q':
                    for (int k = 0; k + 3 < vals.size(); k += 4) {
                        for (int p = 0; p < 2; p++) {
                            double qx = isRel ? curX + vals.get(k + p * 2) : vals.get(k + p * 2);
                            double qy = isRel ? curY + vals.get(k + p * 2 + 1) : vals.get(k + p * 2 + 1);
                            minX = Math.min(minX, qx); maxX = Math.max(maxX, qx);
                            minY = Math.min(minY, qy); maxY = Math.max(maxY, qy);
                        }
                        curX = isRel ? curX + vals.get(k + 2) : vals.get(k + 2);
                        curY = isRel ? curY + vals.get(k + 3) : vals.get(k + 3);
                        found = true;
                    }
                    break;
                case 'A':
                    for (int k = 0; k + 6 < vals.size(); k += 7) {
                        double ax = isRel ? curX + vals.get(k + 5) : vals.get(k + 5);
                        double ay = isRel ? curY + vals.get(k + 6) : vals.get(k + 6);
                        minX = Math.min(minX, ax); maxX = Math.max(maxX, ax);
                        minY = Math.min(minY, ay); maxY = Math.max(maxY, ay);
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
}

