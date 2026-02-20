package de.bund.zrb.runtime;

import de.bund.zrb.mcp.registry.McpServerManager;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.commands.InstallPluginMenuCommand;
import de.zrb.bund.api.MenuCommand;
import de.bund.zrb.ui.commands.config.CommandRegistryImpl;
import de.zrb.bund.api.MainframeMatePlugin;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.mcp.McpTool;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

public class PluginManager {

    private static final List<MainframeMatePlugin> plugins = new ArrayList<>();
    /** Track loaded plugin names to prevent duplicates. */
    private static final Set<String> loadedPluginNames = new HashSet<>();

    public static void registerPlugin(MainframeMatePlugin plugin) {
        plugins.add(plugin);
    }

    public static void initializePlugins(MainFrame mainFrame) {
        CommandRegistryImpl.register(new InstallPluginMenuCommand());

        // 1. Statisch registrierte Plugins initialisieren
        for (MainframeMatePlugin plugin : plugins) {
            plugin.initialize(mainFrame);
            registerCommandsSafely(plugin, mainFrame);
            registerToolsSafely(plugin, mainFrame);
        }

        // 2. Plugins vom Classpath laden (runtimeOnly-Dependencies)
        loadClasspathPlugins(mainFrame);

        // 3. Dynamisch geladene Plugins aus JARs im Plugin-Verzeichnis
        loadExternalPlugins(mainFrame);
    }

    public static List<MainframeMatePlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    /**
     * Load plugins available on the application classpath (e.g. runtimeOnly Gradle dependencies).
     * This ensures they are registered and deduplicated before scanning external JAR files.
     */
    private static void loadClasspathPlugins(MainFrame mainFrame) {
        ServiceLoader<MainframeMatePlugin> serviceLoader =
                ServiceLoader.load(MainframeMatePlugin.class, PluginManager.class.getClassLoader());

        for (MainframeMatePlugin plugin : serviceLoader) {
            String pluginName = plugin.getPluginName();
            if (loadedPluginNames.contains(pluginName)) {
                continue; // already loaded (e.g. statically registered)
            }
            loadedPluginNames.add(pluginName);
            System.out.println("✅ Plugin geladen (classpath): " + pluginName);
            plugin.initialize(mainFrame);
            registerCommandsSafely(plugin, mainFrame);
            registerToolsSafely(plugin, mainFrame);
            plugins.add(plugin);
        }
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

        // Collect MCP-server JAR filenames declared by already-loaded plugins
        // so we don't try to load them as regular plugins via ServiceLoader
        Set<String> mcpServerJars = new HashSet<>();
        for (MainframeMatePlugin p : plugins) {
            String jar = p.getMcpServerJarName();
            if (jar != null && !jar.isEmpty()) {
                mcpServerJars.add(jar);
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginDir, "*.jar")) {
            for (Path jarPath : stream) {
                String fileName = jarPath.getFileName().toString();
                if (mcpServerJars.contains(fileName)) {
                    System.out.println("⏭️ MCP-Server JAR übersprungen (gehört zu Plugin): " + fileName);
                    continue;
                }
                loadPluginJar(jarPath, mainFrame);
            }
        } catch (IOException e) {
            System.err.println("❌ Fehler beim Lesen der Plugins: " + e.getMessage());
        }

        // After all plugins are loaded, register their MCP servers
        registerMcpServersFromPlugins();
    }

    private static void loadPluginJar(Path jarPath, MainFrame mainFrame) {
        try {
            URL[] urls = { jarPath.toUri().toURL() };
            URLClassLoader loader = new URLClassLoader(urls, PluginManager.class.getClassLoader());

            ServiceLoader<MainframeMatePlugin> serviceLoader =
                    ServiceLoader.load(MainframeMatePlugin.class, loader);

            for (MainframeMatePlugin plugin : serviceLoader) {
                String pluginName = plugin.getPluginName();
                if (loadedPluginNames.contains(pluginName)) {
                    System.out.println("⏭️ Plugin bereits geladen, übersprungen: " + pluginName);
                    continue;
                }
                loadedPluginNames.add(pluginName);
                System.out.println("✅ Plugin geladen: " + pluginName);
                plugin.initialize(mainFrame);
                registerCommandsSafely(plugin, mainFrame);
                registerToolsSafely(plugin, mainFrame);
                plugins.add(plugin);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Fehler beim Laden von Plugin " + jarPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * For every loaded plugin that declares an MCP server JAR,
     * register it as a built-in server in the McpServerManager.
     */
    private static void registerMcpServersFromPlugins() {
        McpServerManager manager = McpServerManager.getInstance();
        for (MainframeMatePlugin plugin : plugins) {
            String jarName = plugin.getMcpServerJarName();
            if (jarName == null || jarName.isEmpty()) continue;

            String displayName = plugin.getMcpServerDisplayName();
            boolean enabledByDefault = plugin.isMcpServerEnabledByDefault();

            manager.registerPluginMcpServer(displayName, jarName, enabledByDefault);
            System.out.println("🔌 MCP-Server registriert via Plugin: "
                    + displayName + " → " + jarName);
        }
    }

    private static void registerCommandsSafely(MainframeMatePlugin plugin, MainFrame mainFrame) {
        for (Object obj : plugin.getCommands(mainFrame)) {
            if (obj instanceof MenuCommand) {
                CommandRegistryImpl.register((MenuCommand) obj);
            } else {
                System.err.println("⚠️ Plugin \"" + plugin.getPluginName()
                        + "\" liefert ungültigen Command-Typ: " + obj.getClass().getName());
            }
        }
    }

    private static void registerToolsSafely(MainframeMatePlugin plugin, MainFrame mainFrame) {
        ToolRegistry registry = mainFrame.getToolRegistry();

        if (registry == null) {
            System.err.println("⚠️ ToolRegistry nicht verfügbar.");
            return;
        }

        for (McpTool tool : plugin.getTools()) {
            if (tool != null) {
                registry.registerTool(tool);
            } else {
                System.err.println("⚠️ Plugin \"" + plugin.getPluginName() + "\" liefert null bei getTools().");
            }
        }
    }


}
