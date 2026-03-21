package de.bund.zrb.mail.service;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.mail.infrastructure.PstMailboxReader;
import de.bund.zrb.mail.model.MailboxCategory;
import de.bund.zrb.mail.port.MailboxReader;
import de.bund.zrb.model.Settings;

import java.io.OutputStream;
import java.io.PrintStream;
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

        PrintStream filteredErr = null;
        PrintStream originalErr = null;
        if (isSuppressStderr()) {
            originalErr = System.err;
            filteredErr = createFilteredErrStream(originalErr);
            System.setErr(filteredErr);
        }

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
            if (originalErr != null) {
                System.setErr(originalErr);
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

        Set<MailboxCategory> categories = loadSyncCategories();

        PrintStream filteredErr = null;
        PrintStream originalErr = null;
        if (isSuppressStderr()) {
            originalErr = System.err;
            filteredErr = createFilteredErrStream(originalErr);
            System.setErr(filteredErr);
        }

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
            if (originalErr != null) {
                System.setErr(originalErr);
            }
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
    //  stderr filter
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a filtered stderr that suppresses java-libpst noise:
     * - "Unknown message type: ..."
     * - "Can't get children for folder ..."
     * - "getNodeInfo: block doesn't exist! ..."
     * - Standalone numbers, hex dumps, and "---" lines
     */
    private static PrintStream createFilteredErrStream(final PrintStream original) {
        return new PrintStream(new OutputStream() {
            private final StringBuilder line = new StringBuilder();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    String msg = line.toString().trim();
                    if (!shouldSuppress(msg)) {
                        original.println(msg);
                    }
                    line.setLength(0);
                } else {
                    line.append((char) b);
                }
            }

            private boolean shouldSuppress(String msg) {
                if (msg.isEmpty()) return true;
                if (msg.startsWith("Unknown message type:")) return true;
                if (msg.startsWith("Can't get children for folder")) return true;
                if (msg.startsWith("getNodeInfo:")) return true;
                if (msg.equals("---")) return true;
                // Suppress standalone numbers (e.g. "4", "1")
                if (msg.matches("^\\d+$")) return true;
                // Suppress hex dump lines (e.g. "f1f1 436a  ññCj")
                if (msg.matches("^[0-9a-f]{4}\\s.*")) return true;
                return false;
            }
        });
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

