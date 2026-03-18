package de.bund.zrb.winml.rt;

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
 * <b>Must be called before any JNA class is loaded</b> — typically as the
 * very first statement in {@link WinRtRuntime#initialize()}.
 */
final class JnaBootstrap {

    private static final Logger LOG = Logger.getLogger(JnaBootstrap.class.getName());

    private static final String APP_DIR = ".mainframemate";
    private static final String NATIVE_DIR = "native";

    /** JNA 5.x resource path for Windows x86-64. */
    private static final String JNA_RESOURCE_64 = "/com/sun/jna/win32-x86-64/jnidispatch.dll";
    /** JNA 5.x resource path for Windows x86 (32-bit). */
    private static final String JNA_RESOURCE_32 = "/com/sun/jna/win32-x86/jnidispatch.dll";

    private static volatile boolean configured = false;

    private JnaBootstrap() { /* utility */ }

    /**
     * Set up JNA to use a fixed native library directory.
     * Safe to call multiple times (idempotent). Does nothing on non-Windows.
     */
    static synchronized void configure() {
        if (configured) return;
        configured = true;

        if (!isWindows()) return;

        // Respect explicit user/launcher override
        if (System.getProperty("jna.boot.library.path") != null) {
            LOG.fine("[JNA/WinML] jna.boot.library.path already set — skipping bootstrap.");
            return;
        }

        try {
            File nativeDir = getNativeDir();
            File target = new File(nativeDir, "jnidispatch.dll");

            String resource = is64Bit() ? JNA_RESOURCE_64 : JNA_RESOURCE_32;

            if (!target.exists() || hasResourceChanged(resource, target)) {
                extractResource(resource, target);
            }

            if (target.exists()) {
                System.setProperty("jna.boot.library.path", nativeDir.getAbsolutePath());
                System.setProperty("jna.nounpack", "true");
                LOG.info("[JNA/WinML] Configured boot library path: " + nativeDir.getAbsolutePath());
            } else {
                LOG.warning("[JNA/WinML] Could not provision jnidispatch.dll — "
                        + "JNA will fall back to default temp extraction.");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[JNA/WinML] Bootstrap failed — "
                    + "JNA will fall back to default temp extraction.", e);
        }
    }

    // ── internals ────────────────────────────────────────────────────

    private static void extractResource(String resource, File target) throws IOException {
        InputStream in = JnaBootstrap.class.getResourceAsStream(resource);
        if (in == null) {
            LOG.warning("[JNA/WinML] Resource not found on classpath: " + resource);
            return;
        }

        File parentDir = target.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Cannot create directory: " + parentDir);
        }

        File tmp = new File(parentDir, "jnidispatch.dll.tmp");
        try (InputStream is = in; FileOutputStream fos = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        }

        if (target.exists() && !target.delete()) {
            tmp.delete();
            LOG.fine("[JNA/WinML] jnidispatch.dll is locked; keeping existing version.");
            return;
        }

        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("Cannot rename temp file to " + target);
        }

        LOG.info("[JNA/WinML] Extracted jnidispatch.dll to " + target.getAbsolutePath());
    }

    /** Re-extract if file sizes differ (covers JNA version upgrades). */
    private static boolean hasResourceChanged(String resource, File target) {
        try (InputStream in = JnaBootstrap.class.getResourceAsStream(resource)) {
            if (in == null) return false;
            long resourceSize = 0;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                resourceSize += n;
            }
            return resourceSize != target.length();
        } catch (IOException e) {
            return true;
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
        return System.getProperty("os.arch", "").contains("64");
    }
}

