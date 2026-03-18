package de.bund.zrb.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configures JNA to load its native library ({@code jnidispatch.dll}) from a
 * fixed, trusted directory instead of extracting it to {@code %TEMP%}.
 * <p>
 * <b>Why?</b> Hardened Windows 11 (Smart App Control / AppLocker / WDAC) blocks
 * DLLs loaded from writable temp directories. JNA's default behaviour is to
 * extract {@code jnidispatch.dll} from its JAR into {@code %TEMP%/jna-*},
 * which triggers this block.
 * <p>
 * <b>How?</b> This class:
 * <ol>
 *   <li>Copies {@code jnidispatch.dll} from the JNA JAR resource to
 *       {@code ~/.mainframemate/native/jnidispatch.dll} (once, or when version changes).</li>
 *   <li>Sets {@code jna.boot.library.path} to that directory.</li>
 *   <li>Sets {@code jna.nounpack=true} to suppress temp extraction entirely.</li>
 * </ol>
 * <p>
 * <b>Must be called before any JNA class is loaded</b> — typically as the
 * very first statement in {@code Main.main()}.
 *
 * @see <a href="https://github.com/java-native-access/jna/blob/master/src/com/sun/jna/overview.html">
 *      JNA loading order documentation</a>
 */
public final class JnaBootstrap {

    private static final Logger LOG = Logger.getLogger(JnaBootstrap.class.getName());

    private static final String APP_DIR = ".mainframemate";
    private static final String NATIVE_DIR = "native";

    /**
     * The resource path inside the JNA JAR where the Windows x86-64
     * jnidispatch.dll lives. JNA 5.x uses this convention.
     */
    private static final String JNA_RESOURCE = "/com/sun/jna/win32-x86-64/jnidispatch.dll";

    /**
     * Fallback for 32-bit JVMs (unlikely but handled).
     */
    private static final String JNA_RESOURCE_32 = "/com/sun/jna/win32-x86/jnidispatch.dll";

    private JnaBootstrap() { /* utility */ }

    /**
     * Set up JNA to use a fixed native library directory.
     * Safe to call on non-Windows platforms (will simply do nothing).
     */
    public static void configure() {
        if (!isWindows()) {
            return;
        }

        // If the user has already set a boot path explicitly, respect it
        if (System.getProperty("jna.boot.library.path") != null) {
            LOG.fine("[JNA] jna.boot.library.path already set — skipping bootstrap.");
            return;
        }

        try {
            File nativeDir = getNativeDir();
            File target = new File(nativeDir, "jnidispatch.dll");

            // Pick the right resource for this JVM architecture
            String resource = is64Bit() ? JNA_RESOURCE : JNA_RESOURCE_32;

            if (!target.exists() || hasResourceChanged(resource, target)) {
                extractResource(resource, target);
            }

            if (target.exists()) {
                System.setProperty("jna.boot.library.path", nativeDir.getAbsolutePath());
                System.setProperty("jna.nounpack", "true");
                LOG.info("[JNA] Configured boot library path: " + nativeDir.getAbsolutePath());
            } else {
                LOG.warning("[JNA] Could not provision jnidispatch.dll — "
                        + "JNA will fall back to default temp extraction.");
            }
        } catch (Exception e) {
            // Non-fatal: JNA may still work via temp extraction on lenient systems
            LOG.log(Level.WARNING, "[JNA] Bootstrap failed — "
                    + "JNA will fall back to default temp extraction.", e);
        }
    }

    // ── internals ────────────────────────────────────────────────────

    private static void extractResource(String resource, File target) throws IOException {
        InputStream in = JnaBootstrap.class.getResourceAsStream(resource);
        if (in == null) {
            LOG.warning("[JNA] Resource not found on classpath: " + resource);
            return;
        }

        File parentDir = target.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Cannot create directory: " + parentDir);
        }

        // Write to a temp file first, then rename (atomic on same filesystem)
        File tmp = new File(parentDir, "jnidispatch.dll.tmp");
        try (InputStream is = in; FileOutputStream fos = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        }

        // Delete old file if it exists (may fail if locked — that's fine,
        // it means the DLL is already loaded from there)
        if (target.exists() && !target.delete()) {
            // Already in use — skip update this time
            tmp.delete();
            LOG.fine("[JNA] jnidispatch.dll is locked; keeping existing version.");
            return;
        }

        if (!tmp.renameTo(target)) {
            // Rename failed — try direct copy as fallback
            tmp.delete();
            throw new IOException("Cannot rename temp file to " + target);
        }

        LOG.info("[JNA] Extracted jnidispatch.dll to " + target.getAbsolutePath());
    }

    /**
     * Simple heuristic: re-extract if file sizes differ (covers JNA upgrades).
     */
    private static boolean hasResourceChanged(String resource, File target) {
        try (InputStream in = JnaBootstrap.class.getResourceAsStream(resource)) {
            if (in == null) return false;
            // Count resource size
            long resourceSize = 0;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                resourceSize += n;
            }
            return resourceSize != target.length();
        } catch (IOException e) {
            return true; // re-extract on error
        }
    }

    private static File getNativeDir() {
        String home = System.getProperty("user.home");
        return new File(new File(home, APP_DIR), NATIVE_DIR);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean is64Bit() {
        String arch = System.getProperty("os.arch", "");
        return arch.contains("64");
    }
}

