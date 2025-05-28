package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.plugins.PluginManager;
import de.bund.zrb.plugins.excel.ExcelImportPlugin;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;

public class MainFrame extends JFrame {

    private TabbedPaneManager tabManager;
    private ActionToolbar actionToolbar;
    private BookmarkDrawer bookmarkDrawer;

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
        tabManager = new TabbedPaneManager();

        // 1. Plugins registrieren (aber noch nicht initialisieren!)
        registerPlugins();

        // 2. Menü erstellen → JMenuBar existiert jetzt
        setJMenuBar(createMenuBar());

        // 3. Layout
        setLayout(new BorderLayout());

        // Toolbar ganz oben
        actionToolbar = new ActionToolbar(this);
        add(actionToolbar, BorderLayout.NORTH);

        // SplitPane mit Content und BookmarkDrawer links und Tabs rechts
        bookmarkDrawer = new BookmarkDrawer(path -> {
            final FtpManager ftpManager = new FtpManager();
            if (ConnectDialog.show(this, ftpManager)) {
                ConnectionTab tab = new ConnectionTab(ftpManager, tabManager);
                tabManager.addTab(tab);
                tab.loadDirectory(path);
            }
        });
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bookmarkDrawer, tabManager.getComponent());
        splitPane.setDividerLocation(220); // oder settings-basiert
        splitPane.setOneTouchExpandable(true);
        add(splitPane, BorderLayout.CENTER);

        // ✅ 4. Jetzt ist Menü verfügbar → Plugins initialisieren
        PluginManager.initializePlugins(this);
    }



    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createFileMenu());
        menuBar.add(createSettingsMenu());
        menuBar.add(createHelpMenu());
        return menuBar;
    }

    private JMenu createFileMenu() {
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
        return fileMenu;
    }

    private JMenu createSettingsMenu() {
        JMenu settingsMenu = new JMenu("Einstellungen");

        JMenuItem settingsItem = new JMenuItem("Allgemein...");
        settingsItem.addActionListener(e -> {
            FtpManager dummy = new FtpManager();
            SettingsDialog.show(this, dummy);
        });
        settingsMenu.add(settingsItem);

        PluginManager.getPlugins().forEach(plugin ->
                plugin.getSettingsMenuItem(this).ifPresent(settingsMenu::add)
        );

        return settingsMenu;
    }

    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu("Hilfe");

        JMenuItem featureItem = new JMenuItem("📋 Server-Features anzeigen");
        featureItem.addActionListener(e -> FeatureDialog.show(this));
        helpMenu.add(featureItem);

        JMenuItem aboutItem = new JMenuItem("ℹ️ Über MainframeMate");
        aboutItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                    "MainframeMate\nVersion 1.1.0\n© 2025 GZD",
                    "Über", JOptionPane.INFORMATION_MESSAGE);
        });
        helpMenu.add(aboutItem);

        return helpMenu;
    }

    @Deprecated
    private BookmarkToolbar createBookmarkToolbar() {
        return new BookmarkToolbar(path -> {
            final FtpManager ftpManager = new FtpManager();
            if (ConnectDialog.show(this, ftpManager)) {
                ConnectionTab tab = new ConnectionTab(ftpManager, tabManager);
                tabManager.addTab(tab);
                tab.loadDirectory(path);
            }
        });
    }

    public BookmarkDrawer getBookmarkDrawer() {
        return bookmarkDrawer;
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Plugin-Management
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // ToDo: Extend with ServiceLoader for external plugins / JARs
    private void registerPlugins() {
        PluginManager.registerPlugin(new ExcelImportPlugin());
    }

    public JMenu getOrCreatePluginMenu() {
        JMenuBar menuBar = getJMenuBar();
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if ("Plugins".equals(menu.getText())) {
                return menu;
            }
        }
        JMenu pluginMenu = new JMenu("Plugins");
        menuBar.add(pluginMenu);
        return pluginMenu;
    }


    public TabbedPaneManager getTabManager() {
        return tabManager;
    }
}
