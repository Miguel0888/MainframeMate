package org.example.model;

import org.example.ftp.FtpFileStructure;
import org.example.ftp.FtpFileType;
import org.example.ftp.FtpTextFormat;
import org.example.ftp.FtpTransferMode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class Settings {
    public String host;
    public String user;
    public String encryptedPassword;
    public boolean savePassword = false;
    public boolean autoConnect = false;

    public Map<String, String> bookmarks = new HashMap<>(); // Pfad → Name
    public Map<String, String> pathActions = new HashMap<>(); // Pfad → Aktion

    public String encoding = "ISO-8859-1"; // Standardwert
    public boolean hideLoginDialog = false;

    // Plugin-Settings pro Plugin-Name
    public Map<String, Map<String, String>> pluginSettings = new LinkedHashMap<>();

    public String editorFont = "Monospaced"; // default
    public int editorFontSize = 12; // Standardgröße
    public String lineEnding = "FF01"; // Hex-Werte
    public String fileEndMarker = "FF02"; // z. B. "FF02", oder leer/null = deaktiviert
    public int marginColumn = 80; // 0 bedeutet: keine Linie

    public Map<String, String> fieldColorOverrides = new HashMap<>();

    public FtpFileType ftpFileType;
    public FtpTextFormat ftpTextFormat;
    public FtpFileStructure ftpFileStructure; // Enum-Name aus FtpFileStructure oder null für "Automatisch"
    public FtpTransferMode ftpTransferMode;

    public boolean enableHexDump = false; // Standard = aus
}
