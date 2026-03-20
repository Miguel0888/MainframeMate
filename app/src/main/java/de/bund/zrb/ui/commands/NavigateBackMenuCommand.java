package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.newApi.ui.Navigable;

/**
 * Navigates the active tab backwards in its history.
 * When the tab has no internal back history, the tab is closed
 * and the previously active tab is selected (tab-level back).
 */
public class NavigateBackMenuCommand extends ShortcutMenuCommand {

    private final MainframeContext context;
    private final TabbedPaneManager tabManager;

    public NavigateBackMenuCommand(MainframeContext context, TabbedPaneManager tabManager) {
        this.context = context;
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
        // 1) Try within-tab navigation
        java.util.Optional<de.zrb.bund.api.Bookmarkable> sel = context.getSelectedTab();
        if (sel.isPresent() && sel.get() instanceof Navigable) {
            Navigable nav = (Navigable) sel.get();
            if (nav.canNavigateBack()) {
                nav.navigateBack();
                return;
            }
        }
        // 2) Fall back to tab-level back (close current tab, return to previous)
        tabManager.navigateTabBack();
    }
}

