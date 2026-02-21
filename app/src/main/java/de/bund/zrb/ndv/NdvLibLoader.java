package de.bund.zrb.ndv;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

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

        // Fallback: if default dir doesn't exist or has no JARs, try project-local lib/ (for development)
        File[] jars = libDir.isDirectory() ? findNdvJars(libDir) : new File[0];
        if (jars.length == 0) {
            File projectLib = new File("lib");
            if (projectLib.isDirectory()) {
                File[] devJars = findNdvJars(projectLib);
                if (devJars.length > 0) {
                    libDir = projectLib;
                    jars = devJars;
                    System.out.println("[NdvLibLoader] Using development lib/ directory: " + projectLib.getAbsolutePath());
                }
            }
        }

        if (!libDir.isDirectory() || jars.length == 0) {
            loadError = "Keine NDV-JARs gefunden."
                    + "\n\nBitte folgende Dateien ablegen in:"
                    + "\n  " + getLibDir().getAbsolutePath()
                    + "\n\nBenötigte JARs:"
                    + "\n• com.softwareag.naturalone.natural.ndvserveraccess_*.jar"
                    + "\n• com.softwareag.naturalone.natural.auxiliary_*.jar"
                    + "\n\nAlternativ: Pfad in Einstellungen → NDV-Verbindung anpassen.";
            throw new NdvException(loadError);
        }

        // Find a URLClassLoader we can inject into.
        // Priority: 1) this class's classloader, 2) thread context CL, 3) system CL
        URLClassLoader targetCL = findTargetClassLoader();
        if (targetCL == null) {
            loadError = "Kein URLClassLoader gefunden – NDV-JARs können nicht dynamisch geladen werden.";
            throw new NdvException(loadError);
        }

        try {
            Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);

            for (File jar : jars) {
                System.out.println("[NdvLibLoader] Loading: " + jar.getAbsolutePath());
                addUrl.invoke(targetCL, jar.toURI().toURL());
            }

            loaded = true;
            loadError = null;
            System.out.println("[NdvLibLoader] NDV libraries loaded successfully ("
                    + jars.length + " JARs via " + targetCL.getClass().getSimpleName() + ")");
        } catch (Exception e) {
            loadError = "NDV-JARs konnten nicht geladen werden: " + e.getMessage();
            throw new NdvException(loadError, e);
        }
    }

    /**
     * Walks classloader hierarchy to find a URLClassLoader we can inject into.
     */
    private static URLClassLoader findTargetClassLoader() {
        // 1) Try this class's own classloader (application CL in Gradle/Shadow)
        ClassLoader cl = NdvLibLoader.class.getClassLoader();
        if (cl instanceof URLClassLoader) {
            return (URLClassLoader) cl;
        }

        // 2) Try thread context classloader
        cl = Thread.currentThread().getContextClassLoader();
        while (cl != null) {
            if (cl instanceof URLClassLoader) {
                return (URLClassLoader) cl;
            }
            cl = cl.getParent();
        }

        // 3) Try system classloader
        cl = ClassLoader.getSystemClassLoader();
        if (cl instanceof URLClassLoader) {
            return (URLClassLoader) cl;
        }

        return null;
    }

    /**
     * Finds all JAR files in the given directory.
     * Loads ALL .jar files, not just NDV-specific ones, so transitive deps are included.
     */
    private static File[] findNdvJars(File dir) {
        File[] matched = dir.listFiles(file ->
                file.isFile() && file.getName().endsWith(".jar")
        );
        if (matched == null) return new File[0];
        return matched;
    }
}

