package de.bund.zrb.files.api;

import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;

import java.util.List;

public interface FileService extends AutoCloseable {

    List<FileNode> list(String absolutePath) throws FileServiceException;

    FilePayload readFile(String absolutePath) throws FileServiceException;

    void writeFile(String absolutePath, FilePayload payload) throws FileServiceException;

    FileWriteResult writeIfUnchanged(String absolutePath, FilePayload payload, String expectedHash)
            throws FileServiceException;

    boolean delete(String absolutePath) throws FileServiceException;

    boolean createDirectory(String absolutePath) throws FileServiceException;

    @Override
    void close() throws FileServiceException;
}

