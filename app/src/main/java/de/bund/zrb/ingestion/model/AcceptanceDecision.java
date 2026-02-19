package de.bund.zrb.ingestion.model;

/**
 * Result of document acceptance policy evaluation.
 * Indicates whether a document should be processed or rejected.
 */
public class AcceptanceDecision {

    public enum Decision {
        ACCEPT,
        REJECT
    }

    private final Decision decision;
    private final String reason;
    private final String mimeType;

    private AcceptanceDecision(Decision decision, String reason, String mimeType) {
        this.decision = decision;
        this.reason = reason;
        this.mimeType = mimeType;
    }

    /**
     * Create an ACCEPT decision.
     */
    public static AcceptanceDecision accept(String mimeType) {
        return new AcceptanceDecision(Decision.ACCEPT, null, mimeType);
    }

    /**
     * Create a REJECT decision with a reason.
     */
    public static AcceptanceDecision reject(String reason, String mimeType) {
        return new AcceptanceDecision(Decision.REJECT, reason, mimeType);
    }

    /**
     * Create a REJECT decision with a reason (no MIME type known).
     */
    public static AcceptanceDecision reject(String reason) {
        return new AcceptanceDecision(Decision.REJECT, reason, null);
    }

    public Decision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isAccepted() {
        return decision == Decision.ACCEPT;
    }

    public boolean isRejected() {
        return decision == Decision.REJECT;
    }

    @Override
    public String toString() {
        if (decision == Decision.ACCEPT) {
            return "ACCEPT (mimeType=" + mimeType + ")";
        } else {
            return "REJECT: " + reason + " (mimeType=" + mimeType + ")";
        }
    }
}

