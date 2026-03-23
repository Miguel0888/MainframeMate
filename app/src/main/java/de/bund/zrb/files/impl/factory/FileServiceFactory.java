package de.bund.zrb.files.impl.factory;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.impl.ftp.CommonsNetFtpFileService;
import de.bund.zrb.files.impl.vfs.VfsFileService;
import de.bund.zrb.ui.FtpResourceState;
import de.bund.zrb.ui.NdvResourceState;
import de.bund.zrb.ui.VirtualBackendType;
import de.bund.zrb.ui.VirtualResource;

import java.nio.file.Path;

/**
 * Factory for creating FileService instances.
 * <p>
 * All backends (local, FTP, NDV) are now backed by Apache Commons VFS2
 * through the unified {@link VfsFileService}.
 */
public class FileServiceFactory {

    public FileService createLocal() {
        return VfsFileService.forLocal();
    }

    public FileService createLocal(Path baseRoot) {
        return VfsFileService.forLocal(baseRoot);
    }

    public FileService createFtp(String host, String user, String password) throws FileServiceException {
        return VfsFileService.forFtp(host, user, password);
    }

    public FileService createFtp(ConnectionId connectionId, CredentialsProvider credentialsProvider) throws FileServiceException {
        if (credentialsProvider == null) {
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.AUTH_FAILED,
                    "No CredentialsProvider configured");
        }

        return VfsFileService.forFtp(connectionId, credentialsProvider);
    }

    /**
     * Creates a FileService based on the VirtualResource backend type.
     * All backends use Apache Commons VFS2 through {@link VfsFileService}.
     * For backends without a dedicated VFS provider (SHAREPOINT, WIKI, CONFLUENCE,
     * MAIL, BETAVIEW), returns a local VFS service as a placeholder — those backends
     * are typically accessed through their own connection tabs and prefetch services.
     */
    public FileService create(VirtualResource resource, CredentialsProvider credentialsProvider) throws FileServiceException {
        if (resource == null) {
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.IO_ERROR, "VirtualResource is null");
        }

        switch (resource.getBackendType()) {
            case LOCAL:
                return createLocal();

            case NDV: {
                NdvResourceState ndvState = resource.getNdvState();
                if (ndvState == null) {
                    throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.IO_ERROR,
                            "Missing NdvResourceState in VirtualResource");
                }
                return VfsFileService.forNdv(ndvState.getService(), ndvState.getLibrary(), ndvState.getObjectInfo());
            }

            case FTP: {
                FtpResourceState ftpState = resource.getFtpState();
                ConnectionId connectionId = ftpState != null ? ftpState.getConnectionId() : null;
                if (connectionId == null) {
                    throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.AUTH_FAILED,
                            "Missing ConnectionId in VirtualResource");
                }
                // MVS paths use quoted dataset names and member parentheses which
                // VFS2's URI-based path handling cannot represent.  Route MVS FTP
                // through CommonsNetFtpFileService which passes paths directly to
                // the FTP RETR/STOR commands.
                if (ftpState.getMvsMode() != null && ftpState.getMvsMode()) {
                    return new CommonsNetFtpFileService(credentialsProvider, connectionId);
                }
                return createFtp(connectionId, credentialsProvider);
            }

            case MAIL:
            case SHAREPOINT:
            case WIKI:
            case CONFLUENCE:
            case BETAVIEW:
            case WEB:
            case ARCHIVE:
                // These backends use their own connection tabs and prefetch services
                // for content retrieval. A local VFS fallback is returned so callers
                // that need a FileService object don't fail.
                return createLocal();

            default:
                return createLocal();
        }
    }
}
