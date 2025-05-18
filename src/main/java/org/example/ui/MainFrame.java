package org.example.ui;

import org.example.ftp.FtpManager;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;

public class MainFrame extends JFrame {

    private TabbedPaneManager tabManager;
    private BookmarkToolbar bookmarkToolbar;

    public MainFrame() {
        setTitle("MainframeMate");
        setCompatibleFontIfNecessary();
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();

        final FtpManager ftpManager = new FtpManager();
        if (ConnectDialog.connectIfNeeded(this, ftpManager)) {
            tabManager.addTab(new ConnectionTab(ftpManager, tabManager));
        }
    }

    /**
     * Setze den Font auf "Segoe UI", wenn verfügbar.
     */
    private void setCompatibleFontIfNecessary() {
        String unicodeTest = "ÄÖÜß 📁";
        Font testFont = UIManager.getFont("Label.font");

        boolean unicodeOk = testFont.canDisplayUpTo(unicodeTest) == -1;

        System.out.println("Font: " + testFont.getFontName() + " | Unicode OK: " + unicodeOk);
        System.out.println("file.encoding: " + System.getProperty("file.encoding"));
        System.out.println("defaultCharset: " + java.nio.charset.Charset.defaultCharset());

        if (!unicodeOk) {
            System.out.println("⚠️ Unicode-Darstellung unvollständig – versuche Korrektur...");

            if (isFontAvailable("Segoe UI")) {
                for (Enumeration<Object> keys = UIManager.getDefaults().keys(); keys.hasMoreElements(); ) {
                    Object key = keys.nextElement();
                    Object value = UIManager.get(key);
                    if (value instanceof FontUIResource) {
                        UIManager.put(key, new FontUIResource("Segoe UI", Font.PLAIN, 12));
                    }
                }
                System.out.println("→ Font auf 'Segoe UI' gesetzt.");
            }

            // Benutzer-Hinweis anzeigen
            JOptionPane.showMessageDialog(this,
                    "Einige Unicode-Zeichen (z. B. 📁 oder ÄÖÜ) werden auf deinem System nicht korrekt dargestellt.\n\n" +
                            "Die Darstellung wurde automatisch angepasst.\n\n" +
                            "💡 Hinweis: Du kannst die App mit folgendem Startparameter ausführen,\n" +
                            "um das Problem dauerhaft zu vermeiden:\n\n" +
                            "    -Dfile.encoding=UTF-8\n\n" +
                            "Beispiel:\n" +
                            "    java -Dfile.encoding=UTF-8 -jar MainframeMate.jar",
                    "Darstellungsproblem erkannt", JOptionPane.WARNING_MESSAGE);
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

        // Hilfe-Menü
        JMenu helpMenu = new JMenu("Hilfe");
        JMenuItem featureItem = new JMenuItem("📋 Server-Features anzeigen");
        featureItem.addActionListener(e -> FeatureDialog.show(this));
        helpMenu.add(featureItem);

        JMenuItem aboutItem = new JMenuItem("ℹ️ Über MainframeMate");
        aboutItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                    "MainframeMate\nVersion 1.0\n© 2025 GZD",
                    "Über", JOptionPane.INFORMATION_MESSAGE);
        });
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(settingsMenu);
        menuBar.add(helpMenu);
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

    // Fix Win 11 Problem
    private boolean isFontAvailable(String fontName) {
        String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String name : availableFonts) {
            if (name.equalsIgnoreCase(fontName)) {
                return true;
            }
        }
        return false;
    }

}
