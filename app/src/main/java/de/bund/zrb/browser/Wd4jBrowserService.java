package de.bund.zrb.browser;

import de.zrb.bund.newApi.browser.BrowserException;
import de.zrb.bund.newApi.browser.BrowserService;
import de.zrb.bund.newApi.browser.BrowserSession;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Browser API implementation backed by wd4j (WebDriver BiDi).
 *
 * <p>This class manages the lifecycle of a shared browser session.
 * It delegates to the wd4j-mcp-server's {@code de.bund.zrb.mcpserver.browser.BrowserSession}
 * but wraps it behind the core Browser API interfaces so that plugins
 * never see wd4j-specific types.</p>
 */
public class Wd4jBrowserService implements BrowserService {

    private static final Logger LOG = Logger.getLogger(Wd4jBrowserService.class.getName());
    private static final String PLUGIN_KEY = "webSearch";

    private final SettingsProvider settingsProvider;
    private volatile Wd4jBrowserSessionAdapter sessionAdapter;
    private volatile de.bund.zrb.mcpserver.browser.BrowserSession wd4jSession;

    /**
     * Functional interface for loading plugin settings.
     * Decouples from MainframeContext to avoid circular deps.
     */
    public interface SettingsProvider {
        Map<String, String> loadSettings(String key);
    }

    public Wd4jBrowserService(SettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    @Override
    public synchronized BrowserSession openSession() throws BrowserException {
        if (sessionAdapter != null && sessionAdapter.isConnected()) {
            return sessionAdapter;
        }

        // Close stale session if any
        if (wd4jSession != null) {
            try { wd4jSession.close(); } catch (Exception ignored) {}
            wd4jSession = null;
            sessionAdapter = null;
        }

        String browserPath = getBrowserPath();
        if (browserPath == null || browserPath.trim().isEmpty()) {
            throw new BrowserException(
                    "Browser-Pfad ist nicht konfiguriert. "
                  + "Bitte unter Einstellungen → Plugin-Einstellungen → Websearch setzen.");
        }

        Map<String, String> settings = settingsProvider.loadSettings(PLUGIN_KEY);
        int debugPort = parseDebugPort(settings);
        String savedTimeout = settings.getOrDefault("navigateTimeoutSeconds", "30");
        System.setProperty("websearch.navigate.timeout.seconds", savedTimeout);

        LOG.info("[BrowserService] Launching browser: " + browserPath + " (debugPort=" + debugPort + ")");

        wd4jSession = new de.bund.zrb.mcpserver.browser.BrowserSession();
        try {
            wd4jSession.launchAndConnect(browserPath, new java.util.ArrayList<String>(), false, 30000L, debugPort);
            LOG.info("[BrowserService] Browser connected, contextId=" + wd4jSession.getContextId());
        } catch (Exception e) {
            LOG.severe("[BrowserService] Failed to launch browser: " + e.getMessage());
            try { wd4jSession.close(); } catch (Exception ignored) {}
            wd4jSession = null;
            throw new BrowserException("Browser konnte nicht gestartet werden: " + e.getMessage(), e);
        }

        sessionAdapter = new Wd4jBrowserSessionAdapter(wd4jSession);
        return sessionAdapter;
    }

    @Override
    public synchronized BrowserSession getActiveSession() {
        if (sessionAdapter != null && sessionAdapter.isConnected()) {
            return sessionAdapter;
        }
        return null;
    }

    @Override
    public synchronized void closeSession() {
        if (wd4jSession != null) {
            try {
                wd4jSession.close();
            } catch (Exception e) {
                LOG.warning("[BrowserService] Error closing browser session: " + e.getMessage());
                try { wd4jSession.killBrowserProcess(); } catch (Exception ignored) {}
            }
            wd4jSession = null;
            sessionAdapter = null;
        }
    }

    @Override
    public boolean isSessionActive() {
        return sessionAdapter != null && sessionAdapter.isConnected();
    }

    @Override
    public String getBrowserType() {
        Map<String, String> settings = settingsProvider.loadSettings(PLUGIN_KEY);
        return settings.getOrDefault("browser", "Firefox");
    }

    @Override
    public String getBrowserPath() {
        Map<String, String> settings = settingsProvider.loadSettings(PLUGIN_KEY);
        String path = settings.getOrDefault("browserPath", "");
        if (path != null && !path.trim().isEmpty()) {
            return path;
        }
        String browser = settings.getOrDefault("browser", "Firefox");
        if ("Chrome".equalsIgnoreCase(browser)) {
            return de.bund.zrb.mcpserver.browser.BrowserLauncher.DEFAULT_CHROME_PATH;
        }
        if ("Edge".equalsIgnoreCase(browser)) {
            return de.bund.zrb.mcpserver.browser.BrowserLauncher.resolveEdgePath();
        }
        return de.bund.zrb.mcpserver.browser.BrowserLauncher.DEFAULT_FIREFOX_PATH;
    }

    /**
     * Returns the underlying wd4j BrowserSession for use by tools that need direct access.
     * This should only be used within the browser-core layer, never by plugins.
     */
    public synchronized de.bund.zrb.mcpserver.browser.BrowserSession getWd4jSession() {
        if (wd4jSession != null && wd4jSession.isConnected()) {
            return wd4jSession;
        }
        // Auto-open if needed (this matches old WebSearchBrowserManager behavior)
        openSession();
        return wd4jSession;
    }

    private int parseDebugPort(Map<String, String> settings) {
        String portStr = settings.getOrDefault("debugPort", "0");
        try {
            int port = Integer.parseInt(portStr.trim());
            return Math.max(0, Math.min(port, 65535));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

