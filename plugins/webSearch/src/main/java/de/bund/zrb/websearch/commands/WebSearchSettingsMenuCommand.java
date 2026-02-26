package de.bund.zrb.websearch.commands;

import de.bund.zrb.websearch.plugin.WebSearchBrowserManager;
import de.bund.zrb.websearch.ui.WebSearchSettingsDialog;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;

public class WebSearchSettingsMenuCommand extends ShortcutMenuCommand {

    private final MainframeContext context;
    private final WebSearchBrowserManager browserManager;

    public WebSearchSettingsMenuCommand(MainframeContext context, WebSearchBrowserManager browserManager) {
        this.context = context;
        this.browserManager = browserManager;
    }

    @Override
    public String getId() {
        return "settings.plugins.websearch";
    }

    @Override
    public String getLabel() {
        return "Websearch-Einstellungen...";
    }

    @Override
    public void perform() {
        WebSearchSettingsDialog dialog = new WebSearchSettingsDialog(context, browserManager);
        dialog.setVisible(true);
    }
}

