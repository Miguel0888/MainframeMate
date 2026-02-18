package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.helper.ShortcutManager;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.mcp.FilterColumnTool;
import de.bund.zrb.mcp.OpenFileTool;
import de.bund.zrb.mcp.SetVariableTool;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.ExpressionRegistryImpl;
import de.bund.zrb.runtime.PluginManager;
import de.bund.zrb.runtime.SentenceTypeRegistryImpl;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.service.*;
import de.bund.zrb.ui.commands.*;
import de.bund.zrb.helper.BookmarkHelper;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ui.commands.config.CommandRegistryImpl;
import de.bund.zrb.ui.commands.config.MenuTreeBuilder;
import de.bund.zrb.ui.commands.config.ShowShortcutConfigMenuCommand;
import de.bund.zrb.ui.commands.sub.FocusSearchFieldCommand;
import de.bund.zrb.ui.commands.sub.ShowComparePanelCommand;
import de.bund.zrb.ui.lock.ApplicationLocker;
import de.bund.zrb.ui.drawer.LeftDrawer;
import de.bund.zrb.ui.drawer.RightDrawer;
import de.bund.zrb.ui.file.DragAndDropImportHandler;
import de.bund.zrb.runtime.VariableRegistryImpl;
import de.bund.zrb.workflow.WorkflowRunnerImpl;
import de.zrb.bund.api.*;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.ui.FileTab;
import de.zrb.bund.newApi.ui.FtpTab;
import de.zrb.bund.newApi.workflow.WorkflowRunner;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static de.bund.zrb.util.StringUtil.tryParseInt;
import static de.bund.zrb.util.StringUtil.unquote;

public class MainFrame extends JFrame implements MainframeContext {
    private final ApplicationLocker locker;
    private TabbedPaneManager tabManager;
    private ActionToolbar actionToolbar;
    private LeftDrawer leftDrawer;
    private RightDrawer rightDrawer;
    private volatile ChatManager chatManager;
    private JSplitPane rightSplitPane;
    private JSplitPane leftSplitPane;
    private final ToolRegistry toolRegistry;
    private final VariableRegistryImpl variableRegistryImpl;
    private final McpService mcpService;
    private final WorkflowRunner workflowRunner;
    private DragAndDropImportHandler importHandler;

    // Builds the menu
    private void registerCoreCommands() {
        CommandRegistryImpl.register(new SaveMenuCommand(tabManager));
        CommandRegistryImpl.register(new SaveAndCloseMenuCommand(tabManager));
        CommandRegistryImpl.register(new ConnectMenuCommand(this, tabManager));
        CommandRegistryImpl.register(new ConnectLocalMenuCommand(this, tabManager));
        CommandRegistryImpl.register(new ExitMenuCommand());
        CommandRegistryImpl.register(new ShowSettingsDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowServerSettingsDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowSentenceDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowExpressionEditorMenuCommand(this));
        CommandRegistryImpl.register(new ShowToolDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowFeatureDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowAboutDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowShortcutConfigMenuCommand(this));

        // Advanced
        CommandRegistryImpl.register(new BookmarkMenuCommand(this));

        // Sub Commands
        CommandRegistryImpl.register(new ShowComparePanelCommand(this));
        CommandRegistryImpl.register(new FocusSearchFieldCommand(this));
    }

    // MCP Tools
    private void registerTools() {
        toolRegistry.registerTool(new OpenFileTool(this));
        toolRegistry.registerTool(new FilterColumnTool(this));
        toolRegistry.registerTool(new SetVariableTool(this));
    }

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
        locker = new ApplicationLocker(this, LoginManager.getInstance());
        LoginManager.getInstance().setCredentialsProvider(locker); // ← locker übernimmt Login
        locker.start();
        this.toolRegistry = ToolRegistryImpl.getInstance();
        this.variableRegistryImpl = VariableRegistryImpl.getInstance();
        this.mcpService = new McpServiceImpl(toolRegistry);
        this.workflowRunner = new WorkflowRunnerImpl(this, mcpService, getExpressionRegistry());
        registerTools();

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
        tabManager = new TabbedPaneManager(this);

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

