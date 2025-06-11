package de.bund.zrb.ftp;

import de.bund.zrb.model.Settings;
import de.bund.zrb.util.StringUtil;
import org.apache.commons.net.ftp.*;
import de.bund.zrb.helper.SettingsHelper;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import static de.bund.zrb.util.ByteUtil.parseHexByte;

public class FtpManager {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Fields und Konstruktor
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static boolean wrongCredentials = false; // Flag to avoid multiple retry with wrong password

    private final FTPClient ftpClient = new FTPClient();
    private final Integer ftpFileType;
    private final Byte padding; // null if not present or not compatible with ftpFileType
    private String currentPath = "/";
    private final List<FtpObserver> observers = new ArrayList<>();
    private boolean mvsMode = false;

    public FtpManager() {
        Settings settings = SettingsHelper.load();
        this.ftpFileType = settings.ftpFileType == null ? null : SettingsHelper.load().ftpFileType.getCode();
        this.padding = this.ftpFileType == null || this.ftpFileType == FTP.ASCII_FILE_TYPE ? parseHexByte(settings.padding) : null;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Connect und Disconnect
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean connect(String host, String user, String password) throws IOException {
        if(wrongCredentials) {
            throw new IOException("Login ist vorübergehend deaktiviert, damit die Kennung nicht gesperrt wird. Bitte die Zugangsdaten prüfen und die Anwendung anschließend neu starten!"); // don't try again, until restart to avoid user is getting blocked
        }
        Settings settings = SettingsHelper.load();
        ftpClient.setControlEncoding(settings.encoding);
        ftpClient.connect(host);

        if (!ftpClient.login(user, password)) {
            wrongCredentials = true;
            settings.savePassword = false;
            SettingsHelper.save(settings);
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

        ftpClient.changeWorkingDirectory("''"); // root instead of user home dir

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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Query
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void applyTransferSettings(Settings settings) throws IOException {
        // TYPE
        if (ftpFileType != null) {
            // FORMAT – Apache Commons Net setzt das Format beim TYPE-Aufruf, wenn überladen (nicht separat)
            // FORMAT
            if (settings.ftpTextFormat != null) {
                ftpClient.setFileType(ftpFileType, settings.ftpTextFormat.getCode());
            }
            else {
                ftpClient.setFileType(ftpFileType);
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Observer-Pattern für Verzeichnisänderungen
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Getter und Setter für FTP-Client und Charset etc.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean isMvsMode() {
        return mvsMode;
    }

    public FTPClient getClient() {
        return ftpClient;
    }

    public Charset getCharset() {
        return Charset.forName(ftpClient.getControlEncoding());
    }

    public void setCharset(Charset charset) {
        ftpClient.setControlEncoding(charset.name());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Dateiverwaltung


    public List<String> listDirectory() {
        try {
            return Arrays.asList(ftpClient.listNames());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FtpFileBuffer open(String filename) throws IOException {
        if (isMvsMode()) {
            if(filename.contains(".")) {
                String[] parts = filename.split("\\.");
                for (int i = 0; i < parts.length - 1; i++) {
                    openDirectory(parts[i]);
                }
                filename = parts[parts.length - 1];
            } else {
                if(StringUtil.unquote(ftpClient.printWorkingDirectory()).isEmpty()) {
                    ftpClient.changeWorkingDirectory(filename);
                    return null; // must be the high level qualifier, cannot be a dataset
                }
            }
        }

        String remotePath = getCurrentPath();
        String finalName = filename;
        // fileMeta will be null for FBA (but FB is working)
        FTPFile fileMeta = Arrays.stream(ftpClient.listFiles())
                .filter(f -> f.getName().equalsIgnoreCase(finalName))
                .findFirst().orElse(null);

        // Caution: Will be NULL for FBA Record Format !!!
        if (fileMeta == null) {
            fileMeta = new FTPFile();
            fileMeta.setName(finalName);
        } else {
            if (fileMeta.isDirectory()) {
                openDirectory(filename);
                return null;
            }
        }

        InputStream in = ftpClient.retrieveFileStream(filename);
        if (in == null) {
            throw new IOException("Konnte Datei nicht laden: " + filename + "\nAntwort: " + ftpClient.getReplyString());
        }

        FtpFileBuffer buffer = new FtpFileBuffer(
                in,
                padding,
                getCharset(),
                remotePath,
                fileMeta,
                true, // recordStructure
                null,  // kein ProgressListener
                mvsMode
        );

        in.close();
        if (!ftpClient.completePendingCommand()) {
            throw new IOException("FTP-Übertragung unvollständig: " + filename);
        }

        if (SettingsHelper.load().enableHexDump) {
            System.out.println("📥 Received:");
            buffer.printHexDump();
        }

        return buffer;
    }

    /**
     * Versucht, die Datei zu speichern. Liefert bei Konflikt den aktuellen Stand vom Server zurück.
     * @param original Originaler Buffer (mit gemerktem Hash)
     * @param altered Neuer Inhalt (z. B. über withContent erzeugt)
     * @return Optional.empty() bei Erfolg, andernfalls aktueller Remote-Buffer
     */
    public Optional<FtpFileBuffer> commit(FtpFileBuffer original, FtpFileBuffer altered) throws IOException {
        FtpFileBuffer remote = readRemoteBuffer(original);

        if (!original.equals(remote)) {
            return Optional.of(remote); // Konflikt
        }

        push(altered);
        return Optional.empty(); // Erfolg
    }

    /**
     * Speichert den gegebenen Buffer auf dem Server.
     * @throws IOException wenn der Schreibvorgang fehlschlägt
     */
    public void push(FtpFileBuffer buffer) throws IOException {
        if (SettingsHelper.load().enableHexDump) {
            System.out.println("📤 Sending:");
            buffer.printHexDump();
        }

        ByteArrayInputStream in = new ByteArrayInputStream(buffer.getRawBytes());

        boolean success = ftpClient.storeFile(buffer.getName(), in);

        if (!success) {
            throw new IOException("Speichern fehlgeschlagen: " + ftpClient.getReplyString());
        }

        // Optional: Zustand nach Speichern validieren
//        InputStream check = ftpClient.retrieveFileStream(buffer.getRemotePath());
//        if (check != null) check.close();
//        ftpClient.completePendingCommand();
    }

    private boolean hasRemoteChanged(FtpFileBuffer original) throws IOException {
        FtpFileBuffer remote = readRemoteBuffer(original);
        return !original.equals(remote);
    }

    private FtpFileBuffer readRemoteBuffer(FtpFileBuffer reference) throws IOException {
        InputStream in = ftpClient.retrieveFileStream(reference.getName());
        if (in == null) {
            throw new IOException("Konnte Server-Datei nicht erneut laden: " + reference.getName());
        }

        FtpFileBuffer buffer = reference.withContent(in);

        in.close();
        ftpClient.completePendingCommand();
        return buffer;
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

    // ToDo: Funktioniert eventuell nur auf dem Großrechner, daher evtl. anders lösen
    public boolean isDirectory(String path) {
        try {
            return ftpClient.listDirectories(path).length > 0; // ein Verzeichnis listet sich immer mindestens selbst
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}