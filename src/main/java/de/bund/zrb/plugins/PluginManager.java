package de.bund.zrb.plugins;

import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.commands.Command;
import de.bund.zrb.ui.commands.CommandRegistry;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

public class PluginManager {
    private static final List<MainframeMatePlugin> plugins = new ArrayList<>();

    public static void registerPlugin(MainframeMatePlugin plugin) {
        plugins.add(plugin);
    }

    public static void initializePlugins(MainFrame mainFrame) {
        // 1. Plugins aus Code registrieren
        for (MainframeMatePlugin plugin : plugins) {
            plugin.initialize(mainFrame);
            plugin.getCommands(mainFrame).forEach(CommandRegistry::register);
        }

        // 2. Plugins aus JAR-Dateien laden
        loadExternalPlugins(mainFrame);
    }

    public static List<MainframeMatePlugin> getPlugins() {
        return plugins;
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
                plugin.getCommands(mainFrame).forEach(CommandRegistry::register);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Fehler beim Laden von Plugin " + jarPath + ": " + e.getMessage());
        }
    }
}
