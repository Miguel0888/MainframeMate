package org.example.ui;

import org.example.ftp.FtpService;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

public class MainFrame extends JFrame {

    private final FtpService ftpService = new FtpService();
    private FtpBrowserPanel browserPanel;

    public MainFrame() {
        setTitle("MainframeMate");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();

        if (ConnectDialog.connectIfNeeded(this, ftpService)) {
            browserPanel.loadInitialDirectory();
        }
    }

    private void initUI() {
        // Menüleiste
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Datei");
        JMenuItem connectItem = new JMenuItem("Verbinden...");
        connectItem.addActionListener(e -> {
            boolean connected = ConnectDialog.show(this, ftpService);
            if (connected) {
                browserPanel.loadInitialDirectory();
            }
        });

        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(connectItem);
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // Hauptbereich
        browserPanel = new FtpBrowserPanel(ftpService);
        add(browserPanel, BorderLayout.CENTER);
    }
}
