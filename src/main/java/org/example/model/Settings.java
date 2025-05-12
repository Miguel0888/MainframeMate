package org.example.model;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class Settings {
    public String host;
    public String user;
    public boolean autoConnect = false;

    public Map<String, String> bookmarks = new HashMap<>(); // Pfad → Name
    public Map<String, String> pathActions = new HashMap<>(); // Pfad → Aktion
}
