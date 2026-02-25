package de.bund.zrb.chrome;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Quick integration test: launches Chrome automatically (or connects to an already-running instance),
 * injects the BiDi mapper, and sends simple BiDi commands.
 * <p>
 * Usage:
 *   Option A: Just run this class – it will start Chrome on port 9222.
 *   Option B: Start Chrome manually first: chrome.exe --remote-debugging-port=9222
 *             Then run this class.
 *   Arg 1 (optional): port (default: 9222)
 *   Arg 2 (optional): chrome path (default: auto-detect)
 */
public class ChromeBidiTest {

    private static final String DEFAULT_CHROME_PATH = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";

    public static void main(String[] args) throws Exception {
        int port = 9222;
        String chromePath = DEFAULT_CHROME_PATH;

        if (args.length > 0) port = Integer.parseInt(args[0]);
        if (args.length > 1) chromePath = args[1];

        System.out.println("=== Chrome BiDi Mapper Test ===");

        // Try to get CDP URL from an already-running Chrome
        String cdpWsUrl = getCdpWebSocketUrl(port);

        Process chromeProcess = null;
        if (cdpWsUrl == null) {
            System.out.println("No Chrome instance found on port " + port + " – launching Chrome...");
            chromeProcess = launchChrome(chromePath, port);
            // Wait and retry
            for (int i = 0; i < 10; i++) {
                Thread.sleep(1000);
                cdpWsUrl = getCdpWebSocketUrl(port);
                if (cdpWsUrl != null) break;
                System.out.println("  Waiting for Chrome to start... (" + (i + 1) + "/10)");
            }
        }

        if (cdpWsUrl == null) {
            System.err.println("ERROR: Could not get webSocketDebuggerUrl from http://127.0.0.1:" + port + "/json/version");
            System.err.println("Make sure Chrome is installed at: " + chromePath);
            if (chromeProcess != null) chromeProcess.destroyForcibly();
            System.exit(1);
        }
        System.out.println("CDP WebSocket URL: " + cdpWsUrl);

        // Create the ChromeBidiWebSocketImpl
        System.out.println("\nCreating ChromeBidiWebSocketImpl...");
        ChromeBidiWebSocketImpl bidiWs = new ChromeBidiWebSocketImpl(cdpWsUrl, true);

        // Register a listener for incoming BiDi responses
        bidiWs.onFrameReceived(frame -> {
            System.out.println("\n[BiDi Response] " + frame.text());
        });

        // Send a simple session.status command
        System.out.println("\nSending BiDi session.status command...");
        bidiWs.send("{\"id\":1,\"method\":\"session.status\",\"params\":{}}");
        Thread.sleep(3000);

        // Send browsingContext.getTree
        System.out.println("\nSending BiDi browsingContext.getTree command...");
        bidiWs.send("{\"id\":2,\"method\":\"browsingContext.getTree\",\"params\":{}}");
        Thread.sleep(3000);

        // Navigate to a page
        System.out.println("\nSending BiDi browsingContext.navigate to example.com...");
        bidiWs.send("{\"id\":3,\"method\":\"browsingContext.navigate\",\"params\":{\"url\":\"https://example.com\",\"wait\":\"interactive\"}}");
        Thread.sleep(5000);

        System.out.println("\n=== Test complete! ===");
        bidiWs.close();

        if (chromeProcess != null) {
            System.out.println("Killing Chrome...");
            chromeProcess.destroyForcibly();
        }
        System.exit(0);
    }

    private static Process launchChrome(String chromePath, int port) throws Exception {
        File chromeExe = new File(chromePath);
        if (!chromeExe.exists()) {
            throw new RuntimeException("Chrome not found at: " + chromePath);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(chromePath);
        cmd.add("--remote-debugging-port=" + port);
        cmd.add("--no-first-run");
        cmd.add("--no-default-browser-check");
        cmd.add("--disable-extensions");
        cmd.add("--user-data-dir=" + System.getProperty("java.io.tmpdir") + "\\chrome-bidi-test-profile");
        cmd.add("about:blank");

        System.out.println("Starting Chrome: " + cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Drain stdout in background
        Thread drainer = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println("[Chrome] " + line);
                }
            } catch (Exception ignored) {}
        }, "chrome-stdout-drainer");
        drainer.setDaemon(true);
        drainer.start();

        return process;
    }

    private static String getCdpWebSocketUrl(int port) {
        try {
            URL url = new URL("http://127.0.0.1:" + port + "/json/version");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String json = sb.toString();
                String wsKey = "\"webSocketDebuggerUrl\"";
                int idx = json.indexOf(wsKey);
                if (idx >= 0) {
                    int start = json.indexOf("\"ws://", idx);
                    if (start >= 0) {
                        start++;
                        int end = json.indexOf("\"", start);
                        if (end > start) return json.substring(start, end);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}

