package de.bund.zrb.ui.commands;

import de.example.toolbarkit.toolbar.ConfigurableCommandToolbar;
import de.zrb.bund.api.ShortcutMenuCommand;

/**
 * Opens the toolbar configuration dialog.
 * Registered under {@code settings.toolbar} so it appears in the Settings menu
 * and can be assigned a keyboard shortcut.
 */
public class ShowToolbarConfigMenuCommand extends ShortcutMenuCommand {

    private final ConfigurableCommandToolbar toolbar;

    public ShowToolbarConfigMenuCommand(ConfigurableCommandToolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public String getId() {
        return "settings.toolbar";
    }

    @Override
    public String getLabel() {
        return "\u2630 Toolbar...";
    }

    @Override
    public void perform() {
        toolbar.openConfigDialog();
    }
}

