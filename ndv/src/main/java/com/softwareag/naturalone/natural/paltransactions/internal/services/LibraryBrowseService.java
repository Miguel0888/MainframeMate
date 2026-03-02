package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.IOException;
import java.util.Set;

/**
 * Bibliotheks-Browsing:
 * getSystemFiles, getLibrariesFirst/Next, getNumberOfLibraries, isLibraryEmpty,
 * getLibraryStatistics, getLibraryInfo, getLibraryOfObject.
 */
public class LibraryBrowseService {

    private final PalSessionContext ctx;

    public LibraryBrowseService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    public IPalTypeSystemFile[] getSystemFiles() throws IOException, PalResultException {
        throw new UnsupportedOperationException("LibraryBrowseService.getSystemFiles not yet implemented");
    }

    public IPalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("LibraryBrowseService.getLibrariesFirst not yet implemented");
    }

    public IPalTypeLibrary[] getLibrariesNext() throws IOException, PalResultException {
        throw new UnsupportedOperationException("LibraryBrowseService.getLibrariesNext not yet implemented");
    }

    public int getNumberOfLibraries(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("LibraryBrowseService.getNumberOfLibraries not yet implemented");
    }

    public boolean isLibraryEmpty(IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("LibraryBrowseService.isLibraryEmpty not yet implemented");
    }

    public IPalTypeLibraryStatistics getLibraryStatistics(Set<ELibraryStatisticsOption> options,
                                                           IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("LibraryBrowseService.getLibraryStatistics not yet implemented");
    }

    public ILibraryInfo getLibraryInfo(int option, IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("LibraryBrowseService.getLibraryInfo not yet implemented");
    }

    public IPalTypeLibId getLibraryOfObject(IPalTypeLibId libId, String objectName, Set<EObjectKind> kinds)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("LibraryBrowseService.getLibraryOfObject not yet implemented");
    }
}

