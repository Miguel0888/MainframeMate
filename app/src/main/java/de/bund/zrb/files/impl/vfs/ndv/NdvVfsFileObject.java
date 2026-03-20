package de.bund.zrb.files.impl.vfs.ndv;

import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.service.NdvSourceCacheService;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VFS FileObject implementation for a single NDV source object.
 * <p>
 * Maps NDV read/write operations to the VFS file abstraction.
 * Each instance represents one Natural source (program, subprogram, map, etc.).
 */
public class NdvVfsFileObject extends AbstractFileObject<NdvVfsFileSystem> {

    private static final Logger LOG = Logger.getLogger(NdvVfsFileObject.class.getName());

    private final NdvService ndvService;
    private final String library;
    private final NdvObjectInfo objectInfo;

    // Cached content for read operations
    private byte[] cachedContent;

    protected NdvVfsFileObject(AbstractFileName name, NdvVfsFileSystem fileSystem,
                                NdvService ndvService, String library, NdvObjectInfo objectInfo) {
        super(name, fileSystem);
        this.ndvService = ndvService;
        this.library = library;
        this.objectInfo = objectInfo;
    }

    @Override
    protected FileType doGetType() throws Exception {
        // NDV objects are always files (source code), not directories
        return FileType.FILE;
    }

    @Override
    protected String[] doListChildren() throws Exception {
        // NDV objects have no children (they are leaf files)
        return new String[0];
    }

    @Override
    protected long doGetContentSize() throws Exception {
        ensureContent();
        return cachedContent != null ? cachedContent.length : 0;
    }

    @Override
    protected InputStream doGetInputStream() throws Exception {
        ensureContent();
        if (cachedContent == null) {
            throw new FileSystemException("NDV source not found: " + objectInfo.getName());
        }
        return new ByteArrayInputStream(cachedContent);
    }

    @Override
    protected OutputStream doGetOutputStream(boolean bAppend) throws Exception {
        return new NdvOutputStream(bAppend);
    }

    @Override
    protected long doGetLastModifiedTime() throws Exception {
        return 0L; // NDV doesn't expose modification time in a simple way
    }

    @Override
    protected boolean doIsReadable() throws Exception {
        return true;
    }

    @Override
    protected boolean doIsWriteable() throws Exception {
        return true;
    }

    /**
     * Read the NDV source and cache it.
     */
    private void ensureContent() {
        if (cachedContent != null) return;
        try {
            String source = ndvService.readSource(library, objectInfo);
            cachedContent = source.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to read NDV source: " + objectInfo.getName(), e);
            cachedContent = null;
        }
    }

    /**
     * OutputStream that writes back to NDV when closed.
     */
    private class NdvOutputStream extends ByteArrayOutputStream {
        private final boolean append;

        NdvOutputStream(boolean append) {
            this.append = append;
        }

        @Override
        public void close() throws java.io.IOException {
            super.close();
            byte[] data = toByteArray();

            try {
                String text = new String(data, StandardCharsets.UTF_8);
                ndvService.writeSource(library, objectInfo, text);

                // Update cache + Lucene index after successful save
                try {
                    NdvSourceCacheService.getInstance().onSourceSaved(
                            library, objectInfo.getName(), objectInfo.getTypeExtension(), text, null);
                } catch (Exception cacheEx) {
                    LOG.log(Level.FINE, "NDV cache update after save failed", cacheEx);
                }

                // Invalidate cached content
                cachedContent = data;
            } catch (Exception e) {
                throw new java.io.IOException("NDV write failed for " + objectInfo.getName() + ": " + e.getMessage(), e);
            }
        }
    }
}

