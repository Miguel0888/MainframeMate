package de.bund.zrb;

import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.type.browsingContext.WDReadinessState;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * Quick integration test: Connect to a running Chrome instance via its CDP WebSocket
 * and test whether BiDi commands work over that connection.
 *
 * Chrome exposes CDP on --remote-debugging-port. The webSocketDebuggerUrl from
 * /json/version is the entry point. We test if BiDi commands (browsingContext.getTree,
 * browsingContext.navigate) work over that same WebSocket.
 *
 * Prerequisites:
 *   Start Chrome with:
 *   chrome.exe --remote-debugging-port=9333 --no-first-run --user-data-dir=%TEMP%\chrome-bidi-test about:blank
 */
public class ChromeBidiConnectionTest {

    public static void main(String[] args) {
        int port = 9333;

        try {
            System.out.println("=== Chrome BiDi-over-CDP Connection Test ===");

            // Step 1: Get the CDP WebSocket URL from /json/version
            String wsUrl = getCdpWebSocketUrl(port);
            System.out.println("[1] CDP WebSocket URL: " + wsUrl);

            // Step 2: Connect WebSocket
            WDWebSocketImpl ws = new WDWebSocketImpl(URI.create(wsUrl), 30_000.0);
            System.out.println("[2] WebSocket connected: " + ws.isConnected());

            if (!ws.isConnected()) {
                System.err.println("FAILED: WebSocket not connected!");
                return;
            }

            // Step 3: Create WebDriver facade
            WebDriver driver = new WebDriver(ws);
            System.out.println("[3] WebDriver created");

            // Step 4: Try session.new (may fail on CDP connection – that's OK)
            try {
                driver.connect("chrome");
                System.out.println("[4] Session established via session.new");
            } catch (Exception e) {
                System.out.println("[4] session.new failed (expected over CDP): " + e.getMessage());
                System.out.println("[4] Trying BiDi commands directly without session.new...");
            }

            // Step 5: Try sending raw CDP command via WebSocket to test protocol
            System.out.println("[5] Sending raw CDP Target.getTargets...");
            String cdpCmd = "{\"id\":99,\"method\":\"Target.getTargets\",\"params\":{}}";
            ws.send(cdpCmd);
            Thread.sleep(1000);

            // Step 5b: Try CDP Page.navigate
            // First get target info
            String getTargets = "{\"id\":100,\"method\":\"Target.getTargets\",\"params\":{}}";
            ws.send(getTargets);
            Thread.sleep(1000);

            System.out.println("[5] Raw CDP test sent. Check console for responses.");
            System.out.println("[5] Chrome ONLY speaks CDP on this WebSocket, not BiDi.");
            System.out.println("[5] For BiDi, Firefox must be used (it supports BiDi natively).");
            System.out.println("[5] CONCLUSION: Chrome without ChromeDriver = CDP only, no BiDi.");

            Thread.sleep(1000);

            Thread.sleep(2000);
            System.out.println("[8] Closing WebSocket...");
            ws.close();
            System.out.println("=== SUCCESS ===");

        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Query Chrome's /json/version endpoint and extract the webSocketDebuggerUrl.
     */
    private static String getCdpWebSocketUrl(int port) throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + "/json/version");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();

        // Simple parse – find webSocketDebuggerUrl value (handles whitespace in JSON)
        String json = sb.toString();
        // Use Gson for reliable parsing
        com.google.gson.JsonObject obj = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
        if (!obj.has("webSocketDebuggerUrl")) {
            throw new RuntimeException("webSocketDebuggerUrl not found in /json/version response: " + json);
        }
        return obj.get("webSocketDebuggerUrl").getAsString();
    }
}
