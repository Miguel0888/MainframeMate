import de.bund.zrb.WDWebSocketImpl;
import de.bund.zrb.WebDriver;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.type.browsingContext.WDReadinessState;

import java.net.URI;
import java.util.List;

/**
 * Standalone test: Connect to Chrome via WebDriver BiDi on port 9333,
 * navigate to https://www.w3.org/TR/webdriver-bidi, print the page title.
 *
 * Prerequisites:
 *   chrome.exe --remote-debugging-port=9333 --no-first-run --user-data-dir=... about:blank
 */
public class ChromeBidiTest {

    public static void main(String[] args) throws Exception {
        String wsUrl = "ws://127.0.0.1:9333/session";
        System.out.println("[Test] Connecting to Chrome BiDi at: " + wsUrl);

        // 1. WebSocket verbinden
        WDWebSocketImpl ws = new WDWebSocketImpl(URI.create(wsUrl), 30_000.0);
        System.out.println("[Test] WebSocket connected: " + ws.isConnected());

        // 2. WebDriver erstellen
        WebDriver webDriver = new WebDriver(ws);
        System.out.println("[Test] WebDriver created");

        // 3. Session verbinden (Chrome-Modus)
        webDriver.connect("chrome");
        System.out.println("[Test] Session connected");

        // 4. Browsing-Context-Tree holen (um die Context-ID zu finden)
        WDBrowsingContextResult.GetTreeResult tree = webDriver.browsingContext().getTree();
        System.out.println("[Test] Browsing context tree received");

        if (tree == null || tree.getContexts() == null || tree.getContexts().isEmpty()) {
            System.out.println("[Test] ERROR: No browsing contexts found!");
            // Neuen Tab erstellen
            WDBrowsingContextResult.CreateResult created = webDriver.browsingContext().create();
            System.out.println("[Test] Created new context: " + created.getContext());
            String contextId = created.getContext();
            navigate(webDriver, contextId);
        } else {
            String contextId = tree.getContexts().get(0).getContext();
            System.out.println("[Test] Using context: " + contextId);
            navigate(webDriver, contextId);
        }

        // Warte kurz damit alles fertig wird
        Thread.sleep(3000);

        // Aufräumen
        System.out.println("[Test] Done. Closing WebSocket...");
        ws.close();
        System.out.println("[Test] Finished successfully!");
    }

    private static void navigate(WebDriver webDriver, String contextId) {
        String targetUrl = "https://www.w3.org/TR/webdriver-bidi";
        System.out.println("[Test] Navigating to: " + targetUrl);

        WDBrowsingContextResult.NavigateResult result = webDriver.browsingContext().navigate(
                targetUrl, contextId, WDReadinessState.INTERACTIVE
        );

        System.out.println("[Test] Navigation result:");
        System.out.println("  URL: " + result.getUrl());
        System.out.println("  Navigation: " + result.getNavigation());
    }
}
