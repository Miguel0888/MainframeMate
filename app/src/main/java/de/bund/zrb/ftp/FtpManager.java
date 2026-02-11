package de.bund.zrb.ftp;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.StringUtil;
import org.apache.commons.net.ftp.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import static de.bund.zrb.util.ByteUtil.parseHexByte;

public class FtpManager {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Fields und Konstruktor
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private final LoginManager loginManager;

    private final FTPClient ftpClient = new FTPClient();
    private final Integer ftpFileType;
    private final Byte padding; // null if not present or not compatible with ftpFileType
    private String currentPath = "/";
    private final List<FtpObserver> observers = new ArrayList<>();
    private boolean mvsMode = false;
    private String systemType;

    public FtpManager() {
        this.loginManager = LoginManager.getInstance();
        Settings settings = SettingsHelper.load();
        this.ftpFileType = settings.ftpFileType == null ? null : settings.ftpFileType.getCode();
        this.padding = this.ftpFileType == null || this.ftpFileType == FTP.ASCII_FILE_TYPE ? parseHexByte(settings.padding) : null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Connect und Disconnect
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean connect(String host, String user) throws IOException {
        LoginManager.BlockedLoginDecision decision = LoginManager.BlockedLoginDecision.RETRY;

        if (loginManager.isLoginBlocked()) {
            decision = loginManager.showBlockedLoginDialog();
            if (decision == LoginManager.BlockedLoginDecision.CANCEL) {
                throw new IOException("Login abgebrochen durch Benutzer.");
            }
            // Allow another try after explicit user decision
            loginManager.resetLoginBlock();
        }

        String password = resolvePasswordForDecision(host, user, decision);
        if (password == null) {
            throw new IOException("Kein Passwort verfügbar");
        }

        return connectInternal(host, user, password);
    }

    private String resolvePasswordForDecision(String host, String user, LoginManager.BlockedLoginDecision decision) {
        if (decision == LoginManager.BlockedLoginDecision.RETRY_WITH_NEW_PASSWORD) {
            return loginManager.requestFreshPassword(host, user);
        }
        return loginManager.getPassword(host, user);
    }

    private boolean connectInternal(String host, String user, String password) throws IOException {
        Settings settings = SettingsHelper.load();

        ftpClient.setControlEncoding(settings.encoding);
        ftpClient.connect(host);

        if (!ftpClient.login(user, password)) {
            handleFailedLogin(host, user);
            throw new IOException("Login fehlgeschlagen");
        }

        ftpClient.enterLocalPassiveMode();

        systemType = ftpClient.getSystemType();
        mvsMode = systemType != null && systemType.toUpperCase().contains("MVS");

        // Apply configured FTP transfer options
        applyTransferSettings(settings);

        if (systemType != null && systemType.toUpperCase().contains("WIN32NT")) {
            ftpClient.configure(new FTPClientConfig(FTPClientConfig.SYST_NT));
        }

        ftpClient.changeWorkingDirectory("''"); // root instead of user home dir

        return true;
    }

    private void handleFailedLogin(String host, String user) {
        // Prevent further attempts without explicit user decision
        loginManager.blockLoginTemporarily();

        // Prevent reusing wrong passwords from cache/settings
        loginManager.invalidatePassword(host, user);

        // Disable saving password as a safety measure
        Settings settings = SettingsHelper.load();
        settings.savePassword = false;
        SettingsHelper.save(settings);

        disconnectQuietly();
    }

    private void disconnectQuietly() {
        try {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                } catch (IOException ignore) {
                    // Intentionally ignore logout errors
                }
                ftpClient.disconnect();
            }
        } catch (IOException ignore) {
            // Intentionally ignore disconnect errors
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Query
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void applyTransferSettings(Settings settings) throws IOException {
        // TYPE
        if (ftpFileType != null) {
            // FORMAT – Apache Commons Net sets format inside TYPE if overloaded
            if (settings.ftpTextFormat != null) {
                ftpClient.setFileType(ftpFileType, settings.ftpTextFormat.getCode());
            } else {
                ftpClient.setFileType(ftpFileType);
            }
        } else {
            if (settings.ftpTextFormat != null) {
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE, settings.ftpTextFormat.getCode());
            } else {
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

        // MODE
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

    /**
     * Öffnet eine Datei über ihren absoluten (vollqualifizierten) Namen – unabhängig vom aktuellen Verzeichnis.
     * Beispiel: 'ABC88.J25'
     *
     * HINWEIS: Die Methode funktioniert wegen des Arbeitsverzeichnisses möglicherweise nicht wie erwartet!
     */
    public FtpFileBuffer openAbsolute(String quotedName) throws IOException {
        if (!quotedName.startsWith("'")) {
            throw new IllegalArgumentException("Quoted MVS-Dataset name erwartet, z. B. 'KKR097.J25'");
        }

        InputStream in = ftpClient.retrieveFileStream(quotedName);
        if (in == null) {
            throw new IOException("Konnte Datei nicht laden: " + quotedName + "\nAntwort: " + ftpClient.getReplyString());
        }

        FTPFile fileMeta = new FTPFile();
        fileMeta.setName(quotedName);

        FtpFileBuffer buffer = new FtpFileBuffer(
                in,
                padding,
                getCharset(),
                quotedName,
                fileMeta,
                true,
                null,
                mvsMode
        );

        in.close();
        if (!ftpClient.completePendingCommand()) {
            throw new IOException("FTP-Übertragung unvollständig: " + quotedName);
        }

        return buffer;
    }

    public FtpFileBuffer open(String filename) throws IOException {
        if (isMvsMode()) {
            if (filename.contains(".")) {
                String[] parts = filename.split("\\.");
                for (int i = 0; i < parts.length - 1; i++) {
                    openDirectory(parts[i]);
                }
                filename = parts[parts.length - 1];
            } else {
                if (StringUtil.unquote(ftpClient.printWorkingDirectory()).isEmpty()) {
                    ftpClient.changeWorkingDirectory(filename);
                    return null; // must be the high level qualifier, cannot be a dataset
                }
            }
        }

        String remotePath = getCurrentPath();
        String finalName = filename;

        FTPFile fileMeta = Arrays.stream(ftpClient.listFiles())
                .filter(f -> f.getName().equalsIgnoreCase(finalName))
                .findFirst().orElse(null);

        // Caution: Will be NULL for FBA Record Format
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
                true,
                null,
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
     * @param altered Neuer Inhalt (z. B. über withContent erzeugt)
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

    public boolean changeToParentDirectory() throws IOException {
        return ftpClient.changeToParentDirectory();
    }

    public boolean changeWorkingDirectory(String currentPath) throws IOException {
        return ftpClient.changeWorkingDirectory(currentPath);
    }
}
