package de.bund.zrb.ui.commands;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ui.settings.SettingsDialog;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.api.SimpleMenuCommand;

import javax.swing.*;

public class ShowSettingsDialogMenuCommand extends SimpleMenuCommand {

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
        FtpManager dummy = new FtpManager();
        SettingsDialog.show(parent, dummy);
    }
}
