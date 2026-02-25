package de.bund.zrb.ui;

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
import de.bund.zrb.ui.toolbar.MainframeMateToolbarCommandRegistry;
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

import com.example.toolbarkit.command.ToolbarCommandRegistry;
import com.example.toolbarkit.config.JsonToolbarConfigRepository;
import com.example.toolbarkit.config.ToolbarConfigRepository;
import com.example.toolbarkit.toolbar.ConfigurableCommandToolbar;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static de.bund.zrb.util.StringUtil.tryParseInt;

public class MainFrame extends JFrame implements MainframeContext {
    private final ApplicationLocker locker;
    private TabbedPaneManager tabManager;
    private ConfigurableCommandToolbar actionToolbar;
    private LeftDrawer leftDrawer;
    private RightDrawer rightDrawer;
    private volatile ChatManager chatManager;
    private JSplitPane rightSplitPane;
    private JSplitPane leftSplitPane;
    private final ToolRegistry toolRegistry;
    private final VariableRegistryImpl variableRegistryImpl;
    private final McpService mcpService;
    private final WorkflowRunner workflowRunner;
    private final de.bund.zrb.service.McpChatEventBridge chatEventBridge;
    private DragAndDropImportHandler importHandler;

    // Builds the menu
    private void registerCoreCommands() {
        CommandRegistryImpl.register(new SaveMenuCommand(tabManager));
        CommandRegistryImpl.register(new SaveAndCloseMenuCommand(tabManager));
        CommandRegistryImpl.register(new ConnectMenuCommand(this, tabManager));
        CommandRegistryImpl.register(new ConnectLocalMenuCommand(this, tabManager));
        CommandRegistryImpl.register(new ConnectNdvMenuCommand(this, tabManager));
        CommandRegistryImpl.register(new ConnectMailMenuCommand(this, tabManager));
        CommandRegistryImpl.register(new OpenArchiveMenuCommand(this, tabManager));
        CommandRegistryImpl.register(new ExitMenuCommand());
        CommandRegistryImpl.register(new ShowSettingsDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowServerSettingsDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowMailSettingsDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowSentenceDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowExpressionEditorMenuCommand(this));
        CommandRegistryImpl.register(new ShowToolDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowFeatureDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowAboutDialogMenuCommand(this));
        CommandRegistryImpl.register(new ShowShortcutConfigMenuCommand(this));
        CommandRegistryImpl.register(new ShowIndexingControlPanelMenuCommand(this));

        // Advanced
        CommandRegistryImpl.register(new BookmarkMenuCommand(this));

        // Sub Commands
        CommandRegistryImpl.register(new ShowComparePanelCommand(this));
        CommandRegistryImpl.register(new FocusSearchFieldCommand(this));
        CommandRegistryImpl.register(new SearchMenuCommand(this, tabManager));
    }

