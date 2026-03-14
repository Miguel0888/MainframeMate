package de.bund.zrb.dosbox.launcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates and launches native DOSBox to run DOS executables (EXE/COM/BAT).
 * Falls back to common installation paths on Windows.
 *
 * Usage:
 *   NativeDosLauncher launcher = new NativeDosLauncher();
 *   launcher.launchExe("C:\\Games\\DOOM2", "DOOM2.EXE");
 */
public class NativeDosLauncher {

    /** Listener for launch events (status messages, errors). */
    public interface LaunchListener {
        void onMessage(String msg);
        void onError(String msg);
        void onProcessStarted(Process process);
        void onProcessFinished(int exitCode);
    }

    private String dosboxPath;
    private LaunchListener listener;

    public NativeDosLauncher() {
        this.dosboxPath = findDosBox();
    }

    public void setListener(LaunchListener listener) {
        this.listener = listener;
    }

    /** Check if DOSBox is available on this system. */
    public boolean isAvailable() {
        return dosboxPath != null;
    }

    /** Get the path to the found DOSBox executable. */
    public String getDosboxPath() {
        return dosboxPath;
    }

    /** Override the DOSBox path manually. */
    public void setDosboxPath(String path) {
        if (new File(path).exists()) {
            this.dosboxPath = path;
        }
    }

    /**
     * Launch a DOS executable in the given host directory.
     *
     * @param hostDir  The host directory containing the EXE (will be mounted as C:)
     * @param exeName  The EXE filename to run (e.g., "DOOM2.EXE")
     * @return true if launch was initiated
     */
    public boolean launchExe(String hostDir, String exeName) {
        if (dosboxPath == null) {
            if (listener != null) {
                listener.onError("DOSBox nicht gefunden! Bitte DOSBox installieren.\n"
                        + "Download: https://www.dosbox.com/download.php?main=1\n"
                        + "Oder DOSBox-Staging: https://dosbox-staging.github.io/");
            }
            return false;
        }

        File dir = new File(hostDir);
        if (!dir.isDirectory()) {
            if (listener != null) listener.onError("Verzeichnis nicht gefunden: " + hostDir);
            return false;
        }

        File exe = new File(dir, exeName);
        if (!exe.exists()) {
            if (listener != null) listener.onError("Datei nicht gefunden: " + exe.getAbsolutePath());
            return false;
        }

        // Build DOSBox command line
        // DOSBox syntax: dosbox [exe] -c "MOUNT C dir" -c "C:" -c "exe" -fullscreen
        List<String> cmd = new ArrayList<>();
        cmd.add(dosboxPath);

        // Mount the directory as C: and run the exe
        cmd.add("-c");
        cmd.add("MOUNT C \"" + dir.getAbsolutePath().replace("\\", "\\\\") + "\"");
        cmd.add("-c");
        cmd.add("C:");
        cmd.add("-c");
        cmd.add(exeName);

        // Auto-exit when program ends
        cmd.add("-c");
        cmd.add("EXIT");

        if (listener != null) {
            listener.onMessage("Starte: " + dosboxPath);
            listener.onMessage("Verzeichnis: " + dir.getAbsolutePath());
            listener.onMessage("Programm: " + exeName);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            if (listener != null) listener.onProcessStarted(process);

            // Monitor process on background thread
            Thread monitor = new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    if (listener != null) listener.onProcessFinished(exitCode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "DOSBox-Monitor");
            monitor.setDaemon(true);
            monitor.start();

            return true;
        } catch (IOException e) {
            if (listener != null) listener.onError("Fehler beim Starten von DOSBox: " + e.getMessage());
            return false;
        }
    }

    /**
     * Launch a DOS executable directly (exe path is absolute or relative to hostDir).
     */
    public boolean launchExe(File exeFile) {
        return launchExe(exeFile.getParent(), exeFile.getName());
    }

    // ── DOSBox discovery ────────────────────────────────────

    private String findDosBox() {
        // 1. Check PATH
        String pathResult = findInPath("dosbox");
        if (pathResult != null) return pathResult;

        // 2. Check common Windows installation paths
        String[] candidates = {
                // DOSBox classic
                "C:\\Program Files (x86)\\DOSBox-0.74-3\\DOSBox.exe",
                "C:\\Program Files (x86)\\DOSBox-0.74\\DOSBox.exe",
                "C:\\Program Files\\DOSBox-0.74-3\\DOSBox.exe",
                "C:\\Program Files\\DOSBox-0.74\\DOSBox.exe",
                // DOSBox-X
                "C:\\Program Files\\DOSBox-X\\dosbox-x.exe",
                "C:\\Program Files (x86)\\DOSBox-X\\dosbox-x.exe",
                // DOSBox Staging
                "C:\\Program Files\\dosbox-staging\\dosbox.exe",
                "C:\\Program Files (x86)\\dosbox-staging\\dosbox.exe",
                // Portable / project-local
                "dosbox\\dosbox.exe",
                "tools\\dosbox\\dosbox.exe",
                // Chocolatey / Scoop installs
                System.getenv("LOCALAPPDATA") + "\\Programs\\DOSBox\\DOSBox.exe",
                System.getenv("USERPROFILE") + "\\scoop\\apps\\dosbox\\current\\DOSBox.exe",
                System.getenv("USERPROFILE") + "\\scoop\\apps\\dosbox-staging\\current\\dosbox.exe",
                System.getenv("USERPROFILE") + "\\scoop\\apps\\dosbox-x\\current\\dosbox-x.exe",
        };

        for (String candidate : candidates) {
            if (candidate != null) {
                File f = new File(candidate);
                if (f.exists() && f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        }

        return null;
    }

    private String findInPath(String executable) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        String[] exts = { ".exe", ".com", ".bat", "" };

        for (String dir : pathEnv.split(File.pathSeparator)) {
            for (String ext : exts) {
                File f = new File(dir, executable + ext);
                if (f.exists() && f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * Returns a user-friendly status message about DOSBox availability.
     */
    public String getStatusMessage() {
        if (dosboxPath != null) {
            return "DOSBox gefunden: " + dosboxPath;
        }
        return "DOSBox NICHT gefunden – Bitte installieren: https://www.dosbox.com";
    }
}

