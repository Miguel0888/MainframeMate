package de.bund.zrb.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and sets up ONNX Runtime DirectML native libraries at runtime.
 * <p>
 * The standard Maven artifact {@code com.microsoft.onnxruntime:onnxruntime} only includes
 * CPU support. For DirectML GPU acceleration, the native DLLs from the NuGet package
 * {@code Microsoft.ML.OnnxRuntime.DirectML} are required.
 * <p>
 * This class:
 * <ol>
 *   <li>Downloads the NuGet package (a ZIP file) from nuget.org</li>
 *   <li>Extracts {@code onnxruntime.dll}, {@code onnxruntime_providers_shared.dll},
 *       {@code onnxruntime_providers_dml.dll} from {@code runtimes/win-x64/native/}</li>
 *   <li>Extracts the JNI bridge {@code onnxruntime4j_jni.dll} from the Maven JAR on the classpath</li>
 *   <li>Sets {@code onnxruntime.native.path} so ORT loads the DirectML-enabled binaries</li>
 * </ol>
 * <p>
 * <b>MUST</b> be called before any {@code OrtEnvironment} usage (static initializer loads native libs).
 */
final class DirectMlNativeLoader {

    private static final Logger LOG = Logger.getLogger(DirectMlNativeLoader.class.getName());

    private static final String ORT_VERSION = "1.19.2";

    /** NuGet V2 API — returns 302 redirect to CDN. */
    private static final String NUGET_PACKAGE_URL =
            "https://www.nuget.org/api/v2/package/Microsoft.ML.OnnxRuntime.DirectML/" + ORT_VERSION;

    /** Prefix inside the NuGet ZIP for win-x64 native DLLs. */
    private static final String NUGET_NATIVE_PREFIX = "runtimes/win-x64/native/";

    /** DLLs required from the NuGet package. */
    private static final String[] REQUIRED_NUGET_DLLS = {
            "onnxruntime.dll",
            "onnxruntime_providers_shared.dll",
            "onnxruntime_providers_dml.dll"
    };

    /** JNI bridge DLL from the Maven JAR. */
    private static final String JNI_DLL_NAME = "onnxruntime4j_jni.dll";

    /** Resource path inside the Maven JAR for the JNI bridge. */
    private static final String JNI_RESOURCE_PATH =
            "/ai/onnxruntime/native/win-x64/" + JNI_DLL_NAME;

    private static volatile boolean initialized = false;

    /** Callback for progress messages (can be null). */
    interface ProgressCallback {
        void onMessage(String message);
    }

    private DirectMlNativeLoader() {}

    /**
     * Ensures DirectML native libraries are available.
     * Downloads them from NuGet if not already cached.
     * <p>
     * Sets the system property {@code onnxruntime.native.path} so that
     * {@code OrtEnvironment} loads the DirectML-enabled binaries.
     *
     * @param callback optional progress callback (may be {@code null})
     * @throws IOException if download or extraction fails
     */
    static synchronized void ensureAvailable(ProgressCallback callback) throws IOException {
        if (initialized) {
            return;
        }

        // DirectML is Windows x64 only
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!os.contains("win") || (!arch.contains("amd64") && !arch.contains("x86_64"))) {
            throw new IOException("DirectML ist nur auf Windows x64 verfügbar. "
                    + "Aktuelles System: " + os + "/" + arch);
        }

        Path dmlDir = getDmlDirectory();
        Files.createDirectories(dmlDir);

        // Check if all DLLs are present and non-empty
        boolean needsDownload = false;
        for (String dll : REQUIRED_NUGET_DLLS) {
            Path f = dmlDir.resolve(dll);
            if (!Files.exists(f) || Files.size(f) == 0) {
                needsDownload = true;
                break;
            }
        }

        boolean needsJni = !Files.exists(dmlDir.resolve(JNI_DLL_NAME))
                || Files.size(dmlDir.resolve(JNI_DLL_NAME)) == 0;

        if (needsDownload) {
            msg(callback, "\u2B07 Lade ONNX Runtime DirectML (" + ORT_VERSION + ") von NuGet herunter...");
            downloadAndExtractNuGet(dmlDir, callback);
        }

        if (needsJni) {
            msg(callback, "\u2699 Extrahiere JNI-Bridge aus Maven-JAR...");
            extractJniBridge(dmlDir);
        }

        // Verify all files exist
        for (String dll : REQUIRED_NUGET_DLLS) {
            Path f = dmlDir.resolve(dll);
            if (!Files.exists(f) || Files.size(f) == 0) {
                throw new IOException("DirectML DLL fehlt nach Download: " + dll
                        + "\nPfad: " + dmlDir);
            }
        }
        Path jniFile = dmlDir.resolve(JNI_DLL_NAME);
        if (!Files.exists(jniFile) || Files.size(jniFile) == 0) {
            throw new IOException("JNI-Bridge fehlt: " + JNI_DLL_NAME
                    + "\nStelle sicher, dass die Maven-Dependency 'onnxruntime' vorhanden ist.");
        }

