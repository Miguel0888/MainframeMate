package org.example.ui;

import org.example.ftp.FtpManager;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private TabbedPaneManager tabManager;
    private BookmarkToolbar bookmarkToolbar;

    public MainFrame() {
        setTitle("MainframeMate");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();

        final FtpManager ftpManager = new FtpManager();
        if (ConnectDialog.connectIfNeeded(this, ftpManager)) {
            tabManager.addTab(new ConnectionTab(ftpManager, tabManager));
        }
    }

    private void initUI() {
        JMenuBar menuBar = new JMenuBar();

        // Datei-Menü
        JMenu fileMenu = new JMenu("Datei");
        JMenuItem saveItem = new JMenuItem("Speichern");
        saveItem.addActionListener(e -> tabManager.saveSelectedComponent());

        JMenuItem connectItem = new JMenuItem("Neue Verbindung...");
        connectItem.addActionListener(e -> {
            final FtpManager ftpManager = new FtpManager();
            if (ConnectDialog.show(this, ftpManager)) {
                tabManager.addTab(new ConnectionTab(ftpManager, tabManager));
            }
        });

        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(saveItem);
        fileMenu.add(connectItem);
        fileMenu.add(exitItem);

        // Einstellungen-Menü
        JMenu settingsMenu = new JMenu("Einstellungen");
        JMenuItem settingsItem = new JMenuItem("Allgemein...");
        settingsItem.addActionListener(e -> {
            FtpManager dummy = new FtpManager();
            SettingsDialog.show(this, dummy);
        });
        settingsMenu.add(settingsItem);

        menuBar.add(fileMenu);
        menuBar.add(settingsMenu);
        setJMenuBar(menuBar);

        // Bookmark-Leiste
        bookmarkToolbar = new BookmarkToolbar(path -> {
            final FtpManager ftpManager = new FtpManager();
            if (ConnectDialog.show(this, ftpManager)) {
                ConnectionTab tab = new ConnectionTab(ftpManager, tabManager);
                tabManager.addTab(tab);
                tab.loadDirectory(path);
            }
        });

        tabManager = new TabbedPaneManager();
        setLayout(new BorderLayout());
        add(bookmarkToolbar, BorderLayout.NORTH);
        add(tabManager.getComponent(), BorderLayout.CENTER);
    }

    public BookmarkToolbar getBookmarkToolbar() {
        return bookmarkToolbar;
    }
}
