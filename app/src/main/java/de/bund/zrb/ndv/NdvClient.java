package de.bund.zrb.ndv;

import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * Wrapper around the NDV (Natural Development Server) PAL Transactions API.
 * Provides a simple facade for connecting, browsing libraries/objects, and reading source code
 * via the NATSPOD protocol.
 */
public class NdvClient implements Closeable {

    private IPalTransactions pal;
    private String host;
    private int port;
    private String user;
    private String currentLibrary;
    private volatile boolean connected;

    public NdvClient() {
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
        // Session parameters: CFICU=ON required by server (NAT7022),
        // CP=IBM01141 = EBCDIC Germany/Austria (default from NaturalONE Eclipse plugin)
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
        } catch (java.net.ConnectException e) {
            throw new NdvException(
                    "Verbindung abgelehnt: " + host + ":" + port
                    + "\n\nMögliche Ursachen:"
                    + "\n• NDV-Server läuft nicht oder ist nicht erreichbar"
                    + "\n• Port " + port + " ist falsch (Standard: 2700)"
                    + "\n• Firewall/Proxy blockiert die Verbindung"
                    + "\n• VPN nicht verbunden", e);
        } catch (java.net.UnknownHostException e) {
            throw new NdvException(
                    "Host nicht gefunden: " + host
                    + "\n\nBitte Hostnamen oder IP-Adresse prüfen.", e);
        } catch (PalConnectResultException e) {
            throw new NdvException("NDV-Login fehlgeschlagen: " + e.getMessage(), e);
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

    /**
     * Download source code of a Natural object.
     *
     * @param sysFile  system file
     * @param library  library name
     * @param name     object name
     * @param type     object type (ObjectType.PROGRAM etc.)
     * @return source code as string (lines joined with \n)
     */
    public String readSource(IPalTypeSystemFile sysFile, String library,
                             String name, int type) throws IOException, NdvException {
        checkConnected();
        if (sysFile == null) {
            throw new NdvException("readSource: sysFile is null");
        }
        if (library == null || library.isEmpty()) {
            throw new NdvException("readSource: library is null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new NdvException("readSource: name is null or empty");
        }
        System.out.println("[NdvClient] readSource: library=" + library + ", name=" + name + ", type=" + type);

        // Ensure we are logged on to the correct library
        if (!library.equals(currentLibrary)) {
            logon(library);
        }

        // Log the downloadSource method signature for debugging
        logDownloadSourceSignature();

        IFileProperties props = new ObjectProperties.Builder(name, type).build();
        System.out.println("[NdvClient] readSource: props=" + props
                + ", props.getName()=" + props.getName()
                + ", props.getType()=" + props.getType());

        // Try downloadSource via reflection to handle the monitor parameter correctly
        // First try with null sysFile (server uses session context after logon),
        // then fallback to the provided sysFile
        IDownloadResult result = null;
        NdvException lastError = null;

        IPalTypeSystemFile[] sysFileCandidates = { null, sysFile };
        for (IPalTypeSystemFile sf : sysFileCandidates) {
            try {
                System.out.println("[NdvClient] Trying downloadSource with sysFile="
                        + (sf != null ? "dbid=" + sf.getDatabaseId() + ",fnr=" + sf.getFileNumber() : "null"));
                result = downloadSourceSafe(sf, library, props);
                if (result != null) {
                    break; // success
                }
            } catch (NdvException e) {
                System.err.println("[NdvClient] downloadSource attempt failed: " + e.getMessage());
                lastError = e;
            }
        }

        if (result == null && lastError != null) {
            throw lastError;
        }

        System.out.println("[NdvClient] readSource: downloadSource returned, result=" + (result != null ? "ok" : "null"));
        if (result != null) {
            String[] lines = null;
            try {
                lines = result.getSource();
            } catch (NullPointerException npe) {
                System.err.println("[NdvClient] result.getSource() threw NPE:");
                npe.printStackTrace();
                throw new NdvException("getSource() interner Fehler (NPE) für '" + name + "'", npe);
            }
            System.out.println("[NdvClient] readSource: getSource() returned " + (lines != null ? lines.length + " lines" : "null"));
            if (lines != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(lines[i] != null ? lines[i] : "");
                }
                return sb.toString();
            }
        }
        return "";
    }

    /**
     * Log the downloadSource method signature via reflection (once).
     */
    private static boolean signatureLogged = false;
    private void logDownloadSourceSignature() {
        if (signatureLogged) return;
        signatureLogged = true;
        try {
            for (java.lang.reflect.Method m : pal.getClass().getMethods()) {
                if (m.getName().equals("downloadSource")) {
                    System.out.println("[NdvClient] downloadSource signature: " + m.toGenericString());
                    Class<?>[] params = m.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        System.out.println("[NdvClient]   param[" + i + "]: " + params[i].getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[NdvClient] Failed to log downloadSource signature: " + e);
        }
    }

    /**
     * Safely call downloadSource, creating a dynamic proxy for the monitor parameter if needed.
     */
    private IDownloadResult downloadSourceSafe(IPalTypeSystemFile sysFile, String library,
                                                IFileProperties props) throws NdvException {
        try {
            // Find the downloadSource method
            java.lang.reflect.Method downloadMethod = null;
            for (java.lang.reflect.Method m : pal.getClass().getMethods()) {
                if (m.getName().equals("downloadSource") || m.getName().equals("fileOperationDownloadSource")) {
                    if (downloadMethod == null || m.getName().equals("downloadSource")) {
                        downloadMethod = m;
                    }
                }
            }
            if (downloadMethod == null) {
                throw new NdvException("downloadSource method not found on PAL object");
            }

            Class<?>[] paramTypes = downloadMethod.getParameterTypes();
            System.out.println("[NdvClient] downloadSource has " + paramTypes.length + " parameters:");
            for (int i = 0; i < paramTypes.length; i++) {
                System.out.println("[NdvClient]   param[" + i + "]: " + paramTypes[i].getName());
            }

            // Build arguments in the expected order:
            // downloadSource(IProgressMonitor, IPalTypeSystemFile, String library, IFileProperties, IDownloadOptions/Map)
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> pt = paramTypes[i];

                if (sysFile != null && IPalTypeSystemFile.class.isAssignableFrom(pt)) {
                    args[i] = sysFile;
                    System.out.println("[NdvClient]   arg[" + i + "] = sysFile (dbid=" + sysFile.getDatabaseId() + ", fnr=" + sysFile.getFileNumber() + ")");
                } else if (sysFile == null && IPalTypeSystemFile.class.isAssignableFrom(pt)) {
                    args[i] = null;
                    System.out.println("[NdvClient]   arg[" + i + "] = null (sysFile)");
                } else if (pt == String.class) {
                    args[i] = library;
                    System.out.println("[NdvClient]   arg[" + i + "] = library: " + library);
                } else if (IFileProperties.class.isAssignableFrom(pt)) {
                    args[i] = props;
                    System.out.println("[NdvClient]   arg[" + i + "] = props: " + props.getName() + " type=" + props.getType());
                } else if (pt.isInterface()) {
                    // Create a no-op dynamic proxy (for IProgressMonitor, IDownloadOptions, etc.)
                    System.out.println("[NdvClient]   arg[" + i + "] = no-op proxy for: " + pt.getName());
                    final Class<?> proxyIface = pt;
                    args[i] = java.lang.reflect.Proxy.newProxyInstance(
                            pt.getClassLoader(),
                            new Class<?>[]{ pt },
                            new java.lang.reflect.InvocationHandler() {
                                @Override
                                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] methodArgs) {
                                    Class<?> rt = method.getReturnType();
                                    if (rt == boolean.class) return false;
                                    if (rt == int.class) return 0;
                                    if (rt == long.class) return 0L;
                                    if (rt == void.class) return null;
                                    return null;
                                }
                            }
                    );
                } else if (java.util.Map.class.isAssignableFrom(pt)) {
                    args[i] = null;
                    System.out.println("[NdvClient]   arg[" + i + "] = null (Map)");
                } else {
                    args[i] = null;
                    System.out.println("[NdvClient]   arg[" + i + "] = null (" + pt.getName() + ")");
                }
            }

            System.out.println("[NdvClient] Calling downloadSource...");
            Object result = downloadMethod.invoke(pal, args);
            System.out.println("[NdvClient] downloadSource returned: " + (result != null ? result.getClass().getName() : "null"));
            return (IDownloadResult) result;

        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PalResultException) {
                throw new NdvException("downloadSource failed: " + cause.getMessage(), (Exception) cause);
            }
            if (cause instanceof NullPointerException) {
                System.err.println("[NdvClient] downloadSource NPE even with proxy:");
                cause.printStackTrace();
                throw new NdvException("downloadSource NPE: "
                        + (cause.getStackTrace().length > 0 ? cause.getStackTrace()[0] : "unbekannt"), (Exception) cause);
            }
            String msg = cause != null ? cause.getMessage() : e.getMessage();
            throw new NdvException("downloadSource Fehler: " + msg, cause instanceof Exception ? (Exception) cause : e);
        } catch (Exception e) {
            throw new NdvException("downloadSource reflection Fehler: " + e.getMessage(), e);
        }
    }

    /**
     * Read source using object info.
     */
    public String readSource(IPalTypeSystemFile sysFile, String library, NdvObjectInfo objInfo)
            throws IOException, NdvException {
        return readSource(sysFile, library, objInfo.getName(), objInfo.getType());
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

