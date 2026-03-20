package de.bund.zrb.ui.commands;

import de.bund.zrb.event.ApplicationEventBus;
import de.bund.zrb.event.Severity;
import de.bund.zrb.event.StatusMessageEvent;
import de.bund.zrb.service.SettingsService;
import de.bund.zrb.video.MediaRecorder;
import de.bund.zrb.video.MediaRuntimeBootstrap;
import de.bund.zrb.video.RecordingProfile;
import de.bund.zrb.video.impl.libvlc.LibVlcLocator;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Toggle command for starting/stopping video recording of the MainframeMate application window.
 * Supports multiple recording backends (JCodec, FFmpeg/JavaCV, LibVLC) based on settings.
 */
public class ToggleVideoRecordCommand extends ShortcutMenuCommand {

    private final JFrame mainFrame;
    private MediaRecorder recorder;

    public ToggleVideoRecordCommand(JFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public String getId() {
        return "help.video";
    }

    @Override
    public String getLabel() {
        return "\u25C9 Video-Aufnahme starten/stoppen";
    }

    @Override
    public void perform() {
        try {
            if (recorder != null && recorder.isRecording()) {
                recorder.stop();
                ApplicationEventBus.getInstance().publish(
                        new StatusMessageEvent("\uD83C\uDFAC Aufnahme gestoppt", 2000));
                return;
            }

            if (recorder == null) {
                String backend = SettingsService.getInstance().get("video.backend", String.class);
                if (backend == null || backend.trim().isEmpty()) backend = "jcodec";
                backend = backend.trim().toLowerCase(java.util.Locale.ROOT);

                // For JCodec/FFmpeg backends, set target window HWND
                if ("jcodec".equals(backend) || "ffmpeg".equals(backend)) {
                    setTargetWindowFromFrame();
                }

                if ("vlc".equals(backend)) {
                    System.out.println("vlcj? " + LibVlcLocator.isVlcjAvailable());
                    boolean ok = LibVlcLocator.useVlcjDiscovery() || LibVlcLocator.locateAndConfigure();
                    System.out.println("VLC discovered? " + ok);
                    if (!ok) System.out.println("Hint: Ensure 64-bit VLC and correct VLC_PLUGIN_PATH/jna.library.path");
                }

                try {
                    recorder = MediaRuntimeBootstrap.createRecorder();
                    System.out.println("[Video] Recorder erstellt: " + recorder.getClass().getSimpleName());
                } catch (Exception ex) {
                    System.err.println("[Video] Recorder-Init fehlgeschlagen, versuche interaktiven Fallback: " + ex);
                    // For FFmpeg backend: offer interactive download of missing libraries
                    if ("ffmpeg".equals(backend)) {
                        recorder = MediaRuntimeBootstrap.createRecorderInteractive();
                        if (recorder == null) {
                            ApplicationEventBus.getInstance().publish(new StatusMessageEvent(
                                    "FFmpeg-Libs nicht verfügbar – Download abgebrochen", 4000, Severity.ERROR));
                            return;
                        }
                    } else {
                        ex.printStackTrace();
                        ApplicationEventBus.getInstance().publish(new StatusMessageEvent(
                                "Recorder-Init fehlgeschlagen: " + ex.getMessage(), 4000, Severity.ERROR));
                        return;
                    }
                }
            }

            RecordingProfile profile = createProfileFromSettings();
            recorder.start(profile);
            ApplicationEventBus.getInstance().publish(
                    new StatusMessageEvent("\uD83C\uDFAC Aufnahme gestartet", 2000));

        } catch (Exception ex) {
            System.err.println("[Video] Aufnahme konnte nicht umgeschaltet werden: " + ex);
            ex.printStackTrace();
            ApplicationEventBus.getInstance().publish(new StatusMessageEvent(
                    "Aufnahme konnte nicht umgeschaltet werden: " + ex.getMessage(), 4000, Severity.ERROR));
        }
    }

    /**
     * Attempts to resolve the HWND of the main application window and set it on
     * VideoRecordingService so JCodec/FFmpeg backends know which window to capture.
     */
    private void setTargetWindowFromFrame() {
        if (mainFrame == null) {
            System.out.println("[Video] mainFrame ist null – HWND kann nicht ermittelt werden");
            return;
        }
        try {
            Class.forName("com.sun.jna.platform.win32.WinDef");
            // Ensure native peer exists
            if (!mainFrame.isDisplayable()) {
                System.out.println("[Video] Fenster ist nicht displayable – HWND kann nicht ermittelt werden");
                return;
            }
            com.sun.jna.Pointer ptr = com.sun.jna.Native.getComponentPointer(mainFrame);
            if (ptr == null) {
                System.out.println("[Video] getComponentPointer liefert null – versuche Fallback via getWindowPointer");
                // Fallback: use getWindowPointer instead (works for top-level windows)
                ptr = com.sun.jna.Native.getWindowPointer(mainFrame);
            }
            if (ptr == null) {
                System.out.println("[Video] HWND konnte nicht ermittelt werden (ptr=null)");
                return;
            }
            com.sun.jna.platform.win32.WinDef.HWND hwnd =
                    new com.sun.jna.platform.win32.WinDef.HWND(ptr);
            de.bund.zrb.service.VideoRecordingService.getInstance().setTargetWindow(hwnd);
            System.out.println("[Video] HWND erfolgreich gesetzt: " + hwnd);
        } catch (Throwable t) {
            System.out.println("[Video] HWND-Ermittlung fehlgeschlagen: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private RecordingProfile createProfileFromSettings() {
        Path outDir = resolveOutputDir();
        ensureDir(outDir);
        String filename = "capture-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".mp4";
        Path outFile = outDir.resolve(filename);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) screen.getWidth();
        int height = (int) screen.getHeight();

        Integer fps = SettingsService.getInstance().get("video.fps", Integer.class);
        if (fps == null || fps <= 0) fps = 15;

        return RecordingProfile.builder()
                .source("screen://")
                .outputFile(outFile)
                .width(width)
                .height(height)
                .fps(fps)
                .videoCodec("h264")
                .build();
    }

    private Path resolveOutputDir() {
        String baseDirStr = SettingsService.getInstance().get("video.reportsDir", String.class);
        if (baseDirStr != null && !baseDirStr.trim().isEmpty()) {
            return Paths.get(baseDirStr.trim());
        }
        return Paths.get(System.getProperty("user.home"), "Videos");
    }

    private static void ensureDir(Path dir) {
        try { Files.createDirectories(dir); } catch (Exception ignore) {}
    }
}
