package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.impl.auth.LoginManagerCredentialsProvider;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.impl.ftp.CommonsNetFtpFileService;
import de.bund.zrb.files.path.VirtualResourceRef;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;

public class VirtualResourceResolver {

    public VirtualResource resolve(String rawPath) throws FileServiceException {
        VirtualResourceRef ref = VirtualResourceRef.of(rawPath);
        if (ref.isFileUri() || ref.isLocalAbsolutePath()) {
            String localPath = toLocalPath(ref);
            VirtualResourceKind kind = resolveKindLocal(localPath);
            return new VirtualResource(ref, kind, localPath, VirtualBackendType.LOCAL, null);
        }

        String ftpPath = rawPath == null ? "" : rawPath.trim();
        Settings settings = SettingsHelper.load();
        ConnectionId connectionId = new ConnectionId("ftp", settings.host, settings.user);

        VirtualResourceKind kind = resolveKindFtp(ftpPath, connectionId);
        FtpResourceState ftpState = new FtpResourceState(connectionId, lastMvsMode, lastSystemType, settings.encoding);
        return new VirtualResource(ref, kind, ftpPath, VirtualBackendType.FTP, ftpState);
    }

    // Store last resolved system info (best effort for DTO)
    private Boolean lastMvsMode;
    private String lastSystemType;

    private VirtualResourceKind resolveKindLocal(String localPath) throws FileServiceException {
        try (FileService fs = new FileServiceFactory().createLocal()) {
            try {
                fs.list(localPath);
                return VirtualResourceKind.DIRECTORY;
            } catch (Exception ignored) {
                // Not a directory -> fall through
            }
            fs.readFile(localPath);
            return VirtualResourceKind.FILE;
        } catch (Exception e) {
            if (e instanceof FileServiceException) {
                throw (FileServiceException) e;
            }
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.IO_ERROR, "Local resolve failed", e);
        }
    }

    private VirtualResourceKind resolveKindFtp(String ftpPath, ConnectionId connectionId) throws FileServiceException {
        LoginManagerCredentialsProvider provider = new LoginManagerCredentialsProvider(
                (host, user) -> LoginManager.getInstance().getCachedPassword(host, user)
        );
        FileService fs = null;
        try {
            fs = new FileServiceFactory().createFtp(connectionId, provider);
            if (fs instanceof de.bund.zrb.files.impl.ftp.CommonsNetFtpFileService) {
                de.bund.zrb.files.impl.ftp.CommonsNetFtpFileService svc = (de.bund.zrb.files.impl.ftp.CommonsNetFtpFileService) fs;
                lastMvsMode = svc.isMvsMode();
                lastSystemType = svc.getSystemType();
            } else {
                lastMvsMode = null;
                lastSystemType = null;
            }

            try {
                fs.list(ftpPath);
                return VirtualResourceKind.DIRECTORY;
            } catch (Exception ignored) {
                // Not a directory -> fall through
            }
            fs.readFile(ftpPath);
            return VirtualResourceKind.FILE;
        } catch (Exception e) {
            if (e instanceof FileServiceException) {
                throw (FileServiceException) e;
            }
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.IO_ERROR, "FTP resolve failed", e);
        } finally {
            if (fs != null) {
                try {
                    fs.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }

    private String toLocalPath(VirtualResourceRef ref) {
        if (ref.isFileUri()) {
            try {
                return new java.io.File(java.net.URI.create(ref.raw().trim())).getAbsolutePath();
            } catch (Exception ignore) {
                return ref.raw();
            }
        }
        return ref.normalizeLocalAbsolutePath();
    }
}
