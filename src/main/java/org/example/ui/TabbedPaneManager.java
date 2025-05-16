package org.example.ui;

import org.example.ftp.FtpManager;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TabbedPaneManager {

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private int tabCount = 1;

    public TabbedPaneManager() {
        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;

                int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                if (tabIndex < 0) return;

                Component tabComponent = tabbedPane.getComponentAt(tabIndex);
                JPopupMenu menu = new JPopupMenu();

                JMenuItem bookmarkItem = new JMenuItem("🕮 Bookmark setzen");
                bookmarkItem.addActionListener(a -> {
                    if (tabComponent instanceof FtpBrowserPanel) {
                        FtpBrowserPanel panel = (FtpBrowserPanel) tabComponent;
                        String path = panel.getCurrentPath();
                        SettingsManager.addBookmark(path);

                        MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(tabbedPane);
                        main.getBookmarkToolbar().refreshBookmarks();

                        JOptionPane.showMessageDialog(tabbedPane, "Bookmark gesetzt für: " + path);
                    }
                });

                JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
                closeItem.addActionListener(a -> closeTab(tabIndex));

                menu.add(bookmarkItem);
                menu.add(closeItem);

                menu.show(tabbedPane, e.getX(), e.getY());
            }
        });
    }

    public void openNewTab(FtpManager ftpManager) {
        openNewTab(ftpManager, "/");
    }

    public void openNewTab(FtpManager ftpManager, String path) {
        FtpBrowserPanel panel = new FtpBrowserPanel(ftpManager);
        panel.init();
        panel.loadDirectory(path);
        tabbedPane.addTab("Verbindung " + (tabCount++), panel);
    }

    public void closeTab(int index) {
        Component component = tabbedPane.getComponentAt(index);
        if (component instanceof FtpBrowserPanel) {
            ((FtpBrowserPanel) component).dispose();
        }
        tabbedPane.remove(index);
    }

    public JComponent getComponent() {
        return tabbedPane;
    }
}
