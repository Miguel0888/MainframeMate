package de.bund.zrb.ui.commands;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ui.settings.SettingsDialog;
import de.zrb.bund.api.Command;

import javax.swing.*;

public class ShowSettingsDialogCommand implements Command {

    private final JFrame parent;

    public ShowSettingsDialogCommand(JFrame parent) {
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
        FtpManager dummy = new FtpManager();
        SettingsDialog.show(parent, dummy);
    }
}
