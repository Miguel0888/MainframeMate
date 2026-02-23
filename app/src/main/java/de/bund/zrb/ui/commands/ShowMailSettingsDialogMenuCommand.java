package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.settings.SettingsDialog;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

/**
 * Menu command to open Settings dialog directly on the "Mails" tab.
 */
public class ShowMailSettingsDialogMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ShowMailSettingsDialogMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.mails";
    }

    @Override
    public String getLabel() {
        return "Mails...";
    }

    @Override
    public void perform() {
        // The "Mails" tab will be added as the last tab in SettingsDialog.
        // We need to pass the correct tab index. Since the dialog creates tabs dynamically,
        // we use a named constant approach. The SettingsDialog.show() accepts an initialTabIndex.
        SettingsDialog.show(parent, SettingsDialog.TAB_INDEX_MAILS);
    }
}
