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
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and sets up ONNX Runtime DirectML native libraries at runtime.
 * <p>
 * The standard Maven artifact {@code onnxruntime} only includes CPU support.
 * For DirectML we need native DLLs from <b>two</b> NuGet packages:
 * <ul>
 *   <li>{@code Microsoft.ML.OnnxRuntime.DirectML} → {@code onnxruntime.dll} (DirectML-enabled)</li>
 *   <li>{@code Microsoft.ML.OnnxRuntime} → {@code onnxruntime_providers_shared.dll}</li>
 * </ul>
 * Plus the JNI bridge {@code onnxruntime4j_jni.dll} from the Maven JAR on the classpath.
 * <p>
 * Everything is cached in {@code ~/.mainframemate/onnxruntime-dml/<version>/}.
 * <p>
 * <b>MUST</b> be called before any {@code OrtEnvironment} usage.
 */
final class DirectMlNativeLoader {

    private static final Logger LOG = Logger.getLogger(DirectMlNativeLoader.class.getName());

    private static final String ORT_VERSION = "1.19.2";

    /** DirectML NuGet → contains onnxruntime.dll (built with DML support). */
    private static final String NUGET_DML_URL =
            "https://www.nuget.org/api/v2/package/Microsoft.ML.OnnxRuntime.DirectML/" + ORT_VERSION;

    /** Base NuGet → contains onnxruntime_providers_shared.dll. */
    private static final String NUGET_BASE_URL =
            "https://www.nuget.org/api/v2/package/Microsoft.ML.OnnxRuntime/" + ORT_VERSION;

    private static final String NUGET_NATIVE_PREFIX = "runtimes/win-x64/native/";

    /** Files that MUST be present for DirectML to work. */
    private static final String[] REQUIRED_DLLS = {
            "onnxruntime.dll",
            "onnxruntime_providers_shared.dll",
            "onnxruntime4j_jni.dll"
    };

    private static final String JNI_DLL_NAME = "onnxruntime4j_jni.dll";
    private static final String JNI_RESOURCE_PATH = "/ai/onnxruntime/native/win-x64/" + JNI_DLL_NAME;

    private static volatile boolean initialized = false;

    interface ProgressCallback {
        void onMessage(String message);
    }

    private DirectMlNativeLoader() {}

    static synchronized void ensureAvailable(ProgressCallback callback) throws IOException {
        if (initialized) {
            return;
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!os.contains("win") || (!arch.contains("amd64") && !arch.contains("x86_64"))) {
            throw new IOException("DirectML ist nur auf Windows x64 verfügbar. "
                    + "Aktuelles System: " + os + "/" + arch);
        }

        Path dmlDir = getDmlDirectory();
        Files.createDirectories(dmlDir);

        // 1) onnxruntime.dll from DirectML NuGet
        if (needsFile(dmlDir, "onnxruntime.dll")) {
            msg(callback, "\u2B07 Lade DirectML onnxruntime.dll von NuGet...");
            extractDllFromNuGet(NUGET_DML_URL, "onnxruntime.dll", dmlDir, callback);
        }

        // 2) onnxruntime_providers_shared.dll from base NuGet
        if (needsFile(dmlDir, "onnxruntime_providers_shared.dll")) {
            msg(callback, "\u2B07 Lade onnxruntime_providers_shared.dll von NuGet...");
            extractDllFromNuGet(NUGET_BASE_URL, "onnxruntime_providers_shared.dll", dmlDir, callback);
        }

        // 3) JNI bridge from Maven JAR on classpath
        if (needsFile(dmlDir, JNI_DLL_NAME)) {
            msg(callback, "\u2699 Extrahiere JNI-Bridge aus Maven-JAR...");
            extractJniBridge(dmlDir);
        }

        // Verify
        for (String dll : REQUIRED_DLLS) {
            Path f = dmlDir.resolve(dll);
            if (!Files.exists(f) || Files.size(f) == 0) {
                throw new IOException("DirectML DLL fehlt: " + dll + "\nPfad: " + dmlDir);
            }
        }

        String nativePath = dmlDir.toAbsolutePath().toString();
        System.setProperty("onnxruntime.native.path", nativePath);
        LOG.info("onnxruntime.native.path = " + nativePath);

        msg(callback, "\u2705 DirectML bereit (" + nativePath + ")");
        initialized = true;
    }

