package de.bund.zrb.ui.util;

import java.util.concurrent.CountDownLatch;

/** Wait holder for async approval UI. */
public class ToolApprovalRequest {

    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile ToolApprovalDecision decision = ToolApprovalDecision.CANCELLED;

    public void approve() {
        decision = ToolApprovalDecision.APPROVED;
        latch.countDown();
    }

    public void approveForSession() {
        decision = ToolApprovalDecision.APPROVED_FOR_SESSION;
        latch.countDown();
    }

    public void cancel() {
        decision = ToolApprovalDecision.CANCELLED;
        latch.countDown();
    }

    public ToolApprovalDecision awaitDecision() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return decision;
    }
}
