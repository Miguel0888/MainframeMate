package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Quellcode- und Binaer-Transfer sowie Kompilierung:
 * downloadSource, receiveFiles, uploadSource, sendFiles, abortFileOperation,
 * downloadBinary, uploadBinary, read, catalog, check, stow, save.
 */
public class UploadService {

    private final PalSessionContext ctx;

    public UploadService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    public Object[] receiveFiles(IPalTypeSystemFile sysFile, String library,
                                 IFileProperties props, Set<EDownLoadOption> options)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("SourceTransferService.receiveFiles not yet implemented");
    }

    public void uploadSource(IPalTypeSystemFile sysFile, String library,
                             IFileProperties props, Set<EUploadOption> options, String[] sourceLines)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("SourceTransferService.uploadSource not yet implemented");
    }

    public void sendFiles(IPalTypeSystemFile sysFile, String library,
                          ObjectProperties objProps, Set<EUploadOption> options, Object[] data)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("SourceTransferService.sendFiles not yet implemented");
    }

    public void uploadBinary(IPalTypeSystemFile sysFile, String library,
                             IFileProperties props, ByteArrayOutputStream data)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("SourceTransferService.uploadBinary not yet implemented");
    }

    public String[] read(IPalTypeSystemFile sysFile, String library,
                         String name, Set<EReadOption> options)
            throws IOException, PalResultException {
        throw new UnsupportedOperationException("SourceTransferService.read not yet implemented");
    }

    public void catalog(IPalTypeSystemFile sysFile, String library,
                        IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        throw new UnsupportedOperationException("SourceTransferService.catalog not yet implemented");
    }

    public void check(IPalTypeSystemFile sysFile, String library,
                      IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        throw new UnsupportedOperationException("SourceTransferService.check not yet implemented");
    }

    public void stow(IPalTypeSystemFile sysFile, String library,
                     IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        throw new UnsupportedOperationException("SourceTransferService.stow not yet implemented");
    }

    public void save(IPalTypeSystemFile sysFile, String library,
                     IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        throw new UnsupportedOperationException("SourceTransferService.save not yet implemented");
    }
}

