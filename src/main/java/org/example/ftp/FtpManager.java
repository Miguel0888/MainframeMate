package org.example.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPCmd;
import org.apache.commons.net.ftp.FTPFile;
import org.example.util.SettingsManager;

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
        ftpClient.setControlEncoding(SettingsManager.load().encoding);
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

    public List<String> listDirectory() {
        try {
            return Arrays.asList(ftpClient.listNames());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FtpFileBuffer open(String filename) throws IOException {
        // Suche Meta aus aktuellem Directory
        FTPFile fileMeta = Arrays.stream(ftpClient.listFiles())
                .filter(f -> f.getName().equalsIgnoreCase(filename))
                .findFirst()
                .orElseThrow(() -> new IOException("Datei nicht gefunden: " + filename));

        // Dateityp: wir lesen als Text
        ftpClient.setFileType(FTPClient.ASCII_FILE_TYPE);

        // Retrieve nur mit einfachem Dateinamen – NICHT vollqualifiziert
        InputStream in = ftpClient.retrieveFileStream(filename);
        if (in == null) {
            throw new IOException("Konnte Datei nicht laden: " + filename +
                    "\nAntwort: " + ftpClient.getReplyString());
        }

        // Datei lesen – NICHT vergessen, sonst hängt completePendingCommand
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        in.close();
        if (!ftpClient.completePendingCommand()) {
            throw new IOException("FTP-Übertragung unvollständig: " + filename);
        }

        // Jetzt den Buffer bauen
        FtpFileBuffer result = new FtpFileBuffer(filename, fileMeta);
        result.loadContent(new ByteArrayInputStream(out.toByteArray()), null);
        return result;
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

    public boolean hasFeature(FTPCmd cmd)
    {
        try {
            return ftpClient.hasFeature(cmd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getHelp()
    {
        try {
            return ftpClient.listHelp();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getHelp(String command)
    {
        try {
            return ftpClient.listHelp(command);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