        initDragAndDropImport();
        intiShortcuts();
    }

    private void intiShortcuts() {
        ShortcutManager.loadShortcuts();
        ShortcutManager.registerGlobalShortcuts(getRootPane());
    }

    private Component initChatDrawer(Component content) {
        if (chatManager == null) {
            System.err.println("⚠️ Kein ChatService verfügbar – Eingabe wird ignoriert");
        }
        rightDrawer = new RightDrawer(this, chatManager, toolRegistry, mcpService);

        rightSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, content, rightDrawer);
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
        leftDrawer = new LeftDrawer(this::openFileOrDirectory);

        leftSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftDrawer, content);
        leftSplitPane.setOneTouchExpandable(true);

        Settings settings = SettingsHelper.load();
        String dividerValue = settings.applicationState.get("drawer.bookmark.divider");

        int divider = tryParseInt(dividerValue, 220); // Fallback default
        leftSplitPane.setDividerLocation(divider);

        return leftSplitPane;
    }

    private void initDragAndDropImport() {
        this.importHandler = new DragAndDropImportHandler(this);
        this.importHandler.init();
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
    public Optional<Bookmarkable> getSelectedTab() {
        return tabManager.getSelectedTab()
                .filter(tab -> tab instanceof Bookmarkable)
                .map(tab -> (Bookmarkable) tab);
    }


    @Override
    public FileTab createFile(String content, String sentenceType) {
        final FtpManager ftpManager = new FtpManager(); // ToDo: Use login manager here
        return getTabManager().openFileTab(ftpManager, content, sentenceType);
    }

    @Override
    public FtpTab openFileOrDirectory(String path) {
        return openFileOrDirectory(path, null);
    }

    @Override
    public FtpTab openFileOrDirectory(String path, @Nullable String sentenceType) {
        return openFileOrDirectory(path, null, null);
    }

    @Override
    public FtpTab openFileOrDirectory(String path, @Nullable String sentenceType, String searchPattern) {
        return openFileOrDirectory(path, null, null, null);
    }

    @Override
    public FtpTab openFileOrDirectory(String path, @Nullable String sentenceType, String searchPattern, Boolean toCompare) {
        final FtpManager ftpManager = new FtpManager();
        Settings settings = SettingsHelper.load();

        try {
            ftpManager.connect(settings.host, settings.user);

            String unquoted = unquote(path);

            // 1) Try to open as file first (stateless-by-path intent)
            try {
                FtpFileBuffer buffer = ftpManager.open(unquoted);
                if (buffer != null) {
                    return tabManager.openFileTab(ftpManager, buffer, sentenceType, searchPattern, toCompare);
                }
            } catch (IOException ignore) {
                // fall back to connection tab
            }

            // 2) Otherwise open connection tab and start browsing at the provided path
            ConnectionTabImpl tab = new ConnectionTabImpl(ftpManager, tabManager, searchPattern);
            tabManager.addTab(tab);
            tab.loadDirectory(unquoted);
            return tab;

        } catch (IOException ex) {
            String msg = ex.getMessage();

            if ("Kein Passwort verfügbar".equals(msg)) {
                // Benutzer hat abgebrochen → stillschweigend beenden
                return null;
            }

            if (msg != null && msg.contains("not found")) {
                // Datei existiert (noch) nicht → Polling-Tab öffnen
                tabManager.addTab(new JobPollingTab(ftpManager, tabManager, unquote(path), sentenceType, searchPattern, toCompare));
                return null;
            }

            JOptionPane.showMessageDialog(this, "Fehler beim Öffnen:\n" + msg,
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }
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
    public List<Bookmarkable> getAllFileTabs() {
        return tabManager.getAllTabs().stream()
                .filter(t -> t instanceof Bookmarkable)
                .map(t -> (Bookmarkable) t)
                .collect(Collectors.toList());
    }

    @Override
    public void focusFileTab(Bookmarkable tab) {
        tabManager.focusTabByAdapter(tab);
    }

    @Override
    public void refresh() {
        SwingUtilities.invokeLater(() -> leftDrawer.refreshBookmarks());
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
        ShortcutManager.saveShortcuts();
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
        if (rightDrawer != null) {
            rightDrawer.addApplicationState(state);
        }

    }

    @Override
    public void dispose() {
        if(chatManager != null)
        {
            chatManager.onDispose();
        }
        saveApplicationState();
        super.dispose();
    }

    @Override
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    @Override
    public VariableRegistryImpl getVariableRegistry() {
        return variableRegistryImpl;
    }

    @Override
    public SentenceTypeRegistry getSentenceTypeRegistry() {
        return SentenceTypeRegistryImpl.getInstance();
    }

    @Override
    public ExpressionRegistry getExpressionRegistry() {
        return ExpressionRegistryImpl.getInstance();
    }

    @Override
    public File getSettingsFolder() {
        return SettingsHelper.getSettingsFolder();
    }

    @Override
    public WorkflowRunner getWorkflowRunner() {
        return workflowRunner;
    }

    public LeftDrawer getBookmarkDrawer() {
        return leftDrawer;
    }
}
