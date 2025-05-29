package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.PluginManager;
import de.bund.zrb.service.LocalAiChatService;
import de.bund.zrb.service.OllamaChatService;
import de.bund.zrb.ui.commands.*;
import de.bund.zrb.util.BookmarkManagerImpl;
import de.bund.zrb.util.SettingsManager;
import de.zrb.bund.api.BookmarkManager;
import de.zrb.bund.api.ChatService;
import de.zrb.bund.api.TabAdapter;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static de.bund.zrb.util.StringUtil.unquote;

public class MainFrame extends JFrame implements MainframeContext {

    private TabbedPaneManager tabManager;
    private ActionToolbar actionToolbar;
    private BookmarkDrawer bookmarkDrawer;
    private ChatDrawer chatDrawer;
    private final ChatService chatService;


    @Override
    public Map<String, String> loadPluginSettings(String pluginKey) {
        Settings settings = SettingsManager.load();
        return settings.pluginSettings.computeIfAbsent(pluginKey, k -> new LinkedHashMap<>());
    }

    @Override
    public void savePluginSettings(String pluginKey, Map<String, String> newValues) {
        Settings settings = SettingsManager.load();
        settings.pluginSettings.put(pluginKey, new LinkedHashMap<>(newValues));
        SettingsManager.save(settings);
    }
    
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
        chatService = getAiService();
    }

    private ChatService getAiService() {
        final ChatService chatService;
        if(SettingsManager.load().aiProvider == AiProvider.OLLAMA) {
            chatService = new OllamaChatService();
        } else if(SettingsManager.load().aiProvider == AiProvider.LOCAL_AI) {
            chatService = new LocalAiChatService();
        } else {
            chatService = null; // disabled
        }
        return chatService;
    }

    private void registerCoreCommands() {
        CommandRegistry.register(new SaveCommand(tabManager));
        CommandRegistry.register(new ConnectCommand(this, tabManager));
        CommandRegistry.register(new ExitCommand());
        CommandRegistry.register(new ShowSettingsDialogCommand(this));
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
        chatDrawer = new ChatDrawer(userInput -> {
            String antwort = chatService.send(userInput);
            SwingUtilities.invokeLater(() -> chatDrawer.appendBotMessage(antwort));
        });

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, content, chatDrawer);
        rightSplit.setDividerLocation(content.getPreferredSize().width - 300); // oder dynamisch
        rightSplit.setResizeWeight(1.0); // Inhalt soll bei Resize wachsen
        rightSplit.setOneTouchExpandable(true);
        return rightSplit;
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

        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bookmarkDrawer, content);
        leftSplit.setDividerLocation(220);
        leftSplit.setOneTouchExpandable(true);
        return leftSplit;
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
        return new BookmarkManagerImpl();
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

}
