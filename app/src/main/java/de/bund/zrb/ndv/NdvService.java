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

