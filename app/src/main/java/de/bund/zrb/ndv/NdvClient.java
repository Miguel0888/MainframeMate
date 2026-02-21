package de.bund.zrb.ndv;

import com.softwareag.naturalone.natural.pal.external.ConnectKey;
import com.softwareag.naturalone.natural.pal.external.IPalTransactions;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibrary;
import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSystemFile;
import com.softwareag.naturalone.natural.pal.external.ObjectProperties;
import com.softwareag.naturalone.natural.pal.external.PalConnectResultException;
import com.softwareag.naturalone.natural.pal.external.PalResultException;
import com.softwareag.naturalone.natural.pal.external.PalTransactionsFactory;
import com.softwareag.naturalone.natural.pal.external.PalTypeSystemFileFactory;
import com.softwareag.naturalone.natural.paltransactions.external.EDownLoadOption;
import com.softwareag.naturalone.natural.paltransactions.external.EUploadOption;
import com.softwareag.naturalone.natural.paltransactions.external.IDownloadResult;
import com.softwareag.naturalone.natural.paltransactions.external.IFileProperties;
import com.softwareag.naturalone.natural.paltransactions.external.ITransactionContextDownload;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * Wrapper around the NDV (Natural Development Server) PAL Transactions API.
 * Compiles against stubs in the ndv module; the real ndvserveraccess JAR is
 * loaded at runtime via NdvProxyBridge.
 */
public class NdvClient implements Closeable {

    private IPalTransactions pal;
    private String host;
    private int port;
    private String user;
    private String currentLibrary;
    private volatile boolean connected;

    public NdvClient() throws NdvException {
        NdvLibLoader.ensureLoaded();
        this.pal = PalTransactionsFactory.newInstance();
    }

