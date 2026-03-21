package de.bund.zrb.mail.service;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Represents the technical connection to a single PST/OST mail store file.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Configuration (file path, active flag, display name)</li>
 *   <li>Validation (file exists, is readable)</li>
 *   <li>File signature tracking (size + lastModified)</li>
 * </ul>
 * <p>
 * Does NOT contain business logic for delta detection, indexing, or UI.
 */
public class MailConnection {

    private static final Logger LOG = Logger.getLogger(MailConnection.class.getName());

    /** Unique connection identifier. */
    private final String connectionId;
    /** Absolute path to the PST/OST file. */
    private final String filePath;
    /** Human-readable display name. */
    private final String displayName;
    /** Whether this connection is active (should be watched and synced). */
    private volatile boolean active;

    // ── Last known file signature (for cheap change detection) ──
    private volatile long lastKnownSize = -1;
    private volatile long lastKnownModified = -1;

    // ═══════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════

    public MailConnection(String filePath) {
        this(UUID.randomUUID().toString(), filePath, guessDisplayName(filePath), true);
    }

    public MailConnection(String connectionId, String filePath, String displayName, boolean active) {
        this.connectionId = connectionId;
        this.filePath = filePath;
        this.displayName = displayName;
        this.active = active;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Validation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check whether the configured PST/OST file exists and is readable.
     */
    public boolean isValid() {
        File f = getFile();
        return f.isFile() && f.canRead();
    }

    /**
     * Returns the type of mail store based on file extension.
     */
    public String getStoreType() {
        if (filePath.toLowerCase().endsWith(".pst")) return "PST";
        if (filePath.toLowerCase().endsWith(".ost")) return "OST";
        return "UNKNOWN";
    }

    /**
     * Whether file-system watching is supported for this connection type.
     * PST/OST on local disk → always true.
     */
    public boolean supportsWatching() {
        return isValid();
    }

    // ═══════════════════════════════════════════════════════════════
    //  File signature (cheap change detection)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Snapshot the current file size and lastModified into internal state.
     * Call after a successful sync.
     */
    public void snapshotFileSignature() {
        File f = getFile();
        if (f.isFile()) {
            lastKnownSize = f.length();
            lastKnownModified = f.lastModified();
        }
    }

    /**
     * Check whether the file has changed since the last snapshot.
     * This is a cheap O(1) stat check — no hashing involved.
     *
     * @return {@code true} if size or lastModified differ from snapshot
     */
    public boolean hasFileChanged() {
        File f = getFile();
        if (!f.isFile()) return false;
        return f.length() != lastKnownSize || f.lastModified() != lastKnownModified;
    }

    /**
     * Returns the current file size, or -1 if unavailable.
     */
    public long getCurrentFileSize() {
        File f = getFile();
        return f.isFile() ? f.length() : -1;
    }

    /**
     * Returns the current lastModified, or -1 if unavailable.
     */
    public long getCurrentLastModified() {
        File f = getFile();
        return f.isFile() ? f.lastModified() : -1;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Getters / Setters
    // ═══════════════════════════════════════════════════════════════

    public String getConnectionId()    { return connectionId; }
    public String getFilePath()        { return filePath; }
    public String getDisplayName()     { return displayName; }
    public boolean isActive()          { return active; }
    public void setActive(boolean a)   { this.active = a; }
    public long getLastKnownSize()     { return lastKnownSize; }
    public long getLastKnownModified() { return lastKnownModified; }

    public File getFile() { return new File(filePath); }

    /**
     * Returns the parent directory of the mail store file (for WatchService registration).
     */
    public File getParentDirectory() {
        return getFile().getParentFile();
    }

    /**
     * Returns just the filename part (for WatchService event filtering).
     */
    public String getFileName() {
        return getFile().getName();
    }

    @Override
    public String toString() {
        return "MailConnection{" + displayName + " [" + getStoreType() + "] " + filePath + "}";
    }

    // ═══════════════════════════════════════════════════════════════
    //  Factory helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Build MailConnections for all PST/OST files in a directory.
     */
    public static java.util.List<MailConnection> fromDirectory(String directoryPath) {
        java.util.List<MailConnection> connections = new java.util.ArrayList<MailConnection>();
        File dir = new File(directoryPath);
        if (!dir.isDirectory()) return connections;

        File[] files = dir.listFiles();
        if (files == null) return connections;

        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (f.isFile() && (name.endsWith(".pst") || name.endsWith(".ost"))) {
                connections.add(new MailConnection(f.getAbsolutePath()));
            }
        }
        return connections;
    }

    private static String guessDisplayName(String path) {
        if (path == null) return "Unknown";
        File f = new File(path);
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

