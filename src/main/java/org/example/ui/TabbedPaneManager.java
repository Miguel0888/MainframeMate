package org.example.ui;

import org.example.ftp.FtpManager;

import javax.swing.*;
import java.awt.*;

public class TabbedPaneManager {

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private int tabCount = 1;

    public void openNewTab(FtpManager ftpManager) {
//        String path = ftpService.isMvsMode() ? "'ABC.'" : "/";
        String path = "/";
        openNewTab(ftpManager, path);
    }

    public void openNewTab(FtpManager ftpManager, String path) {
        FtpBrowserPanel panel = new FtpBrowserPanel(ftpManager);
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
