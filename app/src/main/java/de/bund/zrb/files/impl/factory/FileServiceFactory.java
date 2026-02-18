package de.bund.zrb.files.impl.factory;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.impl.ftp.CommonsNetFtpFileService;
import de.bund.zrb.files.impl.local.LocalFileService;

import java.nio.file.Path;

public class FileServiceFactory {

    public FileService createLocal() {
        return new LocalFileService();
    }

    public FileService createLocal(Path baseRoot) {
        return new LocalFileService(baseRoot);
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
}
