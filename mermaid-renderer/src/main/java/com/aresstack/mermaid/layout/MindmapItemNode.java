package com.aresstack.mermaid.layout;

/**
 * A mindmap node with its depth in the tree hierarchy.
 */
public class MindmapItemNode extends DiagramNode {

    private final int depth;

    public MindmapItemNode(String id, String label,
                           double x, double y, double width, double height,
                           String svgId, int depth) {
        super(id, label, "mindmap-node", x, y, width, height, svgId);
        this.depth = depth;
    }

    /** Depth in the mindmap tree (0 = root). */
    public int getDepth() { return depth; }

    /** True if this is the root node (depth 0). */
    public boolean isRoot() { return depth == 0; }

    @Override
    public String toString() {
        return "MindmapItemNode{id='" + getId() + "', depth=" + depth
                + ", label='" + getLabel() + "'}";
    }
}

