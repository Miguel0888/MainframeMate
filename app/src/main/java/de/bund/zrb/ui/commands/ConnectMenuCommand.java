package de.bund.zrb.ui.commands;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ui.ConnectDialog;
import de.bund.zrb.ui.ConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.MenuCommand;

import javax.swing.*;

public class ConnectMenuCommand implements MenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public ConnectMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.connect";
    }

    @Override
    public String getLabel() {
        return "Neue Verbindung...";
    }

    @Override
    public void perform() {
        FtpManager ftpManager = new FtpManager();
        if (ConnectDialog.show(parent, ftpManager)) {
            tabManager.addTab(new ConnectionTab(ftpManager, tabManager));
        }
    }
}
