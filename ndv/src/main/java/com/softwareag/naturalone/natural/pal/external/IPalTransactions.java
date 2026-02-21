package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.paltransactions.external.EDownLoadOption;
import com.softwareag.naturalone.natural.paltransactions.external.EUploadOption;
import com.softwareag.naturalone.natural.paltransactions.external.IDownloadResult;
import com.softwareag.naturalone.natural.paltransactions.external.IFileProperties;

import java.util.Map;
import java.util.Set;

/**
 * Stub-Interface für IPalTransactions aus ndvserveraccess.
 * Wird zur Compile-Zeit verwendet; zur Laufzeit durch einen dynamischen Proxy
 * gegen die echte Implementierung aus dem JAR gebunden.
 */
public interface IPalTransactions {

    void connect(Map<String, String> params) throws PalConnectResultException;

    void disconnect();

    boolean isConnected();

    void logon(String library) throws PalResultException;

    IPalTypeSystemFile[] getSystemFiles() throws PalResultException;

    IPalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile sysFile, String filter) throws PalResultException;

    IPalTypeLibrary[] getLibrariesNext() throws PalResultException;

    IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library,
                                     String filter, int kind, int type) throws PalResultException;

    IPalTypeObject[] getObjectsNext() throws PalResultException;

    /**
     * Erzeugt einen Transaction-Context.
     * Rückgabetyp ist {@code Object}, weil das echte Objekt aus dem JAR-ClassLoader
     * nicht auf den Stub-Typ gecastet werden kann.
     */
    Object createTransactionContext(Class<?> contextClass);

    IDownloadResult downloadSource(Object ctx, IPalTypeSystemFile sysFile,
                                   String library, IFileProperties props,
                                   Set<EDownLoadOption> options) throws PalResultException;

    void disposeTransactionContext(Object ctx);

    void uploadSource(IPalTypeSystemFile sysFile, String library,
                      IFileProperties props, Set<EUploadOption> options,
                      String[] sourceLines) throws PalResultException;
}

