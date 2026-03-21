package de.bund.zrb.mail.service;

import de.bund.zrb.mail.infrastructure.PstMailboxReader;
import de.bund.zrb.mail.port.MailboxReader;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central orchestrator for mail synchronisation.
 * <p>
 * Coordinates the interaction between:
 * <ul>
 *   <li>{@link MailConnection} — configured PST/OST files</li>
 *   <li>{@link MailStoreWatcher} — file-system change detection</li>
 *   <li>{@link MailChangeCoordinator} — cooldown/debounce state machine</li>
 *   <li>{@link MailDeltaDetector} — watermark-based delta detection</li>
 *   <li>{@link MailIndexUpdater} — Lucene index updates</li>
 * </ul>
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>{@link #initialize(String)} — discover PST/OST, run initial sync, start watcher</li>
 *   <li>File change events → coordinator → delta sync → index update</li>
 *   <li>{@link #freshnessCheckBeforeSearch()} — optional pre-search check</li>
 *   <li>{@link #shutdown()} — stop watcher and scheduler</li>
 * </ol>
 * <p>
 * Thread-safe singleton. Does NOT contain UI logic.
 */
public class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class.getName());

    private static volatile MailService instance;

    // ── Components ──
    private final List<MailConnection> connections = new CopyOnWriteArrayList<MailConnection>();
    private final MailboxReader mailboxReader = new PstMailboxReader();
    private final MailDeltaDetector deltaDetector = new MailDeltaDetector(mailboxReader);
    private final MailIndexUpdater indexUpdater = new MailIndexUpdater(mailboxReader);

    private MailStoreWatcher watcher;
    private MailChangeCoordinator coordinator;

    // ── Status ──
    private volatile MailSyncStatus lastStatus = MailSyncStatus.INACTIVE;
    private volatile String lastError;
    private volatile boolean initialized;

    /** Listeners for status changes. */
    private final List<StatusListener> statusListeners = new CopyOnWriteArrayList<StatusListener>();

    /** Callback for status changes (UI can listen to update status display). */
    public interface StatusListener {
        void onStatusChanged(MailSyncStatus status);
    }

    private MailService() {}

    public static synchronized MailService getInstance() {
        if (instance == null) {
            instance = new MailService();
        }
        return instance;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initialize the mail service with the configured mail store directory.
     * Discovers PST/OST files, runs initial sync, starts file watcher.
     * <p>
     * Safe to call multiple times — subsequent calls re-initialize.
     *
     * @param mailStorePath directory containing PST/OST files
     */
    public synchronized void initialize(String mailStorePath) {
        if (mailStorePath == null || mailStorePath.trim().isEmpty()) {
            LOG.info("[MailService] No mail store path configured");
            updateStatus(MailSyncStatus.INACTIVE);
            return;
        }

        LOG.info("[MailService] Initializing with path: " + mailStorePath);

        // Stop any existing watcher
        shutdown();

        // Discover connections
        connections.clear();
        connections.addAll(MailConnection.fromDirectory(mailStorePath));

        if (connections.isEmpty()) {
            LOG.info("[MailService] No PST/OST files found in: " + mailStorePath);
            updateStatus(MailSyncStatus.INACTIVE);
            return;
        }

        LOG.info("[MailService] Found " + connections.size() + " mail store(s):");
        for (MailConnection conn : connections) {
            LOG.info("[MailService]   " + conn);
        }

        // Create coordinator with sync action
        coordinator = new MailChangeCoordinator(new MailChangeCoordinator.SyncAction() {
            @Override
            public void performSync(MailConnection connection, String reason) {
                performDeltaSync(connection, reason);
            }
        });

        // Create and start watcher
        watcher = new MailStoreWatcher(connections);
        watcher.addListener(new MailStoreWatcher.FileChangeListener() {
            @Override
            public void onMailFileChanged(MailConnection connection) {
                LOG.info("[MailService] File change event: " + connection.getDisplayName());
                updateStatus(MailSyncStatus.CHANGES_DETECTED);
                coordinator.onFileChanged(connection);
            }
        });

        // Run initial sync in background
        initialized = true;
        Thread initThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runInitialSync();
                // Start watcher after initial sync
                watcher.start();
            }
        }, "MailService-Init");
        initThread.setDaemon(true);
        initThread.start();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Initial sync
    // ═══════════════════════════════════════════════════════════════

    private void runInitialSync() {
        LOG.info("[MailService] Running initial sync for " + connections.size() + " store(s)");
        updateStatus(MailSyncStatus.SYNCING);

        int totalNew = 0;
        int totalErrors = 0;

        for (MailConnection conn : connections) {
            if (!conn.isActive() || !conn.isValid()) {
                LOG.warning("[MailService] Skipping invalid connection: " + conn);
                continue;
            }

            try {
                LOG.info("[MailService] Initial sync: " + conn.getDisplayName());
                MailDeltaDetector.DeltaResult result = deltaDetector.initialSync(conn);

                // Index all mails
                MailIndexUpdater.UpdateResult indexResult = indexUpdater.indexCandidates(result.newMails);
                totalNew += indexResult.indexed;
                totalErrors += indexResult.errors + result.errors;

                LOG.info("[MailService] Initial sync of " + conn.getDisplayName() + ": "
                        + indexResult.indexed + " indexed, " + result.errors + " scan errors, "
                        + indexResult.errors + " index errors");
            } catch (Exception e) {
                totalErrors++;
                LOG.log(Level.WARNING, "[MailService] Initial sync failed: " + conn, e);
                lastError = e.getMessage();
            }
        }

        if (totalErrors > 0) {
            updateStatus(MailSyncStatus.ERROR);
        } else {
            updateStatus(MailSyncStatus.UP_TO_DATE);
        }

        LOG.info("[MailService] Initial sync complete: " + totalNew + " mails indexed, "
                + totalErrors + " errors");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delta sync (triggered by coordinator)
    // ═══════════════════════════════════════════════════════════════

    private void performDeltaSync(MailConnection connection, String reason) {
        LOG.info("[MailService] Delta sync started: " + connection.getDisplayName()
                + " (Grund: " + reason + ")");
        updateStatus(MailSyncStatus.SYNCING);

        try {
            MailDeltaDetector.DeltaResult result = deltaDetector.deltaSync(connection);

            // Combine new + changed for indexing
            List<MailDeltaDetector.MailCandidate> toIndex =
                    new ArrayList<MailDeltaDetector.MailCandidate>(result.newMails.size() + result.changedMails.size());
            toIndex.addAll(result.newMails);
            toIndex.addAll(result.changedMails);

            MailIndexUpdater.UpdateResult indexResult = indexUpdater.indexCandidates(toIndex);

            LOG.info("[MailService] Delta sync of " + connection.getDisplayName() + ": "
                    + result.newMails.size() + " new, " + result.changedMails.size() + " changed, "
                    + result.skipped + " skipped, " + result.totalScanned + " scanned, "
                    + indexResult.indexed + " indexed, " + indexResult.errors + " errors");

            if (indexResult.errors > 0 || result.errors > 0) {
                lastError = "Teilfehler bei Sync";
            }
            updateStatus(MailSyncStatus.UP_TO_DATE);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailService] Delta sync failed: " + connection.getDisplayName(), e);
            lastError = e.getMessage();
            updateStatus(MailSyncStatus.ERROR);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Freshness check (called before search)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Quick freshness check before a search.
     * Checks if any connection's file has changed since last sync.
     * If so, triggers a sync (non-blocking).
     *
     * @return true if a sync was triggered
     */
    public boolean freshnessCheckBeforeSearch() {
        if (!initialized || coordinator == null) return false;

        boolean triggered = false;
        for (MailConnection conn : connections) {
            if (conn.isActive() && conn.isValid()) {
                if (coordinator.freshnessCheck(conn)) {
                    triggered = true;
                }
            }
        }
        return triggered;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Status
    // ═══════════════════════════════════════════════════════════════

    public MailSyncStatus getStatus() {
        if (coordinator != null) {
            return coordinator.getStatus();
        }
        return lastStatus;
    }

    public String getLastError() { return lastError; }

    public boolean isInitialized() { return initialized; }

    /** Get the number of known mails across all connections. */
    public int getTotalKnownMailCount() {
        int total = 0;
        for (MailConnection conn : connections) {
            total += deltaDetector.getKnownMailCount(conn.getConnectionId());
        }
        return total;
    }

    /** Get all active connections. */
    public List<MailConnection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public void addStatusListener(StatusListener listener) {
        statusListeners.add(listener);
    }

    public void removeStatusListener(StatusListener listener) {
        statusListeners.remove(listener);
    }

    private void updateStatus(MailSyncStatus status) {
        this.lastStatus = status;
        for (StatusListener listener : statusListeners) {
            try {
                listener.onStatusChanged(status);
            } catch (Exception e) {
                LOG.log(Level.FINE, "[MailService] Status listener error", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Shutdown
    // ═══════════════════════════════════════════════════════════════

    /**
     * Stop the watcher, coordinator, and release all resources.
     */
    public synchronized void shutdown() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
        if (coordinator != null) {
            coordinator.shutdown();
            coordinator = null;
        }
        initialized = false;
    }
}

