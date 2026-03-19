package de.zrb.bund.newApi.bot;

import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.browser.BrowserService;

/**
 * Context object passed to agents when creating sessions.
 * Provides access to core services that agents may need.
 *
 * <p>This decouples agents from direct dependencies on app internals —
 * agents only see the services exposed through this context.</p>
 */
public interface AgentContext {

    /**
     * Access to the browser automation service.
     * May return null if no browser service is configured.
     */
    BrowserService getBrowserService();

    /**
     * Access to the tool registry for registering/querying tools.
     */
    ToolRegistry getToolRegistry();

    /**
     * Access to the event service for publishing/subscribing to events.
     */
    de.zrb.bund.newApi.EventService getEventService();

    /**
     * Load plugin settings by key.
     */
    java.util.Map<String, String> loadPluginSettings(String pluginKey);

    /**
     * Save plugin settings by key.
     */
    void savePluginSettings(String pluginKey, java.util.Map<String, String> settings);
}

