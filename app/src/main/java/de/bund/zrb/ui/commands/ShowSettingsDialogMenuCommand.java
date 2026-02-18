package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.settings.SettingsDialog;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ShowSettingsDialogMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ShowSettingsDialogMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.general";
    }

    @Override
    public String getLabel() {
        return "Allgemein...";
    }

    @Override
    public void perform() {
        SettingsDialog.show(parent, null);
    }
}
