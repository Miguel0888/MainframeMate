package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.newApi.ui.Navigable;

/**
 * Navigates the active tab forward in its history.
 * When a tab was previously closed via back navigation, it is reopened first
 * (tab-level forward); otherwise the current tab's internal forward is used.
 */
public class NavigateForwardMenuCommand extends ShortcutMenuCommand {

    private final MainframeContext context;
    private final TabbedPaneManager tabManager;

    public NavigateForwardMenuCommand(MainframeContext context, TabbedPaneManager tabManager) {
        this.context = context;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "navigate.forward";
    }

    @Override
    public String getLabel() {
        return "\u25B6 Vorw\u00e4rts";
    }

    @Override
    public void perform() {
        // 1) Try tab-level forward (reopen a tab closed via back)
        if (tabManager.canNavigateTabForward()) {
            tabManager.navigateTabForward();
            return;
        }
        // 2) Fall back to within-tab forward navigation
        context.getSelectedTab().ifPresent(tab -> {
            if (tab instanceof Navigable) {
                ((Navigable) tab).navigateForward();
            }
        });
    }
}

