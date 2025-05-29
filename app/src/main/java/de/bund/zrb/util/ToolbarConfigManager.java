package de.bund.zrb.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ToolbarConfigManager {

    private static final Path CONFIG_PATH =
            Paths.get(System.getProperty("user.home"), ".mainframemate", "toolbar.json");

    public static Set<String> loadActiveCommandIds() {
        try {
            if (!Files.exists(CONFIG_PATH)) return new LinkedHashSet<>();
            List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
            return new LinkedHashSet<>(lines);
        } catch (IOException e) {
            System.err.println("⚠️ Toolbar-Konfiguration konnte nicht geladen werden: " + e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    public static void saveActiveCommandIds(Set<String> ids) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, ids, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("⚠️ Toolbar-Konfiguration konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
