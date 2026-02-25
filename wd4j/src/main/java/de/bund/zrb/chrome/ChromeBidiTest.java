package de.bund.zrb.chrome;

import de.bund.zrb.chrome.cdp.CdpConnection;
import de.bund.zrb.chrome.cdp.CdpMapperSetup;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Quick integration test: starts Chrome (must be running with --remote-debugging-port),
 * injects the BiDi mapper, and sends a simple BiDi command.
 * <p>
 * Usage:
 *   1. Start Chrome: chrome.exe --remote-debugging-port=9222
 *   2. Run this class
 */
public class ChromeBidiTest {

    public static void main(String[] args) throws Exception {
        int port = 9222;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        System.out.println("=== Chrome BiDi Mapper Test ===");
        System.out.println("Querying CDP endpoint on port " + port + "...");

        // Get the webSocketDebuggerUrl from /json/version
        String cdpWsUrl = getCdpWebSocketUrl(port);
        if (cdpWsUrl == null) {
            System.err.println("ERROR: Could not get webSocketDebuggerUrl from http://127.0.0.1:" + port + "/json/version");
            System.err.println("Make sure Chrome is running with: --remote-debugging-port=" + port);
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
        String sessionStatusCmd = "{\"id\":1,\"method\":\"session.status\",\"params\":{}}";
        bidiWs.send(sessionStatusCmd);

        // Wait for response
        Thread.sleep(3000);

        // Send browsingContext.getTree
        System.out.println("\nSending BiDi browsingContext.getTree command...");
        String getTreeCmd = "{\"id\":2,\"method\":\"browsingContext.getTree\",\"params\":{}}";
        bidiWs.send(getTreeCmd);

        // Wait for response
        Thread.sleep(3000);

        System.out.println("\n=== Test complete! ===");
        bidiWs.close();
        System.exit(0);
    }

    private static String getCdpWebSocketUrl(int port) {
        try {
            URL url = new URL("http://127.0.0.1:" + port + "/json/version");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

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
                        if (end > start) {
                            return json.substring(start, end);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error querying /json/version: " + e.getMessage());
        }
        return null;
    }
}

