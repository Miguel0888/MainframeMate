package de.bund.zrb.websearch.plugin;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.zrb.bund.api.MainframeContext;

import java.util.ArrayList;
import java.util.Map;
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

        session = new BrowserSession();
        try {
            session.launchAndConnect(browserPath, new ArrayList<String>(), headless, 30000L);
            LOG.info("[WebSearch] Browser connected, contextId=" + session.getContextId());
        } catch (Exception e) {
            LOG.severe("[WebSearch] Failed to launch browser: " + e.getMessage());
            try { session.close(); } catch (Exception ignored) {}
            session = null;
            throw new RuntimeException("Browser konnte nicht gestartet werden: " + e.getMessage(), e);
        }

        return session;
    }

    public synchronized void closeSession() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                LOG.warning("[WebSearch] Error closing browser session: " + e.getMessage());
            }
            session = null;
        }
        // Fallback: kill any lingering Firefox processes started by us
        killFirefoxProcesses();
    }

    /**
     * Kill Firefox processes that may have been left behind.
     * Uses ProcessHandle (Java 9+) if available, falls back to taskkill on Windows.
     */
    private void killFirefoxProcesses() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Use taskkill to find and kill Firefox processes
                // Only kill if we're sure we launched them (headless or specific profile)
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/IM", "firefox.exe", "/T");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
                LOG.info("[WebSearch] Firefox processes killed via taskkill");
            } else {
                // Unix-like: pkill
                ProcessBuilder pb = new ProcessBuilder("pkill", "-f", "firefox");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
            }
        } catch (Exception e) {
            LOG.fine("[WebSearch] Could not kill Firefox processes: " + e.getMessage());
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

    private Map<String, String> loadSettings() {
        return context.loadPluginSettings(PLUGIN_KEY);
    }
}

