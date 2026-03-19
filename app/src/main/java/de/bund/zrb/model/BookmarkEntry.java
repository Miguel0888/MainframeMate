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

    // ── NDV-specific metadata (only set for NDV FILE bookmarks) ──
    /** Natural object name (e.g. "#BHOBICP") */
    public String ndvObjectName;
    /** Natural library (e.g. "ABAK-T") */
    public String ndvLibrary;
    /** Natural object typSchluessel id (e.g. ObjectType.PROGRAM) */
    public int ndvObjectType;
    /** File extension (e.g. "NSP") */
    public String ndvTypeExtension;
    /** Adabas database id (from listing) – 0 or -1 means unresolved */
    public int ndvDbid;
    /** Adabas file number (from listing) – 0 or -1 means unresolved */
    public int ndvFnr;

    // Known protocol prefixes
    public static final String PREFIX_LOCAL = "local://";
    public static final String PREFIX_FTP   = "ftp://";
    public static final String PREFIX_NDV   = "ndv://";
    public static final String PREFIX_MAIL     = "mail://";
    public static final String PREFIX_BETAVIEW = "betaview://";
    public static final String PREFIX_TN3270   = "tn3270://";
    public static final String PREFIX_HTTP     = "http://";
    public static final String PREFIX_HTTPS    = "https://";

    // ── TN3270 macro bookmark metadata ──
    /** Recorded macro steps as JSON array string, e.g. [{"type":"TEXT","value":"a"},{"type":"AID","value":"ENTER"}] */
    public String tn3270MacroSteps;

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
     * Build a protocol-prefixed bookmark path from backend typSchluessel + raw path.
     */
    public static String buildPath(String backendType, String rawPath) {
        if (rawPath == null) return null;
        if ("FTP".equals(backendType))   return PREFIX_FTP + rawPath;
        if ("NDV".equals(backendType))   return PREFIX_NDV + rawPath;
        if ("MAIL".equals(backendType))     return PREFIX_MAIL + rawPath;
        if ("BETAVIEW".equals(backendType)) return PREFIX_BETAVIEW + rawPath;
        if ("TN3270".equals(backendType))  return PREFIX_TN3270 + rawPath;
        if ("BROWSER".equals(backendType)) return rawPath; // URLs already have http(s):// scheme
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
        if (path.startsWith(PREFIX_MAIL))     return path.substring(PREFIX_MAIL.length());
        if (path.startsWith(PREFIX_BETAVIEW)) return path.substring(PREFIX_BETAVIEW.length());
        if (path.startsWith(PREFIX_TN3270))   return path.substring(PREFIX_TN3270.length());
        // BROWSER: http/https URLs are returned as-is (the URL IS the raw path)
        if (path.startsWith(PREFIX_HTTP) || path.startsWith(PREFIX_HTTPS)) return path;
        // Legacy: no prefix – treat as local
        return path;
    }

    /**
     * Get the backend typSchluessel string from the protocol prefix.
     * Legacy bookmarks without prefix return "LOCAL".
     */
    public String getBackendType() {
        if (path == null) return "LOCAL";
        if (path.startsWith(PREFIX_FTP))   return "FTP";
        if (path.startsWith(PREFIX_NDV))   return "NDV";
        if (path.startsWith(PREFIX_MAIL))     return "MAIL";
        if (path.startsWith(PREFIX_BETAVIEW)) return "BETAVIEW";
        if (path.startsWith(PREFIX_TN3270))   return "TN3270";
        if (path.startsWith(PREFIX_HTTP) || path.startsWith(PREFIX_HTTPS)) return "BROWSER";
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
        if ("BETAVIEW".equals(backend)) {
            return path; // betaview:// paths are passed as-is
        }
        if ("BROWSER".equals(backend)) {
            return path; // http(s):// URLs are passed as-is
        }
        // LOCAL paths are passed as-is
        return raw;
    }

    @Override
    public String toString() {
        return label;
    }
}

