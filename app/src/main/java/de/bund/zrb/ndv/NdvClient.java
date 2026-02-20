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
            // Fetch remaining pages
            while (libs != null && libs.length > 0) {
                libs = pal.getLibrariesNext();
                if (libs != null) {
                    for (IPalTypeLibrary lib : libs) {
                        if (lib != null && lib.getLibrary() != null) {
                            result.add(lib.getLibrary().trim());
                        }
                    }
                }
            }
        } catch (PalResultException e) {
            throw new NdvException("Failed to list libraries: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * List objects in a library.
     *
     * @param sysFile system file
     * @param library library name
     * @param filter  name filter, e.g. "*" for all
     * @param kind    object kind (ObjectKind.SOURCE, GP, ANY, etc.)
     * @param type    object type bitmask (ObjectType.PROGRAM | ObjectType.SUBPROGRAM | ... or 0 for all)
     * @return list of NdvObjectInfo
     */
    public List<NdvObjectInfo> listObjects(IPalTypeSystemFile sysFile, String library,
                                            String filter, int kind, int type)
            throws IOException, NdvException {
        checkConnected();
        List<NdvObjectInfo> result = new ArrayList<NdvObjectInfo>();
        try {
            IPalTypeObject[] objects = pal.getObjectsFirst(sysFile, library, filter, kind, type);
            addObjects(result, objects);

            // Fetch remaining pages
            while (objects != null && objects.length > 0) {
                objects = pal.getObjectsNext();
                addObjects(result, objects);
            }
        } catch (PalResultException e) {
            throw new NdvException("Failed to list objects in library '" + library + "': " + e.getMessage(), e);
        }
        return result;
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
        try {
            IFileProperties props = new ObjectProperties.Builder(name, type).build();
            IDownloadResult result = pal.downloadSource(null, sysFile, library, props, null);
            if (result != null && result.getSource() != null) {
                StringBuilder sb = new StringBuilder();
                String[] lines = result.getSource();
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(lines[i]);
                }
                return sb.toString();
            }
            return "";
        } catch (PalResultException e) {
            throw new NdvException("Failed to read source '" + name + "' from '" + library + "': " + e.getMessage(), e);
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

