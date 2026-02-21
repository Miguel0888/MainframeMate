package de.bund.zrb.ndv;

import de.bund.zrb.ndv.bridge.NdvProxyBridge;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.io.File;

/**
 * Brücke zwischen den App-Settings und dem {@link NdvProxyBridge}.
 * Setzt die System Property {@value NdvProxyBridge#PROPERTY_LIB_PATH}
 * aus den Benutzer-Einstellungen und delegiert das Laden an den Bridge.
 */
public class NdvLibLoader {

    private static volatile boolean loaded = false;
    private static volatile String loadError = null;

    /**
     * Lib-Verzeichnis, das der Bridge nach Auflösung verwenden wird.
     * Nützlich für UI-Hinweise.
     */
    public static File getLibDir() {
        String prop = System.getProperty(NdvProxyBridge.PROPERTY_LIB_PATH);
        if (prop != null && !prop.trim().isEmpty()) {
            return new File(prop.trim());
        }
        return new File(System.getProperty("user.home"),
                ".mainframemate" + File.separator + "lib");
    }

    public static boolean isAvailable() {
        File libDir = getLibDir();
        if (!libDir.isDirectory()) return false;
        File[] jars = libDir.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
        return jars != null && jars.length > 0;
    }

    public static String getLoadError() {
        return loadError;
    }

    /**
     * Stellt sicher, dass die NDV-JARs geladen sind.
     * Idempotent – kann beliebig oft aufgerufen werden.
     */
    public static synchronized void ensureLoaded() throws NdvException {
        if (loaded) return;

        // System Property aus den Benutzer-Einstellungen befüllen
        publishLibPathProperty();

        try {
            NdvProxyBridge.ensureInitialized();
            loaded = true;
            loadError = null;
        } catch (IllegalStateException e) {
            loadError = "NDV-JARs nicht gefunden oder nicht ladbar."
                    + "\n\nBitte JARs ablegen in:"
                    + "\n  " + getLibDir().getAbsolutePath()
                    + "\n\nBenötigte JARs:"
                    + "\n• com.softwareag.naturalone.natural.ndvserveraccess_*.jar"
                    + "\n• icu4j-*.jar"
                    + "\n\nAlternativ: Pfad in Einstellungen → NDV-Verbindung anpassen."
                    + "\n\nDetails: " + e.getMessage();
            throw new NdvException(loadError, e);
        }
    }

    /**
     * Schreibt den in den Settings konfigurierten NDV-Lib-Pfad
     * als System Property, damit {@link NdvProxyBridge} ihn lesen kann.
     */
    private static void publishLibPathProperty() {
        try {
            Settings settings = SettingsHelper.load();
            String customPath = settings.ndvLibPath;
            if (customPath != null && !customPath.trim().isEmpty()) {
                System.setProperty(NdvProxyBridge.PROPERTY_LIB_PATH, customPath.trim());
            }
        } catch (Exception e) {
            // Settings nicht lesbar – kein Problem, Fallbacks greifen
            System.err.println("[NdvLibLoader] Settings nicht lesbar: " + e.getMessage());
        }
    }
}
