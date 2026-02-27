package de.bund.zrb.ui.util;

public enum ToolApprovalDecision {
    APPROVED,
    APPROVED_FOR_SESSION,
    CANCELLED,
    /** Permanently whitelisted – exact domain */
    ALWAYS_ALLOW,
    /** Permanently whitelisted – domain + all subdomains */
    ALWAYS_ALLOW_SUBDOMAIN,
    /** Permanently blacklisted – exact domain */
    ALWAYS_BLOCK,
    /** Permanently blacklisted – domain + all subdomains */
    ALWAYS_BLOCK_SUBDOMAIN,
    /** Blocked for session – exact domain */
    BLOCKED_FOR_SESSION,
    /** Blocked for session – domain + all subdomains */
    BLOCKED_FOR_SESSION_SUBDOMAIN,
    /** Allowed for session – domain + all subdomains */
    APPROVED_FOR_SESSION_SUBDOMAIN
}
