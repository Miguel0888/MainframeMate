package org.example.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FtpManager {

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
            throw new RuntimeException(e);
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

//    public List<FTPFile> listDirectory(String path) throws IOException {
//        FTPFile[] files = ftpClient.listFiles(path);
//        return Arrays.asList(files);
//    }

    public List<String> listDirectory() {
        try {
            return Arrays.asList(ftpClient.listNames());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FtpFileBuffer open(String filename) throws IOException {
        FTPFile fileMeta = null;

        // Suche nach dem passenden FTPFile im aktuellen Verzeichnis
        for (FTPFile f : ftpClient.listFiles()) {
            if (f.getName().equalsIgnoreCase(filename)) {
                fileMeta = f;
                break;
            }
        }

        if (fileMeta == null) {
            throw new IOException("Datei nicht gefunden: " + filename);
        }

        // PDS Member oder normale Datei?
        String path;
        if (mvsMode && isProbablyMember(fileMeta)) {
            path = currentPath.replaceAll("/$", ""); // remove trailing slash
            if (!path.startsWith("'")) path = "'" + path;
            if (!path.endsWith("'")) path += "'";
            path = path.substring(0, path.length() - 1) + "(" + filename + ")'";
        } else {
            path = currentPath.endsWith("/") ? currentPath + filename : currentPath + "/" + filename;
        }

        ftpClient.setFileType(FTPClient.ASCII_FILE_TYPE);
        InputStream in = ftpClient.retrieveFileStream(path);
        if (in == null) {
            throw new IOException("Konnte Datei nicht laden: " + path +
                    "\nAntwort: " + ftpClient.getReplyString());
        }

        FtpFileBuffer buffer = new FtpFileBuffer(path, fileMeta);
        buffer.loadContent(in, null);
        ftpClient.completePendingCommand();

        return buffer;
    }

    private boolean isProbablyMember(FTPFile file) {
        return file.isFile() && !file.getName().contains("."); // z.B. TEST001
    }

    public boolean storeFile(FtpFileBuffer buffer, String newContent) throws IOException {
        // Remote-Version neu laden
        InputStream in = ftpClient.retrieveFileStream(buffer.getRemotePath());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int len;
        while ((len = in.read(tmp)) != -1) out.write(tmp, 0, len);
        ftpClient.completePendingCommand();
        String currentRemote = out.toString(StandardCharsets.UTF_8.name());

        // Konfliktprüfung
        if (!buffer.isUnchanged(currentRemote)) {
            // Stelle hier ggf. Dialog oder Logik für Konfliktbehandlung bereit
            System.err.println("Konflikt: Die Datei wurde auf dem Server geändert.");
            return false;
        }

        // Upload
        InputStream data = buffer.toInputStream(newContent);
        return ftpClient.storeFile(buffer.getRemotePath(), data);
    }
}
