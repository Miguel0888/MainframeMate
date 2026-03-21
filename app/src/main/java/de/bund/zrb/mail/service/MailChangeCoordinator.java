package de.bund.zrb.mail.service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the cooldown/debounce state machine for mail sync triggers.
 * <p>
 * Behaviour:
 * <ul>
 *   <li><b>Rule A (Sofortreaktion):</b> First event after idle → immediate sync, then cooldown</li>
 *   <li><b>Rule B (Cooldown):</b> Events during cooldown → mark pending, reschedule</li>
 *   <li><b>Rule C (Nachlauf):</b> When cooldown expires with pending changes → one follow-up sync</li>
 * </ul>
 * <p>
 * Thread-safe: may be called from WatchService thread, timer thread, and EDT.
 */
public class MailChangeCoordinator {

    private static final Logger LOG = Logger.getLogger(MailChangeCoordinator.class.getName());

    /** Default cooldown period in seconds. */
    static final int DEFAULT_COOLDOWN_SECONDS = 60;

    /** Callback to execute the actual delta sync. */
    public interface SyncAction {
        /**
         * Execute a mail delta sync for the given connection.
         *
         * @param connection the mail connection to sync
         * @param reason     human-readable reason for logging
         */
        void performSync(MailConnection connection, String reason);
    }

    private final SyncAction syncAction;
    private final int cooldownSeconds;
    private final AtomicReference<MailSyncState> state = new AtomicReference<MailSyncState>(MailSyncState.IDLE);
    private final ScheduledExecutorService scheduler;

    /** The connection that has pending changes. */
    private volatile MailConnection pendingConnection;
    /** Scheduled cooldown-expiry task (cancelled and rescheduled on new events). */
    private volatile ScheduledFuture<?> cooldownFuture;

    public MailChangeCoordinator(SyncAction syncAction) {
        this(syncAction, DEFAULT_COOLDOWN_SECONDS);
    }

    public MailChangeCoordinator(SyncAction syncAction, int cooldownSeconds) {
        this.syncAction = syncAction;
        this.cooldownSeconds = cooldownSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "MailCooldownTimer");
                t.setDaemon(true);
                return t;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  Event handling
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when a file-system change event is detected for the given connection.
     * Applies the cooldown state machine (Rules A/B/C).
     */
    public synchronized void onFileChanged(MailConnection connection) {
        MailSyncState current = state.get();
        LOG.fine("[MailCoord] onFileChanged in state " + current + " for " + connection.getDisplayName());

        switch (current) {
            case IDLE:
                // Rule A: immediate sync
                state.set(MailSyncState.SYNC_RUNNING);
                pendingConnection = null;
                executeSyncAsync(connection, "Sofortreaktion auf Dateiänderung");
                break;

            case SYNC_RUNNING:
                // Sync already in progress — just mark pending
                LOG.fine("[MailCoord] Sync running, marking pending for " + connection.getDisplayName());
                pendingConnection = connection;
                break;

            case COOLDOWN:
                // Rule B: during cooldown, mark pending and reschedule
                state.set(MailSyncState.COOLDOWN_WITH_PENDING);
                pendingConnection = connection;
                rescheduleCooldown();
                LOG.fine("[MailCoord] Cooldown → COOLDOWN_WITH_PENDING, rescheduled");
                break;

            case COOLDOWN_WITH_PENDING:
                // Already pending — just reschedule the timer
                pendingConnection = connection;
                rescheduleCooldown();
                LOG.fine("[MailCoord] COOLDOWN_WITH_PENDING, rescheduled cooldown");
                break;
        }
    }

    /**
     * Called when a sync (initial or delta) completes.
     * Transitions state and starts cooldown or follow-up sync.
     */
    public synchronized void onSyncComplete(boolean success) {
        MailSyncState current = state.get();
        LOG.fine("[MailCoord] onSyncComplete (success=" + success + ") in state " + current);

        if (current == MailSyncState.SYNC_RUNNING) {
            if (pendingConnection != null) {
                // Events arrived during sync — go to cooldown with pending
                state.set(MailSyncState.COOLDOWN_WITH_PENDING);
                scheduleCooldown();
            } else {
                // No pending events — simple cooldown
                state.set(MailSyncState.COOLDOWN);
                scheduleCooldown();
            }
        }
        // If state was somehow already COOLDOWN (shouldn't happen), leave it
    }

