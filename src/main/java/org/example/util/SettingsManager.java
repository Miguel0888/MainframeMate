package org.example.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.model.Settings;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class SettingsManager {

    private static final File SETTINGS_FILE = new File(System.getProperty("user.home"), ".mainframemate/settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Settings load() {
        if (!SETTINGS_FILE.exists()) return new Settings();
        try (Reader reader = new InputStreamReader(new FileInputStream(SETTINGS_FILE), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, Settings.class);
        } catch (IOException e) {
            e.printStackTrace();
            return new Settings();
        }
    }

    public static void save(Settings settings) {
        try {
            SETTINGS_FILE.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(SETTINGS_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(settings, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static File getSettingsFolder() {
        return SETTINGS_FILE.getParentFile();
    }
}
