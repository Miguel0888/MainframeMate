package de.bund.zrb.service;

import com.sun.jna.platform.win32.WinDef;
import de.bund.zrb.config.VideoConfig;
import de.bund.zrb.video.WindowRecorder;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Kleiner Singleton zum manuellen Start/Stop der Fenster-Videoaufnahme.
 * Nutzt WindowRecorder + VideoConfig (fps, Container/Optionen, Zielordner).
 *
 * Adapted for MainframeMate: BrowserImpl dependency removed.
 * Call setTargetWindow(hwnd) before recording to specify the window to capture.
 */
public final class VideoRecordingService {

    private static final VideoRecordingService INSTANCE = new VideoRecordingService();
    public static VideoRecordingService getInstance() { return INSTANCE; }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private volatile WinDef.HWND targetWindow;
    private volatile WindowRecorder current;

    private VideoRecordingService() {}

    private boolean videoStackAvailable() {
        if (checkClassesOnCurrentLoader()) return true;
        Path libDir = settingsLibDir();
        if (libDir == null) {
            if (Boolean.getBoolean("wd4j.log.video")) System.out.println("[Video] Kein settings libDir verfügbar.");
            return false;
        }
        if (!Files.isDirectory(libDir)) {
            if (Boolean.getBoolean("wd4j.log.video")) System.out.println("[Video] libDir existiert nicht: " + libDir);
            return false;
        }
        try {
            File[] jarFiles = libDir.toFile().listFiles((dir, name) -> name != null && name.toLowerCase().endsWith(".jar"));
            if (jarFiles == null || jarFiles.length == 0) {
                if (Boolean.getBoolean("wd4j.log.video")) System.out.println("[Video] Keine JARs in " + libDir);
                return false;
            }
            if (Boolean.getBoolean("wd4j.log.video")) {
                System.out.println("[Video] Gefundene JARs in libDir:");
                for (File f : jarFiles) System.out.println("[Video]  - " + f.getName());
            }
            List<URL> urls = new ArrayList<URL>();
            for (File f : jarFiles) urls.add(f.toURI().toURL());
            attachUrls(urls);
        } catch (Throwable t) {
            if (Boolean.getBoolean("wd4j.log.video")) System.out.println("[Video] Attach Fehler: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        boolean ok = checkClassesOnCurrentLoader();
        if (Boolean.getBoolean("wd4j.log.video")) System.out.println("[Video] Klassen nach Attach geladen: " + ok);
        return ok;
    }

    private boolean checkClassesOnCurrentLoader() {
        try {
            Class.forName("com.sun.jna.platform.win32.WinDef");
            Class.forName("org.bytedeco.javacv.FFmpegFrameRecorder");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Path settingsLibDir() {
        Path base = resolveSettingsDirSafe();
        if (base == null) return null;
        Path lib = base.resolve("lib");
        try { Files.createDirectories(lib); } catch (Throwable ignore) {}
        return lib;
    }

    private static Path resolveSettingsDirSafe() {
        // MainframeMate settings live in ~/.mainframemate
        try {
            Path p = Paths.get(System.getProperty("user.home"), ".mainframemate");
            if (Files.exists(p)) return p;
            Files.createDirectories(p);
            return p;
        } catch (Throwable ignore) {}
        // Generic fallback
        try {
            Path p = Paths.get(System.getProperty("user.home"), ".wd4j");
            Files.createDirectories(p);
            return p;
        } catch (Throwable t) { return null; }
    }

    private static void attachUrls(List<URL> urls) throws Exception {
        ClassLoader sys = ClassLoader.getSystemClassLoader();
        tryAttachToUrlCl(sys, urls);
        ClassLoader own = VideoRecordingService.class.getClassLoader();
        if (own != sys) tryAttachToUrlCl(own, urls);
        URLClassLoader child = new URLClassLoader(urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(child);
    }

    private static void tryAttachToUrlCl(ClassLoader cl, List<URL> urls) {
        try {
            if (cl instanceof URLClassLoader) {
                Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                for (URL u : urls) addURL.invoke(cl, u);
            }
        } catch (Throwable ignore) {}
    }

    /** Versucht einen Auto-Start, wenn in den Settings aktiviert; idempotent. Lädt NICHT nach. */
    public synchronized void autostartIfConfigured() {
        if (!videoStackAvailable()) return;
        if (!VideoConfig.isEnabled()) return;
        if (isRecording()) return;
        try {
            start();
            if (Boolean.getBoolean("wd4j.log.video")) System.out.println("[Video] Auto-Recording gestartet.");
        } catch (Throwable t) {
            System.err.println("[Video] Auto-Recording Start fehlgeschlagen: " + t.getMessage());
        }
    }

    /** Ziel-Fenster setzen (Integration von außen). */
    public synchronized void setTargetWindow(WinDef.HWND hwnd) { this.targetWindow = hwnd; }

    /** Optionaler Getter (falls benötigt). */
    public WinDef.HWND getTargetWindow() { return targetWindow; }

    /** Läuft eine Aufnahme? */
    public boolean isRecording() { return current != null; }

    /**
     * Startet die Aufnahme. Wenn der Video-Stack fehlt, wird der Nutzer hier (und nur hier)
     * nach einem Laufzeit-Download gefragt. Bei Ablehnung bleibt das Feature deaktiviert.
     */
    public synchronized void start() throws Exception {
        if (!videoStackAvailable()) {
            boolean ok = VideoRuntimeLoader.ensureVideoLibsAvailableInteractively();
            if (!ok && !videoStackAvailable()) {
                throw new IllegalStateException("Video-Stack nicht verfügbar (JARs fehlen oder inkompatibel)");
            }
        }
        if (current != null) return;
        WinDef.HWND hwnd = this.targetWindow;
        if (hwnd == null) {
            throw new IllegalStateException("Kein Ziel-Fenster gesetzt – setTargetWindow() muss vor start() aufgerufen werden.");
        }

        String baseDirStr = VideoConfig.getReportsDir();
        if (baseDirStr == null || baseDirStr.trim().isEmpty()) {
            baseDirStr = Paths.get(System.getProperty("user.home"), "Videos").toString();
        }
        Path baseDir = Paths.get(baseDirStr);
        try { Files.createDirectories(baseDir); } catch (Exception ignore) {}
        String ts = java.time.LocalDateTime.now().format(TS);
        Path outfile = baseDir.resolve("video-" + ts + ".mkv");

        int fps = Math.max(1, VideoConfig.getFps());
        WindowRecorder wr = new WindowRecorder(hwnd, outfile, fps);
        wr.start();

        current = wr;
    }

    /** Stoppt die laufende Aufnahme (no-op, wenn keine aktiv). */
    public synchronized void stop() {
        WindowRecorder wr = current;
        current = null;
        if (wr != null) {
            try { wr.stop(); } catch (Throwable ignore) {}
        }
    }

    /** Startet oder stoppt je nach aktuellem Zustand. */
    public synchronized void toggle() throws Exception {
        if (isRecording()) stop(); else start();
    }

    /** Effektive Datei der aktuellen Aufnahme (falls laufend). */
    public Path getCurrentOutput() {
        WindowRecorder wr = current;
        return (wr != null) ? wr.getEffectiveOutput() : null;
    }

    public static boolean quickCheckAvailable() {
        try {
            Class.forName("com.sun.jna.platform.win32.WinDef");
            Class.forName("org.bytedeco.javacv.FFmpegFrameRecorder");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
