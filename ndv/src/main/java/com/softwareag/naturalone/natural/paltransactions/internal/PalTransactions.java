package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.*;
import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Central facade for all transactions between the client and an NDV server.
 */
public class PalTransactions implements IPalTransactions {

    // --- State fields ---
    private Pal pal;
    private boolean isConnected;
    private boolean isdisconnected;
    private PalProperties palProperties;
    private INatParm natParm;
    private IPalTypeCP[] codepages;
    private IPalTypeSysVar[] systemVariables;
    private boolean automaticLogon = true;
    private IPalClientIdentification identification;
    private IPalPreferences preferences;
    private IPalTimeoutHandler timeoutHandler;
    private IPalExecutionContext executionContext;
    private String host;
    private String port;
    private int retrievalKind; // 0=none, 1=libraries, 2=objects
    private int currentNotify;
    private ContextDownload activeContext;

    // ========================================================
    // Constructors
    // ========================================================

    public PalTransactions() {
    }

    public PalTransactions(IPalClientIdentification clientId) {
        this.identification = clientId;
    }

    public PalTransactions(IPalClientIdentification clientId, IPalSQLIdentification sqlId) {
        this.identification = clientId;
    }

    public PalTransactions(IPalClientIdentification clientId, IPalPreferences preferences) {
        this.identification = clientId;
        this.preferences = preferences;
    }

    // ========================================================
    // Connection & Session
    // ========================================================

