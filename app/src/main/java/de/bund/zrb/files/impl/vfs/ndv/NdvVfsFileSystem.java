package de.bund.zrb.files.impl.vfs.ndv;

import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;
import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileSystem;

import java.util.Collection;

/**
 * VFS FileSystem implementation for NDV (Natural Development Server).
 * <p>
 * Represents a connection to an NDV server, scoped to a specific library.
 * Each file in this file system corresponds to a Natural source object.
 */
public class NdvVfsFileSystem extends AbstractFileSystem {

    private final NdvService ndvService;
    private final String library;
    private final NdvObjectInfo objectInfo;

    protected NdvVfsFileSystem(FileName rootName, FileSystemOptions fileSystemOptions,
                                NdvService ndvService, String library, NdvObjectInfo objectInfo) {
        super(rootName, null, fileSystemOptions);
        this.ndvService = ndvService;
        this.library = library;
        this.objectInfo = objectInfo;
    }

    @Override
    protected FileObject createFile(AbstractFileName name) throws FileSystemException {
        return new NdvVfsFileObject(name, this, ndvService, library, objectInfo);
    }

    @Override
    protected void addCapabilities(Collection<Capability> caps) {
        caps.addAll(NdvFileProvider.CAPABILITIES);
    }

    NdvService getNdvService() {
        return ndvService;
    }

    String getLibrary() {
        return library;
    }

    NdvObjectInfo getObjectInfo() {
        return objectInfo;
    }
}

