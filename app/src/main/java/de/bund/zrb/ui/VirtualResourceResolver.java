package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
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
            return new VirtualResource(ref, kind, localPath, true);
        }

        String ftpPath = rawPath == null ? "" : rawPath.trim();
        VirtualResourceKind kind = resolveKindFtp(ftpPath);
        return new VirtualResource(ref, kind, ftpPath, false);
    }

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

    private VirtualResourceKind resolveKindFtp(String ftpPath) throws FileServiceException {
        Settings settings = SettingsHelper.load();
        String host = settings.host;
        String user = settings.user;
        String password = LoginManager.getInstance().getPassword(host, user);

        try (FileService fs = new FileServiceFactory().createFtp(host, user, password)) {
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
