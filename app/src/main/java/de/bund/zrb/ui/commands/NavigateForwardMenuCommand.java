package de.bund.zrb.ui.commands;

import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.newApi.ui.Navigable;

/**
 * Navigates the active tab forward in its history.
 * Works on any tab that implements {@link Navigable}.
 */
public class NavigateForwardMenuCommand extends ShortcutMenuCommand {

    private final MainframeContext context;

    public NavigateForwardMenuCommand(MainframeContext context) {
        this.context = context;
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
        context.getSelectedTab().ifPresent(tab -> {
            if (tab instanceof Navigable) {
                ((Navigable) tab).navigateForward();
            }
        });
    }
}