    public More connect(Map params) throws IOException, PalConnectResultException {
        if (isConnected) {
            throw new IllegalStateException("Already connected");
        }

        String host = (String) params.get("host");
        String port = (String) params.get("port");
        String userId = (String) params.get("user id");
        String password = (String) params.get("password");

        if (host == null) throw new IllegalArgumentException("host must not be null");
        if (port == null) throw new IllegalArgumentException("port must not be null");
        if (userId == null) throw new IllegalArgumentException("user id must not be null");
        if (password != null && password.length() > 8) {
            throw new IllegalArgumentException("password must not exceed 8 characters");
        }
        if (password == null) password = "";

        String sessionParams = (String) params.get("session parameters");
        String internalSessionParams = (String) params.get("internal session parameters");
        String newPassword = (String) params.get("new password");
        String richGui = (String) params.get("rich gui");
        String webioVersion = (String) params.get("webio version");
        String nfnPrivateMode = (String) params.get("nfn private mode");
        String logonCounter = (String) params.get("logon counter");

        if (sessionParams == null) sessionParams = "";
        if (internalSessionParams != null && !internalSessionParams.isEmpty()) {
            sessionParams = internalSessionParams + " " + sessionParams;
        }
        if (logonCounter == null) logonCounter = "0";

        // Determine timeout
        int timeout = 0;
        boolean timeStampCheck = false;
        if (preferences != null) {
            timeout = preferences.getTimeOut();
            timeStampCheck = preferences.checkTimeStamp();
        }

        // Pre-fetch identification values (before TCP connect to ensure they are used)
        String clientIdStr = null;
        String clientVerStr = null;
        int webIOVer = 0;
        if (identification != null) {
            clientIdStr = identification.getNdvClientId();
            clientVerStr = identification.getNdvClientVersion();
            webIOVer = identification.getWebIOVersion();
        }

        // Create Pal instance and connect
        this.pal = new Pal(timeout, timeoutHandler);
        this.host = host;
        this.port = port;
        pal.connect(host, port);

        // Build connect transaction
        PalTypeOperation op = new PalTypeOperation(18);
        pal.add(op);

        String encodedPwd = PassWord.encode(userId, password, "", newPassword != null ? newPassword : "");
        PalTypeConnect conn = new PalTypeConnect(userId, encodedPwd, sessionParams);
        pal.add(conn);

        int logonCtrValue = Integer.parseInt(logonCounter);
        PalTypeEnviron env = new PalTypeEnviron(logonCtrValue);
        if ("true".equalsIgnoreCase(richGui)) {
            env.setRichGui(true);
        }
        if (webioVersion != null) {
            try { env.setWebVersion(Integer.parseInt(webioVersion)); } catch (NumberFormatException ignored) {}
        }
        if ("true".equalsIgnoreCase(nfnPrivateMode)) {
            env.setNfnPrivateMode(true);
        }
        env.setTimeStampChecks(timeStampCheck);
        if (clientIdStr != null) {
            try { env.setNdvClientClientId(Integer.parseInt(clientIdStr)); } catch (NumberFormatException ignored) {}
        }
        if (clientVerStr != null) {
            try { env.setNdvClientClientVersion(Integer.parseInt(clientVerStr)); } catch (NumberFormatException ignored) {}
        }
        pal.add(env);

        PalTypeCP cp = new PalTypeCP();
        cp.setCodePage(System.getProperty("file.encoding", "UTF-8"));
        pal.add(cp);

        pal.commit();

        // Read response
        IPalType[] results = pal.retrieve(10);
        if (results != null && results.length > 0) {
            PalTypeResult result = (PalTypeResult) results[0];
            int natResult = result.getNaturalResult();
            if (natResult != 0) {
                IPalType[] resultExArr = pal.retrieve(11);
                String shortText = "";
                if (resultExArr != null && resultExArr.length > 0) {
                    shortText = ((PalTypeResultEx) resultExArr[0]).getShortText();
                }
                int errorKind = PalResultException.NATERROR;
                int secErrorKind = 0;
                if (natResult == 829) { secErrorKind = PalConnectResultException.PASSWORD_INVALID; errorKind = PalResultException.NATSECERROR; }
                else if (natResult == 838) { secErrorKind = PalConnectResultException.PASSWORD_EXPIRED; errorKind = PalResultException.NATSECERROR; }
                else if (natResult == 855) { secErrorKind = PalConnectResultException.PASSWORD_NEW_CHANGE; errorKind = PalResultException.NATSECERROR; }
                else if (natResult == 873) { secErrorKind = PalConnectResultException.PASSWORD_NEW_INVALID; errorKind = PalResultException.NATSECERROR; }
                else if (natResult == 876) { secErrorKind = PalConnectResultException.PASSWORD_NEW_WRONG_LENGTH; errorKind = PalResultException.NATSECERROR; }
                pal.closeSocket();
                pal = null;
                throw new PalConnectResultException(natResult, shortText, errorKind, secErrorKind);
            }
        }

        // Read environment response
        IPalType[] envResp = pal.retrieve(0);
        int ndvType = 2, ndvVersion = 0, natVersion = 0, palVersion = 0;
        String ndvSessionId = "", defaultCodePage = "";
        boolean mfUnicode = false, webIOServer = false;
        int webVer = 0, logonCtr = 0;
        boolean devEnv = false;
        String devEnvPath = "", hostName = "";
        if (envResp != null && envResp.length > 0) {
            PalTypeEnviron respEnv = (PalTypeEnviron) envResp[0];
            ndvType = respEnv.getNdvType();
            ndvVersion = respEnv.getNdvVersion();
            natVersion = respEnv.getNatVersion();
            palVersion = respEnv.getPalVersion();
            ndvSessionId = respEnv.getSessionId();
            mfUnicode = respEnv.isMfUnicodeSrcPossible();
            webIOServer = respEnv.isWebIOServer();
            webVer = respEnv.getWebVersion();
        }

        pal.setPalVersion(palVersion);
        pal.setSessionId(ndvSessionId);
        pal.setNdvType(ndvType);
        pal.setUserId(userId);

        // Read dev env
        IPalType[] devEnvArr = pal.retrieve(52);
        if (devEnvArr != null && devEnvArr.length > 0) {
            PalTypeDevEnv de = (PalTypeDevEnv) devEnvArr[0];
            devEnv = de.isDevEnv();
            devEnvPath = de.getDevEnvPath();
            hostName = de.getHostName();
        }

        // Load server configuration
        loadServerConfig();

        // Determine logon library
        String logonLib = "";
        palProperties = new PalProperties(ndvType, ndvVersion, natVersion, palVersion,
                ndvSessionId, mfUnicode, webIOServer, webVer, logonCtr, defaultCodePage,
                devEnv, devEnvPath, hostName, timeStampCheck, logonLib,
                EAttachSessionType.NDV);

        isConnected = true;
        isdisconnected = false;

        More more = new More();
        return more;
    }

    public void close() throws IOException, PalResultException {
        requireConnected();
        intClose(0);
    }

    public void close(int flags) throws IOException, PalResultException {
        requireConnected();
        intClose(flags);
    }

    private void intClose(int flags) throws IOException, PalResultException {
        try {
            PalTypeOperation op = new PalTypeOperation(20);
            if (flags != 0) {
                op.setFlags(flags);
            }
            pal.add(op);
            pal.commit();
        } finally {
            pal.closeSocket();
            pal = null;
            isConnected = false;
            isdisconnected = true;
        }
    }

    public void disconnect() throws IOException {
        requireConnected();
        pal.disconnect();
        isConnected = false;
        isdisconnected = true;
    }

    public void reconnect() throws IOException, PalResultException {
        if (palProperties == null) return;
        // Only mainframe needs reconnect
        if (palProperties.getNdvType() != IPalTypeEnviron.MAINFRAME) return;
        if (host == null || port == null) return;
        
        // Reconnect using stored host/port
        Pal newPal = new Pal(preferences != null ? preferences.getTimeOut() : 0, timeoutHandler);
        newPal.connect(host, port);
        newPal.setPalVersion(palProperties.getPalVersion());
        newPal.setSessionId(palProperties.getNdvSessionId());
        this.pal = newPal;
    }

    // ========================================================
    // Logon & Library Switch
    // ========================================================

