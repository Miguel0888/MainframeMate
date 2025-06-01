package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.PluginManager;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.service.LlamaCppChatManager;
import de.bund.zrb.service.LocalAiChatManager;
import de.bund.zrb.service.OllamaChatManager;
import de.bund.zrb.ui.commands.*;
import de.bund.zrb.helper.BookmarkHelper;
import de.bund.zrb.helper.SettingsHelper;
import de.zrb.bund.api.*;
import de.zrb.bund.newApi.ToolRegistry;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static de.bund.zrb.util.StringUtil.tryParseInt;
import static de.bund.zrb.util.StringUtil.unquote;

public class MainFrame extends JFrame implements MainframeContext {

    private TabbedPaneManager tabManager;
    private ActionToolbar actionToolbar;
    private BookmarkDrawer bookmarkDrawer;
    private ChatDrawer chatDrawer;
    private volatile ChatManager chatManager;
    private JSplitPane rightSplitPane;
    private JSplitPane leftSplitPane;
    private ToolRegistry toolregistry;


    @Override
    public Map<String, String> loadPluginSettings(String pluginKey) {
        Settings settings = SettingsHelper.load();
        return settings.pluginSettings.computeIfAbsent(pluginKey, k -> new LinkedHashMap<>());
    }

    @Override
    public void savePluginSettings(String pluginKey, Map<String, String> newValues) {
        Settings settings = SettingsHelper.load();
        settings.pluginSettings.put(pluginKey, new LinkedHashMap<>(newValues));
        SettingsHelper.save(settings);
    }
    
