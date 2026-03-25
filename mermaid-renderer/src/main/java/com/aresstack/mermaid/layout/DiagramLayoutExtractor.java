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
                // Also extract combined fragments (loop, alt, opt, etc.)
                List<DiagramNode> fragments = extractSequenceFragments(doc);
                nodes.addAll(fragments);
            } else {
                nodes = extractNodes(doc);
                edges = extractEdges(doc, nodes, diagramType);
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
        double nx = cx - hw, ny = cy - hh, nw = hw * 2, nh = hh * 2;

        // ── Create typed subclass based on kind ──
        if ("node".equals(kind)) {
            NodeShape shape = detectNodeShape(g);
            return new FlowchartNode(logicalId, label, nx, ny, nw, nh, svgId, shape);
        }
        if ("class".equals(kind)) {
            return extractClassNode(g, logicalId, label, nx, ny, nw, nh, svgId);
        }
        if ("entity".equals(kind)) {
            return extractErEntityNode(g, logicalId, label, nx, ny, nw, nh, svgId);
        }
        if ("state".equals(kind)) {
            boolean isStart = label.contains("[*]") || svgId.contains("root_start")
                    || svgId.contains("-start-");
            boolean isEnd = svgId.contains("root_end") || svgId.contains("-end-");
            return new StateDiagramNode(logicalId, label, nx, ny, nw, nh, svgId,
                    isStart, isEnd, false);
        }
        if ("requirement".equals(kind)) {
            return new RequirementItemNode(logicalId, label, nx, ny, nw, nh, svgId,
                    RequirementItemNode.ReqNodeType.REQUIREMENT, "", "", "");
        }

        // Fallback: plain DiagramNode
        return new DiagramNode(logicalId, label, kind, nx, ny, nw, nh, svgId);
    }

    // ═══════════════════════════════════════════════════════════
    //  Shape detection for flowchart nodes
    // ═══════════════════════════════════════════════════════════

    /**
     * Detect the visual shape of a flowchart node from its SVG child elements.
     */
    private static NodeShape detectNodeShape(Element g) {
        NodeList children = g.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            Element el = (Element) child;
            String tag = el.getLocalName();

            if ("circle".equals(tag)) {
                return NodeShape.CIRCLE;
            }
            if ("rect".equals(tag)) {
                double w = parseDoubleAttr(el, "width", 0);
                double h = parseDoubleAttr(el, "height", 0);
                double rx = parseDoubleAttr(el, "rx", 0);
                double ry = parseDoubleAttr(el, "ry", 0);
                if (rx > 0 || ry > 0) {
                    // Stadium: rx is very large relative to height
                    if (rx >= h / 2.5 || ry >= h / 2.5) {
                        return NodeShape.STADIUM;
                    }
                    return NodeShape.ROUND_RECT;
                }
                // Check for cylinder (has a second rect or specific class)
                String rectCls = attr(el, "class");
                if (rectCls.contains("label-container")) {
                    return NodeShape.RECTANGLE;
                }
                return NodeShape.RECTANGLE;
            }
            if ("polygon".equals(tag)) {
                String points = el.getAttribute("points");
                if (points != null && !points.isEmpty()) {
                    int pointCount = countPolygonPoints(points);
                    if (pointCount == 4) {
                        // Diamond or trapezoid — check if it's rotated 45°
                        if (isDiamondShape(points)) {
                            return NodeShape.DIAMOND;
                        }
                        return NodeShape.TRAPEZOID;
                    }
                    if (pointCount >= 6) {
                        return NodeShape.HEXAGON;
                    }
                }
                return NodeShape.DIAMOND; // fallback for polygons
            }
            if ("path".equals(tag)) {
                // Paths are used for stadium, cylinder, asymmetric, etc.
                String d = el.getAttribute("d");
                if (d != null) {
                    // Cylinder typically has arcs (A commands)
                    if (d.contains("A") || d.contains("a")) {
                        return NodeShape.CYLINDER;
                    }
                }
                return NodeShape.STADIUM; // common fallback for path-based shapes
            }
            if ("g".equals(tag)) {
                // Recurse into child groups
                NodeShape inner = detectNodeShape(el);
                if (inner != NodeShape.RECTANGLE) return inner;
            }
        }
        return NodeShape.RECTANGLE;
    }

    private static int countPolygonPoints(String points) {
        String[] parts = points.trim().split("[\\s,]+");
        return parts.length / 2;
    }

    private static boolean isDiamondShape(String points) {
        String[] parts = points.trim().split("[\\s,]+");
        if (parts.length < 8) return false;
        try {
            double[] xs = new double[4];
            double[] ys = new double[4];
            for (int i = 0; i < 4; i++) {
                xs[i] = Double.parseDouble(parts[i * 2]);
                ys[i] = Double.parseDouble(parts[i * 2 + 1]);
            }
            // Diamond: center point should be equidistant from all vertices
            double cx = (xs[0] + xs[1] + xs[2] + xs[3]) / 4;
            double cy = (ys[0] + ys[1] + ys[2] + ys[3]) / 4;
            // Check if points alternate between top/bottom and left/right
            // A diamond has vertices at top, right, bottom, left
            double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
            double maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
            double minY = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]));
            double maxY = Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]));
            double w = maxX - minX;
            double h = maxY - minY;
            // Diamond: aspect ratio is roughly square and each vertex is near an edge center
            if (w > 0 && h > 0 && Math.abs(w / h - 1.0) < 1.5) {
                return true;
            }
        } catch (NumberFormatException ignored) {}
        return false;
    }

    // ═══════════════════════════════════════════════════════════
    //  Class diagram node extraction (fields + methods)
    // ═══════════════════════════════════════════════════════════

    private static ClassNode extractClassNode(Element g, String logicalId, String fullLabel,
                                               double x, double y, double w, double h,
                                               String svgId) {
        // The class box text contains: ClassName, optional <<stereotype>>, then members
        String allText = fullLabel;
        String stereotype = "";
        java.util.List<ClassMember> members = new java.util.ArrayList<ClassMember>();

        // Try to extract individual text lines from <text> elements
        NodeList texts = g.getElementsByTagNameNS("*", "text");
        java.util.List<String> lines = new java.util.ArrayList<String>();
        for (int i = 0; i < texts.getLength(); i++) {
            String content = texts.item(i).getTextContent();
            if (content != null && !content.trim().isEmpty()) {
                lines.add(content.trim());
            }
        }

        // Parse stereotype and members from text lines
        for (String line : lines) {
            if (line.startsWith("<<") && line.endsWith(">>")) {
                stereotype = line.substring(2, line.length() - 2).trim();
            } else if (line.equals(logicalId)) {
                // Skip the class name itself
                continue;
            } else if (line.contains("(") || line.startsWith("+") || line.startsWith("-")
                    || line.startsWith("#") || line.startsWith("~")) {
                members.add(ClassMember.parse(line));
            } else if (!line.isEmpty() && !line.equals(fullLabel)) {
                // Could be a field without visibility prefix
                members.add(ClassMember.parse(line));
            }
        }

        return new ClassNode(logicalId, logicalId, x, y, w, h, svgId, stereotype, members);
    }

    // ═══════════════════════════════════════════════════════════
    //  ER entity node extraction (attributes)
    // ═══════════════════════════════════════════════════════════

    private static ErEntityNode extractErEntityNode(Element g, String logicalId, String fullLabel,
                                                     double x, double y, double w, double h,
                                                     String svgId) {
        java.util.List<ErAttribute> attributes = new java.util.ArrayList<ErAttribute>();

        // Extract text lines from entity box
        NodeList texts = g.getElementsByTagNameNS("*", "text");
        for (int i = 0; i < texts.getLength(); i++) {
            String content = texts.item(i).getTextContent();
            if (content == null) continue;
            String line = content.trim();
            // Skip the entity name line
            if (line.equals(logicalId) || line.isEmpty()) continue;
            // Try to parse as attribute: "type name [PK|FK]"
            ErAttribute attr = ErAttribute.parse(line);
            if (attr != null && !attr.getName().isEmpty()) {
                attributes.add(attr);
            }
        }

        return new ErEntityNode(logicalId, logicalId, x, y, w, h, svgId, attributes);
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

        // Estimate depth from CSS class — Mermaid adds "section-N" or "level-N" classes,
        // or by checking the SVG section-depth attribute.
        // Fallback: first node is root (depth 0), subsequent nodes start at depth 1.
        int depth = 0;
        String cls = attr(g, "class");

        // Mermaid 11 uses class like "mindmap-node section-0" etc.
        java.util.regex.Matcher secMatcher = java.util.regex.Pattern.compile(
                "section-(\\d+)").matcher(cls);
        if (secMatcher.find()) {
            depth = Integer.parseInt(secMatcher.group(1));
        } else {
            // Fallback: first is root, rest estimate from position in DOM
            depth = (index == 0) ? 0 : 1;
        }

        return new MindmapItemNode(
                logicalId, label,
                cx - hw, cy - hh, hw * 2, hh * 2,
                svgId, depth
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

        // Detect actor type: check if there are <image> or person-shaped <path> elements
        // that indicate "actor" (stick figure) vs. "participant" (box)
        boolean hasStickFigures = false;
        NodeList images = doc.getElementsByTagNameNS("*", "image");
        if (images.getLength() > 0) hasStickFigures = true;

        NodeList rects = doc.getElementsByTagNameNS("*", "rect");
        for (int i = 0; i < rects.getLength(); i++) {
            Node n = rects.item(i);
            if (!(n instanceof Element)) continue;
            Element rect = (Element) n;
            if (!attr(rect, "class").contains("actor")) continue;

            String name = attr(rect, "name");
            if (name.isEmpty()) continue;

            double rx = parseDoubleAttr(rect, "x", 0);
            double ry = parseDoubleAttr(rect, "y", 0);
            double rw = parseDoubleAttr(rect, "width", 150);
            double rh = parseDoubleAttr(rect, "height", 65);

            if (!actorBounds.containsKey(name)) {
                // Store as [minX, minY, maxX, maxY] for easier merging
                actorBounds.put(name, new double[]{rx, ry, rx + rw, ry + rh});
            } else {
                // Merge: expand bounding box to encompass this rect too
                // (covers both top and bottom actor boxes + lifeline)
                double[] prev = actorBounds.get(name);
                prev[0] = Math.min(prev[0], rx);           // minX
                prev[1] = Math.min(prev[1], ry);           // minY
                prev[2] = Math.max(prev[2], rx + rw);      // maxX
                prev[3] = Math.max(prev[3], ry + rh);      // maxY
            }
        }

        for (Map.Entry<String, double[]> entry : actorBounds.entrySet()) {
            double[] b = entry.getValue(); // [minX, minY, maxX, maxY]
            double ax = b[0], ay = b[1], aw = b[2] - b[0], ah = b[3] - b[1];
            result.add(new SequenceActorNode(
                    entry.getKey(), entry.getKey(),
                    ax, ay, aw, ah,
                    "actor-" + entry.getKey(),
                    hasStickFigures ? SequenceActorNode.ActorType.ACTOR
                                    : SequenceActorNode.ActorType.PARTICIPANT
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
            String lineCls = attr(line, "class");
            if (!lineCls.contains("messageLine")) continue;

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

            // Detect message type from CSS class
            MessageType msgType = MessageType.fromCssClass(lineCls);

            result.add(new SequenceMessage(
                    edgeId, sourceId, targetId, label,
                    null,
                    bx, by, bw, bh,
                    msgType, false, false
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
    //  Sequence diagram fragment extraction (loop, alt, opt…)
    // ═══════════════════════════════════════════════════════════

    /**
     * Extract combined fragments (loop, alt, opt, par, critical, break, rect)
     * from a sequence diagram SVG.
     *
     * <p>Mermaid renders these as {@code <rect class="loopLine">} rectangles
     * paired with {@code <text class="loopText">} labels inside a parent
     * {@code <g>} group.
     */
    private static List<DiagramNode> extractSequenceFragments(Document doc) {
        List<DiagramNode> result = new ArrayList<DiagramNode>();

        NodeList rects = doc.getElementsByTagNameNS("*", "rect");
        for (int i = 0; i < rects.getLength(); i++) {
            Node n = rects.item(i);
            if (!(n instanceof Element)) continue;
            Element rect = (Element) n;
            String cls = attr(rect, "class");
            if (!cls.contains("loopLine")) continue;

            double rx = parseDoubleAttr(rect, "x", 0);
            double ry = parseDoubleAttr(rect, "y", 0);
            double rw = parseDoubleAttr(rect, "width", 0);
            double rh = parseDoubleAttr(rect, "height", 0);
            if (rw < 1 || rh < 1) continue;

            // Walk up to the parent <g> and find sibling <text class="loopText"> elements
            String fragmentKeyword = "";
            String fragmentCondition = "";
            Node parent = rect.getParentNode();
            if (parent instanceof Element) {
                Element pg = (Element) parent;
                NodeList texts = pg.getElementsByTagNameNS("*", "text");
                for (int t = 0; t < texts.getLength(); t++) {
                    Element txt = (Element) texts.item(t);
                    String txtCls = attr(txt, "class");
                    if (!txtCls.contains("loopText")) continue;
                    String content = txt.getTextContent();
                    if (content == null) continue;
                    content = content.trim();
                    if (content.isEmpty()) continue;

                    // First loopText is typically the keyword (loop, alt, opt…)
                    // Second loopText (if any) is the condition
                    if (fragmentKeyword.isEmpty()) {
                        // Sometimes keyword and condition are in one text node: "loop Diskussion"
                        // or just "loop" with condition in brackets as separate text
                        String lower = content.toLowerCase();
                        if (lower.startsWith("loop") || lower.startsWith("alt") || lower.startsWith("opt")
                                || lower.startsWith("par") || lower.startsWith("critical")
                                || lower.startsWith("break") || lower.startsWith("rect")
                                || lower.startsWith("else")) {
                            // Split keyword from condition
                            int spaceIdx = content.indexOf(' ');
                            if (spaceIdx > 0) {
                                fragmentKeyword = content.substring(0, spaceIdx);
                                fragmentCondition = content.substring(spaceIdx + 1).trim();
                            } else {
                                fragmentKeyword = content;
                            }
                        } else {
                            // Might be a condition in brackets like "[Diskussion]"
                            fragmentCondition = content.replace("[", "").replace("]", "").trim();
                        }
                    } else if (fragmentCondition.isEmpty()) {
                        fragmentCondition = content.replace("[", "").replace("]", "").trim();
                    }
                }
            }

            if (fragmentKeyword.isEmpty()) fragmentKeyword = "loop"; // fallback

            String fragId = "fragment-" + result.size();
            String svgId = "loopRect-" + result.size();
            String label = fragmentKeyword + (fragmentCondition.isEmpty() ? "" : " " + fragmentCondition);

            SequenceFragment.FragmentType fragType =
                    SequenceFragment.FragmentType.fromKeyword(fragmentKeyword);
            result.add(new SequenceFragment(
                    fragId, label, rx, ry, rw, rh, svgId,
                    fragType, fragmentCondition));
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  Edge extraction (flowchart, class, ER, etc.)
    // ═══════════════════════════════════════════════════════════

    private static List<DiagramEdge> extractEdges(Document doc, List<DiagramNode> nodes,
                                                     String diagramType) {
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

            // Skip ER relationship lines — they're handled by the dedicated ER section below
            if (cls.contains("er") && cls.contains("relationshipLine")) continue;

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

            // Detect line style from SVG style/class attributes
            LineStyle lineStyle = detectLineStyle(path);
            ArrowHead headType = detectArrowHead(path, "marker-end");
            ArrowHead tailType = detectArrowHead(path, "marker-start");

            // Create typed edge based on diagram context
            if ("classDiagram".equals(diagramType)) {
                RelationType relType = inferRelationType(lineStyle, headType, tailType);
                result.add(new ClassRelation(
                        edgeId, sourceId, targetId,
                        label, d,
                        pathBounds[0], pathBounds[1],
                        pathBounds[2] - pathBounds[0],
                        pathBounds[3] - pathBounds[1],
                        relType, "", ""
                ));
            } else {
                result.add(new FlowchartEdge(
                        edgeId, sourceId, targetId,
                        label, d,
                        pathBounds[0], pathBounds[1],
                        pathBounds[2] - pathBounds[0],
                        pathBounds[3] - pathBounds[1],
                        lineStyle, headType, tailType
                ));
            }
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
        int erRelIdx = 0;
        for (int i = 0; i < allPaths.getLength(); i++) {
            Node n = allPaths.item(i);
            if (!(n instanceof Element)) continue;
            Element path = (Element) n;
            String cls = attr(path, "class");
            if (!cls.contains("er") || !cls.contains("relationshipLine")) continue;

            String d = path.getAttribute("d");
            double[] bounds = (d != null && !d.isEmpty()) ? parsePathBounds(d) : null;
            if (bounds == null) bounds = new double[]{0, 0, 0, 0};

            // Use actual path start/end points for proximity matching
            // (bounding box midpoints fail for vertical or diagonal lines)
            double[] endpoints = (d != null && !d.isEmpty()) ? parsePathEndpoints(d) : null;
            double startX, startY, endX, endY;
            if (endpoints != null) {
                startX = endpoints[0]; startY = endpoints[1];
                endX = endpoints[2]; endY = endpoints[3];
            } else {
                startX = bounds[0]; startY = bounds[1];
                endX = bounds[2]; endY = bounds[3];
            }
            String srcId = "", tgtId = "";
            double srcDist = Double.MAX_VALUE, tgtDist = Double.MAX_VALUE;
            for (DiagramNode node : nodes) {
                if (!"entity".equals(node.getKind())) continue;
                double ncx = node.getCenterX(), ncy = node.getCenterY();
                double dStart = Math.hypot(ncx - startX, ncy - startY);
                double dEnd = Math.hypot(ncx - endX, ncy - endY);
                if (dStart < srcDist) { srcDist = dStart; srcId = node.getId(); }
                if (dEnd < tgtDist) { tgtDist = dEnd; tgtId = node.getId(); }
            }
            // Guard: source and target must be different entities
            if (srcId.equals(tgtId) && !srcId.isEmpty()) {
                // Fallback: assign the second-closest entity as target
                double secondBest = Double.MAX_VALUE;
                for (DiagramNode node : nodes) {
                    if (!"entity".equals(node.getKind())) continue;
                    if (node.getId().equals(srcId)) continue;
                    double ncx = node.getCenterX(), ncy = node.getCenterY();
                    double dEnd2 = Math.hypot(ncx - endX, ncy - endY);
                    if (dEnd2 < secondBest) { secondBest = dEnd2; tgtId = node.getId(); }
                }
            }

            // Try to detect cardinality from marker references
            ErCardinality srcCard = detectErCardinality(path, "marker-start");
            ErCardinality tgtCard = detectErCardinality(path, "marker-end");
            // Mermaid ER: "--" = identifying (solid), ".." = non-identifying (dashed)
            boolean identifying = cls.contains("identify");

            // Try to find the relationship label from nearby text elements
            String erLabel = "";
            double midX = (bounds[0] + bounds[2]) / 2;
            double midY = (bounds[1] + bounds[3]) / 2;

            // Strategy 1: text in parent group
            Node parent = path.getParentNode();
            if (parent instanceof Element) {
                NodeList texts = ((Element) parent).getElementsByTagNameNS("*", "text");
                for (int t = 0; t < texts.getLength(); t++) {
                    String txt = texts.item(t).getTextContent();
                    if (txt != null && !txt.trim().isEmpty()) {
                        erLabel = txt.trim();
                        break;
                    }
                }
            }

            // Strategy 2: search all text elements with class "er relationshipLabel"
            if (erLabel.isEmpty()) {
                NodeList allTexts = doc.getElementsByTagNameNS("*", "text");
                double bestLabelDist = Double.MAX_VALUE;
                for (int t = 0; t < allTexts.getLength(); t++) {
                    Node textNode = allTexts.item(t);
                    if (!(textNode instanceof Element)) continue;
                    Element textEl = (Element) textNode;
                    String textCls = attr(textEl, "class");
                    if (!textCls.contains("er") || !textCls.contains("Label")) continue;
                    String txt = textEl.getTextContent();
                    if (txt == null || txt.trim().isEmpty()) continue;
                    // Use position to match label to the closest relationship line
                    double tx = parseDoubleAttr(textEl, "x", 0);
                    double ty = parseDoubleAttr(textEl, "y", 0);
                    // Also check transform
                    if (tx == 0 && ty == 0) {
                        double[] trans = parseTranslate(textEl);
                        tx = trans[0]; ty = trans[1];
                    }
                    double dist = Math.hypot(tx - midX, ty - midY);
                    if (dist < bestLabelDist) {
                        bestLabelDist = dist;
                        erLabel = txt.trim();
                    }
                }
            }

            result.add(new ErRelationship(
                    srcId.isEmpty() ? "er-relation-" + erRelIdx : srcId + "->" + tgtId,
                    srcId, tgtId, erLabel,
                    d,
                    bounds[0], bounds[1],
                    bounds[2] - bounds[0], bounds[3] - bounds[1],
                    srcCard, tgtCard, identifying
            ));
            erRelIdx++;
        }

        // ═══ State diagram transitions: resolve empty endpoints by proximity ═══
        if ("stateDiagram".equals(diagramType)) {
            List<DiagramEdge> resolved = new ArrayList<DiagramEdge>();
            for (DiagramEdge edge : result) {
                if (!edge.getSourceId().isEmpty() && !edge.getTargetId().isEmpty()) {
                    // Already has endpoints — convert to StateTransition if needed
                    if (!(edge instanceof StateTransition)) {
                        resolved.add(new StateTransition(
                                edge.getId(), edge.getSourceId(), edge.getTargetId(),
                                edge.getLabel(), edge.getPathData(),
                                edge.getX(), edge.getY(), edge.getWidth(), edge.getHeight(),
                                edge.getLabel()));
                    } else {
                        resolved.add(edge);
                    }
                    continue;
                }

                // Resolve source/target by proximity to state nodes
                double[] pathBounds = (edge.getPathData() != null && !edge.getPathData().isEmpty())
                        ? parsePathBounds(edge.getPathData()) : null;
                if (pathBounds == null) {
                    pathBounds = new double[]{edge.getX(), edge.getY(),
                            edge.getX() + edge.getWidth(), edge.getY() + edge.getHeight()};
                }

                double startX = pathBounds[0], startY = pathBounds[1];
                double endX = pathBounds[2], endY = pathBounds[3];
                String srcId = "", tgtId = "";
                double srcDist = Double.MAX_VALUE, tgtDist = Double.MAX_VALUE;
                for (DiagramNode node : nodes) {
                    if (!"state".equals(node.getKind())) continue;
                    double ncx = node.getCenterX(), ncy = node.getCenterY();
                    double dStart = Math.hypot(ncx - startX, ncy - startY);
                    double dEnd = Math.hypot(ncx - endX, ncy - endY);
                    if (dStart < srcDist) { srcDist = dStart; srcId = node.getId(); }
                    if (dEnd < tgtDist) { tgtDist = dEnd; tgtId = node.getId(); }
                }

                String edgeId = srcId.isEmpty() ? edge.getId() : (srcId + "->" + tgtId);
                String label = edge.getLabel();
                resolved.add(new StateTransition(
                        edgeId, srcId, tgtId, label,
                        edge.getPathData(),
                        edge.getX(), edge.getY(), edge.getWidth(), edge.getHeight(),
                        label));
            }
            result = resolved;
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
    //  Edge style detection
    // ═══════════════════════════════════════════════════════════

    /**
     * Infer the UML relation type from detected arrowheads and line style.
     */
    private static RelationType inferRelationType(LineStyle lineStyle,
                                                   ArrowHead headType,
                                                   ArrowHead tailType) {
        // Triangle (open) → inheritance or realization
        if (headType == ArrowHead.TRIANGLE_OPEN || tailType == ArrowHead.TRIANGLE_OPEN) {
            return (lineStyle == LineStyle.DASHED || lineStyle == LineStyle.DOTTED)
                    ? RelationType.REALIZATION : RelationType.INHERITANCE;
        }
        // Diamond → composition or aggregation
        if (headType == ArrowHead.DIAMOND_FILLED || tailType == ArrowHead.DIAMOND_FILLED) {
            return RelationType.COMPOSITION;
        }
        if (headType == ArrowHead.DIAMOND_OPEN || tailType == ArrowHead.DIAMOND_OPEN) {
            return RelationType.AGGREGATION;
        }
        // Dashed/dotted with arrow → dependency
        if ((lineStyle == LineStyle.DASHED || lineStyle == LineStyle.DOTTED)
                && (headType == ArrowHead.NORMAL || tailType == ArrowHead.NORMAL)) {
            return RelationType.DEPENDENCY;
        }
        // Arrow → association
        if (headType == ArrowHead.NORMAL || tailType == ArrowHead.NORMAL) {
            return RelationType.ASSOCIATION;
        }
        // No arrowhead → link
        return RelationType.LINK;
    }


    /**
     * Detect the line style of an edge from its SVG attributes.
     * Checks {@code stroke-dasharray}, {@code stroke-width}, and CSS classes.
     */
    private static LineStyle detectLineStyle(Element path) {
        // Check inline style
        String style = attr(path, "style");
        String cls = attr(path, "class");

        // stroke-dasharray indicates dashed
        String dashArray = extractStyleProperty(style, "stroke-dasharray");
        if (dashArray == null || dashArray.isEmpty()) {
            dashArray = path.getAttribute("stroke-dasharray");
        }
        if (dashArray != null && !dashArray.isEmpty()
                && !"none".equalsIgnoreCase(dashArray) && !"0".equals(dashArray)) {
            return LineStyle.DASHED;
        }

        // stroke-width > threshold indicates thick
        String strokeWidth = extractStyleProperty(style, "stroke-width");
        if (strokeWidth == null || strokeWidth.isEmpty()) {
            strokeWidth = path.getAttribute("stroke-width");
        }
        if (strokeWidth != null && !strokeWidth.isEmpty()) {
            try {
                double sw = Double.parseDouble(strokeWidth.replace("px", "").trim());
                if (sw > 2.5) return LineStyle.THICK;
            } catch (NumberFormatException ignored) {}
        }

        // CSS class heuristics
        if (cls.contains("dotted") || cls.contains("dashed")) return LineStyle.DASHED;
        if (cls.contains("thick")) return LineStyle.THICK;

        return LineStyle.SOLID;
    }

    /**
     * Detect arrowhead type from a marker reference attribute.
     *
     * @param path      the SVG path element
     * @param markerAttr  "marker-end" or "marker-start"
     * @return detected arrowhead type
     */
    private static ArrowHead detectArrowHead(Element path, String markerAttr) {
        // Check inline style first
        String style = attr(path, "style");
        String markerUrl = extractStyleProperty(style, markerAttr);
        if (markerUrl == null || markerUrl.isEmpty()) {
            markerUrl = path.getAttribute(markerAttr);
        }
        if (markerUrl == null || markerUrl.isEmpty() || "none".equals(markerUrl)) {
            return ArrowHead.NONE;
        }

        // Parse url(#markerId) → check marker element
        String markerId = markerUrl.replaceAll(".*#([^)\"]+).*", "$1");
        if (markerId.isEmpty()) return ArrowHead.NORMAL;

        // Heuristic: marker id often contains the type
        String lower = markerId.toLowerCase();
        if (lower.contains("aggregation") || lower.contains("diamond") && lower.contains("open")) {
            return ArrowHead.DIAMOND_OPEN;
        }
        if (lower.contains("composition") || lower.contains("diamond")) {
            return ArrowHead.DIAMOND_FILLED;
        }
        if (lower.contains("extension") || lower.contains("triangle") || lower.contains("open")) {
            return ArrowHead.TRIANGLE_OPEN;
        }
        if (lower.contains("cross")) {
            return ArrowHead.CROSS;
        }
        if (lower.contains("circle")) {
            return ArrowHead.CIRCLE;
        }

        return ArrowHead.NORMAL;
    }

    /**
     * Extract a CSS property value from an inline style string.
     *
     * @param style    e.g. "stroke-dasharray: 3; stroke-width: 2px"
     * @param property e.g. "stroke-dasharray"
     * @return the value, or null if not found
     */
    private static String extractStyleProperty(String style, String property) {
        if (style == null || style.isEmpty()) return null;
        String[] parts = style.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(property)) {
                int colon = trimmed.indexOf(':');
                if (colon >= 0) {
                    return trimmed.substring(colon + 1).trim();
                }
            }
        }
        return null;
    }

    /**
     * Detect ER cardinality from a marker reference on a relationship path.
     *
     * @param path       the SVG path element for the relationship line
     * @param markerAttr "marker-start" or "marker-end"
     * @return detected cardinality
     */
    private static ErCardinality detectErCardinality(Element path, String markerAttr) {
        String style = attr(path, "style");
        String markerUrl = extractStyleProperty(style, markerAttr);
        if (markerUrl == null || markerUrl.isEmpty()) {
            markerUrl = path.getAttribute(markerAttr);
        }
        if (markerUrl == null || markerUrl.isEmpty() || "none".equals(markerUrl)) {
            return ErCardinality.EXACTLY_ONE;
        }

        String markerId = markerUrl.replaceAll(".*#([^)\"]+).*", "$1");
        String lower = markerId.toLowerCase();

        // Mermaid ER marker IDs typically contain cardinality hints
        if (lower.contains("zero_or_more") || lower.contains("crowfoot") && lower.contains("zero")) {
            return ErCardinality.ZERO_OR_MORE;
        }
        if (lower.contains("one_or_more") || lower.contains("crowfoot") && !lower.contains("zero")) {
            return ErCardinality.ONE_OR_MORE;
        }
        if (lower.contains("zero_or_one") || lower.contains("optionality")) {
            return ErCardinality.ZERO_OR_ONE;
        }
        if (lower.contains("one") || lower.contains("only_one")) {
            return ErCardinality.EXACTLY_ONE;
        }

        return ErCardinality.EXACTLY_ONE;
    }

    // ═══════════════════════════════════════════════════════════
    //  SVG path endpoint parser (for ER relationship proximity matching)
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse an SVG path {@code d} attribute and return its first and last points
     * as {@code [startX, startY, endX, endY]}, or {@code null} if parsing fails.
     * This is more precise than using bounding box corners for proximity matching.
     */
    /**
     * Parse an SVG path {@code d} attribute and return the actual start and end
     * coordinates as {@code [startX, startY, endX, endY]}.
     *
     * <p>Unlike {@link #parsePathBounds}, which returns the bounding box,
     * this returns the first {@code M} point and the final cursor position after
     * executing all path commands.</p>
     *
     * @param d the SVG path {@code d} attribute
     * @return {@code [startX, startY, endX, endY]}, or {@code null} if parsing fails
     */
    public static double[] parsePathEndpoints(String d) {
        if (d == null || d.isEmpty()) return null;
        double curX = 0, curY = 0, startX = 0, startY = 0;
        boolean hasStart = false;

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
                        curX = isRel ? curX + vals.get(k) : vals.get(k);
                        curY = isRel ? curY + vals.get(k + 1) : vals.get(k + 1);
                        if (!hasStart) { startX = curX; startY = curY; hasStart = true; }
                    }
                    break;
                case 'L': case 'T':
                    for (int k = 0; k + 1 < vals.size(); k += 2) {
                        curX = isRel ? curX + vals.get(k) : vals.get(k);
                        curY = isRel ? curY + vals.get(k + 1) : vals.get(k + 1);
                        if (!hasStart) { startX = curX; startY = curY; hasStart = true; }
                    }
                    break;
                case 'H':
                    for (int k = 0; k < vals.size(); k++) {
                        curX = isRel ? curX + vals.get(k) : vals.get(k);
                    }
                    break;
                case 'V':
                    for (int k = 0; k < vals.size(); k++) {
                        curY = isRel ? curY + vals.get(k) : vals.get(k);
                    }
                    break;
                case 'C':
                    for (int k = 0; k + 5 < vals.size(); k += 6) {
                        curX = isRel ? curX + vals.get(k + 4) : vals.get(k + 4);
                        curY = isRel ? curY + vals.get(k + 5) : vals.get(k + 5);
                    }
                    break;
                case 'S':
                    for (int k = 0; k + 3 < vals.size(); k += 4) {
                        curX = isRel ? curX + vals.get(k + 2) : vals.get(k + 2);
                        curY = isRel ? curY + vals.get(k + 3) : vals.get(k + 3);
                    }
                    break;
                case 'Q':
                    for (int k = 0; k + 3 < vals.size(); k += 4) {
                        curX = isRel ? curX + vals.get(k + 2) : vals.get(k + 2);
                        curY = isRel ? curY + vals.get(k + 3) : vals.get(k + 3);
                    }
                    break;
                case 'A':
                    for (int k = 0; k + 6 < vals.size(); k += 7) {
                        curX = isRel ? curX + vals.get(k + 5) : vals.get(k + 5);
                        curY = isRel ? curY + vals.get(k + 6) : vals.get(k + 6);
                    }
                    break;
                case 'Z':
                    curX = startX; curY = startY;
                    break;
            }
        }
        if (!hasStart) return null;
        return new double[]{startX, startY, curX, curY};
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

