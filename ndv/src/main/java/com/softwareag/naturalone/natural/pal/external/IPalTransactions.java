package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.paltransactions.external.EDownLoadOption;
import com.softwareag.naturalone.natural.paltransactions.external.EUploadOption;
import com.softwareag.naturalone.natural.paltransactions.external.IDownloadResult;

import java.util.Map;
import java.util.Set;

/**
 * Unser eigenes Fassaden-Interface fuer IPalTransactions.
 * Liegt in pal.external (nicht in paltransactions.external), daher
 * KEIN Namenskonflikt mit dem echten Interface aus dem JAR.
 *
 * NdvClient kompiliert gegen dieses Interface.
 * PalTransactionsFactory erzeugt einen Proxy der an die echte Implementierung delegiert.
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

    Object createTransactionContext(Class<?> contextClass);

    IDownloadResult downloadSource(Object ctx, IPalTypeSystemFile sysFile,
                                   String library, IFileProperties props,
                                   Set<EDownLoadOption> options) throws PalResultException;

    void disposeTransactionContext(Object ctx);

    void uploadSource(IPalTypeSystemFile sysFile, String library,
                      IFileProperties props, Set<EUploadOption> options,
                      String[] sourceLines) throws PalResultException;
}
