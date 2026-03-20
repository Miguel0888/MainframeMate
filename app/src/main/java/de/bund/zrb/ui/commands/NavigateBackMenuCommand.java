package de.bund.zrb.ui.commands;

import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.newApi.ui.Navigable;

/**
 * Navigates the active tab backwards in its history.
 * Works on any tab that implements {@link Navigable}.
 */
public class NavigateBackMenuCommand extends ShortcutMenuCommand {

    private final MainframeContext context;

    public NavigateBackMenuCommand(MainframeContext context) {
        this.context = context;
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
        context.getSelectedTab().ifPresent(tab -> {
            if (tab instanceof Navigable) {
                ((Navigable) tab).navigateBack();
            }
        });
    }
}

