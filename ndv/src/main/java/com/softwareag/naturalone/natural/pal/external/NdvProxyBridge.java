package com.softwareag.naturalone.natural.pal.external;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * Injiziert die NDV-JARs (ndvserveraccess + ICU4J) zur Laufzeit in den
 * App-ClassLoader via {@code URLClassLoader.addURL()}.
 *
 * <p>Damit landen alle Klassen im selben ClassLoader — keine Proxy-Wrapping-,
 * mapArgs/mapResult- oder ClassCast-Probleme mehr.
 *
 * <p>Voraussetzung: Java 8 (App-ClassLoader ist ein {@code URLClassLoader}).
 */
public final class NdvProxyBridge {

    /**
     * System Property für den Pfad zum Verzeichnis mit den NDV-JARs.
     * Wird von der App-Schicht aus den Settings gesetzt.
     */
    public static final String PROPERTY_LIB_PATH = "mainframemate.ndv.libpath";

    private static volatile boolean initialized;

    private NdvProxyBridge() {}

    // ── Initialisierung ──────────────────────────────────────────────────────

    /**
     * Fügt die angegebenen JARs dem App-ClassLoader hinzu.
     * Mehrfachaufruf ist sicher (idempotent).
     */
    public static synchronized void init(File... jars) throws Exception {
        if (initialized) return;

        URLClassLoader appCl = getAppClassLoader();
        Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        addURL.setAccessible(true);

        for (File jar : jars) {
            if (!jar.isFile()) {
                throw new IllegalArgumentException("JAR nicht gefunden: " + jar.getAbsolutePath());
            }
            addURL.invoke(appCl, jar.toURI().toURL());
            System.out.println("[NdvProxyBridge] JAR geladen: " + jar.getName());
        }

        initialized = true;
    }

    /**
     * Initialisiert automatisch aus System Property oder bekannten Verzeichnissen.
     */
    public static synchronized void ensureInitialized() {
        if (initialized) return;

        File libDir = resolveLibDir();
        if (libDir == null || !libDir.isDirectory()) {
            throw new IllegalStateException(
                    "Kein NDV-JAR-Verzeichnis gefunden. Bitte System Property '"
                    + PROPERTY_LIB_PATH + "' setzen oder JARs in "
                    + getDefaultLibDir().getAbsolutePath() + " ablegen.");
        }

        File[] jars = findJars(libDir);
        if (jars.length == 0) {
            throw new IllegalStateException(
                    "Keine JAR-Dateien in " + libDir.getAbsolutePath() + " gefunden.");
        }

        try {
            init(jars);
            System.out.println("[NdvProxyBridge] Initialisiert mit " + jars.length
                    + " JARs aus " + libDir.getAbsolutePath());
        } catch (Exception e) {
            throw new IllegalStateException("NdvProxyBridge-Init fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Für Tests: Reset-Flag (JARs bleiben im ClassLoader). */
    public static synchronized void reset() {
        initialized = false;
    }

    /** Prüft ob bereits initialisiert. */
    public static boolean isInitialized() {
        return initialized;
    }

    // ── ClassLoader-Zugriff ──────────────────────────────────────────────────

    /**
     * Gibt den App-ClassLoader zurück (nach ensureInitialized enthält er die JARs).
     */
    public static ClassLoader getClassLoader() {
        if (!initialized) {
            ensureInitialized();
        }
        return getAppClassLoader();
    }

    private static URLClassLoader getAppClassLoader() {
        ClassLoader cl = NdvProxyBridge.class.getClassLoader();
        if (cl instanceof URLClassLoader) {
            return (URLClassLoader) cl;
        }
        // Fallback: System ClassLoader
        cl = ClassLoader.getSystemClassLoader();
        if (cl instanceof URLClassLoader) {
            return (URLClassLoader) cl;
        }
        throw new IllegalStateException(
                "App-ClassLoader ist kein URLClassLoader. Bitte Java 8 verwenden.");
    }

    // ── Pfad-Auflösung ──────────────────────────────────────────────────────

    private static File resolveLibDir() {
        // 1) Explizit gesetzte Property
        String path = System.getProperty(PROPERTY_LIB_PATH);
        if (path != null && !path.trim().isEmpty()) {
            File dir = new File(path.trim());
            if (dir.isDirectory() && findJars(dir).length > 0) {
                return dir;
            }
        }

        // 2) Standard-Verzeichnis
        File defaultDir = getDefaultLibDir();
        if (defaultDir.isDirectory() && findJars(defaultDir).length > 0) {
            return defaultDir;
        }

        // 3) Entwicklungs-Fallback: ./lib/
        File projectLib = new File("lib");
        if (projectLib.isDirectory() && findJars(projectLib).length > 0) {
            return projectLib;
        }

        return null;
    }

    private static File getDefaultLibDir() {
        return new File(System.getProperty("user.home"),
                ".mainframemate" + File.separator + "lib");
    }

    private static File[] findJars(File dir) {
        File[] jars = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
        return jars != null ? jars : new File[0];
    }
}
