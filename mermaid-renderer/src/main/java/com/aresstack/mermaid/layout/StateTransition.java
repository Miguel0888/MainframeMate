package com.aresstack.mermaid.layout;

/**
 * A state diagram transition (edge between states).
 *
 * <h3>Mermaid syntax examples</h3>
 * <pre>
 *   Rot --&gt; Rot_Gelb : warten
 *   [*] --&gt; Rot
 *   Gelb --&gt; Rot
 * </pre>
 */
public class StateTransition extends DiagramEdge {

    private final String guard;

    public StateTransition(String id, String sourceId, String targetId, String label,
                           String pathData,
                           double x, double y, double width, double height,
                           String guard) {
        super(id, sourceId, targetId, label, "transition", pathData,
                x, y, width, height);
        this.guard = guard != null ? guard : "";
    }

    /**
     * Optional guard condition (text after {@code :} in the transition).
     * May be the same as the label.
     */
    public String getGuard() { return guard; }

    @Override
    public String toString() {
        return "StateTransition{" + getSourceId() + " -> " + getTargetId()
                + (getLabel().isEmpty() ? "" : " [" + getLabel() + "]") + "}";
    }
}

