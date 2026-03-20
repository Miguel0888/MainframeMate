package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

/**
 * Navigates the active tab forward in its history.
 * When a tab was previously closed via back navigation, it is reopened first
 * (tab-level forward); otherwise the current tab's internal forward is used.
 * <p>
 * Delegates to {@link TabbedPaneManager#performForward()} which is also
 * used by the global mouse-button-5 handler — no duplicated logic.
 */
public class NavigateForwardMenuCommand extends ShortcutMenuCommand {

    private final TabbedPaneManager tabManager;

    public NavigateForwardMenuCommand(TabbedPaneManager tabManager) {
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
        tabManager.performForward();
    }
}

