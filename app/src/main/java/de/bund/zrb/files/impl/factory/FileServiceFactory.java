package de.bund.zrb.files.impl.factory;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.impl.ftp.CommonsNetFtpFileService;
import de.bund.zrb.files.impl.local.VfsLocalFileService;
import de.bund.zrb.files.impl.ndv.NdvFileService;
import de.bund.zrb.ui.FtpResourceState;
import de.bund.zrb.ui.NdvResourceState;
import de.bund.zrb.ui.VirtualBackendType;
import de.bund.zrb.ui.VirtualResource;

import java.nio.file.Path;

public class FileServiceFactory {

    public FileService createLocal() {
        return new VfsLocalFileService();
    }

    public FileService createLocal(Path baseRoot) {
        return new VfsLocalFileService(baseRoot);
    }

    public FileService createFtp(String host, String user, String password) throws FileServiceException {
        return new CommonsNetFtpFileService(host, user, password);
    }

    public FileService createFtp(ConnectionId connectionId, CredentialsProvider credentialsProvider) throws FileServiceException {
        if (credentialsProvider == null) {
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.AUTH_FAILED,
                    "No CredentialsProvider configured");
        }

        return new CommonsNetFtpFileService(credentialsProvider, connectionId);
    }

    /**
     * Creates a FileService based on the VirtualResource backend type.
     * For FTP, uses connectionId from FtpResourceState; credentials come from provider.
     */
    public FileService create(VirtualResource resource, CredentialsProvider credentialsProvider) throws FileServiceException {
        if (resource == null) {
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.IO_ERROR, "VirtualResource is null");
        }

        if (resource.getBackendType() == VirtualBackendType.LOCAL) {
            return createLocal();
        }

        if (resource.getBackendType() == VirtualBackendType.NDV) {
            NdvResourceState ndvState = resource.getNdvState();
            if (ndvState == null) {
                throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.IO_ERROR,
                        "Missing NdvResourceState in VirtualResource");
            }
            return new NdvFileService(ndvState.getService(), ndvState.getLibrary(), ndvState.getObjectInfo());
        }

        // FTP
        FtpResourceState ftpState = resource.getFtpState();
        ConnectionId connectionId = ftpState != null ? ftpState.getConnectionId() : null;
        if (connectionId == null) {
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.AUTH_FAILED, "Missing ConnectionId in VirtualResource");
        }

        return createFtp(connectionId, credentialsProvider);
    }
}
