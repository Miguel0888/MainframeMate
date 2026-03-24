package com.aresstack.mermaid.layout;

/**
 * A state diagram node (state, start/end marker, or composite state).
 */
public class StateDiagramNode extends DiagramNode {

    private final boolean startState;
    private final boolean endState;
    private final boolean composite;

    public StateDiagramNode(String id, String label,
                            double x, double y, double width, double height,
                            String svgId, boolean startState, boolean endState,
                            boolean composite) {
        super(id, label, "state", x, y, width, height, svgId);
        this.startState = startState;
        this.endState = endState;
        this.composite = composite;
    }

    /** True if this is the initial state marker {@code [*]}. */
    public boolean isStartState() { return startState; }

    /** True if this is the final state marker {@code [*]}. */
    public boolean isEndState() { return endState; }

    /** True if this is a composite (nested) state. */
    public boolean isComposite() { return composite; }

    @Override
    public String toString() {
        return "StateDiagramNode{id='" + getId() + "'"
                + (startState ? ", START" : "")
                + (endState ? ", END" : "")
                + (composite ? ", composite" : "")
                + ", bounds=[" + fmt(getX()) + "," + fmt(getY()) + " "
                + fmt(getWidth()) + "x" + fmt(getHeight()) + "]}";
    }
}

