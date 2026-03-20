package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

/**
 * Navigates the active tab backwards in its history.
 * When the tab has no internal back history, the tab is closed
 * and the previously active tab is selected (tab-level back).
 * <p>
 * Delegates to {@link TabbedPaneManager#performBack()} which is also
 * used by the global mouse-button-4 handler — no duplicated logic.
 */
public class NavigateBackMenuCommand extends ShortcutMenuCommand {

    private final TabbedPaneManager tabManager;

    public NavigateBackMenuCommand(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "navigate.back";
    }

    @Override
    public String getLabel() {
        return "\u25C0 Zur\u00fcck";
    }

    @Override
    public void perform() {
        tabManager.performBack();
    }
}

