package de.bund.zrb.mail.service;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.mail.infrastructure.MailMetadataIndex;
import de.bund.zrb.mail.infrastructure.PstMailboxReader;
import de.bund.zrb.mail.infrastructure.PstStderrFilter;
import de.bund.zrb.mail.model.MailboxCategory;
import de.bund.zrb.mail.port.MailboxReader;
import de.bund.zrb.model.Settings;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central orchestrator for mail synchronisation.
 * <p>
 * Reads sync preferences from {@link Settings} to determine:
 * <ul>
 *   <li>Which folder categories to sync (MAIL, CALENDAR, CONTACTS, TASKS, NOTES)</li>
 *   <li>Whether to suppress java-libpst stderr noise</li>
 *   <li>Whether sync is enabled at all</li>
 * </ul>
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

    /** Listeners for new-mail notifications (sender, subject). */
    private final List<NewMailListener> newMailListeners = new CopyOnWriteArrayList<NewMailListener>();

    /** Callback for status changes (UI can listen to update status display). */
    public interface StatusListener {
        void onStatusChanged(MailSyncStatus status);
    }

    /** Callback when new mails arrive (after delta sync). */
    public interface NewMailListener {
        /**
         * Called for each new mail detected.
         * @param sender  sender name / address
         * @param subject mail subject (may be null)
         */
        void onNewMail(String sender, String subject);
    }

    private MailService() {}

    public static synchronized MailService getInstance() {
        if (instance == null) {
            instance = new MailService();
        }
        return instance;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Settings → sync categories
    // ═══════════════════════════════════════════════════════════════

    /**
     * Build the set of categories to sync from current Settings.
     */
    private Set<MailboxCategory> loadSyncCategories() {
        Settings s = SettingsHelper.load();
        Set<MailboxCategory> cats = new LinkedHashSet<MailboxCategory>();
        if (s.mailSyncMails)    cats.add(MailboxCategory.MAIL);
        if (s.mailSyncCalendar) cats.add(MailboxCategory.CALENDAR);
        if (s.mailSyncContacts) cats.add(MailboxCategory.CONTACTS);
        if (s.mailSyncTasks)    cats.add(MailboxCategory.TASKS);
        if (s.mailSyncNotes)    cats.add(MailboxCategory.NOTES);
        // Default: at least MAIL if nothing selected
        if (cats.isEmpty()) cats.add(MailboxCategory.MAIL);
        return cats;
    }

    /** Returns true if stderr suppression is enabled in settings. */
    private boolean isSuppressStderr() {
        Settings s = SettingsHelper.load();
        return s.mailSyncSuppressStderr;
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
        Settings s = SettingsHelper.load();
        if (!s.mailSyncEnabled) {
            LOG.info("[MailService] Mail sync is disabled in settings");
            updateStatus(MailSyncStatus.INACTIVE);
            return;
        }

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

        // Create coordinator with sync action (use configured cooldown / Totzeit)
        int cooldown = s.mailSyncCooldownSeconds > 0 ? s.mailSyncCooldownSeconds
                : MailChangeCoordinator.DEFAULT_COOLDOWN_SECONDS;
        coordinator = new MailChangeCoordinator(new MailChangeCoordinator.SyncAction() {
            @Override
            public void performSync(MailConnection connection, String reason) {
                performDeltaSync(connection, reason);
            }
        }, cooldown);

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

        Set<MailboxCategory> categories = loadSyncCategories();
        LOG.info("[MailService] Sync categories: " + categories);

        int totalNew = 0;
        int totalErrors = 0;

        PstStderrFilter.Guard stderrGuard = isSuppressStderr() ? PstStderrFilter.install() : null;

        try {
            for (MailConnection conn : connections) {
                if (!conn.isActive() || !conn.isValid()) {
                    LOG.warning("[MailService] Skipping invalid connection: " + conn);
                    continue;
                }

                try {
                    LOG.info("[MailService] Initial sync: " + conn.getDisplayName());
                    MailDeltaDetector.DeltaResult result = deltaDetector.initialSync(conn, categories);

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
        } finally {
            if (stderrGuard != null) stderrGuard.uninstall();
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

        Set<MailboxCategory> categories = loadSyncCategories();

        PstStderrFilter.Guard stderrGuard = isSuppressStderr() ? PstStderrFilter.install() : null;

        try {
            MailDeltaDetector.DeltaResult result = deltaDetector.deltaSync(connection, categories);

            // Combine new + changed for indexing
            List<MailDeltaDetector.MailCandidate> toIndex =
                    new ArrayList<MailDeltaDetector.MailCandidate>(result.newMails.size() + result.changedMails.size());
            toIndex.addAll(result.newMails);
            toIndex.addAll(result.changedMails);

            MailIndexUpdater.UpdateResult indexResult = indexUpdater.indexCandidates(toIndex);

            // Notify listeners about new mails (for marquee display)
            if (!result.newMails.isEmpty()) {
                fireNewMails(result.newMails);
            }

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
        } finally {
            if (stderrGuard != null) stderrGuard.uninstall();
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

    /** Get the dedicated mail metadata index (for sorted queries / folder listing). */
    public MailMetadataIndex getMetadataIndex() {
        return MailMetadataIndex.getInstance();
    }

    public void addStatusListener(StatusListener listener) {
        statusListeners.add(listener);
    }

    public void removeStatusListener(StatusListener listener) {
        statusListeners.remove(listener);
    }

    public void addNewMailListener(NewMailListener listener) {
        newMailListeners.add(listener);
    }

    public void removeNewMailListener(NewMailListener listener) {
        newMailListeners.remove(listener);
    }

    private void fireNewMails(List<MailDeltaDetector.MailCandidate> newMails) {
        if (newMailListeners.isEmpty() || newMails.isEmpty()) return;
        for (MailDeltaDetector.MailCandidate mc : newMails) {
            String sender = mc.sender != null ? mc.sender : "(unbekannt)";
            String subject = mc.subject;
            for (NewMailListener listener : newMailListeners) {
                try {
                    listener.onNewMail(sender, subject);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "[MailService] NewMailListener error", e);
                }
            }
        }
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
    //  On-demand metadata indexing (fast, for sort support)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ensures the metadata index has entries for the given folder.
     * If the index is empty for this folder, performs a fast header-only scan
     * (no full-text extraction) so that sorted browsing works immediately.
     *
     * @return number of newly indexed entries (0 if index was already populated)
     */
    public int ensureMetadataIndexForFolder(String mailboxPath, String folderPath) {
        MailMetadataIndex metaIdx = MailMetadataIndex.getInstance();
        int existing = metaIdx.countByFolder(mailboxPath, folderPath);
        if (existing > 0) {
            LOG.fine("[MailService] Metadata index already has " + existing + " entries for " + folderPath);
            return 0;
        }
        LOG.info("[MailService] Building metadata index for folder: " + folderPath);
        PstStderrFilter.Guard g = PstStderrFilter.install();
        try {
            return indexUpdater.indexMetadataForFolder(mailboxPath, folderPath);
        } finally {
            g.uninstall();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Phase 1: Skeleton indexing (ultra-fast, timestamp + URL only)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ensures the skeleton index has entries for the given folder.
     * If the index is empty for this folder, performs an ultra-fast skeleton scan
     * (only nodeId + deliveryTime, no subject/sender) so that a date-sorted
     * view is available immediately.
     *
     * @return number of newly indexed skeleton entries (0 if index was already populated)
     */
    public int ensureSkeletonIndexForFolder(String mailboxPath, String folderPath) {
        MailMetadataIndex metaIdx = MailMetadataIndex.getInstance();
        int existing = metaIdx.countByFolder(mailboxPath, folderPath);
        if (existing > 0) {
            LOG.fine("[MailService] Index already has " + existing + " entries for " + folderPath);
            return 0;
        }
        LOG.info("[MailService] Building skeleton index for folder: " + folderPath);
        PstStderrFilter.Guard g = PstStderrFilter.install();
        try {
            return indexUpdater.indexSkeletonForFolder(mailboxPath, folderPath);
        } finally {
            g.uninstall();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Phase 2: Enrichment of visible entries
    // ═══════════════════════════════════════════════════════════════

    /**
     * Enriches skeleton index entries with full header metadata (subject, sender, etc.).
     * Called from the UI for the currently visible page only.
     *
     * @param nodeIds  descriptor node IDs to enrich
     * @param callback called for each enriched entry (on the calling thread)
     * @return number of entries enriched
     */
    public int enrichVisibleEntries(String mailboxPath, String folderPath,
                                    java.util.List<Long> nodeIds,
                                    MailIndexUpdater.EnrichmentCallback callback) {
        PstStderrFilter.Guard g = PstStderrFilter.install();
        try {
            return indexUpdater.enrichMetadataEntries(mailboxPath, folderPath, nodeIds, callback);
        } finally {
            g.uninstall();
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

