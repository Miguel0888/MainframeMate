package de.bund.zrb.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BookmarkEntry {
    public String id = UUID.randomUUID().toString();
    public String label;
    public String path;      // protocol-prefixed: "local://C:\file", "ftp:///path", "ndv://LIB/OBJ"
    public boolean folder;
    public String resourceKind; // "FILE" or "DIRECTORY" – null treated as FILE for leaf bookmarks
    public List<BookmarkEntry> children;

    // Known protocol prefixes
    public static final String PREFIX_LOCAL = "local://";
    public static final String PREFIX_FTP   = "ftp://";
    public static final String PREFIX_NDV   = "ndv://";

    public BookmarkEntry() {
        // Für GSON
    }

    public BookmarkEntry(String label, String path, boolean folder) {
        this.label = label;
        this.path = path;
        this.folder = folder;
        if (folder) this.children = new ArrayList<>();
    }

    public boolean isLeaf() {
        return !folder;
    }

    /**
     * Build a protocol-prefixed bookmark path from backend type + raw path.
     */
    public static String buildPath(String backendType, String rawPath) {
        if (rawPath == null) return null;
        if ("FTP".equals(backendType))   return PREFIX_FTP + rawPath;
        if ("NDV".equals(backendType))   return PREFIX_NDV + rawPath;
        // LOCAL or unknown: prefix with local://
        return PREFIX_LOCAL + rawPath;
    }

    /**
     * Extract the raw path (without protocol prefix).
     * Legacy bookmarks without prefix are returned as-is.
     */
    public String getRawPath() {
        if (path == null) return null;
        if (path.startsWith(PREFIX_LOCAL)) return path.substring(PREFIX_LOCAL.length());
        if (path.startsWith(PREFIX_FTP))   return path.substring(PREFIX_FTP.length());
        if (path.startsWith(PREFIX_NDV))   return path.substring(PREFIX_NDV.length());
        // Legacy: no prefix – treat as local
        return path;
    }

    /**
     * Get the backend type string from the protocol prefix.
     * Legacy bookmarks without prefix return "LOCAL".
     */
    public String getBackendType() {
        if (path == null) return "LOCAL";
        if (path.startsWith(PREFIX_FTP))   return "FTP";
        if (path.startsWith(PREFIX_NDV))   return "NDV";
        return "LOCAL";
    }

    /**
     * Returns the path suitable for passing to openFileOrDirectory().
     * For FTP bookmarks this adds the 'ftp:' routing prefix that VirtualResourceResolver expects.
     */
    public String getRoutingPath() {
        if (path == null) return "";
        String raw = getRawPath();
        String backend = getBackendType();
        if ("FTP".equals(backend)) {
            return "ftp:" + raw;    // VirtualResourceResolver expects 'ftp:/path'
        }
        // LOCAL paths are passed as-is
        return raw;
    }

    @Override
    public String toString() {
        return label;
    }
}