    public void logon(String library) throws IOException, PalResultException {
        requireConnected();
        // TODO: full logon implementation
        throw new UnsupportedOperationException("logon not yet implemented");
    }

    public void logon(String library, IPalTypeLibId[] stepLibs) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("logon not yet implemented");
    }

    // ========================================================
    // Library & Object Navigation
    // ========================================================

    public IPalTypeSystemFile[] getSystemFiles() throws IOException, PalResultException {
        requireConnected();
        PalTypeOperation op = new PalTypeOperation(6);
        pal.add(op);
        pal.commit();
        checkResult();
        IPalType[] files = pal.retrieve(3);
        if (files == null) return new PalTypeSystemFile[0];
        IPalTypeSystemFile[] result = new IPalTypeSystemFile[files.length];
        for (int i = 0; i < files.length; i++) result[i] = (IPalTypeSystemFile) files[i];
        return result;
    }

    public IPalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile sysFile, String filter) throws IOException, PalResultException {
        requireConnected();
        if (filter == null || filter.isEmpty()) throw new IllegalArgumentException("filter must not be null or empty");
        if (sysFile == null) throw new IllegalArgumentException("sysFile must not be null");
        retrievalKind = 1;
        // TODO: full implementation
        throw new UnsupportedOperationException("getLibrariesFirst not yet implemented");
    }

    public IPalTypeLibrary[] getLibrariesNext() throws IOException, PalResultException {
        requireConnected();
        if (currentNotify == 0) throw new IllegalStateException("getLibrariesFirst must be called first");
        throw new UnsupportedOperationException("getLibrariesNext not yet implemented");
    }

    public int getNumberOfLibraries(IPalTypeSystemFile sysFile, String filter) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getNumberOfLibraries not yet implemented");
    }

    public int getNumberOfObjects(IPalTypeSystemFile sysFile, String library, String filter, int kind, int type) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getNumberOfObjects not yet implemented");
    }

    public int getNumberOfObjects(IPalTypeSystemFile sysFile, String library, int type) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getNumberOfObjects not yet implemented");
    }

    public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library, String filter, int kind, int type) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getObjectsFirst not yet implemented");
    }

    public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library, int type) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getObjectsFirst not yet implemented");
    }

    public IPalTypeObject[] getObjectsNext() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getObjectsNext not yet implemented");
    }

    public void terminateRetrieval() throws IOException, PalResultException {
        requireConnected();
        if (retrievalKind == 0) throw new IllegalStateException("No active retrieval");
        throw new UnsupportedOperationException("terminateRetrieval not yet implemented");
    }

    public boolean isLibraryEmpty(IPalTypeSystemFile sysFile, String library) throws IOException, PalResultException {
        requireConnected();
        if (sysFile == null) throw new IllegalArgumentException("sysFile must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        throw new UnsupportedOperationException("isLibraryEmpty not yet implemented");
    }

    // ========================================================
    // Object Existence & Search
    // ========================================================

    public IPalTypeObject exists(IPalTypeSystemFile sysFile, String library, String name, int type) throws IOException, PalResultException {
        requireConnected();
        if (sysFile == null) throw new IllegalArgumentException("sysFile must not be null");
        if (library == null || library.isEmpty()) throw new IllegalArgumentException("library must not be null or empty");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name must not be null or empty");
        throw new UnsupportedOperationException("exists not yet implemented");
    }

    public IPalTypeObject exists(IPalTypeSystemFile sysFile, String name, int type) throws IOException, PalResultException {
        requireConnected();
        if (sysFile == null) throw new IllegalArgumentException("sysFile must not be null");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name must not be null or empty");
        throw new UnsupportedOperationException("exists not yet implemented");
    }

    public ISourceLookupResult getObjectByName(IPalTypeSystemFile sysFile, String library, String name, int type, boolean searchStepLibs) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getObjectByName not yet implemented");
    }

    public ISourceLookupResult getObjectByLongName(IPalTypeSystemFile sysFile, String library, String longName, int type, boolean searchStepLibs) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getObjectByLongName not yet implemented");
    }

    public IPalTypeLibId getLibraryOfObject(IPalTypeLibId libId, String objectName, Set options) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getLibraryOfObject not yet implemented");
    }

    // ========================================================
    // Source Code Read & Write
    // ========================================================

    public String[] read(IPalTypeSystemFile sysFile, String library, String sourceName, Set readOptions) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("read not yet implemented");
    }

    public IDownloadResult downloadSource(ITransactionContext ctx, IPalTypeSystemFile sysFile, String library, IFileProperties props, Set options) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("downloadSource not yet implemented");
    }

    public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile, String library, IFileProperties props) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("downloadBinary not yet implemented");
    }

    public ByteArrayOutputStream downloadBinary(ITransactionContext ctx, IPalTypeSystemFile sysFile, String library, IFileProperties props) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("downloadBinary not yet implemented");
    }

    public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile, String library, IFileProperties props, int mode) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("downloadBinary not yet implemented");
    }

    public ByteArrayOutputStream downloadBinary(ITransactionContext ctx, IPalTypeSystemFile sysFile, String library, IFileProperties props, int mode) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("downloadBinary not yet implemented");
    }

    public void uploadSource(IPalTypeSystemFile sysFile, String library, IFileProperties props, Set options, String[] lines) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("uploadSource not yet implemented");
    }

    public void uploadBinary(IPalTypeSystemFile sysFile, String library, IFileProperties props, ByteArrayOutputStream contents) throws IOException, PalResultException {
        requireConnected();
        if (sysFile == null) throw new IllegalArgumentException("sysFile must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (props == null) throw new IllegalArgumentException("props must not be null");
        if (contents == null) throw new IllegalArgumentException("contents must not be null");
        throw new UnsupportedOperationException("uploadBinary not yet implemented");
    }

    // ========================================================
    // File Operations (Copy, Move, Delete, Lock)
    // ========================================================

    public void copy(IPalTypeSystemFile srcSysFile, String srcLibrary, String objectName,
                     IPalTypeSystemFile dstSysFile, String dstLibrary, IPalTypeObject obj) throws IOException, PalResultException {
        requireConnected();
        if (srcSysFile == null) throw new IllegalArgumentException("srcSysFile must not be null");
        if (srcLibrary == null) throw new IllegalArgumentException("srcLibrary must not be null");
        if (objectName == null) throw new IllegalArgumentException("objectName must not be null");
        if (dstSysFile == null) throw new IllegalArgumentException("dstSysFile must not be null");
        if (dstLibrary == null) throw new IllegalArgumentException("dstLibrary must not be null");
        throw new UnsupportedOperationException("copy not yet implemented");
    }

    public void copy(IPalTypeSystemFile srcSysFile, String srcLibrary, String objectName,
                     IPalTypeSystemFile dstSysFile, String dstLibrary, int kind, int type) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("copy not yet implemented");
    }

    public void move(IPalTypeSystemFile srcSysFile, String srcLibrary, String srcName,
                     IPalTypeSystemFile dstSysFile, String dstLibrary, String dstName, IPalTypeObject obj) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("move not yet implemented");
    }

    public void move(IPalTypeSystemFile srcSysFile, String srcLibrary, String srcName,
                     IPalTypeSystemFile dstSysFile, String dstLibrary, String dstName, int kind, int type) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("move not yet implemented");
    }

    public void delete(IPalTypeSystemFile sysFile, String library, IFileProperties props) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("delete not yet implemented");
    }

    public void delete(IPalTypeSystemFile sysFile, int type, String name) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("delete not yet implemented");
    }

    public void delete1(IPalTypeSystemFile sysFile, String library, int kind, int type, String name) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("delete1 not yet implemented");
    }

    public void delete2(IPalTypeSystemFile sysFile, String library, int kind, int type, String name, String longName) throws IOException, PalResultException {
        requireConnected();
        if (library == null) throw new IllegalArgumentException("library must not be null");
        throw new UnsupportedOperationException("delete2 not yet implemented");
    }

    public void lock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("lock not yet implemented");
    }

    public void unlock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type) throws IOException, PalResultException {
        requireConnected();
        if (kind < 0) throw new IllegalArgumentException("kind must not be negative");
        throw new UnsupportedOperationException("unlock not yet implemented");
    }

    public void isLocked(IPalTypeSystemFile sysFile, String library, String name, int kind, int type) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("isLocked not yet implemented");
    }

    // ========================================================
    // Source Code Commands (Save, Stow, Catalog, Check)
    // ========================================================

    public void save(IPalTypeSystemFile sysFile, String library, IFileProperties props, String[] source) throws IOException, PalResultException, PalCompileResultException {
        requireConnected();
        if (props == null) throw new IllegalArgumentException("props must not be null");
        throw new UnsupportedOperationException("save not yet implemented");
    }

    public void stow(IPalTypeSystemFile sysFile, String library, IFileProperties props, String[] source) throws IOException, PalResultException, PalCompileResultException {
        requireConnected();
        if (props == null) throw new IllegalArgumentException("props must not be null");
        throw new UnsupportedOperationException("stow not yet implemented");
    }

    public void catalog(IPalTypeSystemFile sysFile, String library, IFileProperties props, String[] source) throws IOException, PalResultException, PalCompileResultException {
        requireConnected();
        throw new UnsupportedOperationException("catalog not yet implemented");
    }

    public void check(IPalTypeSystemFile sysFile, String library, IFileProperties props, String[] source) throws IOException, PalResultException, PalCompileResultException {
        requireConnected();
        throw new UnsupportedOperationException("check not yet implemented");
    }

    // ========================================================
    // File Transfer
    // ========================================================

    public Object[] receiveFiles(IPalTypeSystemFile sysFile, String library, IFileProperties props, Set options) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("receiveFiles not yet implemented");
    }

    public void sendFiles(IPalTypeSystemFile sysFile, String library, ObjectProperties props, Set options, Object[] files) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("sendFiles not yet implemented");
    }

    public void abortFileOperation(Set options) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("abortFileOperation not yet implemented");
    }

    // ========================================================
    // Server Configuration
    // ========================================================

    public IServerConfiguration getServerConfiguration(boolean refresh) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getServerConfiguration not yet implemented");
    }

    public IPalTypeCP[] getCodePages() throws IOException, PalResultException {
        requireConnected();
        if (codepages != null) return codepages;
        throw new UnsupportedOperationException("getCodePages not yet implemented");
    }

    public INatParm getNaturalParameters() {
        return natParm;
    }

    public void setNaturalParameters(INatParm params) {
        this.natParm = params;
    }

    public IPalTypeSysVar[] getSystemVariables() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getSystemVariables not yet implemented");
    }

    public void setSystemVariables(IPalTypeSysVar[] vars) {
        this.systemVariables = vars;
    }

    // ========================================================
    // Library Information
    // ========================================================

    public String getLogonLibrary() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getLogonLibrary not yet implemented");
    }

    public PalTypeLibId[] getStepLibs() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getStepLibs not yet implemented");
    }

    public void setStepLibs(IPalTypeLibId[] stepLibs, EStepLibFormat format) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("setStepLibs not yet implemented");
    }

    public IPalTypeLibraryStatistics getLibraryStatistics(IPalTypeSystemFile sysFile, String library) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getLibraryStatistics not yet implemented");
    }

    public IPalTypeLibraryStatistics getLibraryStatistics(Set options, IPalTypeSystemFile sysFile, String library) throws IOException, PalResultException {
        requireConnected();
        if (sysFile == null) throw new IllegalArgumentException("sysFile must not be null");
        if (library == null || library.isEmpty()) throw new IllegalArgumentException("library must not be null or empty");
        throw new UnsupportedOperationException("getLibraryStatistics not yet implemented");
    }

    public ILibraryInfo getLibraryInfo(int mode, IPalTypeSystemFile sysFile, String library) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getLibraryInfo not yet implemented");
    }

    public PalTypeCmdGuard getCmdGuardInfo(int mode, IPalTypeSystemFile sysFile, String library) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getCmdGuardInfo not yet implemented");
    }

    // ========================================================
    // DDM Generation
    // ========================================================

    public String[] generateAdabasDdm(int databaseId, int fileNumber, String name) throws IOException, PalResultException {
        requireConnected();
        throw new IllegalStateException("generateAdabasDdm not supported without PalProperties");
    }

    public String[] generateSqlDdm(int databaseId, int fileNumber, String name, String sqlTable) throws IOException, PalResultException {
        requireConnected();
        throw new IllegalStateException("SQL DDM generation not supported");
    }

    public String[] generateXmlDdm(int databaseId, int fileNumber, String name, String xmlFile, String rootElement, String namespace) throws IOException, PalResultException {
        requireConnected();
        throw new IllegalStateException("XML DDM generation not supported");
    }

    // ========================================================
    // Command Execution
    // ========================================================

    public ISuspendResult execute(String command) throws IOException {
        requireConnected();
        if (command == null) throw new IllegalArgumentException("command must not be null");
        throw new UnsupportedOperationException("execute not yet implemented");
    }

    public void executeWithoutIO(String command) throws IOException, PalResultException {
        requireConnected();
        if (command == null) throw new IllegalArgumentException("command must not be null");
        throw new UnsupportedOperationException("executeWithoutIO not yet implemented");
    }

    public ISuspendResult command(String command, int subOperation) throws IOException {
        requireConnected();
        if (command == null) throw new IllegalArgumentException("command must not be null");
        throw new UnsupportedOperationException("command not yet implemented");
    }

    // ========================================================
    // Debug
    // ========================================================

    public ISuspendResult debugStart(String program, String library, String stepLibs) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("debugStart not yet implemented");
    }

    public ISuspendResult debugResume() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("debugResume not yet implemented");
    }

    public ISuspendResult debugStepInto() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("debugStepInto not yet implemented");
    }

    public ISuspendResult debugStepOver() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("debugStepOver not yet implemented");
    }

    public ISuspendResult debugStepReturn() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("debugStepReturn not yet implemented");
    }

    public void debugExit() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("debugExit not yet implemented");
    }

    public IPalTypeDbgSpy spySet(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("spySet not yet implemented");
    }

    public IPalTypeDbgSpy spySet(IPalTypeDbgSpy spy, String library, String object) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("spySet not yet implemented");
    }

    public IPalTypeDbgSpy spyModify(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("spyModify not yet implemented");
    }

    public IPalTypeDbgSpy spyModify(IPalTypeDbgSpy spy, String library, String object) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("spyModify not yet implemented");
    }

    public IPalTypeDbgSpy spyDelete(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("spyDelete not yet implemented");
    }

    public PalTypeDbgStackFrame[] setNextStatement(PalTypeDbgStackFrame frame) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("setNextStatement not yet implemented");
    }

    public IPalTypeDbgSyt[] getSymbolTable(IPalTypeDbgStackFrame frame) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getSymbolTable not yet implemented");
    }

    public IPalTypeDbgVarValue[] getValue(IPalTypeDbgSyt[] syts, IPalTypeDbgStackFrame frame) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("getValue not yet implemented");
    }

    public void modifyValue(IPalTypeDbgSyt syt, IPalTypeDbgVarValue value, IPalTypeDbgStackFrame frame) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("modifyValue not yet implemented");
    }

    public IPalTypeDbgVarDesc[] resolveIndices(boolean expand, IPalTypeDbgSyt syt, IPalTypeDbgStackFrame frame) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("resolveIndices not yet implemented");
    }

    // ========================================================
    // Screen / IO
    // ========================================================

    public ISuspendResult sendScreen(byte[] data) throws IOException {
        requireConnected();
        throw new UnsupportedOperationException("sendScreen not yet implemented");
    }

    public ISuspendResult nextScreen() throws IOException {
        requireConnected();
        throw new UnsupportedOperationException("nextScreen not yet implemented");
    }

    public void terminateIO() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("terminateIO not yet implemented");
    }

    // ========================================================
    // Utility Buffer
    // ========================================================

    public void utilityBufferSend(short id, byte[] data) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("utilityBufferSend not yet implemented");
    }

    public byte[] utilityBufferReceive() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("utilityBufferReceive not yet implemented");
    }

    // ========================================================
    // DAS (Debug Attach Service)
    // ========================================================

    public void dasConnect(Map params, int sessionType) throws IOException, PalConnectResultException {
        // Similar to connect but for DAS
        String host = (String) params.get("host");
        String port = (String) params.get("port");
        String userId = (String) params.get("user id");
        if (host == null) throw new IllegalArgumentException("host must not be null");
        if (port == null) throw new IllegalArgumentException("port must not be null");
        if (userId == null) throw new IllegalArgumentException("user id must not be null");
        throw new UnsupportedOperationException("dasConnect not yet implemented");
    }

    public void dasWaitForAttach(IPalTypeDbgaRecord record, IDebugAttachWaitCallBack callback) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("dasWaitForAttach not yet implemented");
    }

    public void dasRegisterDebugAttachRecord(IPalTypeDbgaRecord record) throws IOException, PalResultException {
        if (pal == null) return;
        PalTypeOperation op = new PalTypeOperation(52, PalTypeOperation.SUBKEY_DASRECORD);
        pal.add(op);
        if (record != null) {
            pal.add((IPalType) record);
        }
        pal.commit();
    }

    public void dasUnregisterDebugAttachRecord(IPalTypeDbgaRecord record, int id) throws IOException, PalResultException {
        if (pal == null) return;
        PalTypeOperation op = new PalTypeOperation(52, PalTypeOperation.SUBKEY_DASTERMINATE);
        pal.add(op);
        if (record != null) {
            pal.add((IPalType) record);
        }
        pal.commit();
    }

    public void dasBindToAttachSession(String sessionId, String library, String stepLibs) throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("dasBindToAttachSession not yet implemented");
    }

    public void dasSignIn() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("dasSignIn not yet implemented");
    }

    public ISuspendResult dasDebugStart() throws IOException, PalResultException {
        requireConnected();
        throw new UnsupportedOperationException("dasDebugStart not yet implemented");
    }

    // ========================================================
    // Transaction Context
    // ========================================================

    public Object createTransactionContext(Class contextClass) {
        if (contextClass == null) throw new NullPointerException("contextClass must not be null");
        if (!ITransactionContextDownload.class.equals(contextClass)) {
            throw new IllegalStateException("Only ITransactionContextDownload is supported");
        }
        if (activeContext != null) {
            throw new IllegalStateException("A transaction context is already active");
        }
        activeContext = new ContextDownload();
        return activeContext;
    }

    public void disposeTransactionContext(ITransactionContext ctx) {
        if (ctx == null) return;
        if (activeContext == null || activeContext != ctx) {
            throw new IllegalStateException("Context mismatch");
        }
        activeContext = null;
    }

    // ========================================================
    // Getters & Setters
    // ========================================================

    public IPalProperties getPalProperties() {
        return palProperties;
    }

    public IPalClientIdentification getIdentification() {
        return identification;
    }

    public void setIdentification(IPalClientIdentification id) {
        this.identification = id;
    }

    public void setPalProperties(IPalProperties props) {
        // no-op: properties are managed internally
    }

    public void setPalSQLIdentification(IPalSQLIdentification id) {
        // ignored in our implementation
    }

    public void setPalPreferences(IPalPreferences prefs) {
        this.preferences = prefs;
    }

    public void setPalTimeoutHandler(IPalTimeoutHandler handler) {
        this.timeoutHandler = handler;
        if (pal != null) {
            pal.setPalTimeoutHandler(handler);
        }
    }

    public void setApplicationExecutionContext(IPalExecutionContext ctx) {
        this.executionContext = ctx;
    }

    public void setArabicShapingContext(IPalArabicShaping ctx) {
        // ignored - no Arabic/BiDi support
    }

    public void setAutomaticLogon(boolean value) {
        this.automaticLogon = value;
    }

    public boolean isConnected() {
        return isConnected && pal != null && !pal.isConnectionLost();
    }

    public boolean isDisconnected() {
        return isdisconnected;
    }

    public String getSessionId() {
        return pal != null ? pal.getSessionId() : null;
    }

    public void setCodePageOfSource(IPalTypeSystemFile sysFile, String library, String name, int type, String codePage) throws IOException, PalResultException {
        // no-op
    }

    public void initTraceSession(String path) throws IOException {
        requireConnected();
        throw new UnsupportedOperationException("initTraceSession not yet implemented");
    }

    public String toString() {
        if (pal != null && isConnected && !pal.isConnectionLost()) {
            return pal.toString();
        }
        return "no connection available";
    }

    // ========================================================
    // IInsertLabels implementation
    // ========================================================

    public Boolean isInsertLabels() {
        return false;
    }

    public Boolean isCreateNewLine() {
        return false;
    }

    public String getLabelFormat() {
        return "L%d.";
    }

    // ========================================================
    // Static Methods
    // ========================================================

    public static IPalTypeSystemFile createInactiveSystemFileKey(String name, int databaseId, int fileNumber) {
        if (name == null || databaseId <= 0 || fileNumber <= 0) return null;
        PalTypeSystemFile sf = new PalTypeSystemFile(databaseId, fileNumber, IPalTypeSystemFile.INACTIVE);
        sf.setAlias(name);
        return sf;
    }

    public static synchronized IPalTypeLibId[] librarySearchOrder2DisplayOrder(IPalTypeLibId[] searchOrder) {
        if (searchOrder == null || searchOrder.length == 0) return new IPalTypeLibId[0];

        IPalTypeLibId first = searchOrder[0];
        List<IPalTypeLibId> result = new ArrayList<IPalTypeLibId>();
        Set<String> seen = new HashSet<String>();

        // Add all except first, skip nulls, empties and duplicates
        for (int i = 1; i < searchOrder.length; i++) {
            IPalTypeLibId lib = searchOrder[i];
            String name = lib.getLibrary();
            if (name == null || name.isEmpty()) continue;
            if (seen.contains(name)) continue;
            seen.add(name);
            result.add(lib);
        }

        // Add first at the end (unless null/empty)
        String firstName = first.getLibrary();
        if (firstName != null && !firstName.isEmpty() && !seen.contains(firstName)) {
            result.add(first);
        }

        return result.toArray(new IPalTypeLibId[0]);
    }

    // ========================================================
    // Internal Helpers
    // ========================================================

    private void requireConnected() {
        if (!isConnected) throw new IllegalStateException("Not connected");
    }

    private void checkResult() throws IOException, PalResultException {
        IPalType[] results = pal.retrieve(10);
        if (results != null && results.length > 0) {
            PalTypeResult result = (PalTypeResult) results[0];
            int natResult = result.getNaturalResult();
            if (natResult != 0) {
                IPalType[] exArr = pal.retrieve(11);
                String shortText = "";
                int kind = PalResultException.NATERROR;
                if (exArr != null && exArr.length > 0) {
                    shortText = ((PalTypeResultEx) exArr[0]).getShortText();
                }
                if (natResult == 7000) kind = 1;
                else if (natResult == 9999) kind = PalResultException.SYSTEMERROR;
                throw new PalResultException(natResult, kind, shortText);
            }
        }
    }

    private void loadServerConfig() {
        // TODO: Load NatParm, codepages, system variables, client config, DBMS info from server
    }

    private void putServerConfig(boolean send) throws IOException, PalResultException {
        // TODO: Send server configuration changes
        throw new UnsupportedOperationException("putServerConfig not yet implemented");
    }

    // Synthetic access method for inner class ServerConfiguration
    static void access$11(PalTransactions pt, boolean send) throws IOException, PalResultException {
        pt.putServerConfig(send);
    }

    // ========================================================
    // Inner Classes
    // ========================================================

    /**
     * NatParm: wraps IPalTypeNatParm array for typed access.
     */
    public static class NatParm implements INatParm {
        private static final long serialVersionUID = 1L;
        private final IPalTypeNatParm[] parms;

        public NatParm(IPalTypeNatParm[] parms) {
            this.parms = parms;
        }

        public IReport getReport() {
            for (IPalTypeNatParm p : parms) {
                IReport r = p.getReport();
                if (r != null) return r;
            }
            return null;
        }

        public ICharAssign getCharAssign() {
            for (IPalTypeNatParm p : parms) {
                ICharAssign c = p.getCharAssign();
                if (c != null) return c;
            }
            return null;
        }

        public IFldApp getFldApp() {
            for (IPalTypeNatParm p : parms) {
                IFldApp f = p.getFldApp();
                if (f != null) return f;
            }
            return null;
        }

        public ICompOpt getCompOpt() {
            for (IPalTypeNatParm p : parms) {
                ICompOpt c = p.getCompOpt();
                if (c != null) return c;
            }
            return null;
        }

        public ILimit getLimit() {
            for (IPalTypeNatParm p : parms) {
                ILimit l = p.getLimit();
                if (l != null) return l;
            }
            return null;
        }

        public IRegional getRegional() {
            for (IPalTypeNatParm p : parms) {
                IRegional r = p.getRegional();
                if (r != null) return r;
            }
            return null;
        }

        public IRpc getRpc() {
            for (IPalTypeNatParm p : parms) {
                IRpc r = p.getRpc();
                if (r != null) return r;
            }
            return null;
        }

        public IBuffSize getBuffSize() {
            for (IPalTypeNatParm p : parms) {
                IBuffSize b = p.getBuffSize();
                if (b != null) return b;
            }
            return null;
        }

        public IErr getErr() {
            for (IPalTypeNatParm p : parms) {
                IErr e = p.getErr();
                if (e != null) return e;
            }
            return null;
        }

        public IPalTypeNatParm[] get(int ndvType) {
            List<IPalTypeNatParm> filtered = new ArrayList<IPalTypeNatParm>();
            for (IPalTypeNatParm p : parms) {
                int idx = p.getRecordIndex();
                if (ndvType == 0) {
                    // ndvType 0 = return all records
                    filtered.add(p);
                } else {
                    // ndvType != 0 = exclude Rpc (6) and BuffSize (7)
                    if (idx != 6 && idx != 7) {
                        filtered.add(p);
                    }
                }
            }
            return filtered.toArray(new IPalTypeNatParm[0]);
        }
    }

    /**
     * DownloadResult: result of a source download.
     */
    private static class DownloadResult implements IDownloadResult {
        private final String[] source;
        private final int lineIncrement;

        private DownloadResult(String[] source, int lineIncrement, DownloadResult unused) {
            this.source = source;
            this.lineIncrement = lineIncrement;
        }

        public String[] getSource() {
            return source;
        }

        public int getLineIncrement() {
            return lineIncrement;
        }
    }

    /**
     * SourceLookupResult: result of an object name search.
     */
    private static class SourceLookupResult implements ISourceLookupResult {
        private final IPalTypeObject object;
        private final String library;
        private final int databaseId;
        private final int fileNumber;

        private SourceLookupResult(IPalTypeObject object, String library, int databaseId, int fileNumber, SourceLookupResult unused) {
            this.object = object;
            this.library = library;
            // Both zero means "unknown" -> convert to -1
            if (databaseId == 0 && fileNumber == 0) {
                this.databaseId = -1;
                this.fileNumber = -1;
            } else {
                this.databaseId = databaseId;
                this.fileNumber = fileNumber;
            }
        }

        public IPalTypeObject getObject() {
            return object;
        }

        public String getLibrary() {
            return library;
        }

        public int getDatabaseId() {
            return databaseId;
        }

        public int getFileNumber() {
            return fileNumber;
        }

        public String getSourceName() {
            return object != null ? object.getName() : null;
        }

        public String getLongSourceName() {
            return object != null ? object.getLongName() : null;
        }

        public int getSourceType() {
            return object != null ? object.getType() : 0;
        }
    }

    /**
     * ContextDownload: manages batch download state.
     */
    private static class ContextDownload implements ITransactionContextDownload {
        private boolean started;
        private boolean terminated;
        private Set initOptions;

        private ContextDownload() {
        }

        private boolean isStarted() {
            return started;
        }

        private void setStarted(boolean started) {
            this.started = started;
        }

        private boolean isTerminated() {
            return terminated;
        }

        private void setTerminated(boolean terminated) {
            this.terminated = terminated;
        }

        private Set getInitOptions() {
            return initOptions;
        }

        private void setInitOptions(Set initOptions) {
            this.initOptions = initOptions;
        }

        // Synthetic access methods (generated by javac for private inner class access)
        static void access$4(ContextDownload ctx, boolean val) {
            ctx.setTerminated(val);
        }

        static void access$5(ContextDownload ctx, Set val) {
            ctx.setInitOptions(val);
        }

        static void access$6(ContextDownload ctx, boolean val) {
            ctx.setStarted(val);
        }
    }
}
