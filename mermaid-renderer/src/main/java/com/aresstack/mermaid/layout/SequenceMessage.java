package com.aresstack.mermaid.layout;

/**
 * A sequence diagram message with type and activation info.
 *
 * <h3>Mermaid syntax examples</h3>
 * <pre>
 *   Alice -&gt;&gt; Bob : Hello          SYNC_SOLID
 *   Bob --&gt;&gt; Alice : Reply         SYNC_DOTTED (reply)
 *   Alice -&gt;&gt;+ Bob : Call          SYNC_SOLID + activating
 *   Bob --&gt;&gt;- Alice : Return       SYNC_DOTTED + deactivating
 * </pre>
 */
public class SequenceMessage extends DiagramEdge {

    private final MessageType messageType;
    private final boolean activating;
    private final boolean deactivating;

    public SequenceMessage(String id, String sourceId, String targetId, String label,
                           String pathData,
                           double x, double y, double width, double height,
                           MessageType messageType,
                           boolean activating, boolean deactivating) {
        super(id, sourceId, targetId, label, "messageLine", pathData,
                x, y, width, height);
        this.messageType = messageType != null ? messageType : MessageType.SYNC_SOLID;
        this.activating = activating;
        this.deactivating = deactivating;
    }

    /** The message type (sync/async/reply). */
    public MessageType getMessageType() { return messageType; }

    /** True if this message activates the target (creates an activation box). */
    public boolean isActivating() { return activating; }

    /** True if this message deactivates the target (ends activation box). */
    public boolean isDeactivating() { return deactivating; }

    /** Convenience: true if the line is dashed (typically a reply). */
    public boolean isDashed() { return messageType.isDashed(); }

    /**
     * Generate Mermaid syntax for this message.
     */
    public String toMermaid() {
        String activate = activating ? "+" : (deactivating ? "-" : "");
        return getSourceId() + messageType.getMermaidSyntax() + activate
                + getTargetId() + ": " + getLabel();
    }

    @Override
    public String toString() {
        return "SequenceMessage{" + getSourceId() + " " + messageType
                + " " + getTargetId()
                + (getLabel().isEmpty() ? "" : " [" + getLabel() + "]")
                + (activating ? " +activate" : "")
                + (deactivating ? " -deactivate" : "") + "}";
    }
}

