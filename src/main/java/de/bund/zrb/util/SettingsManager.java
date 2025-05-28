package de.bund.zrb.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.model.Settings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SettingsManager {
    private static final File SETTINGS_FILE = new File(System.getProperty("user.home"), ".mainframemate/settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<String> SUPPORTED_ENCODINGS = Arrays.asList(
            "UTF-8",
            "ISO-8859-1",
            "windows-1252",   // ANSI-ähnlich
            "Cp1047",         // EBCDIC (Latin-1)
            "Cp037",          // EBCDIC US/Canada
            "IBM01140"        // weitere EBCDIC-Variante
    );

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

    @Deprecated
    public static void addBookmark(String path) {
        Settings settings = load();
        Map<String, String> bookmarks = settings.bookmarks;

        // Einfacher Name als Schlüssel vorschlagen (falls leerer Schlüssel ok ist, sonst Dialog)
        String label = new File(path).getName();

        // Stelle sicher, dass Schlüssel eindeutig ist
        int suffix = 1;
        String originalLabel = label;
        while (bookmarks.containsKey(label)) {
            label = originalLabel + "_" + suffix++;
        }

        bookmarks.put(label, path);
        save(settings);
    }

    @Deprecated
    public static void removeBookmark(String path) {
        Settings settings = load();
        settings.bookmarks.values().removeIf(value -> value.equals(path));
        save(settings);
    }
}
