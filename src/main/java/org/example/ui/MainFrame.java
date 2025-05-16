package org.example.ui;

import org.example.ftp.FtpManager;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private final FtpManager ftpManager = new FtpManager();
    private FtpBrowserPanel browserPanel;
    private TabbedPaneManager tabManager;
    private BookmarkToolbar bookmarkToolbar;

    public MainFrame() {
        setTitle("MainframeMate");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();

        if (ConnectDialog.connectIfNeeded(this, ftpManager)) {
            tabManager.openNewTab(ftpManager);
        }
    }

    private void initUI() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Datei");
        JMenuItem connectItem = new JMenuItem("Verbinden...");
        connectItem.addActionListener(e -> {
            if (ConnectDialog.show(this, ftpManager)) {
                tabManager.openNewTab(ftpManager);
            }
        });

        fileMenu.add(connectItem);
        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // Bookmark-Leiste
        bookmarkToolbar = new BookmarkToolbar(path -> {
            tabManager.openNewTab(ftpManager, path);
        });

        // Tabs
        tabManager = new TabbedPaneManager();
        this.setLayout(new BorderLayout());
        add(bookmarkToolbar, BorderLayout.NORTH);
        add(tabManager.getComponent(), BorderLayout.CENTER);
    }

    // ToDo: Better use Obersver etc here?
    public BookmarkToolbar getBookmarkToolbar() {
        return bookmarkToolbar;
    }
}
