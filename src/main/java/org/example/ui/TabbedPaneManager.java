package org.example.ui;

import org.example.ftp.FtpService;

import javax.swing.*;
import java.awt.*;

public class TabbedPaneManager {

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private int tabCount = 1;

    public void openNewTab(FtpService ftpService) {
//        String path = ftpService.isMvsMode() ? "'ABC.'" : "/";
        String path = "/";
        openNewTab(ftpService, path);
    }

    public void openNewTab(FtpService ftpService, String path) {
        FtpBrowserPanel panel = new FtpBrowserPanel(ftpService);
        panel.init(); // ← wichtig!
        panel.loadDirectory(path);
        tabbedPane.addTab("Verbindung " + (tabCount++), panel);
    }

    public void closeTab(int index) {
        Component component = tabbedPane.getComponentAt(index);
        if (component instanceof FtpBrowserPanel) {
            FtpBrowserPanel panel = (FtpBrowserPanel) component;
            panel.dispose();
        }
        tabbedPane.remove(index);
    }

    public JComponent getComponent() {
        return tabbedPane;
    }
}
