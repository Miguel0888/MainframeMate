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

        // Apply saved timeout to system property
        String savedTimeout = loadSettings().getOrDefault("navigateTimeoutSeconds", "30");
        System.setProperty("websearch.navigate.timeout.seconds", savedTimeout);

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
            // Fallback: if close() didn't terminate the process, kill it explicitly
            killOwnBrowserProcess();
            session = null;
        }
    }

    /**
     * Kill only the browser process that WE started (tracked via BrowserSession.getBrowserProcess()).
     * Never kills other Firefox instances belonging to the user or other applications.
     */
    private void killOwnBrowserProcess() {
        if (session == null) return;
        Process proc = session.getBrowserProcess();
        if (proc == null) return;
        if (!proc.isAlive()) {
            LOG.fine("[WebSearch] Browser process already exited.");
            return;
        }
        LOG.info("[WebSearch] Forcefully killing our browser process (pid=" + getPid(proc) + ")");
        proc.destroyForcibly();
        try {
            proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Best-effort PID extraction for logging. Returns -1 if unavailable (Java 8).
     */
    private static long getPid(Process proc) {
        try {
            // Java 9+: Process.pid() via reflection
            java.lang.reflect.Method pidMethod = proc.getClass().getMethod("pid");
            return (Long) pidMethod.invoke(proc);
        } catch (Exception e) {
            return -1;
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

