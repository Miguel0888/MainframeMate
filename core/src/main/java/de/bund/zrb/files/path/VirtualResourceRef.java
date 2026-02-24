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
    /** Prefix for local paths (used by bookmarks and search results) */
    public static final String LOCAL_PREFIX = "local://";
    /** Prefix for mail paths (OST/PST mail resources) */
    public static final String MAIL_PREFIX = "mail://";
    /** Prefix for NDV paths */
    public static final String NDV_PREFIX = "ndv://";

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
        if (isFtpPath() || isLocalPrefixed() || isMailPath() || isNdvPath()) {
            return false; // these are handled by their own prefixes
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
        return isLocalPrefixed() || isWindowsAbsolutePath() || isUnixAbsolutePath();
    }

    public String normalizeLocalAbsolutePath() {
        if (!isLocalAbsolutePath()) {
            return raw;
        }
        return new File(raw.trim()).getAbsoluteFile().toPath().normalize().toString();
    }

    /**
     * Check if this path uses the explicit local:// prefix (from bookmarks/search results).
     */
    public boolean isLocalPrefixed() {
        return raw.trim().toLowerCase().startsWith(LOCAL_PREFIX.toLowerCase());
    }

    /**
     * Get the local path without the local:// prefix.
     */
    public String getLocalPath() {
        if (!isLocalPrefixed()) return raw;
        return raw.trim().substring(LOCAL_PREFIX.length());
    }

    /**
     * Check if this is a mail resource path (mail:// prefix).
     */
    public boolean isMailPath() {
        return raw.trim().toLowerCase().startsWith(MAIL_PREFIX.toLowerCase());
    }

    /**
     * Get the mail path without the mail:// prefix.
     */
    public String getMailPath() {
        if (!isMailPath()) return raw;
        return raw.trim().substring(MAIL_PREFIX.length());
    }

    /**
     * Check if this is an NDV path (ndv:// prefix).
     */
    public boolean isNdvPath() {
        return raw.trim().toLowerCase().startsWith(NDV_PREFIX.toLowerCase());
    }

    /**
     * Get the NDV path without the ndv:// prefix.
     */
    public String getNdvPath() {
        if (!isNdvPath()) return raw;
        return raw.trim().substring(NDV_PREFIX.length());
    }

    /**
     * Build a prefixed path from a backend type and raw path.
     */
    public static String buildPrefixedPath(String backendType, String rawPath) {
        if (rawPath == null) return null;
        switch (backendType) {
            case "FTP":  return FTP_PREFIX + rawPath;
            case "NDV":  return NDV_PREFIX + rawPath;
            case "MAIL": return MAIL_PREFIX + rawPath;
            default:     return LOCAL_PREFIX + rawPath;
        }
    }
}

