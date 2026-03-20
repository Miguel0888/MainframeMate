package de.bund.zrb.files.impl.vfs.ndv;

import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;
import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.provider.AbstractOriginatingFileProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom Apache Commons VFS2 provider for NDV (Natural Development Server).
 * <p>
 * Registers the {@code ndv://} scheme with the VFS FileSystemManager, allowing
 * NDV source objects to be accessed through the same VFS API as local files and FTP.
 * <p>
 * URI format: {@code ndv://library/objectType/objectName}
 */
public class NdvFileProvider extends AbstractOriginatingFileProvider {

    private static final Logger LOG = Logger.getLogger(NdvFileProvider.class.getName());

    /** The NDV scheme name */
    public static final String SCHEME = "ndv";

    /** Capabilities of the NDV file system */
    static final Collection<Capability> CAPABILITIES = Collections.unmodifiableList(
            Arrays.asList(
                    Capability.READ_CONTENT,
                    Capability.WRITE_CONTENT,
                    Capability.GET_TYPE,
                    Capability.URI
            )
    );

    // Global NDV context (set before creating file systems)
    private static volatile NdvService globalNdvService;
    private static volatile String globalLibrary;
    private static volatile NdvObjectInfo globalObjectInfo;
    private static volatile boolean registered = false;

    /**
     * Ensure the NDV provider is registered with the default VFS FileSystemManager.
     * Must be called before resolving ndv:// URIs.
     */
    public static synchronized void ensureRegistered(NdvService service, String library, NdvObjectInfo objectInfo)
            throws FileSystemException {
        globalNdvService = service;
        globalLibrary = library;
        globalObjectInfo = objectInfo;

        if (!registered) {
            try {
                DefaultFileSystemManager mgr = (DefaultFileSystemManager) VFS.getManager();
                if (!mgr.hasProvider(SCHEME)) {
                    mgr.addProvider(SCHEME, new NdvFileProvider());
                    LOG.info("[NDV-VFS] Registered NDV provider for scheme: " + SCHEME);
                }
                registered = true;
            } catch (ClassCastException e) {
                LOG.log(Level.WARNING, "Cannot register NDV VFS provider: manager is not DefaultFileSystemManager", e);
                throw new FileSystemException("Cannot register NDV VFS provider", e);
            }
        }
    }

    static NdvService getNdvService() {
        return globalNdvService;
    }

    static String getLibrary() {
        return globalLibrary;
    }

    static NdvObjectInfo getObjectInfo() {
        return globalObjectInfo;
    }

    @Override
    protected FileSystem doCreateFileSystem(FileName rootName, FileSystemOptions fileSystemOptions)
            throws FileSystemException {
        return new NdvVfsFileSystem(rootName, fileSystemOptions, globalNdvService, globalLibrary, globalObjectInfo);
    }

    @Override
    public Collection<Capability> getCapabilities() {
        return CAPABILITIES;
    }
}