    /**
     * Perform a freshness check before a search.
     * If the file has changed and no sync is running/pending, trigger immediate sync.
     *
     * @param connection the connection to check
     * @return true if a sync was triggered (caller may want to wait briefly)
     */
    public synchronized boolean freshnessCheck(MailConnection connection) {
        if (!connection.isActive() || !connection.isValid()) return false;

        MailSyncState current = state.get();
        if (current == MailSyncState.SYNC_RUNNING) {
            LOG.fine("[MailCoord] Freshness check: sync already running");
            return true; // sync in progress, search will get latest available
        }

        if (connection.hasFileChanged()) {
            LOG.info("[MailCoord] Freshness check: file changed, triggering sync");
            if (current == MailSyncState.IDLE) {
                state.set(MailSyncState.SYNC_RUNNING);
                pendingConnection = null;
                executeSyncAsync(connection, "Freshness-Check vor Suche");
                return true;
            } else {
                // In cooldown — mark pending
                pendingConnection = connection;
                if (current == MailSyncState.COOLDOWN) {
                    state.set(MailSyncState.COOLDOWN_WITH_PENDING);
                }
                return false;
            }
        }

        // Check pending status
        if (current == MailSyncState.COOLDOWN_WITH_PENDING) {
            LOG.fine("[MailCoord] Freshness check: pending changes, follow-up scheduled");
            return true;
        }

        return false;
    }

    /** Get the current sync state. */
    public MailSyncState getState() {
        return state.get();
    }

    /** Get the human-readable sync status. */
    public MailSyncStatus getStatus() {
        switch (state.get()) {
            case IDLE:                    return MailSyncStatus.UP_TO_DATE;
            case SYNC_RUNNING:            return MailSyncStatus.SYNCING;
            case COOLDOWN:                return MailSyncStatus.UP_TO_DATE;
            case COOLDOWN_WITH_PENDING:   return MailSyncStatus.CHANGES_DETECTED;
            default:                      return MailSyncStatus.INACTIVE;
        }
    }

    /** Shutdown the scheduler. */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal: async execution and cooldown scheduling
    // ═══════════════════════════════════════════════════════════════

    private void executeSyncAsync(final MailConnection connection, final String reason) {
        Thread syncThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    syncAction.performSync(connection, reason);
                    onSyncComplete(true);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[MailCoord] Sync failed for " + connection.getDisplayName(), e);
                    onSyncComplete(false);
                }
            }
        }, "MailDeltaSync-" + connection.getDisplayName());
        syncThread.setDaemon(true);
        syncThread.start();
    }

    private void scheduleCooldown() {
        cancelCooldown();
        cooldownFuture = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                onCooldownExpired();
            }
        }, cooldownSeconds, TimeUnit.SECONDS);
        LOG.fine("[MailCoord] Cooldown scheduled: " + cooldownSeconds + "s");
    }

    private void rescheduleCooldown() {
        // Cancel existing and reschedule to now + cooldown
        scheduleCooldown();
    }

    private void cancelCooldown() {
        if (cooldownFuture != null && !cooldownFuture.isDone()) {
            cooldownFuture.cancel(false);
        }
    }

    /**
     * Called when the cooldown timer expires.
     * Rule C: if pending changes exist, start a follow-up sync.
     */
    private synchronized void onCooldownExpired() {
        MailSyncState current = state.get();
        LOG.fine("[MailCoord] Cooldown expired in state " + current);

        if (current == MailSyncState.COOLDOWN_WITH_PENDING && pendingConnection != null) {
            // Rule C: follow-up sync
            MailConnection conn = pendingConnection;
            pendingConnection = null;
            state.set(MailSyncState.SYNC_RUNNING);
            executeSyncAsync(conn, "Nachlauf nach Cooldown");
        } else {
            // Nothing pending — back to idle
            state.set(MailSyncState.IDLE);
            pendingConnection = null;
            LOG.fine("[MailCoord] → IDLE");
        }
    }
}

