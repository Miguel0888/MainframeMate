package de.bund.zrb.websearch.plugin;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.zrb.bund.api.MainframeContext;

import java.util.Map;

/**
 * Manages the shared BrowserSession lifecycle for the WebSearch plugin.
 * Lazily initializes the browser on first tool call.
 */
public class WebSearchBrowserManager {

    private static final String PLUGIN_KEY = "webSearch";

    private final MainframeContext context;
    private BrowserSession session;

    public WebSearchBrowserManager(MainframeContext context) {
        this.context = context;
    }

    /**
     * Get the shared browser session, creating it if necessary.
     * The session is NOT automatically connected — tools like browser_open
     * or browser_launch handle that.
     */
    public synchronized BrowserSession getSession() {
        if (session == null) {
            session = new BrowserSession();
        }
        return session;
    }

    public synchronized void closeSession() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                System.err.println("[WebSearch] Error closing browser session: " + e.getMessage());
            }
            session = null;
        }
    }

    public String getBrowser() {
        return loadSettings().getOrDefault("browser", "Firefox");
    }

    public boolean isHeadless() {
        // TODO: vorübergehend deaktiviert zum Debuggen – später wieder aktivieren
        return false;
        // return !"false".equals(loadSettings().getOrDefault("headless", "true"));
    }

    public String getBrowserPath() {
        return loadSettings().getOrDefault("browserPath", "");
    }

    private Map<String, String> loadSettings() {
        return context.loadPluginSettings(PLUGIN_KEY);
    }
}

