package de.bund.zrb.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.ui.commands.config.CommandRegistryImpl;
import de.zrb.bund.api.MenuCommand;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ShortcutManager {

    private static final File SHORTCUT_FILE = new File(SettingsHelper.getSettingsFolder(), "shortcut.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<String>> shortcutMap = new HashMap<>();

    private ShortcutManager() {}

    public static void loadShortcuts() {
        if (!SHORTCUT_FILE.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(SHORTCUT_FILE), StandardCharsets.UTF_8)) {
            Map<String, List<String>> loaded = GSON.fromJson(reader, Map.class);
            shortcutMap.clear();
            for (Map.Entry<String, List<String>> entry : loaded.entrySet()) {
                shortcutMap.put(entry.getKey(), entry.getValue());
                CommandRegistryImpl.getById(entry.getKey()).ifPresent(cmd -> cmd.setShortcut(entry.getValue()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveShortcuts() {
        Map<String, List<String>> current = new LinkedHashMap<>();
        for (MenuCommand cmd : CommandRegistryImpl.getAll()) {
            List<String> shortcut = cmd.getShortcut();
            if (shortcut != null && !shortcut.isEmpty()) {
                current.put(cmd.getId(), shortcut);
            }
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(SHORTCUT_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(current, writer);
            System.out.println("✅ Shortcuts gespeichert nach: " + SHORTCUT_FILE.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void registerGlobalShortcuts(JRootPane rootPane) {
        for (MenuCommand cmd : CommandRegistryImpl.getAll()) {
            for (String key : cmd.getShortcut()) {
                KeyStroke keyStroke = KeyStroke.getKeyStroke(key);
                if (keyStroke == null) continue;

                rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, key);
                rootPane.getActionMap().put(key, new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        cmd.perform();
                    }
                });
            }
        }
    }

    public static KeyStroke getKeyStrokeFor(MenuCommand cmd) {
        List<String> shortcuts = cmd.getShortcut();
        if (shortcuts == null || shortcuts.isEmpty()) {
            return null;
        }
        return KeyStroke.getKeyStroke(shortcuts.get(0));
    }

    public static void assignShortcut(String id, KeyStroke ks) {
        if (ks == null) {
            shortcutMap.remove(id);
            CommandRegistryImpl.getById(id).ifPresent(cmd -> cmd.setShortcut(Collections.emptyList()));
        } else {
            List<String> shortcut = Collections.singletonList(ks.toString());
            shortcutMap.put(id, shortcut);
            CommandRegistryImpl.getById(id).ifPresent(cmd -> cmd.setShortcut(shortcut));
        }
    }

}
