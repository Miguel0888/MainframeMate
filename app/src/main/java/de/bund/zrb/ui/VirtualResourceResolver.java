package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.impl.auth.InteractiveCredentialsProvider;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.path.VirtualResourceRef;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;

public class VirtualResourceResolver {

    public VirtualResource resolve(String rawPath) throws FileServiceException {
        VirtualResourceRef ref = VirtualResourceRef.of(rawPath);

        // Check for explicit FTP prefix first (e.g. "ftp:/", "ftp:/dir/file.txt")
        if (ref.isFtpPath()) {
            String ftpPath = ref.getFtpPath();
            Settings settings = SettingsHelper.load();
            ConnectionId connectionId = new ConnectionId("ftp", settings.host, settings.user);

            VirtualResourceKind kind = resolveKindFtp(ftpPath, connectionId);
            FtpResourceState ftpState = new FtpResourceState(connectionId, lastMvsMode, lastSystemType, settings.encoding);
            return new VirtualResource(ref, kind, ftpPath, VirtualBackendType.FTP, ftpState);
        }

        if (ref.isFileUri() || ref.isLocalAbsolutePath()) {
            String localPath = toLocalPath(ref);
            VirtualResourceKind kind = resolveKindLocal(localPath);
            return new VirtualResource(ref, kind, localPath, VirtualBackendType.LOCAL, null);
        }

        // Default: treat as FTP path (legacy behavior for non-prefixed paths like relative FTP paths)
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
        java.io.File f = new java.io.File(localPath);
        if (!f.exists()) {
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.NOT_FOUND,
                    "Path does not exist: " + localPath);
        }
        return f.isDirectory() ? VirtualResourceKind.DIRECTORY : VirtualResourceKind.FILE;
    }

    private VirtualResourceKind resolveKindFtp(String ftpPath, ConnectionId connectionId) throws FileServiceException {
        // Use interactive credentials provider that can show password dialog
        InteractiveCredentialsProvider provider = new InteractiveCredentialsProvider(
                (host, user) -> LoginManager.getInstance().getPassword(host, user)
        );
        FileService fs = null;
        try {
            fs = new FileServiceFactory().createFtp(connectionId, provider);

            // Mark session as active after successful FTP connection
            LoginManager.getInstance().onLoginSuccess(connectionId.getHost(), connectionId.getUsername());

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
        } catch (de.bund.zrb.files.auth.AuthCancelledException e) {
            // Benutzer hat abgebrochen - als AUTH_CANCELLED weiterleiten
            throw new FileServiceException(de.bund.zrb.files.api.FileServiceErrorCode.AUTH_CANCELLED,
                    "Authentifizierung abgebrochen");
        } catch (Exception e) {
            // On FTP auth failure, clear the password immediately
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("auth") || errorMsg.contains("login") || errorMsg.contains("credentials")) {
                LoginManager.getInstance().onLoginFailed(connectionId.getHost(), connectionId.getUsername());
            }

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