    // MCP Tools
    private void registerTools() {
        toolRegistry.registerTool(new OpenFileTool(this));
        toolRegistry.registerTool(new de.bund.zrb.mcp.ReadFileTool(this));
        toolRegistry.registerTool(new de.bund.zrb.mcp.SearchFileTool(this));
        toolRegistry.registerTool(new de.bund.zrb.mcp.StatPathTool(this));
        toolRegistry.registerTool(new de.bund.zrb.mcp.GrepSearchTool(this));
        toolRegistry.registerTool(new de.bund.zrb.mcp.ClockTimerTool(this));
        toolRegistry.registerTool(new FilterColumnTool(this));
        toolRegistry.registerTool(new SetVariableTool(this));

        // Attachment RAG Tools
        toolRegistry.registerTool(new de.bund.zrb.mcp.ListAttachmentsTool(this));
        toolRegistry.registerTool(new de.bund.zrb.mcp.SearchAttachmentsTool(this));
        toolRegistry.registerTool(new de.bund.zrb.mcp.ReadChunksTool(this));
        toolRegistry.registerTool(new de.bund.zrb.mcp.ReadDocumentWindowTool(this));

        // Global Search Tool (searches Lucene index across all sources)
        toolRegistry.registerTool(new de.bund.zrb.mcp.SearchIndexTool(this));
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
        this.chatEventBridge = new de.bund.zrb.service.McpChatEventBridge();
        this.mcpService = new McpServiceImpl(toolRegistry, chatEventBridge);
        this.workflowRunner = new WorkflowRunnerImpl(this, mcpService, getExpressionRegistry());
        registerTools();


        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                de.bund.zrb.runtime.PluginManager.shutdownAll();
                de.bund.zrb.mcp.registry.McpServerManager.getInstance().stopAll();
                dispose(); // sauber beenden
                System.exit(0); // oder dispatchEvent(new WindowEvent(..., WINDOW_CLOSING));
            }
        });

        // Sprache explizit setzen (nur zu Demo-Zwecken):
        Locale.setDefault(Locale.GERMAN); // oder Locale.ENGLISH
        chatManager = getAiService();

        setTitle("MainframeMate");

        // Apply branding icons to this window (multi-size for OS/taskbar selection)
        java.util.List<java.awt.Image> brandIcons = de.bund.zrb.ui.branding.IconThemeInstaller.getAppIcons();
        if (!brandIcons.isEmpty()) {
            setIconImages(brandIcons);
        }

        setCompatibleFontIfNecessary();
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        restoreWindowState();
        initUI();

        // Start enabled MCP servers (after plugins have registered via initUI → PluginManager)
        de.bund.zrb.mcp.registry.McpServerManager.getInstance().startEnabledServers();
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
            case CLOUD:
                return new CloudChatManager();
            case LOCAL_AI:
                return new LocalAiChatManager(); // analog auf settings.aiConfig zugreifen
            case LLAMA_CPP_SERVER:
                return new LlamaCppChatManager();
            case CUSTOM:
                return new CustomChatManager(); // selbstgehosteter Server mit erweiterten Optionen
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

        // Toolbar ganz oben (nach Plugin-Init, damit Plugin-Commands verfügbar sind)
        ToolbarCommandRegistry toolbarRegistry = new MainframeMateToolbarCommandRegistry();
        Path toolbarConfigFile = Paths.get(SettingsHelper.getSettingsFolder().getAbsolutePath(), "toolbar.json");
        ToolbarConfigRepository toolbarRepo = new JsonToolbarConfigRepository(toolbarConfigFile);
        actionToolbar = new ConfigurableCommandToolbar(toolbarRegistry, toolbarRepo);
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

        // Register live settings listener – applies changes without restart
        SettingsHelper.addChangeListener(this::onSettingsChanged);
    }

    /** Called whenever settings are saved – applies changes to running UI components. */
    private void onSettingsChanged(Settings s) {
        SwingUtilities.invokeLater(() -> {
            // 1. Update editor font/margin on all open RSyntaxTextArea instances
            Font editorFont = new Font(s.editorFont, Font.PLAIN, s.editorFontSize);
            applyFontRecursively(tabManager.getComponent(), editorFont, s.marginColumn);

            // 2. Re-apply log levels
            de.bund.zrb.util.AppLogger.applySettings();
        });
    }

    /** Recursively find all RSyntaxTextArea components and apply font + margin settings. */
    private void applyFontRecursively(Component root, Font font, int marginColumn) {
        if (root instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) {
            org.fife.ui.rsyntaxtextarea.RSyntaxTextArea area = (org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) root;
            area.setFont(font);
            if (marginColumn > 0) {
                area.setMarginLineEnabled(true);
                area.setMarginLinePosition(marginColumn);
            } else {
                area.setMarginLineEnabled(false);
            }
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                applyFontRecursively(child, font, marginColumn);
            }
        }
    }

    private void intiShortcuts() {
        ShortcutManager.loadShortcuts();
        ShortcutManager.registerGlobalShortcuts(getRootPane());
    }

    private Component initChatDrawer(Component content) {
        if (chatManager == null) {
            System.err.println("⚠️ Kein ChatService verfügbar – Eingabe wird ignoriert");
        }
        rightDrawer = new RightDrawer(this, chatManager, toolRegistry, mcpService, chatEventBridge);

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
        leftDrawer = new LeftDrawer(this::openBookmark);

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
        VirtualResource res = new VirtualResource(de.bund.zrb.files.path.VirtualResourceRef.of(""),
                VirtualResourceKind.FILE,
                null,
                true);
        return getTabManager().openFileTab(res, content, sentenceType, null, false);
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
        if (path == null || path.isEmpty()) return null;

        // Route mail:// paths to the mail-opening logic (same as bookmarks)
        if (path.startsWith(de.bund.zrb.files.path.VirtualResourceRef.MAIL_PREFIX)) {
            String mailPath = path.substring(de.bund.zrb.files.path.VirtualResourceRef.MAIL_PREFIX.length());
            openMailBookmark(mailPath);
            return null; // opened async via SwingWorker
        }

        // Route ndv:// paths to the NDV-opening logic (same as bookmarks)
        if (path.startsWith(de.bund.zrb.files.path.VirtualResourceRef.NDV_PREFIX)) {
            String ndvPath = path.substring(de.bund.zrb.files.path.VirtualResourceRef.NDV_PREFIX.length());
            // Create a minimal BookmarkEntry with the raw path – openNdvFileBookmark
            // handles the fallback (no NDV metadata) via resolvePath(rawPath)
            de.bund.zrb.model.BookmarkEntry ndvEntry = new de.bund.zrb.model.BookmarkEntry();
            ndvEntry.path = de.bund.zrb.model.BookmarkEntry.PREFIX_NDV + ndvPath;
            ndvEntry.resourceKind = "FILE";
            openNdvFileBookmark(ndvEntry);
            return null; // opened async
        }

        return new VirtualResourceOpener(tabManager)
                .open(path, sentenceType, searchPattern, toCompare);
    }

    /**
     * Open a bookmark – routes to the correct backend based on the bookmark's protocol prefix.
     */
    private void openBookmark(de.bund.zrb.model.BookmarkEntry entry) {
        if (entry == null || entry.path == null) return;
        String backend = entry.getBackendType();
        String rawPath = entry.getRawPath();
        boolean isFile = !"DIRECTORY".equals(entry.resourceKind); // bookmarks from FileTabs are always files

        switch (backend) {
            case "FTP":
                // Use ftp: prefix so VirtualResourceResolver routes it to FTP
                // forceFile=true skips the list() probe that misclassifies MVS members as directories
                new VirtualResourceOpener(tabManager)
                        .open("ftp:" + rawPath, null, null, null, isFile);
                break;
            case "NDV":
                if ("DIRECTORY".equals(entry.resourceKind)) {
                    openNdvDirectoryBookmark(rawPath);
                } else {
                    openNdvFileBookmark(entry);
                }
                break;
            case "MAIL":
                openMailBookmark(rawPath);
                break;
            default:
                // LOCAL – forceFile avoids unnecessary list() probe
                new VirtualResourceOpener(tabManager)
                        .open(rawPath, null, null, null, isFile);
                break;
        }
    }

    /**
     * Open a mail bookmark. rawPath format: "mailboxPath#folderPath#descriptorNodeId"
     */
    private void openMailBookmark(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) return;

        // Parse: mailboxPath#folderPath#nodeId
        String[] parts = rawPath.split("#", 3);
        if (parts.length < 3) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Ungültiges Mail-Bookmark-Format:\n" + rawPath,
                    "Mail-Bookmark", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        String mailboxPath = parts[0];
        String folderPath = parts[1];
        long nodeId;
        try {
            nodeId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Ungültige Nachrichten-ID im Bookmark:\n" + parts[2],
                    "Mail-Bookmark", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Load message in background
        javax.swing.SwingWorker<de.bund.zrb.mail.model.MailMessageContent, Void> worker =
                new javax.swing.SwingWorker<de.bund.zrb.mail.model.MailMessageContent, Void>() {
            @Override
            protected de.bund.zrb.mail.model.MailMessageContent doInBackground() throws Exception {
                de.bund.zrb.mail.infrastructure.PstMailboxReader reader =
                        new de.bund.zrb.mail.infrastructure.PstMailboxReader();
                return reader.readMessage(mailboxPath, folderPath, nodeId);
            }

            @Override
            protected void done() {
                try {
                    de.bund.zrb.mail.model.MailMessageContent content = get();
                    de.bund.zrb.ui.mail.MailPreviewTab tab =
                            new de.bund.zrb.ui.mail.MailPreviewTab(content, mailboxPath);
                    tabManager.addTab(tab);
                } catch (Exception e) {
                    javax.swing.JOptionPane.showMessageDialog(MainFrame.this,
                            "Fehler beim Öffnen der Mail aus Bookmark:\n" + e.getMessage(),
                            "Mail-Bookmark", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Open an NDV directory bookmark: connect, open NdvConnectionTab, navigate to library.
     */
    private void openNdvDirectoryBookmark(String rawPath) {
        // rawPath format: "LIBRARY/OBJECTNAME" or just "LIBRARY"
        Settings settings = SettingsHelper.load();
        String host = settings.host;
        String user = settings.user;
        int port = settings.ndvPort;

        if (host == null || host.isEmpty() || user == null || user.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Bitte zuerst Server-Einstellungen konfigurieren.",
                    "NDV-Verbindung", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        String password = LoginManager.getInstance().getPassword(host, user);
        if (password == null || password.isEmpty()) return;

        // Parse library and object name from rawPath
        String library = "";
        String objectName = null;
        if (rawPath != null && !rawPath.isEmpty()) {
            if (rawPath.contains("/")) {
                int slash = rawPath.indexOf('/');
                library = rawPath.substring(0, slash);
                objectName = rawPath.substring(slash + 1);
                if (objectName != null && objectName.isEmpty()) objectName = null;
            } else {
                library = rawPath;
            }
        }
        // Fallback to default library from settings if raw path was empty
        if (library.isEmpty() && settings.ndvDefaultLibrary != null && !settings.ndvDefaultLibrary.trim().isEmpty()) {
            library = settings.ndvDefaultLibrary.trim();
        }

        final String fHost = host;
        final String fUser = user;
        final int fPort = port;
        final String fPassword = password;
        final String fLibrary = library.toUpperCase();
        final String fObjectName = objectName;

        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

        // Only connect in background, create UI on EDT
        new javax.swing.SwingWorker<de.bund.zrb.ndv.NdvService, Void>() {
            @Override
            protected de.bund.zrb.ndv.NdvService doInBackground() throws Exception {
                de.bund.zrb.ndv.NdvService service = new de.bund.zrb.ndv.NdvService();
                service.connect(fHost, fPort, fUser, fPassword);
                LoginManager.getInstance().onLoginSuccess(fHost, fUser);

                return service;
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    de.bund.zrb.ndv.NdvService service = get();
                    // Create tab on EDT - skip auto-load if we navigate to library immediately
                    boolean hasLibrary = !fLibrary.isEmpty();
                    NdvConnectionTab tab = new NdvConnectionTab(tabManager, service, !hasLibrary);
                    tabManager.addTab(tab);
                    // Navigate to library (and optionally auto-open object)
                    if (hasLibrary) {
                        if (fObjectName != null && !fObjectName.isEmpty()) {
                            tab.navigateToLibraryAndOpen(fLibrary, fObjectName);
                        } else {
                            tab.navigateToLibrary(fLibrary);
                        }
                    }
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    if (msg != null && (msg.contains("Login") || msg.contains("login")
                            || msg.contains("NAT0873") || msg.contains("NAT7734"))) {
                        LoginManager.getInstance().invalidatePassword(fHost, fUser);
                    }
                    javax.swing.JOptionPane.showMessageDialog(MainFrame.this,
                            "NDV-Verbindung fehlgeschlagen:\n" + msg,
                            "Fehler", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Open an NDV FILE bookmark directly: connect, read source, open FileTab.
     * Uses the NDV metadata stored in the bookmark entry (objectName, type, dbid, fnr)
     * so no ConnectionTab is needed.
     * Falls back to directory flow if metadata is missing (legacy bookmarks).
     */
    private void openNdvFileBookmark(de.bund.zrb.model.BookmarkEntry entry) {
        Settings settings = SettingsHelper.load();
        String host = settings.host;
        String user = settings.user;
        int port = settings.ndvPort;

        if (host == null || host.isEmpty() || user == null || user.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Bitte zuerst Server-Einstellungen konfigurieren.",
                    "NDV-Verbindung", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        String password = LoginManager.getInstance().getPassword(host, user);
        if (password == null || password.isEmpty()) return;

        // Use NdvService resolver to parse the path and reconstruct NdvObjectInfo
        de.bund.zrb.ndv.NdvService tempResolver = new de.bund.zrb.ndv.NdvService();
        final de.bund.zrb.ndv.NdvService.ResolvedNdvPath resolved;

        if (entry.ndvLibrary != null && !entry.ndvLibrary.isEmpty()
                && entry.ndvObjectName != null && !entry.ndvObjectName.isEmpty()) {
            // Rich metadata from bookmark: use the full resolver with DBID/FNR
            resolved = tempResolver.resolvePath(
                    entry.ndvLibrary + "/" + entry.ndvObjectName
                            + (entry.ndvTypeExtension != null && !entry.ndvTypeExtension.isEmpty()
                            ? "." + entry.ndvTypeExtension : ""),
                    entry.ndvObjectType,
                    entry.ndvTypeExtension,
                    entry.ndvDbid,
                    entry.ndvFnr
            );
        } else {
            // No metadata (legacy bookmark): parse from raw path
            resolved = tempResolver.resolvePath(entry.getRawPath());
        }

        if (!resolved.isFile()) {
            // Resolved as library, not a file → fall back to directory flow
            openNdvDirectoryBookmark(entry.getRawPath());
            return;
        }

        final de.bund.zrb.ndv.NdvObjectInfo objInfo = resolved.getObjectInfo();
        final String fHost = host;
        final String fUser = user;
        final int fPort = port;
        final String fPassword = password;
        final String fLibrary = resolved.getLibrary();
        final String fullPath = fLibrary + "/" + objInfo.getName()
                + (objInfo.getTypeExtension().isEmpty() ? "" : "." + objInfo.getTypeExtension());

        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

        new javax.swing.SwingWorker<String, Void>() {
            de.bund.zrb.ndv.NdvService service;

            @Override
            protected String doInBackground() throws Exception {
                service = new de.bund.zrb.ndv.NdvService();
                service.connect(fHost, fPort, fUser, fPassword);
                LoginManager.getInstance().onLoginSuccess(fHost, fUser);
                return service.readSource(fLibrary, objInfo);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    String source = get();
                    if (source == null) source = "";

                    NdvResourceState ndvState = new NdvResourceState(service, fLibrary, objInfo);
                    VirtualResource resource = new VirtualResource(
                            de.bund.zrb.files.path.VirtualResourceRef.of(fullPath),
                            VirtualResourceKind.FILE,
                            fullPath,
                            VirtualBackendType.NDV,
                            null, ndvState
                    );

                    FileTabImpl fileTab = new FileTabImpl(
                            tabManager, resource, source, null, null, false
                    );
                    tabManager.addTab(fileTab);
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    if (msg != null && (msg.contains("Login") || msg.contains("login")
                            || msg.contains("NAT0873") || msg.contains("NAT7734"))) {
                        LoginManager.getInstance().invalidatePassword(fHost, fUser);
                    }
                    javax.swing.JOptionPane.showMessageDialog(MainFrame.this,
                            "NDV-Datei konnte nicht geöffnet werden:\n" + msg,
                            "Fehler", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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
    public List<FtpTab> getAllOpenTabs() {
        return tabManager.getAllOpenTabs();
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

    /**
     * Get the right drawer for outline updates etc.
     */
    public RightDrawer getRightDrawer() {
        return rightDrawer;
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

    public de.bund.zrb.service.McpChatEventBridge getChatEventBridge() {
        return chatEventBridge;
    }

    private java.util.UUID getActiveChatSessionIdOrNull() {
        try {
            if (rightDrawer == null) {
                return null;
            }
            // RightDrawer -> Chat -> selected tab may be a ChatSession
            java.awt.Component drawerComponent = rightDrawer;
            // Find any ChatSession within the right drawer hierarchy that is currently selected
            java.util.List<de.bund.zrb.ui.components.ChatSession> sessions = new java.util.ArrayList<>();
            findChatSessions(drawerComponent, sessions);
            if (sessions.isEmpty()) {
                return null;
            }
            // Prefer the one that is currently showing
            for (de.bund.zrb.ui.components.ChatSession s : sessions) {
                if (s != null && s.isShowing()) {
                    return s.getSessionId();
                }
            }
            return sessions.get(0).getSessionId();
        } catch (Exception ignore) {
            return null;
        }
    }

    private void findChatSessions(java.awt.Component root, java.util.List<de.bund.zrb.ui.components.ChatSession> out) {
        if (root == null || out == null) {
            return;
        }
        if (root instanceof de.bund.zrb.ui.components.ChatSession) {
            out.add((de.bund.zrb.ui.components.ChatSession) root);
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component c : ((java.awt.Container) root).getComponents()) {
                findChatSessions(c, out);
            }
        }
    }
}











