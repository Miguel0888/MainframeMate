package de.bund.zrb.mcpserver.browser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Launches a browser process (Firefox or Chrome) with BiDi support and extracts the
 * WebSocket endpoint URL.
 *
 * <p>Firefox: reads ws:// URL from stdout.
 * <p>Chrome: uses {@code --remote-debugging-port} and queries
 * {@code /json/version} for the {@code webSocketDebuggerUrl}.
 */
public class BrowserLauncher {

    private static final Pattern WS_PATTERN = Pattern.compile("(ws://\\S+)");
    private static final String SESSION_SUFFIX = "/session";

    /** Default browser paths on Windows. */
    public static final String DEFAULT_FIREFOX_PATH = "C:\\Program Files\\Mozilla Firefox\\firefox.exe";
    public static final String DEFAULT_CHROME_PATH = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";

    /**
     * Browser type enum.
     */
    public enum BrowserType {
        FIREFOX, CHROME;

        /** Detect type from executable path. */
        public static BrowserType fromPath(String path) {
            if (path == null) return FIREFOX;
            String lower = path.toLowerCase();
            if (lower.contains("chrome") || lower.contains("chromium")) return CHROME;
            return FIREFOX;
        }

        /** WebDriver BiDi browser name for session.new. */
        public String bidiName() {
            return this == CHROME ? "chrome" : "firefox";
        }
    }

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
        BrowserType type = BrowserType.fromPath(browserPath);
        if (type == BrowserType.CHROME) {
            return launchChrome(browserPath, extraArgs, headless, timeoutMs);
        } else {
            return launchFirefox(browserPath, extraArgs, headless, timeoutMs);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Firefox
    // ══════════════════════════════════════════════════════════════════

    private static LaunchResult launchFirefox(String browserPath, List<String> extraArgs,
                                              boolean headless, long timeoutMs) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(browserPath);

        if (headless) {
            command.add("--headless");
        }

        command.add("--no-remote");

        boolean hasProfile = false;
        boolean hasRemoteDebugging = false;
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                command.add(arg);
                if (arg.contains("--remote-debugging-port")) hasRemoteDebugging = true;
                if (arg.equals("--profile") || arg.startsWith("--profile=")) hasProfile = true;
            }
        }

        if (!hasProfile) {
            File profileDir = createTempDir("mainframemate-firefox-profile");
            command.add("--profile");
            command.add(profileDir.getAbsolutePath());
        }

        if (!hasRemoteDebugging) {
            command.add("--remote-debugging-port=0");
        }

        System.err.println("[BrowserLauncher] Starting Firefox: " + command);
        System.err.println("[BrowserLauncher] headless=" + headless);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        checkProcessAlive(process);

        // Read stdout looking for ws:// URL
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

        wsUrl = ensureSessionPath(wsUrl);
        System.err.println("[BrowserLauncher] BiDi endpoint: " + wsUrl);
        return new LaunchResult(wsUrl, process, BrowserType.FIREFOX);
    }

    // ══════════════════════════════════════════════════════════════════
    // Chrome
    // ══════════════════════════════════════════════════════════════════

    private static LaunchResult launchChrome(String browserPath, List<String> extraArgs,
                                             boolean headless, long timeoutMs) throws Exception {
        // Find a free port for debugging
        int debugPort = findFreePort();

        List<String> command = new ArrayList<>();
        command.add(browserPath);

        if (headless) {
            command.add("--headless=new");
        }

        // Chrome BiDi requires these flags
        command.add("--remote-debugging-port=" + debugPort);
        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        command.add("--disable-background-networking");
        command.add("--disable-client-side-phishing-detection");
        command.add("--disable-default-apps");
        command.add("--disable-extensions");
        command.add("--disable-sync");
        command.add("--disable-translate");

        // Dedicated user data dir
        boolean hasUserData = false;
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                command.add(arg);
                if (arg.startsWith("--user-data-dir")) hasUserData = true;
            }
        }
        if (!hasUserData) {
            File userDataDir = createTempDir("mainframemate-chrome-profile");
            command.add("--user-data-dir=" + userDataDir.getAbsolutePath());
        }

        // Open about:blank initially
        command.add("about:blank");

        System.err.println("[BrowserLauncher] Starting Chrome: " + command);
        System.err.println("[BrowserLauncher] headless=" + headless + ", debugPort=" + debugPort);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Drain stdout in background (Chrome doesn't print ws:// to stdout)
        Thread drainer = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.err.println("[Chrome] " + line);
                }
            } catch (Exception ignored) {}
        }, "chrome-stdout-drainer");
        drainer.setDaemon(true);
        drainer.start();

        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        checkProcessAlive(process);

        // Query /json/version for the WebSocket URL
        String wsUrl = queryChromeBidiEndpoint(debugPort, timeoutMs);
        if (wsUrl == null) {
            process.destroyForcibly();
            throw new RuntimeException("Failed to get Chrome BiDi WebSocket URL from port " + debugPort);
        }

        System.err.println("[BrowserLauncher] BiDi endpoint: " + wsUrl);
        return new LaunchResult(wsUrl, process, BrowserType.CHROME);
    }

    /**
     * Query Chrome's /json/version endpoint to get the webSocketDebuggerUrl.
     * Chrome exposes this on the debugging port as a JSON response.
     */
    private static String queryChromeBidiEndpoint(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Exception lastError = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                URL url = new URL("http://127.0.0.1:" + port + "/json/version");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    String json = sb.toString();
                    // Extract webSocketDebuggerUrl from JSON
                    // Format: {"webSocketDebuggerUrl":"ws://127.0.0.1:PORT/devtools/browser/UUID"}
                    String wsKey = "\"webSocketDebuggerUrl\"";
                    int idx = json.indexOf(wsKey);
                    if (idx >= 0) {
                        int start = json.indexOf("\"ws://", idx);
                        if (start >= 0) {
                            start++; // skip opening quote
                            int end = json.indexOf("\"", start);
                            if (end > start) {
                                return json.substring(start, end);
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                lastError = e;
            }

            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        System.err.println("[BrowserLauncher] Failed to query Chrome /json/version: "
                + (lastError != null ? lastError.getMessage() : "timeout"));
        return null;
    }

    // ══════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════

    private static String ensureSessionPath(String wsUrl) {
        if (wsUrl.endsWith(SESSION_SUFFIX)) return wsUrl;
        if (wsUrl.endsWith("/")) return wsUrl + "session";
        return wsUrl + SESSION_SUFFIX;
    }

    private static File createTempDir(String name) {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), name);
        if (!tempDir.exists()) tempDir.mkdirs();
        return tempDir;
    }

    private static void checkProcessAlive(Process process) throws Exception {
        if (!process.isAlive()) {
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder errOutput = new StringBuilder();
            String errLine;
            while ((errLine = errReader.readLine()) != null) errOutput.append(errLine).append("\n");
            throw new RuntimeException("Browser process died immediately (exit=" + process.exitValue()
                    + "):\n" + errOutput.toString());
        }
    }

    private static int findFreePort() {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        } catch (Exception e) {
            return 9222; // fallback
        }
    }

    /**
     * Result holder for browser launch.
     */
    public static class LaunchResult {
        public final String wsUrl;
        public final Process process;
        public final BrowserType browserType;

        public LaunchResult(String wsUrl, Process process, BrowserType browserType) {
            this.wsUrl = wsUrl;
            this.process = process;
            this.browserType = browserType;
        }
    }
}


