package de.bund.zrb.ui.commands;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ui.ConnectDialog;
import de.bund.zrb.ui.ConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;

import javax.swing.*;

public class ConnectCommand implements Command {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public ConnectCommand(JFrame parent, TabbedPaneManager tabManager) {
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
