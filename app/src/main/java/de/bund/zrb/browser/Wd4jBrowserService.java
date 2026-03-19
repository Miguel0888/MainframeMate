package de.bund.zrb.browser;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.zrb.bund.newApi.browser.BrowserException;
import de.zrb.bund.newApi.browser.BrowserService;
import de.zrb.bund.newApi.browser.BrowserSession;

import java.util.logging.Logger;

/**
 * Browser API implementation backed by wd4j (WebDriver BiDi).
 *
 * <p>This class manages the lifecycle of a shared browser session.
 * Browser configuration (type, path, debug-port, timeout) is read from
 * the central {@link Settings} fields ({@code browserType}, {@code browserPath}, etc.)
 * which are edited in <em>Einstellungen → Browser</em>.</p>
 */
public class Wd4jBrowserService implements BrowserService {

    private static final Logger LOG = Logger.getLogger(Wd4jBrowserService.class.getName());

    private volatile Wd4jBrowserSessionAdapter sessionAdapter;
    private volatile de.bund.zrb.mcpserver.browser.BrowserSession wd4jSession;

    public Wd4jBrowserService() {
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

        Settings settings = SettingsHelper.load();
        String browserPath = resolveBrowserPath(settings);
        if (browserPath == null || browserPath.trim().isEmpty()) {
            throw new BrowserException(
                    "Browser-Pfad ist nicht konfiguriert. "
                  + "Bitte unter Einstellungen \u2192 Browser setzen.");
        }

        int debugPort = settings.browserDebugPort;
        System.setProperty("websearch.navigate.timeout.seconds",
                String.valueOf(settings.browserNavigateTimeoutSeconds));

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
        Settings settings = SettingsHelper.load();
        return settings.browserType != null ? settings.browserType : "Firefox";
    }

    @Override
    public String getBrowserPath() {
        return resolveBrowserPath(SettingsHelper.load());
    }

    /**
     * Returns the underlying wd4j BrowserSession for use by tools that need direct access.
     * This should only be used within the browser-core layer, never by plugins.
     */
    public synchronized de.bund.zrb.mcpserver.browser.BrowserSession getWd4jSession() {
        if (wd4jSession != null && wd4jSession.isConnected()) {
            return wd4jSession;
        }
        openSession();
        return wd4jSession;
    }

    /**
     * Resolve the browser executable path from Settings.
     * If {@code browserPath} is set, use it; otherwise derive from {@code browserType}.
     */
    private static String resolveBrowserPath(Settings settings) {
        String path = settings.browserPath;
        if (path != null && !path.trim().isEmpty()) {
            return path;
        }
        String type = settings.browserType != null ? settings.browserType : "Firefox";
        if ("Chrome".equalsIgnoreCase(type)) {
            return de.bund.zrb.mcpserver.browser.BrowserLauncher.DEFAULT_CHROME_PATH;
        }
        if ("Edge".equalsIgnoreCase(type)) {
            return de.bund.zrb.mcpserver.browser.BrowserLauncher.resolveEdgePath();
        }
        return de.bund.zrb.mcpserver.browser.BrowserLauncher.DEFAULT_FIREFOX_PATH;
    }
}
