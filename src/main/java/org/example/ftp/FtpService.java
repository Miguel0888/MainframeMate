package org.example.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.IOException;

public class FtpService {

    private final FTPClient ftpClient = new FTPClient();

    public boolean connect(String host, String user, String password) throws IOException {
        ftpClient.connect(host);
        int replyCode = ftpClient.getReplyCode();

        if (!ftpClient.login(user, password)) {
            throw new IOException("Login fehlgeschlagen");
        }

        ftpClient.enterLocalPassiveMode(); // wichtig hinter Firewalls
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
        return true;
    }

    public boolean isConnected() {
        return ftpClient.isConnected();
    }

    public void disconnect() {
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Später: listFiles, downloadFile, uploadFile etc.
    public FTPFile[] listDirectory(String path) throws IOException {
        return ftpClient.listFiles(path);
    }
}
