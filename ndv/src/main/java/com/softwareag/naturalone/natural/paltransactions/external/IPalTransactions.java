package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IFileProperties;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibrary;
import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSystemFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Hauptschnittstelle für PAL-Transaktionen mit dem NDV-Server.
 * Umfasst Verbindung, Anmeldung, Bibliotheks-/Objektverwaltung sowie Quellcode-Transfer.
 */
public interface IPalTransactions {

    void connect(Map<String, String> params) throws IOException, PalConnectResultException;

    void disconnect() throws IOException;

    boolean isConnected();

    void logon(String library) throws IOException, PalResultException;

    IPalTypeSystemFile[] getSystemFiles() throws IOException, PalResultException;

    IPalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException;

    IPalTypeLibrary[] getLibrariesNext() throws IOException, PalResultException;

    IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library,
                                     String filter, int kind, int type)
            throws IOException, PalResultException;

    IPalTypeObject[] getObjectsNext() throws IOException, PalResultException;

    ITransactionContext createTransactionContext(Class<?> contextClass)
            throws IOException, PalResultException;

    void disposeTransactionContext(ITransactionContext ctx)
            throws IOException, PalResultException;

    IDownloadResult downloadSource(ITransactionContextDownload ctx,
                                   IPalTypeSystemFile sysFile, String library,
                                   IFileProperties props, Set<EDownLoadOption> options)
            throws IOException, PalResultException;

    void uploadSource(IPalTypeSystemFile sysFile, String library,
                      IFileProperties props, Set<EUploadOption> options,
                      String[] sourceLines)
            throws IOException, PalResultException;
}

