package com.aresstack.mermaid.layout;

/**
 * A sequence diagram actor (participant or actor).
 */
public class SequenceActorNode extends DiagramNode {

    /** Visual representation type. */
    public enum ActorType {
        /** Box with name label. */
        PARTICIPANT("participant"),
        /** Stick figure. */
        ACTOR("actor");

        private final String keyword;
        ActorType(String keyword) { this.keyword = keyword; }
        public String getKeyword() { return keyword; }
    }

    private final ActorType actorType;

    public SequenceActorNode(String id, String label,
                             double x, double y, double width, double height,
                             String svgId, ActorType actorType) {
        super(id, label, "actor", x, y, width, height, svgId);
        this.actorType = actorType != null ? actorType : ActorType.PARTICIPANT;
    }

    /** Whether this is a {@code participant} (box) or {@code actor} (stick figure). */
    public ActorType getActorType() { return actorType; }

    @Override
    public String toString() {
        return "SequenceActorNode{id='" + getId() + "', type=" + actorType + "}";
    }
}

