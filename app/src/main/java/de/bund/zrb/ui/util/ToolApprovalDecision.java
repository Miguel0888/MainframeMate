package de.bund.zrb.ui.util;

public enum ToolApprovalDecision {
    APPROVED,
    APPROVED_FOR_SESSION,
    CANCELLED,
    /** Permanently whitelisted (navigation) */
    ALWAYS_ALLOW,
    /** Permanently blacklisted (navigation) */
    ALWAYS_BLOCK,
    /** Blocked for session only (navigation) */
    BLOCKED_FOR_SESSION
}
