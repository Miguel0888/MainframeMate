package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.settings.ServerSettingsDialog;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ShowServerSettingsDialogMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ShowServerSettingsDialogMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.server";
    }

    @Override
    public String getLabel() {
        return "Server...";
    }

    @Override
    public void perform() {
        ServerSettingsDialog.show(parent);
    }
}
