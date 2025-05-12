package org.example.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FtpService {

    private final FTPClient ftpClient = new FTPClient();
    private String currentPath = "/";
    private final List<FtpObserver> observers = new ArrayList<>();
    private boolean mvsMode = false;

    public boolean isMvsMode() {
        return mvsMode;
    }

    public boolean connect(String host, String user, String password) throws IOException {
        ftpClient.connect(host);
        int replyCode = ftpClient.getReplyCode();

        if (!ftpClient.login(user, password)) {
            throw new IOException("Login fehlgeschlagen");
        }

        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);

        String systemType = ftpClient.getSystemType();
        System.out.println("Systemtyp laut FTP-Server: " + systemType);
        mvsMode = systemType != null && systemType.toUpperCase().contains("MVS");

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
        if (mvsMode) {
            // Pfad sicher in einfache Anführungszeichen setzen, falls nötig
            String mvsPath = path;
            if (!mvsPath.startsWith("'")) {
                mvsPath = "'" + mvsPath;
            }
            if (!mvsPath.endsWith("'")) {
                mvsPath = mvsPath + "'";
            }

            // In das Dataset-Verzeichnis wechseln (oft erforderlich)
            boolean changed = ftpClient.changeWorkingDirectory(mvsPath);
            if (!changed) {
                throw new IOException("MVS-Verzeichnis nicht gefunden: " + mvsPath);
            }

            // Versuche Liste zu bekommen
            String[] names = ftpClient.listNames();
            if (names == null) return new FTPFile[0];

            // Mache FTPFile[] aus Namen
            FTPFile[] fakeFiles = new FTPFile[names.length];
            for (int i = 0; i < names.length; i++) {
                FTPFile file = new FTPFile();
                file.setName(names[i]);
                file.setType(FTPFile.FILE_TYPE); // oder DIRECTORY_TYPE, wenn du weißt, dass es ein PDS ist
                fakeFiles[i] = file;
            }

            return fakeFiles;
        } else {
            // Normales Verhalten
            return ftpClient.listFiles(path);
        }
    }

    public boolean changeDirectory(String path) throws IOException {
        if (!ftpClient.changeWorkingDirectory(path)) {
            return false;
        }
        this.currentPath = path;
        notifyObservers();
        return true;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void addObserver(FtpObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(FtpObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        for (FtpObserver observer : observers) {
            observer.onDirectoryChanged(currentPath);
        }
    }

}
