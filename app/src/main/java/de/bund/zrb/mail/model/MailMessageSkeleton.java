package de.bund.zrb.mail.model;

/**
 * Ultra-lightweight mail skeleton — only the two fields needed for Phase 1
 * index building: the PST descriptor node ID and the delivery timestamp.
 * <p>
 * All other metadata (subject, sender, …) is populated later during the
 * enrichment phase (Phase 2).
 */
public class MailMessageSkeleton {

    public final long nodeId;
    public final long deliveryTimeMillis;

    public MailMessageSkeleton(long nodeId, long deliveryTimeMillis) {
        this.nodeId = nodeId;
        this.deliveryTimeMillis = deliveryTimeMillis;
    }
}

