package org.example.ui;

import org.example.ftp.FtpService;

import javax.swing.*;

public class TabbedPaneManager {

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private int tabCount = 1;

    public void openNewTab(FtpService ftpService) {
        openNewTab(ftpService, "/");
    }

    public void openNewTab(FtpService ftpService, String path) {
        FtpBrowserPanel panel = new FtpBrowserPanel(ftpService);
        panel.loadDirectory(path);
        tabbedPane.addTab("Verbindung " + (tabCount++), panel);
    }

    public JComponent getComponent() {
        return tabbedPane;
    }
}
