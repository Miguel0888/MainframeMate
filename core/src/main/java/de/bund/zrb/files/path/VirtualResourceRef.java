package de.bund.zrb.files.path;

import java.io.File;

/**
 * Lightweight reference to a virtual resource (local file path or URI).
 *
 * Contract:
 * - If {@link #isUri()} is true, {@link #raw()} contains a URI like "file:///C:/Temp/x.txt".
 * - If {@link #isWindowsAbsolutePath()} / {@link #isUnixAbsolutePath()} is true, {@link #raw()} contains an absolute OS path.
 * - If {@link #isFtpPath()} is true, {@link #raw()} starts with "ftp:" prefix (e.g. "ftp:/", "ftp:/dir/file.txt").
 *
 * This is used by the UI / tool layer to decide whether to open a LOCAL tab or an FTP tab.
 */
public final class VirtualResourceRef {

    /** Internal prefix to explicitly mark FTP paths, avoiding confusion with local "/" on Unix */
    public static final String FTP_PREFIX = "ftp:";

    private final String raw;

    private VirtualResourceRef(String raw) {
        this.raw = raw == null ? "" : raw;
    }

    public static VirtualResourceRef of(String raw) {
        return new VirtualResourceRef(raw);
    }

    public String raw() {
        return raw;
    }

    /**
     * Check if this is an explicit FTP path (starts with "ftp:" prefix).
     * Example: "ftp:/" represents FTP root, "ftp:/dir/file.txt" represents FTP path.
     */
    public boolean isFtpPath() {
        String s = raw.trim().toLowerCase();
        return s.startsWith(FTP_PREFIX.toLowerCase());
    }

    /**
     * Get the FTP path without the "ftp:" prefix.
     * Example: "ftp:/dir/file.txt" returns "/dir/file.txt"
     *          "ftp:/" returns "/"
     */
    public String getFtpPath() {
        if (!isFtpPath()) {
            return raw;
        }
        String path = raw.trim().substring(FTP_PREFIX.length());
        // Handle "ftp://" (double slash) gracefully -> treat as "/"
        if (path.startsWith("/") && path.length() > 1 && path.charAt(1) == '/') {
            path = path.substring(1);
        }
        return path.isEmpty() ? "/" : path;
    }

    public boolean isUri() {
        if (isFtpPath()) {
            return false; // ftp: prefix is not a standard URI
        }
        String s = raw.trim().toLowerCase();
        return s.contains("://") || s.startsWith("file:");
    }

    public boolean isFileUri() {
        String s = raw.trim().toLowerCase();
        return s.startsWith("file:");
    }

    public boolean isWindowsAbsolutePath() {
        if (isFtpPath()) {
            return false;
        }
        String s = raw.trim();
        // "C:\..." or "C:/..."
        return s.length() >= 3
                && Character.isLetter(s.charAt(0))
                && s.charAt(1) == ':'
                && (s.charAt(2) == '\\' || s.charAt(2) == '/');
    }

    public boolean isUnixAbsolutePath() {
        if (isFtpPath()) {
            return false;
        }
        String s = raw.trim();
        return s.startsWith("/");
    }

    public boolean isLocalAbsolutePath() {
        return isWindowsAbsolutePath() || isUnixAbsolutePath();
    }

    public String normalizeLocalAbsolutePath() {
        if (!isLocalAbsolutePath()) {
            return raw;
        }
        return new File(raw.trim()).getAbsoluteFile().toPath().normalize().toString();
    }
}