    public MainFrame() {
        this.toolregistry = ToolRegistryImpl.getInstance();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose(); // sauber beenden
                System.exit(0); // oder dispatchEvent(new WindowEvent(..., WINDOW_CLOSING));
            }
        });

        // Sprache explizit setzen (nur zu Demo-Zwecken):
        Locale.setDefault(Locale.GERMAN); // oder Locale.ENGLISH
        chatManager = getAiService();

        setTitle("MainframeMate");
        setCompatibleFontIfNecessary();
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        restoreWindowState();
        initUI();

        final FtpManager ftpManager = new FtpManager();
        if (ConnectDialog.connectIfNeeded(this, ftpManager)) {
            tabManager.addTab(new ConnectionTab(ftpManager, tabManager));
        }
    }

    private ChatManager getAiService() {
        Settings settings = SettingsHelper.load();
        String providerName = settings.aiConfig.getOrDefault("provider", "DISABLED");

        AiProvider provider;
        try {
            provider = AiProvider.valueOf(providerName);
        } catch (IllegalArgumentException ex) {
            provider = AiProvider.DISABLED;
        }

        switch (provider) {
            case OLLAMA:
                return new OllamaChatManager(); // verwendet intern settings.aiConfig
            case LOCAL_AI:
                return new LocalAiChatManager(); // analog auf settings.aiConfig zugreifen
            case LLAMA_CPP_SERVER:
                return new LlamaCppChatManager();
            default:
                return null; // DISABLED oder unbekannt
        }
    }


    private void registerCoreCommands() {
        CommandRegistry.register(new SaveCommand(tabManager));
        CommandRegistry.register(new ConnectCommand(this, tabManager));
        CommandRegistry.register(new ExitCommand());
        CommandRegistry.register(new ShowSettingsDialogCommand(this));
        CommandRegistry.register(new ShowToolDialogCommand(this));
        CommandRegistry.register(new ShowFeatureDialogCommand(this));
        CommandRegistry.register(new ShowAboutDialogCommand(this));

        // Advanced
        CommandRegistry.register(new BookmarkCommand(this));

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
        System.out.println("defaultCharset: " + Charset.defaultCharset());

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

        // Initialisiere die mittlere Komponente
        Component tabContent = tabManager.getComponent();

        // Rechts: ChatDrawer mit SplitPane
        Component withChat = initChatDrawer(tabContent);

        // Links: BookmarkDrawer mit SplitPane
        Component withBookmarks = initBookmarkDrawer(withChat);

        // Das ist dann der eigentliche Inhalt
        add(withBookmarks, BorderLayout.CENTER);
    }

    private Component initChatDrawer(Component content) {
        if (chatManager == null) {
            System.err.println("⚠️ Kein ChatService verfügbar – Eingabe wird ignoriert");
        }
        chatDrawer = new ChatDrawer(chatManager);

        rightSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, content, chatDrawer);
        int defaultDivider = content.getPreferredSize().width - 300;

        Settings settings = SettingsHelper.load();
        String dividerValue = settings.applicationState.get("drawer.chat.divider");

        int divider = tryParseInt(dividerValue, defaultDivider);
        rightSplitPane.setDividerLocation(divider);
        rightSplitPane.setResizeWeight(1.0);
        rightSplitPane.setOneTouchExpandable(true);
        return rightSplitPane;
    }



    private Component initBookmarkDrawer(Component content) {
        bookmarkDrawer = new BookmarkDrawer(path -> {
            final FtpManager ftpManager = new FtpManager();
            if (ConnectDialog.show(this, ftpManager)) {
                try {
                    FtpFileBuffer buffer = ftpManager.open(unquote(path));
                    if (buffer != null) {
                        tabManager.openFileTab(ftpManager, buffer);
                    } else {
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

        leftSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bookmarkDrawer, content);
        leftSplitPane.setOneTouchExpandable(true);

        Settings settings = SettingsHelper.load();
        String dividerValue = settings.applicationState.get("drawer.bookmark.divider");

        int divider = tryParseInt(dividerValue, 220); // Fallback default
        leftSplitPane.setDividerLocation(divider);

        return leftSplitPane;
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

    public TabbedPaneManager getTabManager() {
        return tabManager;
    }

    @Override
    public Optional<TabAdapter> getSelectedTab() {
        return tabManager.getSelectedTab()
                .filter(tab -> tab instanceof TabAdapter)
                .map(tab -> (TabAdapter) tab);
    }


    @Override
    public void openFileTab(String content) {
        getTabManager().openFileTab(content);
    }

    @Override
    public JFrame getMainFrame() {
        return this;
    }

    @Override
    public BookmarkManager getBookmarkManager() {
        return new BookmarkHelper();
    }

    @Override
    public List<TabAdapter> getAllFileTabs() {
        return tabManager.getAllTabs().stream()
                .filter(t -> t instanceof TabAdapter)
                .map(t -> (TabAdapter) t)
                .collect(Collectors.toList());
    }

    @Override
    public void focusFileTab(TabAdapter tab) {
        tabManager.focusTabByAdapter(tab);
    }

    @Override
    public void refresh() {
        SwingUtilities.invokeLater(() -> bookmarkDrawer.refreshBookmarks());
        // ToDo: And mayby active tabs too..
    }

    private void restoreWindowState() {
        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        // Fenstergröße
        int width = tryParseInt(state.get("window.width"), 1000);
        int height = tryParseInt(state.get("window.height"), 700);
        setSize(width, height);

        // Fensterposition
        int x = tryParseInt(state.get("window.x"), -1);
        int y = tryParseInt(state.get("window.y"), -1);
        if (x >= 0 && y >= 0) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(null);
        }

        // Maximierungsstatus (muss nach setSize erfolgen)
        int extendedState = tryParseInt(state.get("window.extendedState"), JFrame.NORMAL);
        setExtendedState(extendedState);
    }

    private void saveApplicationState() {
        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        saveWindowState(state);
        saveDrawerState(state);

        SettingsHelper.save(settings);
    }

    private void saveWindowState(Map<String, String> state) {
        // Allgemeine Fensterinformationen
        state.put("window.width", String.valueOf(getWidth()));
        state.put("window.height", String.valueOf(getHeight()));
        state.put("window.x", String.valueOf(getX()));
        state.put("window.y", String.valueOf(getY()));
        state.put("window.extendedState", String.valueOf(getExtendedState()));
    }

    private void saveDrawerState(Map<String, String> state) {
        // Drawer-Zustände
        if (leftSplitPane != null) {
            state.put("drawer.bookmark.divider", String.valueOf(leftSplitPane.getDividerLocation()));
        }

        if (rightSplitPane != null) {
            state.put("drawer.chat.divider", String.valueOf(rightSplitPane.getDividerLocation()));
        }

        // ChatDrawer-interne Settings
        if (chatDrawer != null) {
            chatDrawer.addApplicationState(state);
        }

    }

    @Override
    public void dispose() {
        chatManager.onDispose();
        saveApplicationState();
        super.dispose();
    }

    @Override
    public ToolRegistry getToolRegistry() {
        return toolregistry;
    }
}
