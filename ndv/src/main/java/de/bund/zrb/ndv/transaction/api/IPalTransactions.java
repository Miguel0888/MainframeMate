package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

/**
 * Hauptschnittstelle für PAL-Transaktionen mit dem NDV-Server.
 */
public interface IPalTransactions {

    void setAutomaticLogon(boolean auto);

    void catalog(IPalTypeSystemFile sysFile, String library, IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException;

    void check(IPalTypeSystemFile sysFile, String library, IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException;

    void close() throws IOException, PalResultException;

    void close(int option) throws IOException, PalResultException;

    ISuspendResult command(String cmd, int option) throws IOException;

    More connect(Map<String, String> params)
            throws IOException, UnknownHostException, ConnectException, PalConnectResultException;

    void copy(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
              IPalTypeSystemFile dstSysFile, String dstLib, int kind, int type)
            throws IOException, PalResultException;

    void copy(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
              IPalTypeSystemFile dstSysFile, String dstLib, IPalTypeObject objDesc)
            throws IOException, PalResultException;

    Object createTransactionContext(Class contextClass);

    void disposeTransactionContext(ITransactionContext ctx);

    void debugExit() throws IOException, PalResultException;

    ISuspendResult debugResume() throws IOException;

    ISuspendResult debugStart(String program, String library, String params)
            throws IOException, PalResultException;

    ISuspendResult debugStepInto() throws IOException;

    ISuspendResult debugStepOver() throws IOException;

    ISuspendResult debugStepReturn() throws IOException;

    void delete(IPalTypeSystemFile sysFile, String library, IFileProperties props)
            throws IOException, PalResultException;

    void delete(IPalTypeSystemFile sysFile, int kind, String name)
            throws IOException, PalResultException;

    void delete1(IPalTypeSystemFile sysFile, String library, int kind, int type, String name)
            throws IOException, PalResultException;

    void delete2(IPalTypeSystemFile sysFile, String library, int kind, int type, String name, String longName)
            throws IOException, PalResultException;

    void disconnect() throws IOException;

    ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile, String library, String name, String longName, int type)
            throws IOException, PalResultException;

    ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile, String library, String name, int type)
            throws IOException, PalResultException;

    ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile, String library, IFileProperties props)
            throws IOException, PalResultException;

    ByteArrayOutputStream downloadBinary(ITransactionContext ctx, IPalTypeSystemFile sysFile, String library, IFileProperties props)
            throws IOException, PalResultException;

    IDownloadResult downloadSource(ITransactionContext ctx, IPalTypeSystemFile sysFile,
                                   String library, IFileProperties props, Set<EDownLoadOption> options)
            throws IOException, PalResultException;

    Object[] receiveFiles(IPalTypeSystemFile sysFile, String library, IFileProperties props, Set<EDownLoadOption> options)
            throws IOException, PalResultException;

    IPalTypeObject exists(IPalTypeSystemFile sysFile, String library, String name, int type)
            throws IOException, PalResultException;

    IPalTypeObject exists(IPalTypeSystemFile sysFile, String name, int type)
            throws IOException, PalResultException;

    String[] generateAdabasDdm(int dbid, int fnr, String ddmName)
            throws IOException, PalResultException;

    String[] generateSqlDdm(int dbid, int fnr, String ddmName, String tableName)
            throws IOException, PalResultException;

    String[] generateXmlDdm(int dbid, int fnr, String ddmName, String schemaUri, String rootElement, String prefix)
            throws IOException, PalResultException;

    IPalTypeCmdGuard getCmdGuardInfo(int option, IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException;

    IPalTypeCP[] getCodePages() throws IOException, PalResultException;

    IPalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException;

    IPalTypeLibId getLibraryOfObject(IPalTypeLibId libId, String objectName, Set<EObjectKind> kinds)
            throws IOException, PalResultException;

    IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library, String filter, int kind, int type)
            throws IOException, PalResultException;

    IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library, int kind)
            throws IOException, PalResultException;

    IPalTypeLibrary[] getLibrariesNext() throws IOException, PalResultException;

    IPalTypeLibraryStatistics getLibraryStatistics(Set<ELibraryStatisticsOption> options,
                                                   IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException;

    String getLogonLibrary() throws IOException, PalResultException;

    int getNumberOfLibraries(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException;

    int getNumberOfObjects(IPalTypeSystemFile sysFile, String library, String filter, int kind, int type)
            throws IOException, PalResultException;

    int getNumberOfObjects(IPalTypeSystemFile sysFile, String library, int kind)
            throws IOException, PalResultException;

    ISourceLookupResult getObjectByLongName(IPalTypeSystemFile sysFile, String library, String longName,
                                             int type, boolean withSource)
            throws IOException, PalResultException;

    ISourceLookupResult getObjectByName(IPalTypeSystemFile sysFile, String library, String name,
                                         int type, boolean withSource)
            throws IOException, PalResultException;

    IPalTypeObject[] getObjectsNext() throws IOException, PalResultException;

    IPalProperties getPalProperties();

    IServerConfiguration getServerConfiguration(boolean refresh) throws IOException, PalResultException;

    IPalTypeLibId[] getStepLibs() throws IOException, PalResultException;

    ILibraryInfo getLibraryInfo(int option, IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException;

    IPalTypeDbgVarValue[] getValue(IPalTypeDbgVarContainer container, IPalTypeDbgVarDesc desc)
            throws IOException, PalResultException;

    IPalTypeDbgSyt[] getSymbolTable(IPalTypeDbgVarContainer container)
            throws IOException, PalResultException;

    IPalTypeSystemFile[] getSystemFiles() throws IOException, PalResultException;

    boolean isConnected();

    boolean isDisconnected();

    boolean isLibraryEmpty(IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException;

    void isLocked(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException;

    void lock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException;

    void logon(String library) throws IOException, PalResultException;

    void logon(String library, IPalTypeLibId[] stepLibs) throws IOException, PalResultException;

    void modifyValue(IPalTypeDbgVarContainer container, IPalTypeDbgVarDesc desc, IPalTypeDbgVarValue value)
            throws IOException, PalResultException;

    void move(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
              IPalTypeSystemFile dstSysFile, String dstLib, String dstObj, int kind, int type)
            throws IOException, PalResultException;

    void move(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
              IPalTypeSystemFile dstSysFile, String dstLib, String dstObj, IPalTypeObject objDesc)
            throws IOException, PalResultException;

    ISuspendResult nextScreen() throws IOException;

    void reconnect() throws IOException, PalResultException;

    void terminateIO() throws IOException, PalResultException;

    IPalTypeDbgVarDesc[] resolveIndices(boolean flag, IPalTypeDbgVarContainer container, IPalTypeDbgVarDesc desc)
            throws IOException, PalResultException;

    ISuspendResult sendScreen(byte[] data) throws IOException;

    void setCodePageOfSource(IPalTypeSystemFile sysFile, String library, String name, int type, String codePage)
            throws IOException, PalResultException;

    void setPalPreferences(IPalPreferences prefs);

    void setPalSQLIdentification(IPalSQLIdentification sqlId);

    void setApplicationExecutionContext(IPalExecutionContext ctx);

    void setArabicShapingContext(IPalArabicShaping shaping);

    void setPalTimeoutHandler(IPalTimeoutHandler handler);

    void setStepLibs(IPalTypeLibId[] libs, EStepLibFormat format)
            throws IOException, PalResultException;

    IPalTypeDbgSpy spyDelete(IPalTypeDbgSpy spy) throws IOException, PalResultException;

    IPalTypeDbgSpy spyModify(IPalTypeDbgSpy spy) throws IOException, PalResultException;

    IPalTypeDbgSpy spyModify(IPalTypeDbgSpy spy, IPalTypeDbgVarDesc desc, IPalTypeDbgVarValue value)
            throws IOException, PalResultException;

    IPalTypeDbgSpy spySet(IPalTypeDbgSpy spy) throws IOException, PalResultException;

    IPalTypeDbgSpy spySet(IPalTypeDbgSpy spy, IPalTypeDbgVarDesc desc, IPalTypeDbgVarValue value)
            throws IOException, PalResultException;

    IPalTypeDbgStackFrame[] setNextStatement(IPalTypeDbgStackFrame frame)
            throws IOException, PalResultException;

    void stow(IPalTypeSystemFile sysFile, String library, IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException;

    void save(IPalTypeSystemFile sysFile, String library, IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException;

    String[] read(IPalTypeSystemFile sysFile, String library, String name, Set<EReadOption> options)
            throws IOException, PalResultException;

    void terminateRetrieval() throws IOException, PalResultException;

    void unlock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException;

    void uploadSource(IPalTypeSystemFile sysFile, String library, IFileProperties props,
                      Set<EUploadOption> options, String[] sourceLines)
            throws IOException, PalResultException;

    void sendFiles(IPalTypeSystemFile sysFile, String library, FileProperties objProps,
                   Set<EUploadOption> options, Object[] data)
            throws IOException, PalResultException;

    void abortFileOperation(Set<EDownLoadOption> options) throws IOException, PalResultException;

    void uploadBinary(IPalTypeSystemFile sysFile, String library, IFileProperties props, ByteArrayOutputStream data)
            throws IOException, PalResultException;

    void utilityBufferSend(short type, byte[] data) throws IOException;

    byte[] utilityBufferReceive() throws IOException, PalResultException;

    ISuspendResult execute(String command) throws IOException;

    INatParm getNaturalParameters();

    IPalTypeSysVar[] getSystemVariables() throws IOException, PalResultException;

    void setNaturalParameters(INatParm parm);

    void setSystemVariables(IPalTypeSysVar[] vars);

    void dasConnect(Map<String, String> params, int type)
            throws IOException, UnknownHostException, ConnectException, PalResultException;

    void dasWaitForAttach(String sessionId, IDebugAttachWaitCallBack callback)
            throws PalResultException;

    void dasRegisterDebugAttachRecord(IPalTypeDbgaRecord record) throws PalResultException;

    void dasUnregisterDebugAttachRecord(IPalTypeDbgaRecord record, int option) throws PalResultException;

    void dasBindToAttachSession(String session, String library, String program)
            throws IOException, PalResultException;

    void dasSignIn() throws IOException, PalResultException;

    ISuspendResult dasDebugStart() throws IOException, PalResultException;

    String getSessionId();

    void initTraceSession(String sessionId) throws PalResultException;
}

