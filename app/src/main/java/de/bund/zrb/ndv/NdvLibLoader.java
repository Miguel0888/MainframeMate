package de.bund.zrb.ndv;

import com.softwareag.naturalone.natural.pal.external.NdvProxyBridge;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.io.File;

/**
 * Lädt die NDV-JARs zur Laufzeit via NdvProxyBridge.
 * Die JARs müssen im konfigurierten lib-Verzeichnis liegen
 * (Standard: ~/.mainframemate/lib/).
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
        return findNdvJars(libDir).length > 0;
    }

    /**
     * Returns a human-readable error message if loading failed, or null if OK.
     */
    public static String getLoadError() {
        return loadError;
    }

    /**
     * Ensures NDV libraries are loaded into the classloader.
     * Safe to call multiple times; only loads once.
     * <p>
     * Tries to add JARs to the application classloader (the one that loaded this class),
     * falling back to the system classloader if needed.
     *
     * @throws NdvException if JARs cannot be found or loaded
     */
    public static synchronized void ensureLoaded() throws NdvException {
        if (loaded) return;

        File libDir = getLibDir();
        File[] jars = libDir.isDirectory() ? findNdvJars(libDir) : new File[0];

        // Fallback: lokales lib/-Verzeichnis (Entwicklung)
        if (jars.length == 0) {
            File projectLib = new File("lib");
            if (projectLib.isDirectory()) {
                File[] devJars = findNdvJars(projectLib);
                if (devJars.length > 0) {
                    libDir = projectLib;
                    jars = devJars;
                    System.out.println("[NdvLibLoader] Using development lib/: " + projectLib.getAbsolutePath());
                }
            }
        }

        if (jars.length == 0) {
            loadError = "Keine NDV-JARs gefunden."
                    + "\n\nBitte folgende Dateien ablegen in:"
                    + "\n  " + getLibDir().getAbsolutePath()
                    + "\n\nBenötigte JARs:"
                    + "\n• com.softwareag.naturalone.natural.ndvserveraccess_*.jar"
                    + "\n• icu4j-*.jar"
                    + "\n\nAlternativ: Pfad in Einstellungen → NDV-Verbindung anpassen.";
            throw new NdvException(loadError);
        }

        try {
            NdvProxyBridge.init(jars);
            loaded = true;
            loadError = null;
            System.out.println("[NdvLibLoader] NDV-JARs geladen (" + jars.length + " JARs)");
        } catch (Exception e) {
            loadError = "NDV-JARs konnten nicht geladen werden: " + e.getMessage();
            throw new NdvException(loadError, e);
        }
    }

    /**
     * Finds all JAR files in the given directory.
     * Loads ALL .jar files, not just NDV-specific ones, so transitive deps are included.
     */
    private static File[] findNdvJars(File dir) {
        File[] matched = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
        return matched != null ? matched : new File[0];
    }
}
