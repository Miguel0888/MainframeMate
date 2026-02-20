package de.bund.zrb.ndv;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSystemFile;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Thread-safe service layer around {@link NdvClient}.
 * <p>
 * The PAL library (NATSPOD protocol) uses a single TCP socket and is
 * <b>not</b> thread-safe. All operations must be serialised. This service
 * provides that serialisation via {@code synchronized} blocks so that
 * callers (UI, SwingWorkers, FileService) can share one connection safely.
 * <p>
 * Typical lifecycle:
 * <pre>
 *   NdvService svc = new NdvService();
 *   svc.connect(host, port, user, password);   // blocking, done in SwingWorker
 *   svc.resolveSystemFiles();                  // optional pre-cache
 *   List&lt;String&gt; libs = svc.listLibraries("*");
 *   svc.logon("MYLIB");
 *   String src = svc.readSource("MYLIB", objInfo);
 *   svc.close();
 * </pre>
 */
public class NdvService implements Closeable {

    private final NdvClient client;
    private final Object lock = new Object();

    // Cached after first resolve
    private volatile IPalTypeSystemFile defaultSysFile;

    public NdvService() {
        this.client = new NdvClient();
    }

    // ───────── connection lifecycle ─────────

    /**
     * Connect to the NDV server. Blocking – call from a worker thread.
     */
    public void connect(String host, int port, String user, String password)
            throws IOException, NdvException {
        synchronized (lock) {
            client.connect(host, port, user, password);
        }
    }

