package de.bund.zrb.mcpserver.browser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Launches a Firefox browser process with BiDi support and extracts the
 * WebSocket endpoint URL from stdout/stderr output.
 *
 * <p>Firefox requires {@code --no-remote} and a dedicated {@code --profile}
 * to start a new instance even when another Firefox is already running.
 */
public class BrowserLauncher {

    private static final Pattern WS_PATTERN = Pattern.compile("(ws://\\S+)");
    private static final String SESSION_SUFFIX = "/session";

    /**
     * Launch a browser and return the BiDi WebSocket URL (with /session suffix).
     */
    public static String launch(String browserPath, List<String> extraArgs,
                                boolean headless, long timeoutMs) throws Exception {
        return launchWithProcess(browserPath, extraArgs, headless, timeoutMs).wsUrl;
    }

    /**
     * Launch a browser and return both the WebSocket URL and the Process handle.
     */
    public static LaunchResult launchWithProcess(String browserPath, List<String> extraArgs,
                                                  boolean headless, long timeoutMs) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(browserPath);

        if (headless) {
            command.add("--headless");
        }

        // --no-remote is required so Firefox starts a new process
        // instead of delegating to an already-running instance (which exits immediately)
        command.add("--no-remote");

        // Dedicated profile so we don't interfere with the user's normal Firefox
        boolean hasProfile = false;
        boolean hasRemoteDebugging = false;
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                command.add(arg);
                if (arg.contains("--remote-debugging-port")) {
                    hasRemoteDebugging = true;
                }
                if (arg.equals("--profile") || arg.startsWith("--profile=")) {
                    hasProfile = true;
                }
            }
        }

        if (!hasProfile) {
            File profileDir = createTempProfile();
            command.add("--profile");
            command.add(profileDir.getAbsolutePath());
        }

        if (!hasRemoteDebugging) {
            // Use port 0 so the OS picks a free port
            command.add("--remote-debugging-port=0");
        }

        System.err.println("[BrowserLauncher] Starting: " + command);
        System.err.println("[BrowserLauncher] headless=" + headless);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Give Firefox a moment to start
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        if (!process.isAlive()) {
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder errOutput = new StringBuilder();
            String errLine;
            while ((errLine = errReader.readLine()) != null) {
                errOutput.append(errLine).append("\n");
            }
            throw new RuntimeException("Browser process died immediately (exit=" + process.exitValue()
                    + "):\n" + errOutput.toString());
        }

        // Read stdout/stderr looking for the WebSocket URL
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        long deadline = System.currentTimeMillis() + timeoutMs;
        String wsUrl = null;

        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line == null) break;
                System.err.println("[Browser] " + line);

                Matcher m = WS_PATTERN.matcher(line);
                if (m.find()) {
                    wsUrl = m.group(1);
                    break;
                }
            } else {
                Thread.sleep(100);
            }
        }

        if (wsUrl == null) {
            process.destroyForcibly();
            throw new RuntimeException("Failed to find BiDi WebSocket URL within " + timeoutMs + "ms");
        }

        // Ensure the URL ends with /session
        wsUrl = ensureSessionPath(wsUrl);

        System.err.println("[BrowserLauncher] BiDi endpoint: " + wsUrl);
        return new LaunchResult(wsUrl, process);
    }

    /**
     * Ensure the WebSocket URL has the /session path suffix.
     */
    private static String ensureSessionPath(String wsUrl) {
        if (wsUrl.endsWith(SESSION_SUFFIX)) {
            return wsUrl;
        }
        if (wsUrl.endsWith("/")) {
            return wsUrl + "session";
        }
        return wsUrl + SESSION_SUFFIX;
    }

    /**
     * Create a temporary Firefox profile directory.
     * This ensures each launch gets a clean, isolated profile.
     */
    private static File createTempProfile() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "mainframemate-firefox-profile");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        return tempDir;
    }

    /**
     * Simple holder for the launch result.
     */
    public static class LaunchResult {
        public final String wsUrl;
        public final Process process;

        public LaunchResult(String wsUrl, Process process) {
            this.wsUrl = wsUrl;
            this.process = process;
        }
    }
}

