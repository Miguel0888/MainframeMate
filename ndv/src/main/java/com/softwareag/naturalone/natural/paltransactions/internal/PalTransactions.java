package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;
import com.softwareag.naturalone.natural.paltransactions.internal.services.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

/**
 * Fassade fuer IPalTransactions.
 * Delegiert alle Aufrufe an fokussierte Service-Klassen,
 * die ueber einen gemeinsamen PalSessionContext kommunizieren.
 */
public class PalTransactions implements IPalTransactions {

    private final PalSessionContext ctx;
    private final ConnectionService connectionService;
    private final ConfigurationService configurationService;
    private final LibraryBrowseService libraryBrowseService;
    private final ObjectBrowseService objectBrowseService;
    private final SourceTransferService sourceTransferService;
    private final ObjectManagementService objectManagementService;
    private final DebugService debugService;

    // ── Konstruktoren ──

    public PalTransactions() {
        this.ctx = new PalSessionContext();
        this.connectionService = new ConnectionService(ctx);
        this.configurationService = new ConfigurationService(ctx);
        this.libraryBrowseService = new LibraryBrowseService(ctx);
        this.objectBrowseService = new ObjectBrowseService(ctx);
        this.sourceTransferService = new SourceTransferService(ctx);
        this.objectManagementService = new ObjectManagementService(ctx);
        this.debugService = new DebugService(ctx);
    }

    public PalTransactions(IPalClientIdentification id) {
        this();
        ctx.setIdentification(id);
    }

    public PalTransactions(IPalClientIdentification id, IPalSQLIdentification sql) {
        this(id);
        ctx.setPalSQLIdentification(sql);
    }

    public PalTransactions(IPalClientIdentification id, IPalPreferences prefs) {
        this(id);
        ctx.setPalPreferences(prefs);
    }

    // ══════════════════════════════════════════════════════════════
    //  Verbindung (→ ConnectionService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public More connect(Map<String, String> params)
            throws IOException, UnknownHostException, ConnectException, PalConnectResultException {
        return connectionService.connect(params);
    }

    @Override
    public void disconnect() throws IOException {
        connectionService.disconnect();
    }

    @Override
    public void reconnect() throws IOException, PalResultException {
        connectionService.reconnect();
    }

    @Override
    public boolean isConnected() {
        return ctx.isConnected();
    }

    @Override
    public boolean isDisconnected() {
        return ctx.isDisconnected();
    }

    @Override
    public String getSessionId() {
        return configurationService.getSessionId();
    }

    @Override
    public void initTraceSession(String sessionId) throws PalResultException {
        // Stub – Tracing ist optional
    }

    @Override
    public void close() throws IOException, PalResultException {
        connectionService.close();
    }

    @Override
    public void close(int option) throws IOException, PalResultException {
        connectionService.close(option);
    }

    @Override
    public void terminateRetrieval() throws IOException, PalResultException {
        // Stub
    }

