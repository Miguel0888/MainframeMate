package de.bund.zrb.winproxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes PowerShell commands and returns the trimmed stdout output.
 * <p>
 * Used to dynamically resolve the PAC URL from the Windows registry or other sources
 * on machines where {@code reg.exe} cannot access the required keys (e.g. GPO-managed
 * proxy settings on hardened Windows 11).
 */
final class ScriptRunner {

    private static final Logger LOG = Logger.getLogger(ScriptRunner.class.getName());
    private static final int TIMEOUT_SECONDS = 10;

    private ScriptRunner() {}

    /**
     * Executes a PowerShell one-liner / command and returns the trimmed stdout.
     * <p>
     * Stderr is drained on a daemon thread to prevent deadlock but discarded.
     * If the command fails or produces no output, {@code null} is returned.
     *
     * @param command the PowerShell command to execute (e.g.
     *        {@code "(Get-ItemProperty -Path 'HKCU:\\...').AutoConfigURL"})
     * @return trimmed stdout output, or {@code null} on error/empty output
     */
    static String executePowerShell(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", command
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Drain stderr on daemon thread to avoid deadlock
            Thread stderrDrainer = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    while (r.readLine() != null) { /* discard */ }
                } catch (Exception ignored) { }
            }, "pac-url-stderr-drain");
            stderrDrainer.setDaemon(true);
            stderrDrainer.start();

            StringBuilder stdout = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stdout.append(line).append('\n');
                }
            }

            process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String result = stdout.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[WinProxy] PowerShell command failed: " + e.getMessage(), e);
            return null;
        }
    }
}
