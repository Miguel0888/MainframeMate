package de.bund.zrb.websearch.plugin;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.event.WDLogEvent;
import de.bund.zrb.type.log.WDLogEntry;
import de.bund.zrb.type.session.WDSubscriptionRequest;
import de.zrb.bund.api.MainframeContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Manages the shared BrowserSession lifecycle for the WebSearch plugin.
 * The browser is launched and connected lazily on first tool call.
 * All chat sessions share the same browser connection.
 */
public class WebSearchBrowserManager {

    private static final Logger LOG = Logger.getLogger(WebSearchBrowserManager.class.getName());
    private static final String PLUGIN_KEY = "webSearch";

    private final MainframeContext context;
    private BrowserSession session;

    public WebSearchBrowserManager(MainframeContext context) {
        this.context = context;
    }

    /**
     * Get the shared browser session. If the browser is not yet running,
     * it is launched and connected automatically using the plugin settings.
     */
    public synchronized BrowserSession getSession() {
        if (session != null && session.isConnected()) {
            return session;
        }

        // Close stale session if any
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
            session = null;
        }

        String browserPath = getBrowserPath();
        if (browserPath == null || browserPath.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Browser-Pfad ist nicht konfiguriert. "
                  + "Bitte unter Einstellungen \u2192 Plugin-Einstellungen \u2192 Websearch setzen.");
        }

        boolean headless = isHeadless();
        LOG.info("[WebSearch] Launching browser: " + browserPath + " (headless=" + headless + ")");

        // Apply saved timeout to system property
        String savedTimeout = loadSettings().getOrDefault("navigateTimeoutSeconds", "30");
        System.setProperty("websearch.navigate.timeout.seconds", savedTimeout);

        session = new BrowserSession();
        try {
            session.launchAndConnect(browserPath, new ArrayList<String>(), headless, 30000L);
            LOG.info("[WebSearch] Browser connected, contextId=" + session.getContextId());
            subscribeToBrowserConsoleLogs();
        } catch (Exception e) {
            LOG.severe("[WebSearch] Failed to launch browser: " + e.getMessage());
            try { session.close(); } catch (Exception ignored) {}
            session = null;
            throw new RuntimeException("Browser konnte nicht gestartet werden: " + e.getMessage(), e);
        }

        return session;
    }

    /**
     * Subscribe to BiDi log.entryAdded events and output them to stderr
     * with [BROWSER] prefix for debugging.
     */
    private void subscribeToBrowserConsoleLogs() {
        try {
            WDSubscriptionRequest logSubscription = new WDSubscriptionRequest(
                    Collections.singletonList("log.entryAdded"));
            Consumer<WDLogEvent.EntryAdded> logListener = event -> {
                if (event != null && event.getParams() != null) {
                    WDLogEntry entry = event.getParams();
                    String level = "INFO";
                    String text = "(no text)";
                    if (entry instanceof WDLogEntry.BaseWDLogEntry) {
                        WDLogEntry.BaseWDLogEntry base = (WDLogEntry.BaseWDLogEntry) entry;
                        level = base.getLevel() != null ? base.getLevel().value().toUpperCase() : "INFO";
                        text = base.getText() != null ? base.getText() : "(no text)";
                    }
                    System.err.println("[BROWSER] [" + level + "] " + text);
                }
            };
            session.getDriver().addEventListener(logSubscription, logListener);
            LOG.info("[WebSearch] Subscribed to browser console logs (log.entryAdded)");
        } catch (Exception e) {
            LOG.warning("[WebSearch] Failed to subscribe to browser console logs: " + e.getMessage());
        }
    }

    public synchronized void closeSession() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                LOG.warning("[WebSearch] Error closing browser session: " + e.getMessage());
                // close() failed – force-kill as fallback (only our own process)
                try { session.killBrowserProcess(); } catch (Exception ignored) {}
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

    private static final String DEFAULT_FIREFOX_PATH = "C:\\Program Files\\Mozilla Firefox\\firefox.exe";

    public String getBrowserPath() {
        String path = loadSettings().getOrDefault("browserPath", "");
        return (path == null || path.trim().isEmpty()) ? DEFAULT_FIREFOX_PATH : path;
    }

    public Map<String, String> loadSettings() {
        return context.loadPluginSettings(PLUGIN_KEY);
    }
}

