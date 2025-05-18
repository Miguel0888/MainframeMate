package org.example.model;

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
    public String lineEnding = "NONE"; // "LF", "CRLF" oder "NONE"

}
