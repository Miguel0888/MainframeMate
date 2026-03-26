package de.bund.zrb.service.codeanalytics;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in a recursive call tree.
 * Root node represents the analyzed source; children represent external calls,
 * each potentially having their own children (recursive resolution).
 */
public class CallTreeNode {
    private final String name;
    private final String callType;   // null for root
    private final int lineNumber;    // 0 for root
    private final List<CallTreeNode> children = new ArrayList<CallTreeNode>();
    private boolean recursive;       // true if this creates a cycle

    public CallTreeNode(String name, String callType, int lineNumber) {
        this.name = name;
        this.callType = callType;
        this.lineNumber = lineNumber;
    }

    public String getName() { return name; }
    public String getCallType() { return callType; }
    public int getLineNumber() { return lineNumber; }
    public List<CallTreeNode> getChildren() { return children; }
    public boolean isRecursive() { return recursive; }
    public boolean isLeaf() { return children.isEmpty(); }

    public void setRecursive(boolean recursive) { this.recursive = recursive; }

    public void addChild(CallTreeNode child) {
        children.add(child);
    }

    public String getDisplayText() {
        StringBuilder sb = new StringBuilder(name);
        if (callType != null) {
            sb.append("  [").append(callType).append("]");
        }
        if (lineNumber > 0) {
            sb.append("  Zeile ").append(lineNumber);
        }
        if (recursive) {
            sb.append("  \uD83D\uDD04 (rekursiv)"); // 🔄
        }
        return sb.toString();
    }

    @Override
    public String toString() { return getDisplayText(); }

    /**
     * Total number of nodes in this subtree (including this node).
     */
    public int totalNodes() {
        int count = 1;
        for (CallTreeNode child : children) {
            count += child.totalNodes();
        }
        return count;
    }

    /**
     * Collect all unique target names in this subtree (excluding root).
     */
    public List<String> getAllTargetNames() {
        List<String> names = new ArrayList<String>();
        collectTargetNames(this, names);
        return names;
    }

    private void collectTargetNames(CallTreeNode node, List<String> names) {
        for (CallTreeNode child : node.getChildren()) {
            String key = child.getName().toUpperCase();
            if (!names.contains(key)) {
                names.add(key);
            }
            if (!child.isRecursive()) {
                collectTargetNames(child, names);
            }
        }
    }
}

