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

        MermaidSourceEditor.EdgeInfo ei = editor.findEdge(edge.getSourceId(), edge.getTargetId());
        if (ei == null) {
            // Try reversed direction (SVG extraction might have swapped)
            ei = editor.findEdge(edge.getTargetId(), edge.getSourceId());
        }
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

        MermaidSourceEditor.EdgeInfo ei = editor.findEdge(edge.getSourceId(), edge.getTargetId());
        if (ei == null) {
            ei = editor.findEdge(edge.getTargetId(), edge.getSourceId());
        }
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

        MermaidSourceEditor.EdgeInfo ei = editor.findEdge(edge.getSourceId(), edge.getTargetId());
        if (ei == null) return source;

        editor.replaceLabel(ei, newLabel);
        return editor.getText();
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
        switch (diagramType) {
            case "erDiagram":    arrow = "||--o{"; break;
            case "classDiagram": arrow = "-->"; break;
            case "sequence":     arrow = "->>"; break;
            default:             arrow = "-->"; break;
        }
        editor.addEdge(sourceId, targetId, arrow);
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
}

