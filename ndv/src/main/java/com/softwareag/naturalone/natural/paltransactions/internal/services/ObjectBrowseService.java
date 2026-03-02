package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.IOException;

/**
 * Objekt-Browsing und -Suche:
 * getObjectsFirst/Next, getNumberOfObjects, exists, getObjectByLongName, getObjectByName.
 */
public class ObjectBrowseService {

    private final PalSessionContext ctx;

    public ObjectBrowseService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library,
                                            String filter, int kind, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.getObjectsFirst not yet implemented");
    }

    public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library, int kind)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.getObjectsFirst not yet implemented");
    }

    public IPalTypeObject[] getObjectsNext() throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.getObjectsNext not yet implemented");
    }

    public int getNumberOfObjects(IPalTypeSystemFile sysFile, String library,
                                  String filter, int kind, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.getNumberOfObjects not yet implemented");
    }

    public int getNumberOfObjects(IPalTypeSystemFile sysFile, String library, int kind)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.getNumberOfObjects not yet implemented");
    }

    public IPalTypeObject exists(IPalTypeSystemFile sysFile, String library, String name, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.exists not yet implemented");
    }

    public IPalTypeObject exists(IPalTypeSystemFile sysFile, String name, int type)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.exists not yet implemented");
    }

    public ISourceLookupResult getObjectByLongName(IPalTypeSystemFile sysFile, String library,
                                                    String longName, int type, boolean withSource)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.getObjectByLongName not yet implemented");
    }

    public ISourceLookupResult getObjectByName(IPalTypeSystemFile sysFile, String library,
                                                String name, int type, boolean withSource)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("ObjectBrowseService.getObjectByName not yet implemented");
    }
}

