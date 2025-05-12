package org.example.ui;

import org.example.ftp.FtpService;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

public class MainFrame extends JFrame {

    private final FtpService ftpService = new FtpService();
    private FtpBrowserPanel browserPanel;
    private TabbedPaneManager tabManager;

    public MainFrame() {
        setTitle("MainframeMate");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();

        if (ConnectDialog.connectIfNeeded(this, ftpService)) {
            tabManager.openNewTab(ftpService);
        }
    }

    private void initUI() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Datei");
        JMenuItem connectItem = new JMenuItem("Verbinden...");
        connectItem.addActionListener(e -> {
            if (ConnectDialog.show(this, ftpService)) {
                tabManager.openNewTab(ftpService);
            }
        });

        fileMenu.add(connectItem);
        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // Bookmark-Leiste
        BookmarkToolbar bookmarkToolbar = new BookmarkToolbar(path -> {
            tabManager.openNewTab(ftpService, path);
        });

        // Tabs
        tabManager = new TabbedPaneManager();
        this.setLayout(new BorderLayout());
        add(bookmarkToolbar, BorderLayout.NORTH);
        add(tabManager.getComponent(), BorderLayout.CENTER);
    }

}