    // ── Extract a single DLL from a NuGet package ────────────

    private static void extractDllFromNuGet(String nugetUrl, String targetDll,
                                            Path targetDir, ProgressCallback callback)
            throws IOException {

        LOG.info("Downloading NuGet: " + nugetUrl + " (looking for " + targetDll + ")");
        HttpURLConnection conn = openWithRedirects(nugetUrl);

        try {
            if (conn.getResponseCode() != 200) {
                throw new IOException("NuGet-Download fehlgeschlagen: HTTP " + conn.getResponseCode());
            }

            String expectedEntry = NUGET_NATIVE_PREFIX + targetDll;
            boolean found = false;

            try (ZipInputStream zis = new ZipInputStream(
                    new BufferedInputStream(conn.getInputStream(), 65536))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName().replace('\\', '/');

                    if (!entry.isDirectory() && name.equals(expectedEntry)) {
                        Path outFile = targetDir.resolve(targetDll);
                        LOG.info("Extracting: " + name);
                        msg(callback, "  \u2192 " + targetDll);

                        try (OutputStream os = new BufferedOutputStream(
                                Files.newOutputStream(outFile), 65536)) {
                            byte[] buf = new byte[65536];
                            int n;
                            while ((n = zis.read(buf)) > 0) {
                                os.write(buf, 0, n);
                            }
                        }
                        found = true;
                        break;
                    }
                    zis.closeEntry();
                }
            }

            if (!found) {
                throw new IOException(targetDll + " nicht im NuGet-Paket gefunden"
                        + " (erwartet: " + expectedEntry + ")");
            }
        } finally {
            conn.disconnect();
        }
    }

    // ── JNI Bridge from classpath ────────────────────────────

    private static void extractJniBridge(Path targetDir) throws IOException {
        InputStream is = DirectMlNativeLoader.class.getResourceAsStream(JNI_RESOURCE_PATH);
        if (is == null) {
            throw new IOException("JNI-Bridge nicht auf dem Classpath: " + JNI_RESOURCE_PATH);
        }

        Path jniFile = targetDir.resolve(JNI_DLL_NAME);
        try (InputStream in = is;
             OutputStream os = new BufferedOutputStream(Files.newOutputStream(jniFile), 65536)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        LOG.info("JNI bridge: " + jniFile + " (" + Files.size(jniFile) + " bytes)");
    }

    // ── HTTP with manual redirect handling ───────────────────

    private static HttpURLConnection openWithRedirects(String urlStr) throws IOException {
        for (int i = 0; i < 5; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(300_000);
            conn.setRequestProperty("User-Agent", "MainframeMate/5.4.0");

            int code = conn.getResponseCode();
            if (code == 200) return conn;

            if (code == 301 || code == 302 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) throw new IOException("Redirect ohne Location bei HTTP " + code);
                urlStr = loc;
                continue;
            }

            conn.disconnect();
            throw new IOException("HTTP " + code + " von " + urlStr);
        }
        throw new IOException("Zu viele Redirects für " + urlStr);
    }

    // ── Helpers ──────────────────────────────────────────────

    private static boolean needsFile(Path dir, String name) throws IOException {
        Path f = dir.resolve(name);
        return !Files.exists(f) || Files.size(f) == 0;
    }

    private static Path getDmlDirectory() {
        return Paths.get(System.getProperty("user.home"),
                ".mainframemate", "onnxruntime-dml", ORT_VERSION);
    }

    private static void msg(ProgressCallback cb, String message) {
        LOG.info(message);
        if (cb != null) cb.onMessage(message);
    }
}
