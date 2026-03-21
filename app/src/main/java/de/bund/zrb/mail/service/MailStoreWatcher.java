package de.bund.zrb.mail.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches the parent directories of configured PST/OST files for changes
 * using the Java NIO {@link WatchService}.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Register directories with WatchService</li>
 *   <li>Filter events to only relevant PST/OST filenames</li>
 *   <li>Notify registered listeners on file change</li>
 * </ul>
 * <p>
 * Does NOT contain sync logic — that is delegated to {@link MailChangeCoordinator}.
 */
public class MailStoreWatcher {

    private static final Logger LOG = Logger.getLogger(MailStoreWatcher.class.getName());

    /** Callback for file change events. */
    public interface FileChangeListener {
        /**
         * Called when a relevant PST/OST file has changed.
         *
         * @param connection the connection whose file was changed
         */
        void onMailFileChanged(MailConnection connection);
    }

    private final List<MailConnection> connections;
    private final List<FileChangeListener> listeners = new CopyOnWriteArrayList<FileChangeListener>();
    private volatile WatchService watchService;
    private volatile Thread watchThread;
    private volatile boolean running;

    public MailStoreWatcher(List<MailConnection> connections) {
        this.connections = connections;
    }

    public void addListener(FileChangeListener listener) {
        listeners.add(listener);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start watching all configured directories.
     * Non-blocking — spawns a daemon thread.
     */
    public synchronized void start() {
        if (running) return;
        running = true;

        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "[MailWatcher] Failed to create WatchService", e);
            running = false;
            return;
        }

        // Register each unique parent directory
        java.util.Set<Path> registered = new java.util.HashSet<Path>();
        for (MailConnection conn : connections) {
            if (!conn.isActive() || !conn.isValid()) continue;
            Path dir = conn.getParentDirectory().toPath();
            if (registered.contains(dir)) continue;
            try {
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);
                registered.add(dir);
                LOG.info("[MailWatcher] Watching directory: " + dir);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "[MailWatcher] Cannot watch: " + dir, e);
            }
        }

        if (registered.isEmpty()) {
            LOG.info("[MailWatcher] No directories to watch");
            closeWatchService();
            running = false;
            return;
        }

        watchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                pollLoop();
            }
        }, "MailStoreWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * Stop watching and release resources.
     */
    public synchronized void stop() {
        running = false;
        closeWatchService();
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Watch loop
    // ═══════════════════════════════════════════════════════════════

    private void pollLoop() {
        LOG.info("[MailWatcher] Watch loop started");
        while (running) {
            WatchKey key;
            try {
                key = watchService.take(); // blocks until event
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path changedFile = pathEvent.context();
                String changedName = changedFile.toString();

                LOG.fine("[MailWatcher] Event: " + kind.name() + " → " + changedName);

                // Check if this filename matches any of our connections
                for (MailConnection conn : connections) {
                    if (conn.isActive() && conn.getFileName().equalsIgnoreCase(changedName)) {
                        LOG.info("[MailWatcher] Relevant file change detected: " + conn.getFilePath());
                        notifyListeners(conn);
                    }
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                LOG.warning("[MailWatcher] Watch key invalidated");
                break;
            }
        }
        LOG.info("[MailWatcher] Watch loop ended");
    }

    private void notifyListeners(MailConnection connection) {
        for (FileChangeListener listener : listeners) {
            try {
                listener.onMailFileChanged(connection);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[MailWatcher] Listener error", e);
            }
        }
    }

    private void closeWatchService() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }
    }
}

