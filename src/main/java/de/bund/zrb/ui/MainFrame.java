package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.plugins.PluginManager;
import de.bund.zrb.plugins.excel.ExcelImportPlugin;
import de.bund.zrb.ui.commands.*;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;

import static de.bund.zrb.util.StringUtil.unquote;

public class MainFrame extends JFrame {

    private TabbedPaneManager tabManager;
    private ActionToolbar actionToolbar;
    private BookmarkDrawer bookmarkDrawer;
    private JMenuBar menuBar;

    public MainFrame() {
        // Sprache explizit setzen (nur zu Demo-Zwecken):
        Locale.setDefault(Locale.GERMAN); // oder Locale.ENGLISH

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

    private void registerCoreCommands() {
        CommandRegistry.register(new SaveCommand(tabManager));
        CommandRegistry.register(new ConnectCommand(this, tabManager));
        CommandRegistry.register(new ExitCommand());
        CommandRegistry.register(new ShowSettingsDialogCommand(this));
        CommandRegistry.register(new ShowFeatureDialogCommand(this));
        CommandRegistry.register(new ShowAboutDialogCommand(this));
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

        // 0. Plugins registrieren (aber noch nicht initialisieren!)
        registerPlugins();

        // 1. Command Registry
        registerCoreCommands();

        // 2. Plugins initialisieren (inkl. Command-Registrierung)
        PluginManager.initializePlugins(this);

        // 3. Menübaum aufbauen (nachdem alle Commands da sind!)
        setJMenuBar(MenuTreeBuilder.buildMenuBar());

        // 4. Layout
        setLayout(new BorderLayout());

        // Toolbar ganz oben
        actionToolbar = new ActionToolbar(this);
        add(actionToolbar, BorderLayout.NORTH);

        // SplitPane mit Content und BookmarkDrawer links und Tabs rechts
        bookmarkDrawer = new BookmarkDrawer(path -> {
            final FtpManager ftpManager = new FtpManager();
            if (ConnectDialog.show(this, ftpManager)) {
                try {
                    FtpFileBuffer buffer = ftpManager.open(unquote(path));
                    if( buffer != null) { // no DIR
                        tabManager.openFileTab(ftpManager, buffer);
                    } else { // Directory
                        ConnectionTab tab = new ConnectionTab(ftpManager, tabManager);
                        tabManager.addTab(tab);
                        tab.loadDirectory(ftpManager.getCurrentPath());
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Fehler beim Öffnen:\n" + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bookmarkDrawer, tabManager.getComponent());
        splitPane.setDividerLocation(220); // oder settings-basiert
        splitPane.setOneTouchExpandable(true);
        add(splitPane, BorderLayout.CENTER);
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
