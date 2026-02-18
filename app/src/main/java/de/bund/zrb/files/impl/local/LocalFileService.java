package de.bund.zrb.files.impl.local;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceErrorCode;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.api.FileWriteResult;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalFileService implements FileService {

    private final Path baseRoot;

    public LocalFileService() {
        this.baseRoot = null;
    }

    public LocalFileService(Path baseRoot) {
        this.baseRoot = baseRoot;
    }

    @Override
    public List<FileNode> list(String absolutePath) throws FileServiceException {
        Path resolved = resolvePath(absolutePath);
        if (!Files.exists(resolved)) {
            return Collections.emptyList();
        }

        if (!Files.isDirectory(resolved)) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Path is not a directory: " + resolved);
        }

        List<FileNode> nodes = new ArrayList<FileNode>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolved)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                boolean directory = Files.isDirectory(child);
                long size = directory ? 0L : Files.size(child);
                long lastModified = Files.getLastModifiedTime(child).toMillis();
                nodes.add(new FileNode(name, child.toAbsolutePath().toString(), directory, size, lastModified));
            }
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local list failed", e);
        }

        return nodes;
    }

    @Override
    public FilePayload readFile(String absolutePath) throws FileServiceException {
        Path resolved = resolvePath(absolutePath);
        try {
            byte[] bytes = Files.readAllBytes(resolved);
            return FilePayload.fromBytes(bytes, Charset.defaultCharset(), false);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local read failed", e);
        }
    }

    @Override
    public void writeFile(String absolutePath, FilePayload payload) throws FileServiceException {
        if (payload == null) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Payload is required");
        }
        Path resolved = resolvePath(absolutePath);
        try {
            Files.write(resolved, payload.getBytes());
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local write failed", e);
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
        Path resolved = resolvePath(absolutePath);
        try {
            return Files.deleteIfExists(resolved);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local delete failed", e);
        }
    }

    @Override
    public boolean createDirectory(String absolutePath) throws FileServiceException {
        Path resolved = resolvePath(absolutePath);
        try {
            Files.createDirectories(resolved);
            return true;
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Local create directory failed", e);
        }
    }

    @Override
    public void close() throws FileServiceException {
        // no-op
    }

    private Path resolvePath(String path) {
        Path resolved = path == null || path.trim().isEmpty()
                ? Paths.get("")
                : Paths.get(path);
        if (baseRoot != null) {
            resolved = baseRoot.resolve(resolved).normalize();
        }
        return resolved.toAbsolutePath().normalize();
    }
}

