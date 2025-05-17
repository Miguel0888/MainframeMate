package org.example.model;

import java.util.HashMap;
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

    public String encoding = "windows-1252"; // Standardwert oder "UTF-8"
}
