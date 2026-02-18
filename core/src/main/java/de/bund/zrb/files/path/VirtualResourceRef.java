package de.bund.zrb.files.path;

import java.io.File;

/**
 * Lightweight reference to a virtual resource (local file path or URI).
 *
 * Contract:
 * - If {@link #isUri()} is true, {@link #raw()} contains a URI like "file:///C:/Temp/x.txt".
 * - If {@link #isWindowsAbsolutePath()} / {@link #isUnixAbsolutePath()} is true, {@link #raw()} contains an absolute OS path.
 *
 * This is used by the UI / tool layer to decide whether to open a LOCAL tab or an FTP tab.
 */
public final class VirtualResourceRef {

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

    public boolean isUri() {
        String s = raw.trim().toLowerCase();
        return s.contains("://") || s.startsWith("file:");
    }

    public boolean isFileUri() {
        String s = raw.trim().toLowerCase();
        return s.startsWith("file:");
    }

    public boolean isWindowsAbsolutePath() {
        String s = raw.trim();
        // "C:\..." or "C:/..."
        return s.length() >= 3
                && Character.isLetter(s.charAt(0))
                && s.charAt(1) == ':'
                && (s.charAt(2) == '\\' || s.charAt(2) == '/');
    }

    public boolean isUnixAbsolutePath() {
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