    /**
     * Logon to a Natural library.
     */
    public void logon(String library) throws IOException, NdvException {
        synchronized (lock) {
            client.logon(library);
        }
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            client.close();
        }
    }

    // ───────── system files ─────────

    /**
     * Pre-resolve and cache system files. Safe to call multiple times.
     */
    public IPalTypeSystemFile[] resolveSystemFiles() throws IOException, NdvException {
        synchronized (lock) {
            return client.getSystemFiles();
        }
    }

    /**
     * Get a resolved system file suitable for listing operations.
     * Prefers FUSER, falls back to first valid, then server default (0/0/0).
     */
    public IPalTypeSystemFile getListingSysFile() {
        if (defaultSysFile != null) {
            return defaultSysFile;
        }
        try {
            synchronized (lock) {
                IPalTypeSystemFile[] files = client.getSystemFiles();
                defaultSysFile = pickBestSysFile(files);
                return defaultSysFile;
            }
        } catch (Exception e) {
            System.err.println("[NdvService] getListingSysFile failed, using default: " + e.getMessage());
            return client.getDefaultSystemFile();
        }
    }

    private static IPalTypeSystemFile pickBestSysFile(IPalTypeSystemFile[] sysFiles) {
        if (sysFiles == null || sysFiles.length == 0) {
            return null;
        }
        IPalTypeSystemFile fuserFile = null;
        IPalTypeSystemFile firstValid = null;
        for (IPalTypeSystemFile sf : sysFiles) {
            if (sf.getKind() == IPalTypeSystemFile.FUSER
                    && sf.getDatabaseId() > 0 && sf.getFileNumber() > 0
                    && fuserFile == null) {
                fuserFile = sf;
            }
            if (firstValid == null && sf.getDatabaseId() > 0 && sf.getFileNumber() > 0) {
                firstValid = sf;
            }
        }
        if (fuserFile != null) return fuserFile;
        if (firstValid != null) return firstValid;
        return sysFiles[0];
    }

    // ───────── browsing ─────────

    /**
     * List all libraries (blocking, all pages).
     */
    public List<String> listLibraries(String filter) throws IOException, NdvException {
        IPalTypeSystemFile sf = getListingSysFile();
        synchronized (lock) {
            return client.listLibraries(sf, filter);
        }
    }

    /**
     * List objects in a library with page-by-page callbacks.
     * The callback is invoked <b>inside</b> the lock – keep it lightweight
     * (just collect / publish to Swing, no further PAL calls).
     */
    public int listObjectsProgressive(String library, String filter,
                                      int kind, int type,
                                      NdvClient.PageCallback callback)
            throws IOException, NdvException {
        IPalTypeSystemFile sf = getListingSysFile();
        synchronized (lock) {
            return client.listObjectsProgressive(sf, library, filter, kind, type, callback);
        }
    }

    // ───────── source read / write ─────────

    /**
     * Read source code of a Natural object.
     */
    public String readSource(String library, NdvObjectInfo objInfo)
            throws IOException, NdvException {
        synchronized (lock) {
            return client.readSource(library, objInfo);
        }
    }

    /**
     * Write (upload) source code.
     */
    public void writeSource(String library, NdvObjectInfo objInfo, String sourceText)
            throws IOException, NdvException {
        synchronized (lock) {
            client.writeSource(library, objInfo, sourceText);
        }
    }

    // ───────── path resolution ─────────

    /**
     * Result of resolving an NDV path string into its components.
     * Determines whether the path refers to a library (DIRECTORY) or
     * a specific Natural object (FILE).
     */
    public static final class ResolvedNdvPath {
        /** The kind of NDV resource this path resolves to. */
        public enum Kind { LIBRARY, OBJECT, UNKNOWN }

        private final Kind kind;
        private final String library;
        private final NdvObjectInfo objectInfo;
        private final String rawPath;

        private ResolvedNdvPath(Kind kind, String rawPath, String library, NdvObjectInfo objectInfo) {
            this.kind = kind;
            this.rawPath = rawPath;
            this.library = library;
            this.objectInfo = objectInfo;
        }

        public static ResolvedNdvPath library(String rawPath, String library) {
            return new ResolvedNdvPath(Kind.LIBRARY, rawPath, library, null);
        }

        public static ResolvedNdvPath object(String rawPath, String library, NdvObjectInfo obj) {
            return new ResolvedNdvPath(Kind.OBJECT, rawPath, library, obj);
        }

        public static ResolvedNdvPath unknown(String rawPath) {
            return new ResolvedNdvPath(Kind.UNKNOWN, rawPath, null, null);
        }

        /** The resolved kind: LIBRARY, OBJECT or UNKNOWN. */
        public Kind getKind()              { return kind; }
        /** The library name (uppercase), or null if unknown. */
        public String getLibrary()         { return library; }
        /** The resolved NdvObjectInfo, or null if this is a library/unknown. */
        public NdvObjectInfo getObjectInfo(){ return objectInfo; }
        /** The original raw path string. */
        public String getRawPath()         { return rawPath; }
        /** Convenience: true if this resolves to a Natural object (FILE). */
        public boolean isFile()            { return kind == Kind.OBJECT; }
        /** Convenience: true if this resolves to a library (DIRECTORY). */
        public boolean isDirectory()       { return kind == Kind.LIBRARY; }
    }

    /**
     * Parse an NDV raw path (e.g. {@code "ABAK-T/#BHOBICP.NSP"}) and determine
     * whether it refers to a library (DIRECTORY) or an object (FILE).
     * <p>
     * Path format:
     * <ul>
     *   <li>{@code "LIBRARY"} &rarr; library (DIRECTORY)</li>
     *   <li>{@code "LIBRARY/OBJECTNAME"} &rarr; object (FILE), type resolved from extension if present</li>
     *   <li>{@code "LIBRARY/OBJECTNAME.EXT"} &rarr; object (FILE), type resolved from extension</li>
     * </ul>
     * <p>
     * This method does <b>not</b> contact the server. It is a pure string parse
     * that creates an {@link NdvObjectInfo} via {@link NdvObjectInfo#forBookmark}.
     */
    public ResolvedNdvPath resolvePath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return ResolvedNdvPath.unknown(rawPath);
        }
        rawPath = rawPath.trim();

        int slash = rawPath.indexOf('/');
        if (slash < 0) {
            // No slash: entire string is a library name
            return ResolvedNdvPath.library(rawPath, rawPath.toUpperCase());
        }

        String library = rawPath.substring(0, slash).toUpperCase();
        String objectPart = rawPath.substring(slash + 1).trim();

        if (objectPart.isEmpty()) {
            // Trailing slash: library only
            return ResolvedNdvPath.library(rawPath, library);
        }

        // Split object name and optional extension (e.g. "#BHOBICP.NSP")
        String objectName;
        String extension;
        int dot = objectPart.lastIndexOf('.');
        if (dot > 0) {
            objectName = objectPart.substring(0, dot);
            extension = objectPart.substring(dot + 1);
        } else {
            objectName = objectPart;
            extension = "";
        }

        NdvObjectInfo objInfo = NdvObjectInfo.forBookmark(objectName, extension);
        return ResolvedNdvPath.object(rawPath, library, objInfo);
    }

    /**
     * Parse an NDV raw path with rich bookmark metadata (DBID/FNR, objectType, extension).
     * <p>
     * If the bookmark carries valid DBID/FNR (&gt; 0), a concrete {@link NdvObjectInfo}
     * is created with those values. Otherwise a minimal NdvObjectInfo is built and
     * DBID/FNR fallback resolution will happen inside {@link NdvClient#readSource}.
     *
     * @param rawPath          raw path from bookmark (e.g. "ABAK-T/#BHOBICP.NSP")
     * @param ndvObjectType    Natural object type id (e.g. ObjectType.PROGRAM), or 0 for auto-detect
     * @param ndvTypeExtension file extension (e.g. "NSP"), or null/empty for auto-detect
     * @param ndvDbid          Adabas database id from listing, or &le; 0 for unresolved
     * @param ndvFnr           Adabas file number from listing, or &le; 0 for unresolved
     */
    @SuppressWarnings("unchecked")
    public ResolvedNdvPath resolvePath(String rawPath, int ndvObjectType, String ndvTypeExtension,
                                       int ndvDbid, int ndvFnr) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return ResolvedNdvPath.unknown(rawPath);
        }
        rawPath = rawPath.trim();

        int slash = rawPath.indexOf('/');
        if (slash < 0) {
            return ResolvedNdvPath.library(rawPath, rawPath.toUpperCase());
        }

        String library = rawPath.substring(0, slash).toUpperCase();
        String objectPart = rawPath.substring(slash + 1).trim();
        if (objectPart.isEmpty()) {
            return ResolvedNdvPath.library(rawPath, library);
        }

        // Strip extension from object part for the name
        String objectName;
        int dot = objectPart.lastIndexOf('.');
        if (dot > 0) {
            objectName = objectPart.substring(0, dot);
        } else {
            objectName = objectPart;
        }

        // Use provided metadata to build a richer NdvObjectInfo
        String ext = ndvTypeExtension != null ? ndvTypeExtension : "";
        int type = ndvObjectType > 0 ? ndvObjectType : NdvObjectInfo.typeFromExtension(ext);

        java.util.Hashtable<Integer, String> idNames =
                com.softwareag.naturalone.natural.pal.external.ObjectType.getInstanceIdName();
        String typeName = idNames.containsKey(type) ? (String) idNames.get(type) : "Unknown";

        NdvObjectInfo objInfo = new NdvObjectInfo(
                objectName, objectName,
                com.softwareag.naturalone.natural.pal.external.ObjectKind.SOURCE,
                type, typeName, ext,
                0, "", "",
                ndvDbid, ndvFnr
        );
        return ResolvedNdvPath.object(rawPath, library, objInfo);
    }

    // ───────── accessors ─────────

    public String getHost() { return client.getHost(); }
    public int    getPort() { return client.getPort(); }
    public String getUser() { return client.getUser(); }
    public String getCurrentLibrary() { return client.getCurrentLibrary(); }

    /**
     * Access the underlying client (e.g. for NdvResourceState).
     * <b>Warning:</b> callers must not invoke PAL operations on the returned
     * client directly – always go through this service.
     * @deprecated use service methods instead; only kept for NdvResourceState/NdvFileService migration
     */
    @Deprecated
    public NdvClient getClient() { return client; }
}
