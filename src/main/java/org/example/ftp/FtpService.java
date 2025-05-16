package org.example.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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

    private String quoteMvsPath(String dataset) {
        if (!dataset.startsWith("'")) dataset = "'" + dataset;
        if (!dataset.endsWith("'")) dataset = dataset + "'";
        return dataset;
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
        try {
            return ftpClient.printWorkingDirectory();
        } catch (IOException e) {
            throw new RuntimeException(e); //ToDo
        }
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

    public FTPClient getClient() {
        return ftpClient;
    }

    public List<FTPFile> listDirectory(String newPath) {
        // ToDo
        return null;
    }

    public String getFile(FTPFile file) {
        try {
            ftpClient.setFileType(FTPClient.ASCII_FILE_TYPE);
        } catch (IOException e) {
            throw new RuntimeException(e); // ToDo
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.out.println("--> RETRIEVING " + out + " IN " + fullPath);
        boolean ok = false;
        try {
            ok = ftpClient.retrieveFile(file, out);
        } catch (IOException e) {
            throw new RuntimeException(e); // ToDo
        }
        if (!ok) {
            int code = ftpClient.getReplyCode();
            String reply = ftpClient.getReplyString();
        }
        try {
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // ToDo
        }
    }

//    public String getFile(String fullPath) {
//        try {
//            ftpClient.setFileType(FTPClient.ASCII_FILE_TYPE);
//        } catch (IOException e) {
//            throw new RuntimeException(e); // ToDo
//        }
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        System.out.println("--> RETRIEVING " + out + " IN " + fullPath);
//        boolean ok = false;
//        try {
//            ok = ftpClient.retrieveFile(fullPath, out);
//        } catch (IOException e) {
//            throw new RuntimeException(e); // ToDo
//        }
//        if (!ok) {
//            int code = ftpClient.getReplyCode();
//            String reply = ftpClient.getReplyString();
//        }
//        try {
//            return out.toString(StandardCharsets.UTF_8.name());
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e); // ToDo
//        }
//    }
}
