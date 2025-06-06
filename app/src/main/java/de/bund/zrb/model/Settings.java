package de.bund.zrb.model;

import de.bund.zrb.ftp.FtpFileStructure;
import de.bund.zrb.ftp.FtpFileType;
import de.bund.zrb.ftp.FtpTextFormat;
import de.bund.zrb.ftp.FtpTransferMode;

import java.lang.reflect.Array;
import java.util.*;

public class Settings {
    public String host;
    public String user;
    public String encryptedPassword;
    public boolean savePassword = false;
    public boolean autoConnect = false;

    @Deprecated // können entfernt, werden da sie in eine seperate JSON ausgelagert wurden
    public Map<String, String> bookmarks = new HashMap<>(); // Pfad → Name
    public Map<String, String> applicationState = new HashMap<>(); // Zustände wie Seitenleiste ein oder aus

    public String encoding = "ISO-8859-1"; // Standardwert
    public boolean hideLoginDialog = false;
    public boolean soundEnabled = true;
    public int importDelay = 3;
    public List<String> supportedFiles = Arrays.asList( ".xls", ".xlsx", ".xlsm");

    // Plugin-Settings pro Plugin-Name
    public Map<String, Map<String, String>> pluginSettings = new LinkedHashMap<>();

    public String editorFont = "Monospaced"; // default
    public int editorFontSize = 12; // Standardgröße
    public String lineEnding = "FF01"; // Hex-Werte
    public String fileEndMarker = "FF02"; // z.B. "FF02", oder leer/null = deaktiviert
    public String padding = "00"; // "00", oder leer/null = deaktiviert
    public int marginColumn = 80; // 0 bedeutet: keine Linie

    public Map<String, String> fieldColorOverrides = new HashMap<>();

    public FtpFileType ftpFileType;
    public FtpTextFormat ftpTextFormat;
    public FtpFileStructure ftpFileStructure; // Enum-Name aus FtpFileStructure oder null für "Automatisch"
    public FtpTransferMode ftpTransferMode;

    public boolean enableHexDump = false; // Standard = aus
    public boolean removeFinalNewline = true; // Standard = an

    public HashMap<String, String> aiConfig = new HashMap<>();
    public String defaultWorkflow = "";
}
