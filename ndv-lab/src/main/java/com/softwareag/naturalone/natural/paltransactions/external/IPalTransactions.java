package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IFileProperties;
import com.softwareag.naturalone.natural.pal.external.IPalTimeoutHandler;
import com.softwareag.naturalone.natural.pal.external.IPalTypeCP;
import com.softwareag.naturalone.natural.pal.external.IPalTypeCmdGuard;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSyt;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarContainer;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarDesc;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarValue;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgaRecord;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibId;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibrary;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibraryStatistics;
import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSysVar;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSystemFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

public interface IPalTransactions {
   void setAutomaticLogon(boolean var1);

   void catalog(IPalTypeSystemFile var1, String var2, IFileProperties var3, String[] var4) throws IOException, PalResultException, PalCompileResultException;

   void check(IPalTypeSystemFile var1, String var2, IFileProperties var3, String[] var4) throws IOException, PalResultException, PalCompileResultException;

   void close() throws IOException, PalResultException;

   void close(int var1) throws IOException, PalResultException;

   ISuspendResult command(String var1, int var2) throws IOException;

   More connect(Map var1) throws IOException, UnknownHostException, ConnectException, PalConnectResultException;

   /** @deprecated */
   void copy(IPalTypeSystemFile var1, String var2, String var3, IPalTypeSystemFile var4, String var5, int var6, int var7) throws IOException, PalResultException;

   void copy(IPalTypeSystemFile var1, String var2, String var3, IPalTypeSystemFile var4, String var5, IPalTypeObject var6) throws IOException, PalResultException;

   Object createTransactionContext(Class var1);

   void disposeTransactionContext(ITransactionContext var1);

   void debugExit() throws IOException, PalResultException;

   ISuspendResult debugResume() throws IOException;

   ISuspendResult debugStart(String var1, String var2, String var3) throws IOException, PalResultException;

   ISuspendResult debugStepInto() throws IOException;

   ISuspendResult debugStepOver() throws IOException;

   ISuspendResult debugStepReturn() throws IOException;

   void delete(IPalTypeSystemFile var1, String var2, IFileProperties var3) throws IOException, PalResultException;

   /** @deprecated */
   void delete(IPalTypeSystemFile var1, int var2, String var3) throws IOException, PalResultException;

   /** @deprecated */
   void delete1(IPalTypeSystemFile var1, String var2, int var3, int var4, String var5) throws IOException, PalResultException;

   /** @deprecated */
   void delete2(IPalTypeSystemFile var1, String var2, int var3, int var4, String var5, String var6) throws IOException, PalResultException;

   void disconnect() throws IOException;

   /** @deprecated */
   ByteArrayOutputStream downloadBinary(IPalTypeSystemFile var1, String var2, String var3, String var4, int var5) throws IOException, PalResultException;

   /** @deprecated */
   ByteArrayOutputStream downloadBinary(IPalTypeSystemFile var1, String var2, String var3, int var4) throws IOException, PalResultException;

   ByteArrayOutputStream downloadBinary(IPalTypeSystemFile var1, String var2, IFileProperties var3) throws IOException, PalResultException;

   ByteArrayOutputStream downloadBinary(ITransactionContext var1, IPalTypeSystemFile var2, String var3, IFileProperties var4) throws IOException, PalResultException;

   IDownloadResult downloadSource(ITransactionContext var1, IPalTypeSystemFile var2, String var3, IFileProperties var4, Set var5) throws IOException, PalResultException;

   Object[] receiveFiles(IPalTypeSystemFile var1, String var2, IFileProperties var3, Set var4) throws IOException, PalResultException;

   IPalTypeObject exists(IPalTypeSystemFile var1, String var2, String var3, int var4) throws IOException, PalResultException;

   IPalTypeObject exists(IPalTypeSystemFile var1, String var2, int var3) throws IOException, PalResultException;

   String[] generateAdabasDdm(int var1, int var2, String var3) throws IOException, PalResultException;

   String[] generateSqlDdm(int var1, int var2, String var3, String var4) throws IOException, PalResultException;

   String[] generateXmlDdm(int var1, int var2, String var3, String var4, String var5, String var6) throws IOException, PalResultException;

   IPalTypeCmdGuard getCmdGuardInfo(int var1, IPalTypeSystemFile var2, String var3) throws IOException, PalResultException;

   IPalTypeCP[] getCodePages() throws IOException, PalResultException;

   IPalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile var1, String var2) throws IOException, PalResultException;

   IPalTypeLibId getLibraryOfObject(IPalTypeLibId var1, String var2, Set var3) throws IOException, PalResultException;

   IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException;

   /** @deprecated */
   @Deprecated
   IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile var1, String var2, int var3) throws IOException, PalResultException;

   IPalTypeLibrary[] getLibrariesNext() throws IOException, PalResultException;

   IPalTypeLibraryStatistics getLibraryStatistics(Set var1, IPalTypeSystemFile var2, String var3) throws IOException, PalResultException;

   String getLogonLibrary() throws IOException, PalResultException;

   int getNumberOfLibraries(IPalTypeSystemFile var1, String var2) throws IOException, PalResultException;

   int getNumberOfObjects(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException;

   int getNumberOfObjects(IPalTypeSystemFile var1, String var2, int var3) throws IOException, PalResultException;

   ISourceLookupResult getObjectByLongName(IPalTypeSystemFile var1, String var2, String var3, int var4, boolean var5) throws IOException, PalResultException;

   ISourceLookupResult getObjectByName(IPalTypeSystemFile var1, String var2, String var3, int var4, boolean var5) throws IOException, PalResultException;

   IPalTypeObject[] getObjectsNext() throws IOException, PalResultException;

   IPalProperties getPalProperties();

   IServerConfiguration getServerConfiguration(boolean var1) throws IOException, PalResultException;

   IPalTypeLibId[] getStepLibs() throws IOException, PalResultException;

   ILibraryInfo getLibraryInfo(int var1, IPalTypeSystemFile var2, String var3) throws IOException, PalResultException;

   IPalTypeDbgVarValue[] getValue(IPalTypeDbgVarContainer var1, IPalTypeDbgVarDesc var2) throws IOException, PalResultException;

   IPalTypeDbgSyt[] getSymbolTable(IPalTypeDbgVarContainer var1) throws IOException, PalResultException;

   IPalTypeSystemFile[] getSystemFiles() throws IOException, PalResultException;

   boolean isConnected();

   boolean isDisconnected();

   boolean isLibraryEmpty(IPalTypeSystemFile var1, String var2) throws IOException, PalResultException;

   void isLocked(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException;

   void lock(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException;

   void logon(String var1) throws IOException, PalResultException;

   void logon(String var1, IPalTypeLibId[] var2) throws IOException, PalResultException;

   void modifyValue(IPalTypeDbgVarContainer var1, IPalTypeDbgVarDesc var2, IPalTypeDbgVarValue var3) throws IOException, PalResultException;

   /** @deprecated */
   void move(IPalTypeSystemFile var1, String var2, String var3, IPalTypeSystemFile var4, String var5, String var6, int var7, int var8) throws IOException, PalResultException;

   void move(IPalTypeSystemFile var1, String var2, String var3, IPalTypeSystemFile var4, String var5, String var6, IPalTypeObject var7) throws IOException, PalResultException;

   ISuspendResult nextScreen() throws IOException;

   void reconnect() throws IOException, PalResultException;

   void terminateIO() throws IOException, PalResultException;

   IPalTypeDbgVarDesc[] resolveIndices(boolean var1, IPalTypeDbgVarContainer var2, IPalTypeDbgVarDesc var3) throws IOException, PalResultException;

   ISuspendResult sendScreen(byte[] var1) throws IOException;

   void setCodePageOfSource(IPalTypeSystemFile var1, String var2, String var3, int var4, String var5) throws IOException, PalResultException;

   void setPalPreferences(IPalPreferences var1);

   void setPalSQLIdentification(IPalSQLIdentification var1);

   void setApplicationExecutionContext(IPalExecutionContext var1);

   void setArabicShapingContext(IPalArabicShaping var1);

   void setPalTimeoutHandler(IPalTimeoutHandler var1);

   void setStepLibs(IPalTypeLibId[] var1, EStepLibFormat var2) throws IOException, PalResultException;

   IPalTypeDbgSpy spyDelete(IPalTypeDbgSpy var1) throws IOException, PalResultException;

   IPalTypeDbgSpy spyModify(IPalTypeDbgSpy var1) throws IOException, PalResultException;

   IPalTypeDbgSpy spyModify(IPalTypeDbgSpy var1, IPalTypeDbgVarDesc var2, IPalTypeDbgVarValue var3) throws IOException, PalResultException;

   IPalTypeDbgSpy spySet(IPalTypeDbgSpy var1) throws IOException, PalResultException;

   IPalTypeDbgSpy spySet(IPalTypeDbgSpy var1, IPalTypeDbgVarDesc var2, IPalTypeDbgVarValue var3) throws IOException, PalResultException;

   IPalTypeDbgStackFrame[] setNextStatement(IPalTypeDbgStackFrame var1) throws IOException, PalResultException;

   void stow(IPalTypeSystemFile var1, String var2, IFileProperties var3, String[] var4) throws IOException, PalResultException, PalCompileResultException;

   void save(IPalTypeSystemFile var1, String var2, IFileProperties var3, String[] var4) throws IOException, PalResultException, PalCompileResultException;

   String[] read(IPalTypeSystemFile var1, String var2, String var3, Set var4) throws IOException, PalResultException;

   void terminateRetrieval() throws IOException, PalResultException;

   void unlock(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException;

   void uploadSource(IPalTypeSystemFile var1, String var2, IFileProperties var3, Set var4, String[] var5) throws IOException, PalResultException;

   void sendFiles(IPalTypeSystemFile var1, String var2, ObjectProperties var3, Set var4, Object[] var5) throws IOException, PalResultException;

   void abortFileOperation(Set var1) throws IOException, PalResultException;

   void uploadBinary(IPalTypeSystemFile var1, String var2, IFileProperties var3, ByteArrayOutputStream var4) throws IOException, PalResultException;

   void utilityBufferSend(short var1, byte[] var2) throws IOException;

   byte[] utilityBufferReceive() throws IOException, PalResultException;

   ISuspendResult execute(String var1) throws IOException;

   INatParm getNaturalParameters();

   IPalTypeSysVar[] getSystemVariables() throws IOException, PalResultException;

   void setNaturalParameters(INatParm var1);

   void setSystemVariables(IPalTypeSysVar[] var1);

   void dasConnect(Map var1, int var2) throws IOException, UnknownHostException, ConnectException, PalResultException;

   void dasWaitForAttach(String var1, IDebugAttachWaitCallBack var2) throws PalResultException;

   void dasRegisterDebugAttachRecord(IPalTypeDbgaRecord var1) throws PalResultException;

   void dasUnregisterDebugAttachRecord(IPalTypeDbgaRecord var1, int var2) throws PalResultException;

   void dasBindToAttachSession(String var1, String var2, String var3) throws IOException, PalResultException;

   void dasSignIn() throws IOException, PalResultException;

   ISuspendResult dasDebugStart() throws IOException, PalResultException;

   String getSessionId();

   void initTraceSession(String var1) throws PalResultException;
}
