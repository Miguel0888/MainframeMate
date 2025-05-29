package de.bund.zrb.runtime;

import de.bund.zrb.ui.MainFrame;
import de.zrb.bund.api.Command;
import de.bund.zrb.ui.commands.CommandRegistry;
import de.zrb.bund.api.MainframeMatePlugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

public class PluginManager {

    private static final List<MainframeMatePlugin> plugins = new ArrayList<>();

    public static void registerPlugin(MainframeMatePlugin plugin) {
        plugins.add(plugin);
    }

    public static void initializePlugins(MainFrame mainFrame) {
        // 1. Statisch registrierte Plugins initialisieren
        for (MainframeMatePlugin plugin : plugins) {
            plugin.initialize(mainFrame);
            registerCommandsSafely(plugin, mainFrame);
        }

        // 2. Dynamisch geladene Plugins aus JARs
        loadExternalPlugins(mainFrame);
    }

    public static List<MainframeMatePlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    private static void loadExternalPlugins(MainFrame mainFrame) {
        Path pluginDir = Paths.get(System.getProperty("user.home"), ".mainframemate", "plugins");

        if (!Files.exists(pluginDir)) {
            try {
                Files.createDirectories(pluginDir);
                System.out.println("📁 Plugin-Verzeichnis angelegt: " + pluginDir);
            } catch (IOException e) {
                System.err.println("❌ Plugin-Verzeichnis konnte nicht erstellt werden: " + e.getMessage());
                return;
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginDir, "*.jar")) {
            for (Path jarPath : stream) {
                loadPluginJar(jarPath, mainFrame);
            }
        } catch (IOException e) {
            System.err.println("❌ Fehler beim Lesen der Plugins: " + e.getMessage());
        }
    }

    private static void loadPluginJar(Path jarPath, MainFrame mainFrame) {
        try {
            URL[] urls = { jarPath.toUri().toURL() };
            URLClassLoader loader = new URLClassLoader(urls, PluginManager.class.getClassLoader());

            ServiceLoader<MainframeMatePlugin> serviceLoader =
                    ServiceLoader.load(MainframeMatePlugin.class, loader);

            for (MainframeMatePlugin plugin : serviceLoader) {
                System.out.println("✅ Plugin geladen: " + plugin.getPluginName());
                plugin.initialize(mainFrame);
                registerCommandsSafely(plugin, mainFrame);
                plugins.add(plugin);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Fehler beim Laden von Plugin " + jarPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void registerCommandsSafely(MainframeMatePlugin plugin, MainFrame mainFrame) {
        for (Object obj : plugin.getCommands(mainFrame)) {
            if (obj instanceof Command) {
                CommandRegistry.register((Command) obj);
            } else {
                System.err.println("⚠️ Plugin \"" + plugin.getPluginName()
                        + "\" liefert ungültigen Command-Typ: " + obj.getClass().getName());
            }
        }
    }
}
