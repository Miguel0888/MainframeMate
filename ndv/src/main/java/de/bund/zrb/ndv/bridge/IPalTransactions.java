package de.bund.zrb.ndv.bridge;

import com.softwareag.naturalone.natural.pal.external.IFileProperties;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibrary;
import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSystemFile;
import com.softwareag.naturalone.natural.paltransactions.external.EDownLoadOption;
import com.softwareag.naturalone.natural.paltransactions.external.EUploadOption;
import com.softwareag.naturalone.natural.paltransactions.external.IDownloadResult;

import java.util.Map;
import java.util.Set;

/**
 * Eigenes Fassaden-Interface fuer IPalTransactions.
 * Liegt in de.bund.zrb.ndv.bridge, daher KEIN Package-Konflikt
 * mit dem signierten ndvserveraccess-JAR.
 *
 * NdvClient kompiliert gegen dieses Interface.
 * PalTransactionsFactory erzeugt einen Proxy, der an die echte
 * Implementierung delegiert.
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

