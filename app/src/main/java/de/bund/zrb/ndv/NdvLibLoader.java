package de.bund.zrb.ndv;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamically loads NDV (Natural Development Server) JAR libraries at runtime.
 * The JARs are not bundled with the application; they must be placed in the configured
 * lib directory (default: ~/.mainframemate/lib/).
 */
public class NdvLibLoader {

    private static volatile boolean loaded = false;
    private static volatile String loadError = null;

    /**
     * Returns the effective lib directory for NDV JARs.
     */
    public static File getLibDir() {
        Settings settings = SettingsHelper.load();
        String customPath = settings.ndvLibPath;
        if (customPath != null && !customPath.trim().isEmpty()) {
            return new File(customPath.trim());
        }
        // Default: ~/.mainframemate/lib/
        String userHome = System.getProperty("user.home");
        return new File(userHome, ".mainframemate" + File.separator + "lib");
    }

    /**
     * Checks if NDV libraries are available (exist in lib dir).
     */
    public static boolean isAvailable() {
        File libDir = getLibDir();
        if (!libDir.isDirectory()) return false;
        File[] jars = findNdvJars(libDir);
        return jars.length > 0;
    }

    /**
     * Returns a human-readable error message if loading failed, or null if OK.
     */
    public static String getLoadError() {
        return loadError;
    }

    /**
     * Ensures NDV libraries are loaded into the system classloader.
     * Safe to call multiple times; only loads once.
     *
     * @throws NdvException if JARs cannot be found or loaded
     */
    public static synchronized void ensureLoaded() throws NdvException {
        if (loaded) return;

        File libDir = getLibDir();
        if (!libDir.isDirectory()) {
            loadError = "NDV-Lib-Verzeichnis nicht gefunden: " + libDir.getAbsolutePath()
                    + "\n\nBitte die NDV-JARs dort ablegen oder den Pfad in den Einstellungen anpassen."
                    + "\nErwartet werden: com.softwareag.naturalone.natural.ndvserveraccess*.jar"
                    + "\n                 com.softwareag.naturalone.natural.auxiliary*.jar";
            throw new NdvException(loadError);
        }

        File[] jars = findNdvJars(libDir);
        if (jars.length == 0) {
            loadError = "Keine NDV-JARs gefunden in: " + libDir.getAbsolutePath()
                    + "\n\nBitte folgende Dateien dort ablegen:"
                    + "\n• com.softwareag.naturalone.natural.ndvserveraccess_*.jar"
                    + "\n• com.softwareag.naturalone.natural.auxiliary_*.jar";
            throw new NdvException(loadError);
        }

        // Add JARs to system classloader (Java 8 hack via reflection on URLClassLoader)
        try {
            URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);

            for (File jar : jars) {
                System.out.println("[NdvLibLoader] Loading: " + jar.getAbsolutePath());
                addUrl.invoke(systemClassLoader, jar.toURI().toURL());
            }

            loaded = true;
            loadError = null;
            System.out.println("[NdvLibLoader] NDV libraries loaded successfully (" + jars.length + " JARs)");
        } catch (Exception e) {
            loadError = "NDV-JARs konnten nicht geladen werden: " + e.getMessage();
            throw new NdvException(loadError, e);
        }
    }

    /**
     * Finds all NDV-related JARs in the given directory.
     */
    private static File[] findNdvJars(File dir) {
        File[] matched = dir.listFiles(file ->
                file.isFile() && file.getName().endsWith(".jar")
                        && (file.getName().contains("ndvserveraccess")
                        || file.getName().contains("auxiliary")
                        || file.getName().contains("naturalone"))
        );
        if (matched == null) return new File[0];
        return matched;
    }
}

