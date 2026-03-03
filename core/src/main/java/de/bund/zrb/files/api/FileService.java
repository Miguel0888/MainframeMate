package de.bund.zrb.files.api;

import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;

import java.util.List;

public interface FileService extends AutoCloseable {

    List<FileNode> list(String absolutePath) throws FileServiceException;

    FilePayload readFile(String absolutePath) throws FileServiceException;

     /**
     * Read a file in binary mode (no ASCII/EBCDIC conversion).
     * Required for binary formats (PDF, DOCX, XLSX, etc.) on FTP servers.
     * Default implementation falls back to readFile().
     */
    default FilePayload readFileBinary(String absolutePath) throws FileServiceException {
        return readFile(absolutePath);
    }

    void writeFile(String absolutePath, FilePayload payload) throws FileServiceException;

    /**
     * Write a file in binary mode (no ASCII/EBCDIC conversion).
     * Required for uploading binary formats (PDF, DOCX, XLSX, etc.) to FTP servers.
     * Default implementation falls back to writeFile().
     */
    default void writeFileBinary(String absolutePath, FilePayload payload) throws FileServiceException {
        writeFile(absolutePath, payload);
    }

    FileWriteResult writeIfUnchanged(String absolutePath, FilePayload payload, String expectedHash)
            throws FileServiceException;

    boolean delete(String absolutePath) throws FileServiceException;

    boolean createDirectory(String absolutePath) throws FileServiceException;

    @Override
    void close() throws FileServiceException;
}