    // ══════════════════════════════════════════════════════════════
    //  Logon (→ DebugService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public void setAutomaticLogon(boolean auto) {
        debugService.setAutomaticLogon(auto);
    }

    @Override
    public void logon(String library) throws IOException, PalResultException {
        debugService.logon(library);
    }

    @Override
    public void logon(String library, IPalTypeLibId[] stepLibs) throws IOException, PalResultException {
        debugService.logon(library, stepLibs);
    }

    @Override
    public String getLogonLibrary() throws IOException, PalResultException {
        return debugService.getLogonLibrary();
    }

    // ══════════════════════════════════════════════════════════════
    //  Bibliotheks-Browsing (→ LibraryBrowseService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public IPalTypeSystemFile[] getSystemFiles() throws IOException, PalResultException {
        return libraryBrowseService.getSystemFiles();
    }

    @Override
    public IPalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException {
        return libraryBrowseService.getLibrariesFirst(sysFile, filter);
    }

    @Override
    public IPalTypeLibrary[] getLibrariesNext() throws IOException, PalResultException {
        return libraryBrowseService.getLibrariesNext();
    }

    @Override
    public int getNumberOfLibraries(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException {
        return libraryBrowseService.getNumberOfLibraries(sysFile, filter);
    }

    @Override
    public boolean isLibraryEmpty(IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {
        return libraryBrowseService.isLibraryEmpty(sysFile, library);
    }

    @Override
    public IPalTypeLibraryStatistics getLibraryStatistics(Set<ELibraryStatisticsOption> options,
                                                           IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {
        return libraryBrowseService.getLibraryStatistics(options, sysFile, library);
    }

    @Override
    public ILibraryInfo getLibraryInfo(int option, IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {
        return libraryBrowseService.getLibraryInfo(option, sysFile, library);
    }

    @Override
    public IPalTypeLibId getLibraryOfObject(IPalTypeLibId libId, String objectName, Set<EObjectKind> kinds)
            throws IOException, PalResultException {
        return libraryBrowseService.getLibraryOfObject(libId, objectName, kinds);
    }

    // ══════════════════════════════════════════════════════════════
    //  Objekt-Browsing (→ ObjectBrowseService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library,
                                            String filter, int kind, int type)
            throws IOException, PalResultException {
        return objectBrowseService.getObjectsFirst(sysFile, library, filter, kind, type);
    }

    @Override
    public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library, int kind)
            throws IOException, PalResultException {
        return objectBrowseService.getObjectsFirst(sysFile, library, kind);
    }

    @Override
    public IPalTypeObject[] getObjectsNext() throws IOException, PalResultException {
        return objectBrowseService.getObjectsNext();
    }

    @Override
    public int getNumberOfObjects(IPalTypeSystemFile sysFile, String library,
                                  String filter, int kind, int type)
            throws IOException, PalResultException {
        return objectBrowseService.getNumberOfObjects(sysFile, library, filter, kind, type);
    }

    @Override
    public int getNumberOfObjects(IPalTypeSystemFile sysFile, String library, int kind)
            throws IOException, PalResultException {
        return objectBrowseService.getNumberOfObjects(sysFile, library, kind);
    }

    @Override
    public IPalTypeObject exists(IPalTypeSystemFile sysFile, String library, String name, int type)
            throws IOException, PalResultException {
        return objectBrowseService.exists(sysFile, library, name, type);
    }

    @Override
    public IPalTypeObject exists(IPalTypeSystemFile sysFile, String name, int type)
            throws IOException, PalResultException {
        return objectBrowseService.exists(sysFile, name, type);
    }

    @Override
    public ISourceLookupResult getObjectByLongName(IPalTypeSystemFile sysFile, String library,
                                                    String longName, int type, boolean withSource)
            throws IOException, PalResultException {
        return objectBrowseService.getObjectByLongName(sysFile, library, longName, type, withSource);
    }

    @Override
    public ISourceLookupResult getObjectByName(IPalTypeSystemFile sysFile, String library,
                                                String name, int type, boolean withSource)
            throws IOException, PalResultException {
        return objectBrowseService.getObjectByName(sysFile, library, name, type, withSource);
    }

    // ══════════════════════════════════════════════════════════════
    //  Transaction Context (→ ConfigurationService)
    // ══════════════════════════════════════════════════════════════

    @Override
    @SuppressWarnings("rawtypes")
    public Object createTransactionContext(Class contextClass) {
        return configurationService.createTransactionContext(contextClass);
    }

    @Override
    public void disposeTransactionContext(ITransactionContext ctx) {
        configurationService.disposeTransactionContext(ctx);
    }

    // ══════════════════════════════════════════════════════════════
    //  Download / Upload (→ SourceTransferService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public IDownloadResult downloadSource(ITransactionContext txCtx,
                                          IPalTypeSystemFile sysFile, String library,
                                          IFileProperties props, Set<EDownLoadOption> options)
            throws IOException, PalResultException {
        return sourceTransferService.downloadSource(txCtx, sysFile, library, props, options);
    }

    @Override
    public Object[] receiveFiles(IPalTypeSystemFile sysFile, String library,
                                 IFileProperties props, Set<EDownLoadOption> options)
            throws IOException, PalResultException {
        return sourceTransferService.receiveFiles(sysFile, library, props, options);
    }

    @Override
    public void uploadSource(IPalTypeSystemFile sysFile, String library,
                             IFileProperties props, Set<EUploadOption> options, String[] sourceLines)
            throws IOException, PalResultException {
        sourceTransferService.uploadSource(sysFile, library, props, options, sourceLines);
    }

    @Override
    public void sendFiles(IPalTypeSystemFile sysFile, String library,
                          ObjectProperties objProps, Set<EUploadOption> options, Object[] data)
            throws IOException, PalResultException {
        sourceTransferService.sendFiles(sysFile, library, objProps, options, data);
    }

    @Override
    public void abortFileOperation(Set<EDownLoadOption> options)
            throws IOException, PalResultException {
        sourceTransferService.abortFileOperation(options);
    }

    @Override
    public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile, String library,
                                                 String name, String longName, int type)
            throws IOException, PalResultException {
        return sourceTransferService.downloadBinary(sysFile, library, name, longName, type);
    }

    @Override
    public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile, String library,
                                                 String name, int type)
            throws IOException, PalResultException {
        return sourceTransferService.downloadBinary(sysFile, library, name, type);
    }

    @Override
    public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile, String library,
                                                 IFileProperties props)
            throws IOException, PalResultException {
        return sourceTransferService.downloadBinary(sysFile, library, props);
    }

    @Override
    public ByteArrayOutputStream downloadBinary(ITransactionContext txCtx, IPalTypeSystemFile sysFile,
                                                 String library, IFileProperties props)
            throws IOException, PalResultException {
        return sourceTransferService.downloadBinary(txCtx, sysFile, library, props);
    }

    @Override
    public void uploadBinary(IPalTypeSystemFile sysFile, String library,
                             IFileProperties props, ByteArrayOutputStream data)
            throws IOException, PalResultException {
        sourceTransferService.uploadBinary(sysFile, library, props, data);
    }

    // ══════════════════════════════════════════════════════════════
    //  Source Operations (→ SourceTransferService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public void catalog(IPalTypeSystemFile sysFile, String library,
                        IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        sourceTransferService.catalog(sysFile, library, props, sourceLines);
    }

    @Override
    public void check(IPalTypeSystemFile sysFile, String library,
                      IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        sourceTransferService.check(sysFile, library, props, sourceLines);
    }

    @Override
    public void stow(IPalTypeSystemFile sysFile, String library,
                     IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        sourceTransferService.stow(sysFile, library, props, sourceLines);
    }

    @Override
    public void save(IPalTypeSystemFile sysFile, String library,
                     IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        sourceTransferService.save(sysFile, library, props, sourceLines);
    }

    @Override
    public String[] read(IPalTypeSystemFile sysFile, String library,
                         String name, Set<EReadOption> options)
            throws IOException, PalResultException {
        return sourceTransferService.read(sysFile, library, name, options);
    }

    // ══════════════════════════════════════════════════════════════
    //  Object Operations (→ ObjectManagementService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public void copy(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, int kind, int type)
            throws IOException, PalResultException {
        objectManagementService.copy(srcSysFile, srcLib, srcObj, dstSysFile, dstLib, kind, type);
    }

    @Override
    public void copy(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, IPalTypeObject objDesc)
            throws IOException, PalResultException {
        objectManagementService.copy(srcSysFile, srcLib, srcObj, dstSysFile, dstLib, objDesc);
    }

    @Override
    public void move(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, String dstObj, int kind, int type)
            throws IOException, PalResultException {
        objectManagementService.move(srcSysFile, srcLib, srcObj, dstSysFile, dstLib, dstObj, kind, type);
    }

    @Override
    public void move(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, String dstObj, IPalTypeObject objDesc)
            throws IOException, PalResultException {
        objectManagementService.move(srcSysFile, srcLib, srcObj, dstSysFile, dstLib, dstObj, objDesc);
    }

    @Override
    public void delete(IPalTypeSystemFile sysFile, String library, IFileProperties props)
            throws IOException, PalResultException {
        objectManagementService.delete(sysFile, library, props);
    }

    @Override
    public void delete(IPalTypeSystemFile sysFile, int kind, String name)
            throws IOException, PalResultException {
        objectManagementService.delete(sysFile, kind, name);
    }

    @Override
    public void delete1(IPalTypeSystemFile sysFile, String library, int kind, int type, String name)
            throws IOException, PalResultException {
        objectManagementService.delete1(sysFile, library, kind, type, name);
    }

    @Override
    public void delete2(IPalTypeSystemFile sysFile, String library, int kind, int type,
                        String name, String longName)
            throws IOException, PalResultException {
        objectManagementService.delete2(sysFile, library, kind, type, name, longName);
    }

    // ══════════════════════════════════════════════════════════════
    //  Lock / Unlock (→ ObjectManagementService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public void isLocked(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        objectManagementService.isLocked(sysFile, library, name, kind, type);
    }

    @Override
    public void lock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        objectManagementService.lock(sysFile, library, name, kind, type);
    }

    @Override
    public void unlock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        objectManagementService.unlock(sysFile, library, name, kind, type);
    }

    // ══════════════════════════════════════════════════════════════
    //  Step Libraries / Code Pages / Server Config (→ ConfigurationService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public IPalTypeLibId[] getStepLibs() throws IOException, PalResultException {
        return configurationService.getStepLibs();
    }

    @Override
    public void setStepLibs(IPalTypeLibId[] libs, EStepLibFormat format)
            throws IOException, PalResultException {
        configurationService.setStepLibs(libs, format);
    }

    @Override
    public IPalTypeCP[] getCodePages() throws IOException, PalResultException {
        return configurationService.getCodePages();
    }

    @Override
    public void setCodePageOfSource(IPalTypeSystemFile sysFile, String library,
                                    String name, int type, String codePage)
            throws IOException, PalResultException {
        configurationService.setCodePageOfSource(sysFile, library, name, type, codePage);
    }

    @Override
    public IServerConfiguration getServerConfiguration(boolean refresh)
            throws IOException, PalResultException {
        return configurationService.getServerConfiguration(refresh);
    }

    @Override
    public IPalProperties getPalProperties() {
        return configurationService.getPalProperties();
    }

    // ══════════════════════════════════════════════════════════════
    //  Preferences / Configuration setters
    // ══════════════════════════════════════════════════════════════

    @Override
    public void setPalPreferences(IPalPreferences prefs) {
        ctx.setPalPreferences(prefs);
    }

    @Override
    public void setPalSQLIdentification(IPalSQLIdentification sqlId) {
        ctx.setPalSQLIdentification(sqlId);
    }

    @Override
    public void setApplicationExecutionContext(IPalExecutionContext exCtx) {
        ctx.setExecutionContext(exCtx);
    }

    @Override
    public void setArabicShapingContext(IPalArabicShaping shaping) {
        ctx.setShapingContext(shaping);
    }

    @Override
    public void setPalTimeoutHandler(IPalTimeoutHandler handler) {
        ctx.setTimeoutHandler(handler);
        if (ctx.getPal() != null) {
            ctx.getPal().setPalTimeoutHandler(handler);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Natural Parameters / System Variables (→ ConfigurationService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public INatParm getNaturalParameters() {
        return configurationService.getNaturalParameters();
    }

    @Override
    public void setNaturalParameters(INatParm parm) {
        configurationService.setNaturalParameters(parm);
    }

    @Override
    public IPalTypeSysVar[] getSystemVariables() throws IOException, PalResultException {
        return configurationService.getSystemVariables();
    }

    @Override
    public void setSystemVariables(IPalTypeSysVar[] vars) {
        configurationService.setSystemVariables(vars);
    }

    // ══════════════════════════════════════════════════════════════
    //  DDM Generation (→ ConfigurationService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public String[] generateAdabasDdm(int dbid, int fnr, String ddmName)
            throws IOException, PalResultException {
        return configurationService.generateAdabasDdm(dbid, fnr, ddmName);
    }

    @Override
    public String[] generateSqlDdm(int dbid, int fnr, String ddmName, String tableName)
            throws IOException, PalResultException {
        return configurationService.generateSqlDdm(dbid, fnr, ddmName, tableName);
    }

    @Override
    public String[] generateXmlDdm(int dbid, int fnr, String ddmName,
                                    String schemaUri, String rootElement, String prefix)
            throws IOException, PalResultException {
        return configurationService.generateXmlDdm(dbid, fnr, ddmName, schemaUri, rootElement, prefix);
    }

    // ══════════════════════════════════════════════════════════════
    //  CmdGuard (→ ConfigurationService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public IPalTypeCmdGuard getCmdGuardInfo(int option, IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {
        return configurationService.getCmdGuardInfo(option, sysFile, library);
    }

    // ══════════════════════════════════════════════════════════════
    //  Debug (→ DebugService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ISuspendResult debugStart(String program, String library, String params)
            throws IOException, PalResultException {
        return debugService.debugStart(program, library, params);
    }

    @Override
    public ISuspendResult debugResume() throws IOException {
        return debugService.debugResume();
    }

    @Override
    public ISuspendResult debugStepInto() throws IOException {
        return debugService.debugStepInto();
    }

    @Override
    public ISuspendResult debugStepOver() throws IOException {
        return debugService.debugStepOver();
    }

    @Override
    public ISuspendResult debugStepReturn() throws IOException {
        return debugService.debugStepReturn();
    }

    @Override
    public void debugExit() throws IOException, PalResultException {
        debugService.debugExit();
    }

    @Override
    public IPalTypeDbgStackFrame[] setNextStatement(IPalTypeDbgStackFrame frame)
            throws IOException, PalResultException {
        return debugService.setNextStatement(frame);
    }

    @Override
    public IPalTypeDbgSyt[] getSymbolTable(IPalTypeDbgVarContainer container)
            throws IOException, PalResultException {
        return debugService.getSymbolTable(container);
    }

    @Override
    public IPalTypeDbgVarValue[] getValue(IPalTypeDbgVarContainer container, IPalTypeDbgVarDesc desc)
            throws IOException, PalResultException {
        return debugService.getValue(container, desc);
    }

    @Override
    public void modifyValue(IPalTypeDbgVarContainer container, IPalTypeDbgVarDesc desc,
                            IPalTypeDbgVarValue value)
            throws IOException, PalResultException {
        debugService.modifyValue(container, desc, value);
    }

    @Override
    public IPalTypeDbgVarDesc[] resolveIndices(boolean flag, IPalTypeDbgVarContainer container,
                                                IPalTypeDbgVarDesc desc)
            throws IOException, PalResultException {
        return debugService.resolveIndices(flag, container, desc);
    }

    @Override
    public IPalTypeDbgSpy spySet(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        return debugService.spySet(spy);
    }

    @Override
    public IPalTypeDbgSpy spySet(IPalTypeDbgSpy spy, IPalTypeDbgVarDesc desc, IPalTypeDbgVarValue value)
            throws IOException, PalResultException {
        return debugService.spySet(spy, desc, value);
    }

    @Override
    public IPalTypeDbgSpy spyModify(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        return debugService.spyModify(spy);
    }

    @Override
    public IPalTypeDbgSpy spyModify(IPalTypeDbgSpy spy, IPalTypeDbgVarDesc desc, IPalTypeDbgVarValue value)
            throws IOException, PalResultException {
        return debugService.spyModify(spy, desc, value);
    }

    @Override
    public IPalTypeDbgSpy spyDelete(IPalTypeDbgSpy spy) throws IOException, PalResultException {
        return debugService.spyDelete(spy);
    }

    // ══════════════════════════════════════════════════════════════
    //  Screen / Command / Execute (→ DebugService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ISuspendResult command(String cmd, int option) throws IOException {
        return debugService.command(cmd, option);
    }

    @Override
    public ISuspendResult sendScreen(byte[] data) throws IOException {
        return debugService.sendScreen(data);
    }

    @Override
    public ISuspendResult nextScreen() throws IOException {
        return debugService.nextScreen();
    }

    @Override
    public ISuspendResult execute(String command) throws IOException {
        return debugService.execute(command);
    }

    @Override
    public void terminateIO() throws IOException, PalResultException {
        debugService.terminateIO();
    }

    // ══════════════════════════════════════════════════════════════
    //  Utility Buffer (→ DebugService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public void utilityBufferSend(short type, byte[] data) throws IOException {
        debugService.utilityBufferSend(type, data);
    }

    @Override
    public byte[] utilityBufferReceive() throws IOException, PalResultException {
        return debugService.utilityBufferReceive();
    }

    // ══════════════════════════════════════════════════════════════
    //  DAS (→ DebugService)
    // ══════════════════════════════════════════════════════════════

    @Override
    public void dasConnect(Map<String, String> params, int type)
            throws IOException, UnknownHostException, ConnectException, PalResultException {
        debugService.dasConnect(params, type);
    }

    @Override
    public void dasWaitForAttach(String sessionId, IDebugAttachWaitCallBack callback)
            throws PalResultException {
        debugService.dasWaitForAttach(sessionId, callback);
    }

    @Override
    public void dasRegisterDebugAttachRecord(IPalTypeDbgaRecord record) throws PalResultException {
        debugService.dasRegisterDebugAttachRecord(record);
    }

    @Override
    public void dasUnregisterDebugAttachRecord(IPalTypeDbgaRecord record, int option)
            throws PalResultException {
        debugService.dasUnregisterDebugAttachRecord(record, option);
    }

    @Override
    public void dasBindToAttachSession(String session, String library, String program)
            throws IOException, PalResultException {
        debugService.dasBindToAttachSession(session, library, program);
    }

    @Override
    public void dasSignIn() throws IOException, PalResultException {
        debugService.dasSignIn();
    }

    @Override
    public ISuspendResult dasDebugStart() throws IOException, PalResultException {
        return debugService.dasDebugStart();
    }
}
