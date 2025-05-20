package org.example.ftp;

import org.apache.commons.net.ftp.*;
import org.example.model.*;
import org.example.util.SettingsManager;

import java.io.*;
import java.nio.charset.Charset;
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
        Settings settings = SettingsManager.load();
        ftpClient.setControlEncoding(settings.encoding);
        ftpClient.connect(host);

        if (!ftpClient.login(user, password)) {
            throw new IOException("Login fehlgeschlagen");
        }

        ftpClient.enterLocalPassiveMode();

        String systemType = ftpClient.getSystemType();
        mvsMode = systemType != null && systemType.toUpperCase().contains("MVS");
        System.out.println("Systemtyp laut FTP-Server: " + systemType);

        // Anwenden der konfigurierten FTP-Transferoptionen
        applyTransferSettings(settings);

        if (systemType != null && systemType.toUpperCase().contains("WIN32NT")) {
            ftpClient.configure(new FTPClientConfig(FTPClientConfig.SYST_NT));
        }

        return true;
    }

    private void applyTransferSettings(Settings settings) throws IOException {
        // TYPE
        if (settings.ftpFileType != null) {
            // FORMAT – Apache Commons Net setzt das Format beim TYPE-Aufruf, wenn überladen (nicht separat)
            // FORMAT
            if (settings.ftpTextFormat != null) {
                ftpClient.setFileType(settings.ftpFileType.getCode(), settings.ftpTextFormat.getCode());
            }
            else {
                ftpClient.setFileType(settings.ftpFileType.getCode());
            }
        } else {
            // FORMAT
            if (settings.ftpTextFormat != null) {
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE, settings.ftpTextFormat.getCode());
            }
            else {
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
            }
        }

        // STRUCTURE
        if (settings.ftpFileStructure != null) {
            ftpClient.setFileStructure(settings.ftpFileStructure.getCode());
            System.out.println(">> FTP setFileStructure: " + settings.ftpFileStructure);
        } else if (isMvsMode()) {
            ftpClient.setFileStructure(FTP.RECORD_STRUCTURE);
        } else {
            ftpClient.setFileStructure(FTP.FILE_STRUCTURE);
        }
// ToDo: Check this
//        // MODE
        if (settings.ftpTransferMode != null) {
            ftpClient.setFileTransferMode(settings.ftpTransferMode.getCode());
        } else {
            ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
        }
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
        if (isMvsMode() && filename.contains(".")) {
            String[] parts = filename.split("\\.");
            for (int i = 0; i < parts.length - 1; i++) {
                openDirectory(parts[i]);
            }
            filename = parts[parts.length - 1];
        }

        String finalName = filename;
        FTPFile fileMeta = Arrays.stream(ftpClient.listFiles())
                .filter(f -> f.getName().equalsIgnoreCase(finalName))
                .findFirst()
                .orElseThrow(() -> new IOException("Datei nicht gefunden: " + finalName));


        if (fileMeta.isDirectory()) {
            openDirectory(filename);
            return null;
        }

        InputStream in = ftpClient.retrieveFileStream(filename);
        if (in == null) {
            throw new IOException("Konnte Datei nicht laden: " + filename + "\nAntwort: " + ftpClient.getReplyString());
        }

        FtpFileBuffer buffer = new FtpFileBuffer(filename, fileMeta, true); // recordStructure = true
        buffer.loadContent(in, null);

        in.close();
        if (!ftpClient.completePendingCommand()) {
            throw new IOException("FTP-Übertragung unvollständig: " + filename);
        }

        return buffer;
    }

    public boolean storeFile(FtpFileBuffer buffer, String newContent) throws IOException {
        // InputStream vorbereiten (inkl. Zeilenumbruch-Mapping)
        InputStream data = buffer.toInputStream(newContent);

        // Remote-Version laden
        InputStream in = ftpClient.retrieveFileStream(buffer.getRemotePath());
        if (in == null) {
            throw new IOException("Konnte Server-Datei zum Vergleich nicht laden");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int len;
        while ((len = in.read(tmp)) != -1) out.write(tmp, 0, len);
        in.close();
        ftpClient.completePendingCommand();

        String currentRemote = new String(out.toByteArray(), buffer.getCharset());

        // Konfliktprüfung
        if (!buffer.isUnchanged(currentRemote)) {
            System.err.println("Konflikt: Die Datei wurde auf dem Server geändert.");
            return false;
        }

        boolean success = ftpClient.storeFile(buffer.getRemotePath(), data);

        if (success) {
            // Datei nach erfolgreichem Speichern neu einlesen, um Zustand zu aktualisieren
            InputStream reloaded = ftpClient.retrieveFileStream(buffer.getRemotePath());
            if (reloaded != null) {
                buffer.loadContent(reloaded, null);
                reloaded.close();
                ftpClient.completePendingCommand();
            }
        }

        return success;
    }

    public boolean hasFeature(FTPCmd cmd) {
        try {
            return ftpClient.hasFeature(cmd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getHelp() {
        try {
            return ftpClient.listHelp();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getHelp(String command) {
        try {
            return ftpClient.listHelp(command);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean createEmptyFile(String name) throws IOException {
        return ftpClient.storeFile(name, new ByteArrayInputStream(new byte[0]));
    }

    public boolean delete(String name) throws IOException {
        return ftpClient.deleteFile(name) || ftpClient.removeDirectory(name);
    }

    public boolean createPds(String name) throws IOException {
        if (!mvsMode) throw new UnsupportedOperationException("Nur unter MVS möglich");
        return ftpClient.makeDirectory(quoteMvsPath(name));
    }

    public void openDirectory(String selected) throws IOException {
        ftpClient.changeWorkingDirectory(selected);
    }

    private String quoteMvsPath(String dataset) {
        if (!dataset.startsWith("'")) dataset = "'" + dataset;
        if (!dataset.endsWith("'")) dataset = dataset + "'";
        return dataset;
    }
}
