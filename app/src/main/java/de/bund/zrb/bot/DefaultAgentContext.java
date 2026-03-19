package de.bund.zrb.bot;

import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.EventService;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.bot.AgentContext;
import de.zrb.bund.newApi.browser.BrowserService;

import java.util.Map;

/**
 * Default implementation of AgentContext that delegates to MainframeContext.
 */
public class DefaultAgentContext implements AgentContext {

    private final MainframeContext mainframeContext;

    public DefaultAgentContext(MainframeContext mainframeContext) {
        this.mainframeContext = mainframeContext;
    }

    @Override
    public BrowserService getBrowserService() {
        return mainframeContext.getBrowserService();
    }

    @Override
    public ToolRegistry getToolRegistry() {
        return mainframeContext.getToolRegistry();
    }

    @Override
    public EventService getEventService() {
        // EventService may not be directly on MainframeContext yet
        // Return null for now; will be wired when EventService is exposed
        return null;
    }

    @Override
    public Map<String, String> loadPluginSettings(String pluginKey) {
        return mainframeContext.loadPluginSettings(pluginKey);
    }

    @Override
    public void savePluginSettings(String pluginKey, Map<String, String> settings) {
        mainframeContext.savePluginSettings(pluginKey, settings);
    }
}

