package com.aresstack.mermaid.editor;

import com.aresstack.mermaid.layout.*;

/**
 * Bridge that provides the same method signatures as the old regex-based
 * source manipulation in MermaidSelectionTest, but uses the ANTLR-based
 * {@link MermaidSourceEditor} internally for precise, AST-based modifications.
 *
 * <p>Drop-in replacement for the static helper methods in MermaidSelectionTest.
 * Each method takes the current Mermaid source and returns the modified source.
 */
public final class SourceEditBridge {

    private SourceEditBridge() {}

    // ═══════════════════════════════════════════════════════════
    //  Edge operations
    // ═══════════════════════════════════════════════════════════

    /**
     * Reverse edge direction (swap source ↔ target) in the Mermaid source.
     */
    public static String reverseEdge(String source, String diagramType, DiagramEdge edge) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        MermaidSourceEditor.EdgeInfo ei = findEdgeRobust(editor,
                edge.getSourceId(), edge.getTargetId());
        if (ei == null) return source;

        editor.reverseEdge(ei);
        return editor.getText();
    }

    /**
     * Delete an edge from the Mermaid source.
     */
    public static String deleteEdge(String source, String diagramType, DiagramEdge edge) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        MermaidSourceEditor.EdgeInfo ei = findEdgeRobust(editor,
                edge.getSourceId(), edge.getTargetId());
        if (ei == null) return source;

        editor.deleteEdge(ei);
        return editor.getText();
    }

    /**
     * Change the edge label.
     */
    public static String changeEdgeLabel(String source, String diagramType,
                                          DiagramEdge edge, String newLabel) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        MermaidSourceEditor.EdgeInfo ei = findEdgeRobust(editor,
                edge.getSourceId(), edge.getTargetId());
        if (ei == null) return source;

        editor.replaceLabel(ei, newLabel);
        return editor.getText();
    }

    /**
     * Change ER diagram cardinalities using ANTLR-based editing.
     *
     * @param source      current Mermaid source
     * @param sourceId    source entity ID
     * @param targetId    target entity ID
     * @param newSrcCard  new source-side cardinality
     * @param newTgtCard  new target-side cardinality
     * @param identifying whether the relationship is identifying (== instead of --)
     * @param label       the relationship label
     * @return modified source
     */
    public static String changeErCardinality(String source,
                                              String sourceId, String targetId,
                                              ErCardinality newSrcCard, ErCardinality newTgtCard,
                                              boolean identifying, String label) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        MermaidSourceEditor.EdgeInfo ei = findEdgeRobust(editor, sourceId, targetId);
        if (ei == null) return source;

        // Determine if source/target are swapped vs ANTLR model
        boolean swapped = !ei.sourceId.equals(stripSvgPrefix(sourceId));
        String leftCard, rightCard;
        if (!swapped) {
            leftCard = toLeftSyntax(newSrcCard);
            rightCard = toRightSyntax(newTgtCard);
        } else {
            leftCard = toLeftSyntax(newTgtCard);
            rightCard = toRightSyntax(newSrcCard);
        }

        String connector = identifying ? "==" : "--";
        String newArrow = leftCard + connector + rightCard;

        // Build the full replacement line
        String labelPart = (label != null && !label.isEmpty()) ? " : " + label : "";
        String newLine = ei.sourceId + " " + newArrow + " " + ei.targetId + labelPart;
        editor.replaceEdgeSegment(ei, newLine);
        return editor.getText();
    }

    /** Left-side cardinality Mermaid syntax. */
    private static String toLeftSyntax(ErCardinality card) {
        switch (card) {
            case EXACTLY_ONE:  return "||";
            case ZERO_OR_ONE:  return "|o";
            case ZERO_OR_MORE: return "}o";
            case ONE_OR_MORE:  return "}|";
            default:           return "||";
        }
    }

    /** Right-side cardinality Mermaid syntax. */
    private static String toRightSyntax(ErCardinality card) {
        switch (card) {
            case EXACTLY_ONE:  return "||";
            case ZERO_OR_ONE:  return "o|";
            case ZERO_OR_MORE: return "o{";
            case ONE_OR_MORE:  return "|{";
            default:           return "||";
        }
    }


    /**
     * Change a flowchart edge's arrow style (line style + arrowhead).
     */
    public static String changeFlowchartEdgeStyle(String source,
                                                    String sourceId, String targetId,
                                                    String label,
                                                    LineStyle oldStyle, ArrowHead oldHead,
                                                    LineStyle newStyle, ArrowHead newHead) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        MermaidSourceEditor.EdgeInfo ei = editor.findEdge(sourceId, targetId);
        if (ei == null) return source;

        String newArrow = buildFlowchartArrow(newStyle, newHead);
        editor.replaceArrow(ei, newArrow);
        return editor.getText();
    }

    /**
     * Rename a node in the Mermaid source (all references).
     */
    public static String renameNode(String source, String diagramType,
                                     String oldId, String oldLabel, String newName) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        editor.renameNode(oldId, newName);
        return editor.getText();
    }

    /**
     * Reconnect an edge to different source/target nodes.
     */
    public static String reconnectEdge(String source, String diagramType,
                                        DiagramEdge edge,
                                        String newSourceId, String newTargetId) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        MermaidSourceEditor.EdgeInfo ei = editor.findEdge(edge.getSourceId(), edge.getTargetId());
        if (ei == null) return source;

        // Replace the entire edge segment with new source/target but same arrow+label
        String labelPart = "";
        if (!ei.label.isEmpty()) {
            String type = editor.getDiagramType();
            if ("flowchart".equals(type)) {
                labelPart = "|" + ei.label + "|";
            } else {
                labelPart = " : " + ei.label;
            }
        }
        String newEdge = newSourceId + " " + ei.arrowText + labelPart + " " + newTargetId;
        editor.replaceEdgeSegment(ei, newEdge);
        return editor.getText();
    }

    /**
     * Change the arrow type of a sequence message.
     */
    public static String changeSequenceMessageType(String source,
                                                     String sourceId, String targetId,
                                                     String label,
                                                     String oldArrow, String newArrow) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        MermaidSourceEditor.EdgeInfo ei = editor.findEdge(sourceId, targetId);
        if (ei == null) return source;

        editor.replaceArrow(ei, newArrow);
        return editor.getText();
    }

    /**
     * Add a new edge to the source.
     */
    public static String addEdge(String source, String diagramType,
                                  String sourceId, String targetId) {
        MermaidSourceEditor editor = MermaidSourceEditor.parse(source);
        if (editor == null) return source;

        String arrow;
        String labelSuffix = "";
        switch (diagramType) {
            case "erDiagram":
                arrow = "||--o{";
                labelSuffix = " : relates";  // ER requires ": label" AFTER target
                break;
            case "classDiagram": arrow = "-->"; break;
            case "sequence":
                arrow = "->>";
                labelSuffix = ": Nachricht";  // sequence label after target
                break;
            default:             arrow = "-->"; break;
        }
        editor.addEdgeWithLabel(sourceId, targetId, arrow, labelSuffix);
        return editor.getText();
    }

    // ═══════════════════════════════════════════════════════════
    //  Helper
    // ═══════════════════════════════════════════════════════════

    /** Build a Mermaid flowchart arrow from line style + arrowhead. */
    public static String buildFlowchartArrow(LineStyle style, ArrowHead head) {
        String suffix;
        switch (head) {
            case CIRCLE: suffix = "o"; break;
            case CROSS:  suffix = "x"; break;
            case NONE:   suffix = "";  break;
            default:     suffix = ">";  break;  // NORMAL
        }
        switch (style) {
            case DASHED:
            case DOTTED:
                return suffix.isEmpty() ? "-.-" : "-.-" + suffix;
            case THICK:
                return suffix.isEmpty() ? "===" : "==" + suffix;
            default:
                return suffix.isEmpty() ? "---" : "--" + suffix;
        }
    }

    /**
     * Find an edge robustly: try exact match in both directions,
     * then try with stripped SVG prefixes.
     */
    static MermaidSourceEditor.EdgeInfo findEdgeRobust(MermaidSourceEditor editor,
                                                        String sourceId, String targetId) {
        // Primary: exact match in both directions
        MermaidSourceEditor.EdgeInfo ei = editor.findEdgeAnyDirection(sourceId, targetId);
        if (ei != null) return ei;

        // Fallback: strip SVG prefixes (entity-AUTOR-1 → AUTOR, state-Rot-0 → Rot)
        String cleanSrc = stripSvgPrefix(sourceId);
        String cleanTgt = stripSvgPrefix(targetId);
        if (!cleanSrc.equals(sourceId) || !cleanTgt.equals(targetId)) {
            ei = editor.findEdgeAnyDirection(cleanSrc, cleanTgt);
        }
        return ei;
    }

    /**
     * Strip common SVG ID prefixes to get the logical Mermaid node name.
     * E.g., "entity-AUTOR-1" → "AUTOR", "state-Rot-0" → "Rot",
     * "flowchart-Alpha-0" → "Alpha".
     */
    static String stripSvgPrefix(String id) {
        if (id == null || id.isEmpty()) return id;
        // entity-Name-N, state-Name-N, flowchart-Name-N, classId-Name-N
        String[] prefixes = {"entity-", "state-", "flowchart-", "classId-"};
        for (String prefix : prefixes) {
            if (id.startsWith(prefix)) {
                String rest = id.substring(prefix.length());
                // Remove trailing -N (digit suffix)
                int lastDash = rest.lastIndexOf('-');
                if (lastDash > 0) {
                    String afterDash = rest.substring(lastDash + 1);
                    if (afterDash.matches("\\d+")) {
                        return rest.substring(0, lastDash);
                    }
                }
                return rest;
            }
        }
        return id;
    }
}