    /**
     * Connect to the NDV server.
     */
    public void connect(String host, int port, String user, String password) throws IOException, NdvException {
        this.host = host;
        this.port = port;
        this.user = user.toUpperCase();

        Map<String, String> params = new HashMap<String, String>();
        params.put(ConnectKey.HOST, host);
        params.put(ConnectKey.PORT, String.valueOf(port));
        params.put(ConnectKey.USERID, this.user);
        params.put(ConnectKey.PASSWORD, password);
        params.put(ConnectKey.PARM, "CFICU=ON,CP=IBM01141");

        // The NDV library sends Charset.defaultCharset().name() as the client codepage.
        // On modern JVMs this is "UTF-8" which the mainframe rejects (NAT7734 "CP UTF-8 not SBCS").
        // Temporarily override file.encoding and reset the cached default charset
        // so the lib reports a SBCS codepage during connect.
        String originalEncoding = System.getProperty("file.encoding");
        try {
            System.setProperty("file.encoding", "ISO-8859-1");
            // Reset the cached default charset in java.nio.charset.Charset (Java 8)
            try {
                java.lang.reflect.Field defaultCharset = java.nio.charset.Charset.class.getDeclaredField("defaultCharset");
                defaultCharset.setAccessible(true);
                defaultCharset.set(null, null);
            } catch (Exception ignored) {
                // If reflection fails, the system property change alone might still work
            }
            pal.connect(params);
            connected = true;
            System.out.println("[NdvClient] Connected to " + host + ":" + port + " as " + this.user);
        } catch (PalResultException e) {
            // PalConnectResultException extends PalResultException – check first
            if (e instanceof PalConnectResultException) {
                throw new NdvException("NDV-Login fehlgeschlagen: " + e.getMessage(), e);
            }
            // Network errors are wrapped by the proxy – inspect cause
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof java.net.ConnectException) {
                throw new NdvException(
                        "Verbindung abgelehnt: " + host + ":" + port
                        + "\n\nMögliche Ursachen:"
                        + "\n• NDV-Server läuft nicht oder ist nicht erreichbar"
                        + "\n• Port " + port + " ist falsch (Standard: 2700)"
                        + "\n• Firewall/Proxy blockiert die Verbindung"
                        + "\n• VPN nicht verbunden", cause);
            }
            if (cause instanceof java.net.UnknownHostException) {
                throw new NdvException(
                        "Host nicht gefunden: " + host
                        + "\n\nBitte Hostnamen oder IP-Adresse prüfen.", cause);
            }
            throw new NdvException("Verbindungsfehler: " + e.getMessage(), e);
        } finally {
            // Restore original encoding and reset charset cache
            if (originalEncoding != null) {
                System.setProperty("file.encoding", originalEncoding);
            }
            try {
                java.lang.reflect.Field defaultCharset = java.nio.charset.Charset.class.getDeclaredField("defaultCharset");
                defaultCharset.setAccessible(true);
                defaultCharset.set(null, null);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Logon to a Natural library.
     */
    public void logon(String library) throws IOException, NdvException {
        checkConnected();
        try {
            pal.logon(library);
            this.currentLibrary = library;
            System.out.println("[NdvClient] Logged on to library: " + library);
        } catch (PalResultException e) {
            throw new NdvException("Logon to library '" + library + "' failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get the default system file (FNAT/FUSER with 0,0,0 = server defaults).
     */
    public IPalTypeSystemFile getDefaultSystemFile() {
        return PalTypeSystemFileFactory.newInstance();
    }

    /**
     * Get all available system files from the server.
     */
    public IPalTypeSystemFile[] getSystemFiles() throws IOException, NdvException {
        checkConnected();
        try {
            return pal.getSystemFiles();
        } catch (PalResultException e) {
            throw new NdvException("Failed to get system files: " + e.getMessage(), e);
        }
    }

    /**
     * List libraries on the server.
     *
     * @param sysFile system file (use getDefaultSystemFile() for defaults)
     * @param filter  filter pattern, e.g. "*" for all
     * @return list of library names
     */
    public List<String> listLibraries(IPalTypeSystemFile sysFile, String filter) throws IOException, NdvException {
        checkConnected();
        List<String> result = new ArrayList<String>();
        try {
            IPalTypeLibrary[] libs = pal.getLibrariesFirst(sysFile, filter);
            if (libs != null) {
                for (IPalTypeLibrary lib : libs) {
                    if (lib != null && lib.getLibrary() != null) {
                        result.add(lib.getLibrary().trim());
                    }
                }
            }
            // Fetch remaining pages (getLibrariesNext throws PalResultException when done)
            if (libs != null && libs.length > 0) {
                int safetyLimit = 1000; // prevent infinite loops
                for (int page = 0; page < safetyLimit; page++) {
                    try {
                        IPalTypeLibrary[] more = pal.getLibrariesNext();
                        if (more == null || more.length == 0) break;
                        for (IPalTypeLibrary lib : more) {
                            if (lib != null && lib.getLibrary() != null) {
                                result.add(lib.getLibrary().trim());
                            }
                        }
                    } catch (PalResultException done) {
                        // End of data
                        break;
                    }
                }
            }
        } catch (PalResultException e) {
            throw new NdvException("Failed to list libraries: " + e.getMessage(), e);
        }
        System.out.println("[NdvClient] Listed " + result.size() + " libraries");
        return result;
    }

    /**
     * Callback interface for progressive object listing.
     */
    public interface PageCallback {
        /** Called for each page of results. Return false to stop loading. */
        boolean onPage(List<NdvObjectInfo> pageItems, int totalSoFar);
    }

    /**
     * List objects in a library with progressive page callbacks.
     */
    public int listObjectsProgressive(IPalTypeSystemFile sysFile, String library,
                                       String filter, int kind, int type,
                                       PageCallback callback)
            throws IOException, NdvException {
        checkConnected();
        int total = 0;
        try {
            IPalTypeObject[] objects = pal.getObjectsFirst(sysFile, library, filter, kind, type);
            List<NdvObjectInfo> page = toInfoList(objects);
            total += page.size();
            System.out.println("[NdvClient] getObjectsFirst returned " + page.size() + " objects");
            if (!page.isEmpty() && !callback.onPage(page, total)) {
                return total;
            }

            if (objects != null && objects.length > 0) {
                int safetyLimit = 1000;
                for (int p = 0; p < safetyLimit; p++) {
                    try {
                        IPalTypeObject[] more = pal.getObjectsNext();
                        if (more == null || more.length == 0) break;
                        List<NdvObjectInfo> morePage = toInfoList(more);
                        total += morePage.size();
                        System.out.println("[NdvClient] getObjectsNext page " + (p + 1) + " returned " + morePage.size() + " objects");
                        if (!morePage.isEmpty() && !callback.onPage(morePage, total)) {
                            return total;
                        }
                    } catch (PalResultException done) {
                        System.out.println("[NdvClient] getObjectsNext ended (end of data)");
                        break;
                    }
                }
            }
        } catch (PalResultException e) {
            throw new NdvException("Failed to list objects in library '" + library + "': " + e.getMessage(), e);
        }
        System.out.println("[NdvClient] Listed " + total + " objects in " + library);
        return total;
    }

    /**
     * List objects in a library (blocking, returns all at once).
     */
    public List<NdvObjectInfo> listObjects(IPalTypeSystemFile sysFile, String library,
                                            String filter, int kind, int type)
            throws IOException, NdvException {
        final List<NdvObjectInfo> result = new ArrayList<NdvObjectInfo>();
        listObjectsProgressive(sysFile, library, filter, kind, type, new PageCallback() {
            @Override
            public boolean onPage(List<NdvObjectInfo> pageItems, int totalSoFar) {
                result.addAll(pageItems);
                return true;
            }
        });
        return result;
    }

    private List<NdvObjectInfo> toInfoList(IPalTypeObject[] objects) {
        List<NdvObjectInfo> list = new ArrayList<NdvObjectInfo>();
        if (objects == null) return list;
        for (IPalTypeObject obj : objects) {
            if (obj != null && obj.getName() != null) {
                list.add(NdvObjectInfo.fromPalObject(obj));
            }
        }
        return list;
    }

    private void addObjects(List<NdvObjectInfo> result, IPalTypeObject[] objects) {
        if (objects == null) return;
        for (IPalTypeObject obj : objects) {
            if (obj != null && obj.getName() != null) {
                result.add(NdvObjectInfo.fromPalObject(obj));
            }
        }
    }

    // Cached system file for downloads (resolved once, reused)
    private volatile IPalTypeSystemFile cachedDownloadSysFile;

    /**
     * Resolve a system file with real DBID/FNR for download operations.
     * Listing operations tolerate (0,0,0) but downloadSource does NOT.
     * <p>
     * Strategy:
     * 1) Prefer FUSER (kind=2) from getSystemFiles()
     * 2) Fall back to any system file with valid DBID/FNR > 0
     * 3) Fall back to first system file at all
     */
    private IPalTypeSystemFile resolveDownloadSystemFile() throws IOException, NdvException {
        if (cachedDownloadSysFile != null) {
            return cachedDownloadSysFile;
        }

        IPalTypeSystemFile[] sysFiles = getCachedSystemFiles();

        if (sysFiles == null || sysFiles.length == 0) {
            throw new NdvException("Server liefert keine SystemFiles – Download nicht möglich");
        }

        // Log all for diagnostics
        IPalTypeSystemFile fuserFile = null;
        IPalTypeSystemFile firstValid = null;

        for (int i = 0; i < sysFiles.length; i++) {
            IPalTypeSystemFile sf = sysFiles[i];
            int dbid = sf.getDatabaseId();
            int fnr = sf.getFileNumber();
            int kind = sf.getKind();
            System.out.println("[NdvClient] sysFile[" + i + "]: kind=" + kind
                    + ", dbid=" + dbid + ", fnr=" + fnr
                    + " (" + sysFileKindName(kind) + ")");

            if (kind == IPalTypeSystemFile.FUSER && dbid > 0 && fnr > 0) {
                fuserFile = sf;
            }
            if (firstValid == null && dbid > 0 && fnr > 0) {
                firstValid = sf;
            }
        }

        // Prefer FUSER, then any valid, then just first
        IPalTypeSystemFile chosen;
        if (fuserFile != null) {
            chosen = fuserFile;
            System.out.println("[NdvClient] Using FUSER system file: dbid=" + chosen.getDatabaseId()
                    + ", fnr=" + chosen.getFileNumber());
        } else if (firstValid != null) {
            chosen = firstValid;
            System.out.println("[NdvClient] No FUSER found, using first valid system file: dbid=" + chosen.getDatabaseId()
                    + ", fnr=" + chosen.getFileNumber() + ", kind=" + chosen.getKind());
        } else {
            // Last resort: use sysFiles[0] even if DBID/FNR are 0 – let the server decide
            chosen = sysFiles[0];
            System.out.println("[NdvClient] WARNING: No system file with valid DBID/FNR found! Using sysFile[0]: dbid="
                    + chosen.getDatabaseId() + ", fnr=" + chosen.getFileNumber());
        }

        cachedDownloadSysFile = chosen;
        return chosen;
    }

    private static String sysFileKindName(int kind) {
        switch (kind) {
            case IPalTypeSystemFile.FNAT:     return "FNAT";
            case IPalTypeSystemFile.FUSER:    return "FUSER";
            case IPalTypeSystemFile.INACTIVE: return "INACTIVE";
            case IPalTypeSystemFile.FSEC:     return "FSEC";
            case IPalTypeSystemFile.FDIC:     return "FDIC";
            case IPalTypeSystemFile.FDDM:     return "FDDM";
            default:                          return "UNKNOWN(" + kind + ")";
        }
    }

    /**
     * Download source code of a Natural object.
     * <p>
     * Uses the DBID/FNR from the {@code NdvObjectInfo} (which came from the listing)
     * to create the correct {@code IPalTypeSystemFile} for this specific object.
     * {@code downloadSource()} requires the exact system file where the object resides;
     * a default (0/0/0) causes NAT3017, and a "global guess" can hit the wrong file.
     *
     * @param library library name
     * @param objInfo object info (from listing, carries DBID/FNR)
     * @return source code as string (lines joined with \n)
     */
    public String readSource(String library, NdvObjectInfo objInfo)
            throws IOException, NdvException {
        checkConnected();
        if (library == null || library.isEmpty()) {
            throw new NdvException("readSource: library is null or empty");
        }
        if (objInfo == null) {
            throw new NdvException("readSource: objInfo is null");
        }

        // Ensure we are logged on to the correct library
        if (!library.equals(currentLibrary)) {
            logon(library);
        }

        // ── Step 1: Resolve system file for THIS object (not a global guess) ──
        IPalTypeSystemFile sysFile = resolveSystemFileForObject(objInfo);

        System.out.println("[NdvClient] readSource: library=" + library
                + ", obj=" + objInfo.getName() + ", type=" + objInfo.getType()
                + ", obj.dbid/fnr=" + objInfo.getDatabaseId() + "/" + objInfo.getFileNumber()
                + ", sysFile=dbid/fnr/kind=" + sysFile.getDatabaseId()
                + "/" + sysFile.getFileNumber() + "/" + sysFile.getKind());

        // ── Step 2: Create download transaction context ──
        ITransactionContextDownload ctx =
                (ITransactionContextDownload) pal.createTransactionContext(ITransactionContextDownload.class);

        try {
            IFileProperties props = new ObjectProperties.Builder(objInfo.getName(), objInfo.getType()).build();
            Set<EDownLoadOption> options = EnumSet.noneOf(EDownLoadOption.class);

            IDownloadResult result = pal.downloadSource(ctx, sysFile, library, props, options);

            if (result != null) {
                String[] lines = result.getSource();
                System.out.println("[NdvClient] readSource: got " + (lines != null ? lines.length : 0) + " lines");
                return joinLines(lines);
            }
            return "";
        } catch (PalResultException e) {
            throw new NdvException("Quellcode-Download fehlgeschlagen für '" + objInfo.getName()
                    + "' in '" + library + "' (DBID=" + sysFile.getDatabaseId()
                    + ", FNR=" + sysFile.getFileNumber() + "): " + e.getMessage(), e);
        } finally {
            try {
                pal.disposeTransactionContext(ctx);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Resolve the correct IPalTypeSystemFile for a specific object.
     * <p>
     * If the object carries valid DBID/FNR (from the listing), we build a concrete
     * system file with those exact values. The kind is looked up from the server's
     * system file list; if not found, FUSER is assumed as default.
     * <p>
     * If the object has invalid DBID/FNR (≤ 0), we fall back to the global
     * resolveDownloadSystemFile() strategy (FUSER preference).
     */
    private IPalTypeSystemFile resolveSystemFileForObject(NdvObjectInfo objInfo)
            throws IOException, NdvException {
        int dbid = objInfo.getDatabaseId();
        int fnr = objInfo.getFileNumber();

        if (dbid > 0 && fnr > 0) {
            // Object has concrete DBID/FNR from the listing – use them
            int kind = findKindByDbidFnr(dbid, fnr);
            System.out.println("[NdvClient] resolveSystemFileForObject: using object's own DBID/FNR: "
                    + dbid + "/" + fnr + ", kind=" + sysFileKindName(kind));
            return PalTypeSystemFileFactory.newInstance(dbid, fnr, kind);
        }

        // Fallback: object didn't carry valid DBID/FNR – use global strategy
        System.out.println("[NdvClient] resolveSystemFileForObject: obj DBID/FNR invalid ("
                + dbid + "/" + fnr + "), falling back to global system file");
        return resolveDownloadSystemFile();
    }

    /**
     * Look up the kind (FNAT/FUSER/FDIC/...) for a given DBID/FNR pair
     * by matching against the server's system files.
     *
     * @return the kind, or FUSER as default if no match found
     */
    private int findKindByDbidFnr(int dbid, int fnr) throws IOException, NdvException {
        IPalTypeSystemFile[] sysFiles = getCachedSystemFiles();
        if (sysFiles != null) {
            for (IPalTypeSystemFile sf : sysFiles) {
                if (sf.getDatabaseId() == dbid && sf.getFileNumber() == fnr) {
                    return sf.getKind();
                }
            }
        }
        // Not found in server's list – default to FUSER (most common for user sources)
        return IPalTypeSystemFile.FUSER;
    }

    // Cached system files (fetched once per connection)
    private volatile IPalTypeSystemFile[] cachedSystemFiles;

    private IPalTypeSystemFile[] getCachedSystemFiles() throws IOException, NdvException {
        if (cachedSystemFiles != null) {
            return cachedSystemFiles;
        }
        try {
            cachedSystemFiles = pal.getSystemFiles();
        } catch (PalResultException e) {
            System.err.println("[NdvClient] getSystemFiles failed: " + e.getMessage());
            return null;
        }
        return cachedSystemFiles;
    }

    private String joinLines(String[] lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i] != null ? lines[i] : "");
        }
        return sb.toString();
    }

    /**
     * Upload (save) source code of a Natural object back to the server.
     *
     * @param library    library name
     * @param objInfo    object info (carries name, type, DBID/FNR)
     * @param sourceText source code as single string (lines separated by \n)
     */
    public void writeSource(String library, NdvObjectInfo objInfo, String sourceText)
            throws IOException, NdvException {
        checkConnected();
        if (library == null || library.isEmpty()) {
            throw new NdvException("writeSource: library is null or empty");
        }
        if (objInfo == null) {
            throw new NdvException("writeSource: objInfo is null");
        }
        if (sourceText == null) {
            sourceText = "";
        }

        // Ensure we are logged on to the correct library
        if (!library.equals(currentLibrary)) {
            logon(library);
        }

        // Resolve system file for this object
        IPalTypeSystemFile sysFile = resolveSystemFileForObject(objInfo);

        // Split text into lines for uploadSource
        String[] sourceLines = sourceText.split("\n", -1);

        System.out.println("[NdvClient] writeSource: library=" + library
                + ", obj=" + objInfo.getName() + ", type=" + objInfo.getType()
                + ", lines=" + sourceLines.length
                + ", sysFile=dbid/fnr/kind=" + sysFile.getDatabaseId()
                + "/" + sysFile.getFileNumber() + "/" + sysFile.getKind());

        try {
            IFileProperties props = new ObjectProperties.Builder(objInfo.getName(), objInfo.getType()).build();

            // Use empty set for upload options (no special options needed for basic save)
            Set<EUploadOption> options = EnumSet.noneOf(EUploadOption.class);

            pal.uploadSource(sysFile, library, props, options, sourceLines);

            System.out.println("[NdvClient] writeSource: successfully uploaded "
                    + sourceLines.length + " lines for " + objInfo.getName());
        } catch (PalResultException e) {
            throw new NdvException("Quellcode-Upload fehlgeschlagen für '" + objInfo.getName()
                    + "' in '" + library + "': " + e.getMessage(), e);
        }
    }


    /**
     * Get the current logon library.
     */
    public String getCurrentLibrary() {
        return currentLibrary;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUser() { return user; }
    public boolean isConnected() { return connected && pal != null && pal.isConnected(); }

    @Override
    public void close() throws IOException {
        if (pal != null && connected) {
            try {
                pal.disconnect();
                System.out.println("[NdvClient] Disconnected from " + host + ":" + port);
            } catch (Exception e) {
                System.err.println("[NdvClient] Error during disconnect: " + e.getMessage());
            } finally {
                connected = false;
            }
        }
    }

    private void checkConnected() throws NdvException {
        if (!connected || pal == null) {
            throw new NdvException("Not connected to NDV server");
        }
    }
}

