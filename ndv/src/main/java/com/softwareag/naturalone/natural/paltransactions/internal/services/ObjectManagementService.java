package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.*;
import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.pal.type.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.IOException;
import java.util.EnumSet;

/**
 * CRUD-Operationen auf Objekten:
 * copy, move, delete, isLocked, lock, unlock.
 *
 * Implementierung exakt nachgebaut aus dem Original-Bytecode
 * (PalTransactions Zeilen 569-606, 727-791, 748-771, 2639-2686, 2748-2786, 3145-3169).
 */
public class ObjectManagementService {

    private final PalSessionContext ctx;

    public ObjectManagementService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    // ══════════════════════════════════════════════════════════════
    //  Copy
    // ══════════════════════════════════════════════════════════════

    public void copy(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, int kind, int type)
            throws IOException, PalResultException {
        PalTypeObject objDesc = new PalTypeObject();
        objDesc.setKind(kind);
        objDesc.setType(type);
        this.copy(srcSysFile, srcLib, srcObj, dstSysFile, dstLib, objDesc);
    }

    public void copy(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, IPalTypeObject objDesc)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (srcLib == null || dstLib == null) {
            throw new IllegalArgumentException("library must not be null");
        }
        if (srcObj == null) {
            throw new IllegalArgumentException("objectName must not be null");
        }
        PalTrace.header("copy");
        PalTypeFileId fileId = new PalTypeFileId();
        fileId.setNatKind(objDesc.getKind());
        fileId.setNatType(objDesc.getType());
        fileId.setObject(srcObj);
        fileId.setNewObject(srcObj);
        fileId.setSourceSize(objDesc.getSourceSize());
        fileId.setUser(objDesc.getSourceUser());
        fileId.setSourceDate(objDesc.getSourceDate());
        fileId.setGpSize(objDesc.getGpSize());
        fileId.setGpUser(objDesc.getGpUser());
        fileId.setGpDate(objDesc.getGpDate());
        dateiOperationServerLokal(40, srcSysFile, srcLib, dstSysFile, dstLib, fileId);
    }

    // ══════════════════════════════════════════════════════════════
    //  Move
    // ══════════════════════════════════════════════════════════════

    public void move(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, String dstObj, int kind, int type)
            throws IOException, PalResultException {
        PalTypeObject objDesc = new PalTypeObject();
        objDesc.setKind(kind);
        objDesc.setType(type);
        this.move(srcSysFile, srcLib, srcObj, dstSysFile, dstLib, dstObj, objDesc);
    }

    public void move(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, String dstObj, IPalTypeObject objDesc)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (srcLib == null || dstLib == null) {
            throw new IllegalArgumentException("library must not be null");
        }
        if (srcObj == null || dstObj == null) {
            throw new IllegalArgumentException("objectName must not be null");
        }
        PalTrace.header("move");
        PalTypeFileId fileId = new PalTypeFileId();
        fileId.setNatKind(objDesc.getKind());
        fileId.setNatType(objDesc.getType());
        fileId.setObject(srcObj);
        fileId.setNewObject(dstObj);
        fileId.setSourceSize(objDesc.getSourceSize());
        fileId.setUser(objDesc.getSourceUser());
        fileId.setSourceDate(objDesc.getSourceDate());
        fileId.setGpSize(objDesc.getGpSize());
        fileId.setGpUser(objDesc.getGpUser());
        fileId.setGpDate(objDesc.getGpDate());
        dateiOperationServerLokal(41, srcSysFile, srcLib, dstSysFile, dstLib, fileId);
    }

    // ══════════════════════════════════════════════════════════════
    //  Delete
    // ══════════════════════════════════════════════════════════════

    public void delete(IPalTypeSystemFile sysFile, String library, IFileProperties props)
            throws IOException, PalResultException {
        ctx.requirePal();
        int ndvType = ctx.getPalProperties().getNdvType();
        if (props.getType() == 8 && props.getKind() != 1 && props.getKind() != 3 && ndvType == 1) {
            throw new IllegalArgumentException("kind  must be ObjectKind.SOURCE for Ndv Mainframe servers");
        }
        if (props.getKind() != 2 && props.getKind() != 1 && props.getKind() != 3
                && props.getKind() != 64 && props.getKind() != 16) {
            throw new IllegalArgumentException(
                    "kind  must be ObjectKind.SOURCE orObjectKind.GP or ObjectKind.SOURCE_OR_GP or ObjectKind.ERRORMSG ");
        }
        if (library == null) {
            throw new IllegalArgumentException("library must not be null");
        }
        PalTrace.header("delete");
        dateiOperationLoeschen(sysFile, library, props.getType(), props.getKind(),
                props.getName(), props.getBaseLibrary());
    }

    public void delete(IPalTypeSystemFile sysFile, int kind, String name)
            throws IOException, PalResultException {
        ctx.requirePal();
        int ndvType = ctx.getPalProperties().getNdvType();
        if (kind != 1 && ndvType == 1) {
            throw new IllegalArgumentException("kind  must be ObjectKind.SOURCE for Ndv Mainframe servers");
        }
        if (kind != 2 && kind != 1 && kind != 3) {
            throw new IllegalArgumentException(
                    "kind  must be ObjectKind.SOURCE orObjectKind.GP or ObjectKind.SOURCE_OR_GP");
        }
        PalTrace.header("delete");
        dateiOperationLoeschen(sysFile, "", 8, kind, name, null);
    }

    public void delete1(IPalTypeSystemFile sysFile, String library, int kind, int type, String name)
            throws IOException, PalResultException {
        this.delete2(sysFile, library, kind, type, name, null);
    }

    public void delete2(IPalTypeSystemFile sysFile, String library, int kind, int type,
                        String name, String longName)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (library == null) {
            throw new IllegalArgumentException("library must not be null");
        }
        if (!ObjectType.getInstanceIdExtension().containsKey(type)) {
            throw new IllegalArgumentException(
                    "type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
        }
        PalTrace.header("delete");
        dateiOperationLoeschen(sysFile, library, type, kind, name, longName);
    }

    // ══════════════════════════════════════════════════════════════
    //  Lock / Unlock / IsLocked
    // ══════════════════════════════════════════════════════════════

    public void isLocked(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        ctx.requirePal();
        pruefeSperrenParameter(sysFile, library, name, kind, type);
        if (type == 65536) return; // kein Locking bei RESOURCE
        PalTrace.header("isLocked");
        sperreVerwalten(44, sysFile, library, name, kind, type);
    }

    public void lock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        ctx.requirePal();
        pruefeSperrenParameter(sysFile, library, name, kind, type);
        if (type == 65536) return;
        PalTrace.header("lock");
        sperreVerwalten(29, sysFile, library, name, kind, type);
    }

    public void unlock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        ctx.requirePal();
        pruefeSperrenParameter(sysFile, library, name, kind, type);
        if (type == 65536) return;
        PalTrace.header("unlock");
        sperreVerwalten(30, sysFile, library, name, kind, type);
    }

    // ══════════════════════════════════════════════════════════════
    //  Private Hilfsmethoden
    // ══════════════════════════════════════════════════════════════

    /**
     * Parameterpruefung fuer Lock-Operationen (isLocked/lock/unlock).
     */
    private void pruefeSperrenParameter(IPalTypeSystemFile sysFile, String library,
                                        String name, int kind, int type) {
        if (kind < 0) {
            throw new IllegalArgumentException(
                    "kind must be one of the ids defined inside utility class 'sag.pal.ObjectKind'");
        }
        if (type != 131072 && type != 0 && !ObjectType.getInstanceIdExtension().containsKey(type)) {
            throw new IllegalArgumentException(
                    "type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
        }
        if (type == 65536) return;
        if (sysFile == null) {
            throw new IllegalArgumentException("systemFileKey must not be null");
        }
        if (library == null) {
            throw new IllegalArgumentException("library must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("filter must not be null");
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException("name parameter must not be empty");
        }
    }

    /**
     * Sperr-Operation ausfuehren (Original handleLocking, Zeile 3683-3699).
     * Operationscode: 44 = isLocked, 29 = lock, 30 = unlock.
     */
    private void sperreVerwalten(int operationsCode, IPalTypeSystemFile sysFile,
                                  String library, String name, int kind, int type)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();
        pal.add((IPalType) new PalTypeOperation(operationsCode, 0));

        String effektiveLib = library;
        if (type == 8 && ctx.getPalProperties().getNdvType() == 1) {
            effektiveLib = "";
        }

        PalTypeLibId libId = new PalTypeLibId(sysFile.getDatabaseId(), sysFile.getFileNumber(),
                effektiveLib, sysFile.getPassword(), sysFile.getCipher(), 6);
        pal.add((IPalType) libId);

        PalTypeObjDesc objDesc = new PalTypeObjDesc(type, kind, name);
        pal.add((IPalType) objDesc);
        pal.commit();

        PalResultException ex = ctx.getResultException();
        if (ex != null) throw ex;
    }

    /**
     * Datei-Operation: Loeschen (Original fileOperationDelete, Zeile 5097-5131).
     */
    private void dateiOperationLoeschen(IPalTypeSystemFile sysFile, String library,
                                         int type, int kind, String name, String baseLib)
            throws IOException, PalResultException {
        if (sysFile == null) {
            throw new IllegalArgumentException("systemFileKey must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("objectName must not be null");
        }

        String effektiveLib = (library.length() == 0 && ctx.isOpenSystemsServer()) ? "SYSTEM" : library;
        ctx.fileOperationInitiate(1, sysFile, effektiveLib, baseLib);

        PalTypeFileId fileId = new PalTypeFileId();
        fileId.setNatKind(kind);
        if (sysFile.getKind() == 6) {
            fileId.setNatType(8);
        } else {
            fileId.setNatType(type);
        }
        if (kind == 64) {
            fileId.setNatType(32768);
        } else if (kind == 16) {
            fileId.setNatType(65536);
        }
        fileId.setObject(name);

        IPalTypeNotify notify = ctx.fileOperationSendDescription(fileId);
        if (notify.getNotification() == 6) {
            ctx.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
        }
    }

    /**
     * Server-lokale Datei-Operation (Copy/Move)
     * (Original fileOperationServerLocal, Zeile 5133-5158).
     */
    private void dateiOperationServerLokal(int operationsCode,
                                            IPalTypeSystemFile srcSysFile, String srcLib,
                                            IPalTypeSystemFile dstSysFile, String dstLib,
                                            PalTypeFileId fileId)
            throws IOException, PalResultException {
        if (srcSysFile == null || dstSysFile == null) {
            throw new IllegalArgumentException("systemFileKey must not be null");
        }
        Pal pal = ctx.getPal();
        pal.add((IPalType) new PalTypeOperation(operationsCode));
        PalTypeLibId[] libs = new PalTypeLibId[]{
            new PalTypeLibId(srcSysFile.getDatabaseId(), srcSysFile.getFileNumber(),
                    srcLib, srcSysFile.getPassword(), srcSysFile.getCipher(), 6),
            new PalTypeLibId(dstSysFile.getDatabaseId(), dstSysFile.getFileNumber(),
                    dstLib, dstSysFile.getPassword(), dstSysFile.getCipher(), 6)
        };
        pal.add((IPalType[]) libs);
        pal.commit();

        PalResultException ex = ctx.getResultException();
        if (ex != null) throw ex;

        IPalTypeNotify notify = ctx.fileOperationSendDescription(fileId);
        if (notify.getNotification() == 6) {
            ctx.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
        }
    }
}
