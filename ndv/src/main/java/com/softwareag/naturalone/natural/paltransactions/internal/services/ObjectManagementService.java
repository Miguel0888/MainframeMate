package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.IOException;

/**
 * CRUD-Operationen auf Objekten:
 * copy, move, delete, isLocked, lock, unlock.
 */
public class ObjectManagementService {

    private final PalSessionContext ctx;

    public ObjectManagementService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    public void copy(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, int kind, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.copy not yet implemented");
    }

    public void copy(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, IPalTypeObject objDesc)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.copy not yet implemented");
    }

    public void move(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, String dstObj, int kind, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.move not yet implemented");
    }

    public void move(IPalTypeSystemFile srcSysFile, String srcLib, String srcObj,
                     IPalTypeSystemFile dstSysFile, String dstLib, String dstObj, IPalTypeObject objDesc)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.move not yet implemented");
    }

    public void delete(IPalTypeSystemFile sysFile, String library, IFileProperties props)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.delete not yet implemented");
    }

    public void delete(IPalTypeSystemFile sysFile, int kind, String name)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.delete not yet implemented");
    }

    public void delete1(IPalTypeSystemFile sysFile, String library, int kind, int type, String name)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.delete1 not yet implemented");
    }

    public void delete2(IPalTypeSystemFile sysFile, String library, int kind, int type,
                        String name, String longName)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.delete2 not yet implemented");
    }

    public void isLocked(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.isLocked not yet implemented");
    }

    public void lock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.lock not yet implemented");
    }

    public void unlock(IPalTypeSystemFile sysFile, String library, String name, int kind, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectManagementService.unlock not yet implemented");
    }
}