        // Set system property BEFORE OrtEnvironment is loaded
        String nativePath = dmlDir.toAbsolutePath().toString();
        System.setProperty("onnxruntime.native.path", nativePath);
        LOG.info("onnxruntime.native.path = " + nativePath);

        msg(callback, "\u2705 DirectML native libraries bereit: " + nativePath);
        initialized = true;
    }

    // ── NuGet Download & Extraction ──────────────────────────

    private static void downloadAndExtractNuGet(Path targetDir, ProgressCallback callback)
            throws IOException {

        LOG.info("Downloading NuGet package: " + NUGET_PACKAGE_URL);
        HttpURLConnection conn = openConnectionWithRedirects(NUGET_PACKAGE_URL);

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new IOException("NuGet-Download fehlgeschlagen: HTTP " + code
                    + "\nURL: " + NUGET_PACKAGE_URL);
        }

        long contentLength = conn.getContentLengthLong();
        if (contentLength > 0) {
            msg(callback, "  Paketgröße: " + formatSize(contentLength));
        }

        Set<String> needed = new HashSet<String>(Arrays.asList(REQUIRED_NUGET_DLLS));
        int extracted = 0;

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(conn.getInputStream(), 65536))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');

                if (!entryName.startsWith(NUGET_NATIVE_PREFIX) || entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String fileName = entryName.substring(NUGET_NATIVE_PREFIX.length());

                // Only extract DLLs we need (or any .dll in the native dir)
                if (needed.contains(fileName) || fileName.endsWith(".dll")) {
                    Path outFile = targetDir.resolve(fileName);
                    LOG.info("Extracting: " + fileName);
                    msg(callback, "  \u2192 " + fileName);

                    try (OutputStream os = new BufferedOutputStream(
                            Files.newOutputStream(outFile), 65536)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            os.write(buf, 0, n);
                        }
                    }
                    extracted++;
                }
                zis.closeEntry();
            }
        } finally {
            conn.disconnect();
        }

        LOG.info("Extracted " + extracted + " files to " + targetDir);

        if (extracted == 0) {
            throw new IOException("Keine DLLs aus NuGet-Paket extrahiert. "
                    + "Erwarteter Pfad im ZIP: " + NUGET_NATIVE_PREFIX);
        }
    }

    // ── JNI Bridge Extraction ────────────────────────────────

    private static void extractJniBridge(Path targetDir) throws IOException {
        Path jniFile = targetDir.resolve(JNI_DLL_NAME);

        InputStream is = DirectMlNativeLoader.class.getResourceAsStream(JNI_RESOURCE_PATH);
        if (is == null) {
            throw new IOException("JNI-Bridge '" + JNI_DLL_NAME + "' nicht auf dem Classpath gefunden.\n"
                    + "Erwartet unter: " + JNI_RESOURCE_PATH + "\n"
                    + "Prüfe, ob die Maven-Dependency 'com.microsoft.onnxruntime:onnxruntime:"
                    + ORT_VERSION + "' vorhanden ist.");
        }

        LOG.info("Extracting JNI bridge from classpath: " + JNI_RESOURCE_PATH);
        try (InputStream in = is;
             OutputStream os = new BufferedOutputStream(Files.newOutputStream(jniFile), 65536)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }

        LOG.info("JNI bridge written to: " + jniFile + " (" + Files.size(jniFile) + " bytes)");
    }

    // ── HTTP Helpers ─────────────────────────────────────────

    private static HttpURLConnection openConnectionWithRedirects(String urlStr)
            throws IOException {
        int maxRedirects = 5;
        for (int i = 0; i < maxRedirects; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setInstanceFollowRedirects(false); // Handle manually for HTTPS→HTTPS
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(300_000); // 5 min for large downloads
            conn.setRequestProperty("User-Agent", "MainframeMate/5.4.0");

            int code = conn.getResponseCode();
            if (code == 200) {
                return conn;
            }
            if (code == 301 || code == 302 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("Redirect ohne Location-Header bei HTTP " + code);
                }
                LOG.fine("Redirect " + code + " → " + location);
                urlStr = location;
                continue;
            }

            conn.disconnect();
            throw new IOException("HTTP " + code + " von " + urlStr);
        }
        throw new IOException("Zu viele Redirects (>" + maxRedirects + ") für " + urlStr);
    }

    // ── Misc ─────────────────────────────────────────────────

    private static Path getDmlDirectory() {
        return Paths.get(System.getProperty("user.home"),
                ".mainframemate", "onnxruntime-dml", ORT_VERSION);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static void msg(ProgressCallback cb, String message) {
        LOG.info(message);
        if (cb != null) {
            cb.onMessage(message);
        }
    }
}
