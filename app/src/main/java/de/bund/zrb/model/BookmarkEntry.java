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
    public static final String PREFIX_SHAREPOINT = "sp://";
    public static final String PREFIX_CONFLUENCE = "confluence://";
    public static final String PREFIX_WIKI       = "wiki://";

    /** Meta-prefix for search bookmarks (prepended to a backend prefix, e.g. "search-wiki://query"). */
    public static final String SEARCH_PREFIX = "search-";

    /** All known backend prefixes (order matters — longer/more-specific first where relevant). */
    private static final String[] ALL_PREFIXES = {
            PREFIX_CONFLUENCE, PREFIX_SHAREPOINT, PREFIX_BETAVIEW,
            PREFIX_TN3270, PREFIX_LOCAL, PREFIX_MAIL, PREFIX_WIKI,
            PREFIX_HTTPS, PREFIX_HTTP, PREFIX_NDV, PREFIX_FTP
    };

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
        if ("FTP".equals(backendType))   return prefixIfNeeded(PREFIX_FTP, rawPath);
        if ("NDV".equals(backendType))   return prefixIfNeeded(PREFIX_NDV, rawPath);
        if ("MAIL".equals(backendType))     return prefixIfNeeded(PREFIX_MAIL, rawPath);
        if ("BETAVIEW".equals(backendType)) return prefixIfNeeded(PREFIX_BETAVIEW, rawPath);
        if ("TN3270".equals(backendType))  return prefixIfNeeded(PREFIX_TN3270, rawPath);
        if ("BROWSER".equals(backendType)) return rawPath; // URLs already have http(s):// scheme
        if ("SHAREPOINT".equals(backendType)) return prefixIfNeeded(PREFIX_SHAREPOINT, rawPath);
        if ("CONFLUENCE".equals(backendType)) return prefixIfNeeded(PREFIX_CONFLUENCE, rawPath);
        if ("WIKI".equals(backendType))       return prefixIfNeeded(PREFIX_WIKI, rawPath);
        // LOCAL or unknown: prefix with local://
        return prefixIfNeeded(PREFIX_LOCAL, rawPath);
    }

   /** Add the prefix only if rawPath does not already start with any known prefix. */
    private static String prefixIfNeeded(String prefix, String rawPath) {
        if (hasKnownPrefix(rawPath)) return rawPath;
        return prefix + rawPath;
    }

    /** Returns {@code true} if the path starts with any known protocol or search-* prefix. */
    private static boolean hasKnownPrefix(String path) {
        if (path == null) return false;
        if (path.startsWith(SEARCH_PREFIX)) return true;
        for (String p : ALL_PREFIXES) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if this bookmark is a search bookmark (path starts with "search-").
     */
    public boolean isSearch() {
        return path != null && path.startsWith(SEARCH_PREFIX);
    }

    /**
     * For search bookmarks, returns the search query (the raw path after stripping "search-<prefix>").
     * For non-search bookmarks returns {@code null}.
     */
    public String getSearchQuery() {
        if (!isSearch()) return null;
        return getRawPath();
    }

    /**
     * Extract the raw path (without protocol prefix).
     * For search bookmarks like "search-wiki://query", strips "search-wiki://".
     * Legacy bookmarks without prefix are returned as-is.
     */
    public String getRawPath() {
        if (path == null) return null;
        // Handle search-* prefixes: strip "search-" then the backend prefix
        String effective = path;
        if (effective.startsWith(SEARCH_PREFIX)) {
            effective = effective.substring(SEARCH_PREFIX.length());
        }
        if (effective.startsWith(PREFIX_LOCAL)) return effective.substring(PREFIX_LOCAL.length());
        if (effective.startsWith(PREFIX_FTP))   return effective.substring(PREFIX_FTP.length());
        if (effective.startsWith(PREFIX_NDV))   return effective.substring(PREFIX_NDV.length());
        if (effective.startsWith(PREFIX_MAIL))     return effective.substring(PREFIX_MAIL.length());
        if (effective.startsWith(PREFIX_BETAVIEW)) return effective.substring(PREFIX_BETAVIEW.length());
        if (effective.startsWith(PREFIX_TN3270))   return effective.substring(PREFIX_TN3270.length());
        if (effective.startsWith(PREFIX_SHAREPOINT)) return effective.substring(PREFIX_SHAREPOINT.length());
        if (effective.startsWith(PREFIX_CONFLUENCE)) return effective.substring(PREFIX_CONFLUENCE.length());
        if (effective.startsWith(PREFIX_WIKI))       return effective.substring(PREFIX_WIKI.length());
        // BROWSER: http/https URLs are returned as-is (the URL IS the raw path)
        if (effective.startsWith(PREFIX_HTTP) || effective.startsWith(PREFIX_HTTPS)) return effective;
        // Legacy: no prefix – treat as local
        return effective;
    }

    /**
     * Get the backend type string from the protocol prefix.
     * For search bookmarks like "search-wiki://query", returns "WIKI" (same as non-search).
     * Legacy bookmarks without prefix return "LOCAL".
     */
    public String getBackendType() {
        if (path == null) return "LOCAL";
        // Strip search- meta-prefix for backend detection
        String effective = path;
        if (effective.startsWith(SEARCH_PREFIX)) {
            effective = effective.substring(SEARCH_PREFIX.length());
        }
        if (effective.startsWith(PREFIX_FTP))   return "FTP";
        if (effective.startsWith(PREFIX_NDV))   return "NDV";
        if (effective.startsWith(PREFIX_MAIL))     return "MAIL";
        if (effective.startsWith(PREFIX_BETAVIEW)) return "BETAVIEW";
        if (effective.startsWith(PREFIX_TN3270))   return "TN3270";
        if (effective.startsWith(PREFIX_SHAREPOINT)) return "SHAREPOINT";
        if (effective.startsWith(PREFIX_CONFLUENCE)) return "CONFLUENCE";
        if (effective.startsWith(PREFIX_WIKI))       return "WIKI";
        if (effective.startsWith(PREFIX_HTTP) || effective.startsWith(PREFIX_HTTPS)) return "BROWSER";
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

