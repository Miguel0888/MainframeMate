package de.bund.zrb.mail.service;

/**
 * Sync state machine states for the mail synchronisation lifecycle.
 *
 * <pre>
 *   IDLE ──(event)──▶ SYNC_RUNNING ──(done)──▶ COOLDOWN ──(timeout)──▶ IDLE
 *                     ▲                        │
 *                     │                        │ (event during cooldown)
 *                     │                        ▼
 *                     └── COOLDOWN_WITH_PENDING ─(timeout)──▶ SYNC_RUNNING
 * </pre>
 */
public enum MailSyncState {
    /** No sync running, no pending changes, ready for next trigger. */
    IDLE,
    /** A delta-sync is currently running. No new sync may start. */
    SYNC_RUNNING,
    /** A sync just completed; we wait before allowing the next one. */
    COOLDOWN,
    /** Like COOLDOWN, but new events arrived — a follow-up sync is required. */
    COOLDOWN_WITH_PENDING
}

