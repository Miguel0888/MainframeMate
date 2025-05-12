package org.example.util;

import java.io.*;
import java.util.Properties;

public class SettingsManager {

    private static final File SETTINGS_FILE = new File(System.getProperty("user.home"), ".mainframemate/settings.properties");

    public static Properties load() {
        Properties props = new Properties();
        if (SETTINGS_FILE.exists()) {
            try (InputStream in = new FileInputStream(SETTINGS_FILE)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }

    public static void save(Properties props) {
        try {
            SETTINGS_FILE.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(SETTINGS_FILE)) {
                props.store(out, "MainframeMate Einstellungen");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static File getSettingsFolder() {
        return SETTINGS_FILE.getParentFile();
    }
}
