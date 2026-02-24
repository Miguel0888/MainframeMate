package de.bund.zrb.mcpserver.browser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Launches a browser process in headless mode and extracts the
 * BiDi WebSocket endpoint URL from stderr output.
 *
 * <p>Inspired by playwright-adapter's {@code BrowserTypeImpl.startProcess()}.
 */
public class BrowserLauncher {

    private static final Pattern WS_PATTERN = Pattern.compile("(ws://\\S+)");

    /**
     * Launch a browser and return the BiDi WebSocket URL.
     * The browser process is started and left running.
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

        // Ensure BiDi is enabled (Firefox-specific)
        boolean hasRemoteDebugging = false;
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                command.add(arg);
                if (arg.contains("--remote-debugging-port")) {
                    hasRemoteDebugging = true;
                }
            }
        }
        if (!hasRemoteDebugging) {
            command.add("--remote-debugging-port");
            command.add("0"); // OS picks a free port
        }

        System.err.println("[BrowserLauncher] Starting: " + command);
        System.err.println("[BrowserLauncher] headless=" + headless);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Check if process is still alive after a short delay
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
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

        System.err.println("[BrowserLauncher] BiDi endpoint: " + wsUrl);
        return new LaunchResult(wsUrl, process);
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

