package de.bund.zrb.files.impl.local;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceErrorCode;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.api.FileWriteResult;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local FileService implementation backed by Apache Commons VFS.
 */
public class VfsLocalFileService implements FileService {

    private final FileSystemManager manager;
    private final Path baseRoot;
    private final FileServiceException initError;

    public VfsLocalFileService() {
        this(null);
    }

    public VfsLocalFileService(Path baseRoot) {
        FileSystemManager resolvedManager = null;
        FileServiceException resolvedError = null;
        try {
            resolvedManager = VFS.getManager();
        } catch (FileSystemException e) {
            resolvedError = new FileServiceException(FileServiceErrorCode.IO_ERROR, "VFS manager init failed", e);
        }
        this.manager = resolvedManager;
        this.initError = resolvedError;
        this.baseRoot = baseRoot;
    }

    @Override
    public List<FileNode> list(String absolutePath) throws FileServiceException {
        FileObject root = resolve(absolutePath);
        try {
            if (!root.exists()) {
                return Collections.emptyList();
            }
            if (root.getType() != FileType.FOLDER) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Path is not a directory: " + root.getName());
            }

            FileObject[] children = root.getChildren();
            List<FileNode> nodes = new ArrayList<FileNode>(children.length);
            for (FileObject child : children) {
                try {
                    FileType type = child.getType();
                    boolean isDir = type == FileType.FOLDER;
                    long size = isDir ? 0L : child.getContent().getSize();
                    long lastModified = child.getContent().getLastModifiedTime();
                    String name = child.getName().getBaseName();
                    String path = new File(child.getName().getPath()).getAbsolutePath();
                    nodes.add(new FileNode(name, path, isDir, size, lastModified));
                } finally {
                    tryClose(child);
                }
            }
            return nodes;
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local list failed", e);
        } finally {
            tryClose(root);
        }
    }

    @Override
    public FilePayload readFile(String absolutePath) throws FileServiceException {
        FileObject file = resolve(absolutePath);
        try {
            if (!file.exists()) {
                throw new FileServiceException(FileServiceErrorCode.NOT_FOUND, "Local file not found: " + file.getName());
            }
            if (file.getType() == FileType.FOLDER) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Path is a directory: " + file.getName());
            }

            byte[] bytes = readAllBytes(file);
            return FilePayload.fromBytes(bytes, Charset.defaultCharset(), false);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local read failed", e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public void writeFile(String absolutePath, FilePayload payload) throws FileServiceException {
        if (payload == null) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Payload is required");
        }

        FileObject file = resolve(absolutePath);
        try (OutputStream out = file.getContent().getOutputStream()) {
            out.write(payload.getBytes());
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local write failed", e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public FileWriteResult writeIfUnchanged(String absolutePath, FilePayload payload, String expectedHash)
            throws FileServiceException {
        if (expectedHash == null || expectedHash.isEmpty()) {
            writeFile(absolutePath, payload);
            return FileWriteResult.success();
        }

        FilePayload current = readFile(absolutePath);
        if (!expectedHash.equals(current.getHash())) {
            return FileWriteResult.conflict(current);
        }

        writeFile(absolutePath, payload);
        return FileWriteResult.success();
    }

    @Override
    public boolean delete(String absolutePath) throws FileServiceException {
        FileObject file = resolve(absolutePath);
        try {
            return file.delete();
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local delete failed", e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public boolean createDirectory(String absolutePath) throws FileServiceException {
        FileObject file = resolve(absolutePath);
        try {
            file.createFolder();
            return true;
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local create directory failed", e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public void close() throws FileServiceException {
        // no-op (VFS manager is shared)
    }

    private FileObject resolve(String path) throws FileServiceException {
        if (initError != null) {
            throw initError;
        }
        try {
            if (path != null && path.trim().toLowerCase().startsWith("file:")) {
                return manager.resolveFile(path.trim());
            }
            File resolved = resolveLocalPath(path);
            return manager.resolveFile(resolved.toURI().toString());
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local resolve failed", e);
        }
    }

    private File resolveLocalPath(String path) {
        File resolved = path == null || path.trim().isEmpty()
                ? new File("")
                : new File(path.trim());
        if (baseRoot != null) {
            resolved = baseRoot.resolve(resolved.toPath()).normalize().toFile();
        }
        return resolved.getAbsoluteFile();
    }

    private byte[] readAllBytes(FileObject file) throws FileServiceException {
        try (InputStream in = file.getContent().getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local read failed", e);
        }
    }

    private void tryClose(FileObject file) {
        if (file == null) {
            return;
        }
        try {
            file.close();
        } catch (FileSystemException ignore) {
            // ignore close failures
        }
    }
}
