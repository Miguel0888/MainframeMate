package de.bund.zrb.files.impl.ndv;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceErrorCode;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.api.FileWriteResult;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.ndv.NdvClient;
import de.bund.zrb.ndv.NdvObjectInfo;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * FileService implementation for NDV (Natural Development Server).
 * Allows reading and writing Natural source code via the NATSPOD protocol,
 * using the same FileService interface as FTP and local files.
 * <p>
 * This makes NDV sources editable and saveable in the same FileTabImpl
 * that is used for FTP and local files.
 */
public class NdvFileService implements FileService {

    private final NdvClient client;
    private final String library;
    private final NdvObjectInfo objectInfo;

    public NdvFileService(NdvClient client, String library, NdvObjectInfo objectInfo) {
        if (client == null) throw new IllegalArgumentException("NdvClient must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (objectInfo == null) throw new IllegalArgumentException("objectInfo must not be null");
        this.client = client;
        this.library = library;
        this.objectInfo = objectInfo;
    }

    @Override
    public List<FileNode> list(String absolutePath) throws FileServiceException {
        // NDV listing is handled separately in NdvConnectionTab
        return Collections.emptyList();
    }

    @Override
    public FilePayload readFile(String absolutePath) throws FileServiceException {
        try {
            String source = client.readSource(library, objectInfo);
            byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
            return FilePayload.fromBytes(bytes, StandardCharsets.UTF_8, false);
        } catch (Exception e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "NDV readSource failed for " + objectInfo.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void writeFile(String absolutePath, FilePayload payload) throws FileServiceException {
        try {
            Charset cs = payload.getCharset() != null ? payload.getCharset() : StandardCharsets.UTF_8;
            String text = new String(payload.getBytes(), cs);
            client.writeSource(library, objectInfo, text);
        } catch (Exception e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "NDV writeSource failed for " + objectInfo.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public FileWriteResult writeIfUnchanged(String absolutePath, FilePayload payload, String expectedHash)
            throws FileServiceException {
        // NDV does not support hash-based conflict detection natively.
        // We do a simple read-compare-write to detect conflicts.
        if (expectedHash != null && !expectedHash.isEmpty()) {
            try {
                FilePayload current = readFile(absolutePath);
                if (!expectedHash.equals(current.getHash())) {
                    return FileWriteResult.conflict(current);
                }
            } catch (FileServiceException e) {
                // If read fails (e.g. new object), proceed with write
            }
        }

        writeFile(absolutePath, payload);
        return FileWriteResult.success();
    }

    @Override
    public boolean delete(String absolutePath) throws FileServiceException {
        throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                "Delete via NDV is not supported yet");
    }

    @Override
    public boolean createDirectory(String absolutePath) throws FileServiceException {
        throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                "createDirectory via NDV is not supported");
    }

    @Override
    public void close() throws FileServiceException {
        // Do NOT close the NdvClient here – it's shared with the NdvConnectionTab
        // and other tabs that may still use it.
    }
}

