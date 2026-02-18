package de.bund.zrb.ui.commands;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ui.settings.SettingsDialog;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ShowServerSettingsDialogMenuCommand extends ShortcutMenuCommand {

    private static final int FTP_TAB_INDEX = 3;
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
        FtpManager dummy = new FtpManager();
        SettingsDialog.show(parent, dummy, FTP_TAB_INDEX);
    }
}

