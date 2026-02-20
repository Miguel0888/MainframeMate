package de.bund.zrb.ui;

import de.bund.zrb.files.codec.RecordStructureCodec;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.filetab.*;
import de.bund.zrb.ui.filetab.event.*;
import de.bund.zrb.ui.preview.SplitPreviewTab;
import de.bund.zrb.helper.SettingsHelper;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceMeta;
import de.zrb.bund.newApi.ui.FileTab;
import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.api.FileWriteResult;
import de.bund.zrb.files.impl.auth.InteractiveCredentialsProvider;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.login.LoginManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Map;

import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.path.VirtualResourceRef;

/**
 * File editor tab that extends SplitPreviewTab to provide:
 * - RAW/RENDERED/SPLIT view modes with syntax highlighting (from SplitPreviewTab)
 * - Search/Filter functionality
 * - Compare panel for diff viewing
 * - Sentence type highlighting
 * - File save operations
 */
public class FileTabImpl extends SplitPreviewTab implements FileTab {

    public static final double DEFAULT_DIVIDER_LOCATION = 0.6;
    private static final int MIN_COMPARE_HEIGHT = 80; // minimum pixels for compare panel
    private double currentDividerLocation = DEFAULT_DIVIDER_LOCATION;
    private static final String DIVIDER_LOCATION_KEY = "comparePanel.dividerLocation";

    final FileTabModel model = new FileTabModel();
    final FileTabEventDispatcher dispatcher = new FileTabEventDispatcher();

    final ComparePanel comparePanel;
    final StatusBarPanel statusBarPanel = new StatusBarPanel();

    final SentenceHighlighter highlighter = new SentenceHighlighter();
    final LegendController legendController;
    final FilterCoordinator filterCoordinator;

    private final JSplitPane compareSplitPane;
    final TabbedPaneManager tabbedPaneManager;
    private final FileTabEventManager eventManager;

    private VirtualResource resource;



    @Override
    public JPopupMenu createContextMenu(Runnable onClose) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem bookmarkItem = new JMenuItem("📌 Als Lesezeichen merken");
        bookmarkItem.addActionListener(e -> {
            MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
            String link = resource != null ? resource.getResolvedPath() : null;
            main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), link);
        });

        JMenuItem saveAsItem = new JMenuItem("💾 Speichern unter...");
        saveAsItem.addActionListener(e -> saveAs());

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onClose.run());

        menu.add(bookmarkItem);
        menu.add(saveAsItem);
        menu.addSeparator();
        menu.add(closeItem);

        return menu;
    }

    private void saveAs() {
        if (resource == null) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Speichern unter... ist nicht möglich (keine Resource).",
                    "Nicht möglich", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (resource.getBackendType() == VirtualBackendType.LOCAL) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Speichern unter...");
            try {
                String current = resource.getResolvedPath();
                if (current != null && !current.trim().isEmpty()) {
                    chooser.setSelectedFile(new java.io.File(current));
                }
            } catch (Exception ignore) {
                // ignore - chooser will use default
            }

            if (chooser.showSaveDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
                java.io.File selected = chooser.getSelectedFile();
                if (selected == null) {
                    return;
                }
                String target = selected.getAbsolutePath();
                this.resource = new VirtualResource(VirtualResourceRef.of(target), VirtualResourceKind.FILE, target,
                        VirtualBackendType.LOCAL, null);
                model.setResource(this.resource);
                tabbedPaneManager.updateTitleFor(this);
                tabbedPaneManager.updateTooltipFor(this);
                saveViaFileService();
            }
            return;
        }

        // FTP: hier nehmen wir erstmal einen absoluten Pfad per Dialog.
        if (resource.getBackendType() == VirtualBackendType.NDV) {
            // NDV: "Save As" is not applicable – save back to same object
            saveViaFileService();
            return;
        }
        String suggested = resource.getResolvedPath() == null ? "" : resource.getResolvedPath();
        String target = JOptionPane.showInputDialog(mainPanel, "Zielpfad (FTP, absolut):", suggested);
        if (target == null) {
            return;
        }
        target = target.trim();
        if (target.isEmpty()) {
            return;
        }

        // Backend bleibt FTP; ftpState wird übernommen.
        this.resource = new VirtualResource(VirtualResourceRef.of(target), VirtualResourceKind.FILE, target,
                VirtualBackendType.FTP, resource.getFtpState());
        model.setResource(this.resource);
        tabbedPaneManager.updateTitleFor(this);
        tabbedPaneManager.updateTooltipFor(this);
        saveViaFileService();
    }

    /**
     * Constructor using VirtualResource + content.
     */
    public FileTabImpl(TabbedPaneManager tabbedPaneManager, VirtualResource resource, String content,
                       String sentenceType, String searchPattern, Boolean toCompare) {
        // Call SplitPreviewTab constructor
        super(extractFileName(resource), content, null, null, null,
                resource.getBackendType() == VirtualBackendType.FTP || resource.getBackendType() == VirtualBackendType.NDV);

        this.tabbedPaneManager = tabbedPaneManager;
        this.resource = resource;
        model.setResource(resource);
        model.setSentenceType(sentenceType);

        // Create compare panel for diff functionality
        comparePanel = new ComparePanel(resource.getResolvedPath(), content);
        comparePanel.setVisible(false);

        // Create vertical split pane for compare functionality
        compareSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Remove the default content from SplitPreviewTab and wrap it in our split pane
        // The rawScrollPane from parent contains the RSyntaxTextArea
        compareSplitPane.setTopComponent(rawScrollPane);
        compareSplitPane.setBottomComponent(comparePanel);
        compareSplitPane.setResizeWeight(1.0);
        compareSplitPane.setDividerSize(6);

        // Re-arrange the layout: replace content in contentPanel with our split pane
        contentPanel.removeAll();
        contentPanel.add(compareSplitPane, BorderLayout.CENTER);

        // Add status bar at bottom of main panel
        mainPanel.add(statusBarPanel, BorderLayout.SOUTH);

        legendController = new LegendController(statusBarPanel.getLegendWrapper());
        filterCoordinator = new FilterCoordinator(
                getRawPane(), // Use inherited rawPane from SplitPreviewTab
                comparePanel.getOriginalTextArea(),
                statusBarPanel.getGrepField(),
                SettingsHelper.load().soundEnabled
        );
        eventManager = new FileTabEventManager(this);

        statusBarPanel.bindEvents(dispatcher);
        comparePanel.bindEvents(dispatcher);
        eventManager.bindAll();

        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        statusBarPanel.setSentenceTypes(new java.util.ArrayList<>(registry.getSentenceTypeSpec().getDefinitions().keySet()));
        statusBarPanel.setSelectedSentenceType(sentenceType);

        restoreDividerLocation();
        initDividerStateListener();
        showComparePanelWithDividerOnReady(toCompare);

        // Content already set via super constructor, but we need to handle sentence type
        if (sentenceType != null) {
            dispatcher.publish(new SentenceTypeChangedEvent(sentenceType));
        }
        statusBarPanel.setSelectedSentenceType(sentenceType);

        if (searchPattern != null && !searchPattern.isEmpty()) {
            searchFor(searchPattern);
        }

        // Record initial version in local history (so we have the opened state for comparison)
        recordInitialVersion(content);
    }

    /**
     * Records the initial version of a file when it is first opened.
     */
    private void recordInitialVersion(String content) {
        if (resource == null || content == null || content.isEmpty()) return;
        String path = resource.getResolvedPath();
        if (path == null || path.isEmpty()) return;
        try {
            Settings s = SettingsHelper.load();
            if (s.historyEnabled) {
                de.bund.zrb.history.LocalHistoryService.getInstance()
                        .recordVersion(resource.getBackendType(), path, content, "geöffnet");
            }
        } catch (Exception e) {
            System.err.println("[LocalHistory] Failed to record initial version: " + e.getMessage());
        }
    }

    /**
     * Extract file name from VirtualResource for display.
     */
    private static String extractFileName(VirtualResource resource) {
        if (resource == null) return "[Neu]";
        String path = resource.getResolvedPath();
        if (path == null) return "[Neu]";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private void initDividerStateListener() {
        compareSplitPane.addPropertyChangeListener("dividerLocation", evt -> {
            int height = compareSplitPane.getHeight();
            if (height > 0) {
                int rawDivider = compareSplitPane.getDividerLocation();
                double relative = rawDivider / (double) height;

                if (relative >= 0.10 && relative <= 1) {
                    currentDividerLocation = relative;
                } else {
                    // optional: Debug-Ausgabe
                    System.err.println("⚠ Ignored out-of-range divider value: " + relative);
                }
            }
        });
    }

    private void showComparePanelWithDividerOnReady(Boolean toCompare) {
        if (Boolean.TRUE.equals(toCompare)) {
            compareSplitPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (compareSplitPane.getHeight() > 0) {
                        compareSplitPane.removeComponentListener(this); // nur einmal ausführen
                        showComparePanel();
                    }
                }
            });
        }
    }

    @Override
    public void searchFor(String searchPattern) {
        if (searchPattern == null) return;
        statusBarPanel.getGrepField().setText(searchPattern.trim());
        filterCoordinator.applyFilter(); // direkt ausführen
    }

    public void updateTitle() {
        tabbedPaneManager.updateTitleFor(this);
    }

    public void showComparePanel() {
        comparePanel.setVisible(true);
        statusBarPanel.getCompareButton().setVisible(false);

        // Apply divider location after layout is done, so the split pane has its final size
        SwingUtilities.invokeLater(() -> {
            applyDividerLocation();
            // Load history versions into the compare panel
            loadHistoryIntoComparePanel();
        });
    }

    /**
     * Load saved versions from LocalHistoryService into the compare panel's version dropdown.
     * When a version is selected, show its content in the compare view.
     */
    private void loadHistoryIntoComparePanel() {
        if (resource == null) return;
        String filePath = resource.getResolvedPath();
        if (filePath == null || filePath.isEmpty()) return;

        try {
            java.util.List<de.bund.zrb.history.HistoryEntry> versions =
                    de.bund.zrb.history.LocalHistoryService.getInstance()
                            .getVersions(resource.getBackendType(), filePath);

            comparePanel.getBarPanel().setHistoryVersions(versions);
            comparePanel.getBarPanel().onHistoryVersionSelected(entry -> {
                if (entry == null) return;
                String content = de.bund.zrb.history.LocalHistoryService.getInstance()
                        .getVersionContent(resource.getBackendType(), entry.getVersionId());
                if (content != null) {
                    comparePanel.getOriginalTextArea().setText(content);
                    applyDiffHighlighting();
                }
            });

            // Auto-select latest version if available
            if (!versions.isEmpty()) {
                de.bund.zrb.history.HistoryEntry latest = versions.get(0);
                String content = de.bund.zrb.history.LocalHistoryService.getInstance()
                        .getVersionContent(resource.getBackendType(), latest.getVersionId());
                if (content != null) {
                    comparePanel.getOriginalTextArea().setText(content);
                    applyDiffHighlighting();
                }
            }
        } catch (Exception e) {
            System.err.println("[FileTabImpl] Failed to load history: " + e.getMessage());
        }
    }

    /**
     * Apply diff highlighting between the editor (top) and the compare panel (bottom).
     */
    private void applyDiffHighlighting() {
        SwingUtilities.invokeLater(() -> {
            try {
                DiffHighlighter.applyDiff(getRawPane(), comparePanel.getOriginalTextArea());
            } catch (Exception e) {
                System.err.println("[FileTabImpl] Diff highlighting failed: " + e.getMessage());
            }
        });
    }

    public void toggleComparePanel() {
        boolean show = !comparePanel.isVisible();

        if (!show) {
            // Closing: save position and clear highlights
            saveDividerLocation();
            DiffHighlighter.clearDiffHighlights(getRawPane());
            DiffHighlighter.clearDiffHighlights(comparePanel.getOriginalTextArea());
        }

        comparePanel.setVisible(show);

        if (show) {
            statusBarPanel.getCompareButton().setVisible(false);
            SwingUtilities.invokeLater(this::applyDividerLocation);
        }
    }

    private SentenceTypeRegistry getRegistry() {
        return tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
    }

    @Override
    public JComponent getComponent() {
        return this; // Return this since we now extend SplitPreviewTab (which extends JPanel)
    }

    @Override
    public String getTitle() {
        if (resource != null) {
            String path = resource.getResolvedPath();
            if (path != null && path.contains("/")) {
                return model.isChanged() ? path.substring(path.lastIndexOf('/') + 1) + " *" : path.substring(path.lastIndexOf('/') + 1);
            }
            if (path != null && path.contains("\\")) {
                return model.isChanged() ? path.substring(path.lastIndexOf('\\') + 1) + " *" : path.substring(path.lastIndexOf('\\') + 1);
            }
            String name = path == null ? "[Neu]" : path;
            return model.isChanged() ? name + " *" : name;
        }
        return model.isChanged() ? "[Neu] *" : "[Neu]";
    }

    @Override
    public String getTooltip() {
        return resource != null ? resource.getResolvedPath() : model.getFullPath();
    }

    @Override
    public void onClose() {
        // Ressourcenfreigabe falls nötig
        saveDividerLocation();
    }


    void saveDividerLocation() {
        int height = compareSplitPane.getHeight();
        if (height > 0 && comparePanel.isVisible()) {
            int dividerPos = compareSplitPane.getDividerLocation();
            double relativeLocation = dividerPos / (double) height;
            if (relativeLocation > 0.1 && relativeLocation < 0.95) {
                currentDividerLocation = relativeLocation;
                Settings settings = SettingsHelper.load();
                settings.applicationState.put(DIVIDER_LOCATION_KEY, String.valueOf(relativeLocation));
                SettingsHelper.save(settings);
            }
        }
    }

    /**
     * Apply the stored divider location as pixel position.
     * Enforces a minimum height for the compare panel so it's always visible.
     */
    private void applyDividerLocation() {
        int totalHeight = compareSplitPane.getHeight();
        if (totalHeight <= 0) {
            // Layout not ready yet – defer once more
            SwingUtilities.invokeLater(this::applyDividerLocation);
            return;
        }

        int dividerSize = compareSplitPane.getDividerSize();
        int pixelPos = (int) (totalHeight * currentDividerLocation);

        // Enforce minimum compare panel height
        int maxDividerPos = totalHeight - dividerSize - MIN_COMPARE_HEIGHT;
        if (pixelPos > maxDividerPos) {
            pixelPos = maxDividerPos;
        }
        if (pixelPos < MIN_COMPARE_HEIGHT) {
            pixelPos = MIN_COMPARE_HEIGHT;
        }

        compareSplitPane.setDividerLocation(pixelPos);
    }

    private void restoreDividerLocation() {
        String divLocation = SettingsHelper.load().applicationState.get(DIVIDER_LOCATION_KEY);
        if (divLocation != null && !divLocation.isEmpty()) {
            try {
                double storedLocation = Double.parseDouble(divLocation);
                if (storedLocation >= 0.0 && storedLocation <= 1.0) {
                    currentDividerLocation = storedLocation;
                } else {
                    System.err.println("⚠ Could not restore divider location due to an invalid value: " + storedLocation);
                }
            } catch (NumberFormatException e) {
                System.err.println("⚠ Invalid format for divider location: " + divLocation);
            }
        }
    }

    @Override
    public void saveIfApplicable() {
        if (resource == null) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Speichern ist nicht möglich (keine Resource).",
                    "Speichern nicht möglich", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String resolvedPath = resource.getResolvedPath();
        if (resolvedPath == null || resolvedPath.trim().isEmpty()) {
            saveAs();
            return;
        }

        saveViaFileService();
    }

    private void saveViaFileService() {
        try {
            CredentialsProvider credentialsProvider = new InteractiveCredentialsProvider(
                    (host, user) -> LoginManager.getInstance().getPassword(host, user)
            );

            String resolvedPath = resource.getResolvedPath();
            if (resolvedPath == null || resolvedPath.trim().isEmpty()) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Speichern ist noch nicht möglich: Pfad fehlt (neue Datei ohne Zielpfad).",
                        "Speichern nicht möglich", JOptionPane.WARNING_MESSAGE);
                return;
            }

            FilePayload current = null;
            String expectedHash = "";

            try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
                try {
                    current = fs.readFile(resolvedPath);
                    expectedHash = current != null ? current.getHash() : "";
                } catch (FileServiceException ignore) {
                    // Not existing yet -> treat as new file
                    expectedHash = "";
                }

                String newText = getRawPane().getText();
                if (model.isAppend() && current != null) {
                    // IMPORTANT: Use getEditorText() for record structure files, not raw bytes
                    String oldText = current.getEditorText();
                    if (!oldText.endsWith("\n") && !newText.startsWith("\n")) {
                        oldText += "\n";
                    }
                    newText = oldText + newText;
                }

                java.nio.charset.Charset targetCharset = java.nio.charset.Charset.defaultCharset();
                if (current != null && current.getCharset() != null) {
                    targetCharset = current.getCharset();
                }

                // Preserve record structure flag if we already know it (FTP/MVS)
                boolean recordStructure = current != null && current.hasRecordStructure();

                // Encode text back to remote format
                byte[] outBytes;
                if (recordStructure) {
                    // Use RecordStructureCodec for MVS record structure
                    Settings appSettings = SettingsHelper.load();
                    outBytes = RecordStructureCodec.encodeForRemote(newText, targetCharset, appSettings);
                } else {
                    outBytes = newText.getBytes(targetCharset);
                }

                FilePayload payload = FilePayload.fromBytes(outBytes, targetCharset, recordStructure);

                // Record version in local history before writing
                try {
                    Settings histSettings = SettingsHelper.load();
                    if (histSettings.historyEnabled) {
                        de.bund.zrb.history.LocalHistoryService.getInstance()
                                .recordVersion(resource.getBackendType(), resolvedPath, newText, "vor Speichern");
                    }
                } catch (Exception histEx) {
                    System.err.println("[LocalHistory] Failed to record version: " + histEx.getMessage());
                }

                FileWriteResult result = fs.writeIfUnchanged(resolvedPath, payload, expectedHash);
                if (result != null && result.getStatus() == FileWriteResult.Status.CONFLICT) {
                    JOptionPane.showMessageDialog(this,
                            "⚠️ Konflikt beim Speichern: Datei wurde zwischenzeitlich verändert.",
                            "Konflikt", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                model.resetChanged();
                tabbedPaneManager.updateTitleFor(this);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Speichern (FileService):\n" + e.getMessage(),
                    "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void setContent(String content) {
        // Use inherited setContent from SplitPreviewTab which sets rawContent and rawPane
        super.setContent(content);
        model.resetChanged();
    }


    @Override
    public void setContent(String content, String sentenceType) {
        setContent(content);
        if(sentenceType == null) {
            try {
                sentenceType = detectSentenceTypeByPath(resource != null ? resource.getResolvedPath() : model.getFullPath());
            } catch (Exception e) { /* just to make sure that any NPE cannot stop the process */ }
        }
        if( sentenceType != null) {
            dispatcher.publish(new SentenceTypeChangedEvent(sentenceType));
        }
        statusBarPanel.setSelectedSentenceType(sentenceType);
    }

    @Override
    public boolean isAppendEnabled() {
        return model.isAppend();
    }

    @Override
    public void setAppend(boolean append) {
        model.setAppend(append);
        comparePanel.setAppendSelected(append);
    }

    @Override
    public void markAsChanged() {
        dispatcher.publish(new EditorContentChangedEvent(true));
    }

    @Override
    public String getPath() {
        return resource != null ? resource.getResolvedPath() : model.getPath();
    }

    @Override
    public Type getType() {
        return Type.FILE;
    }

    @Override
    public String getContent() {
        return getRawPane().getText();
    }

    @Override
    public void focusSearchField() {
        statusBarPanel.getGrepField().requestFocusInWindow();
        statusBarPanel.getGrepField().selectAll();
    }

    private String detectSentenceTypeByPath(String filePath) {
        if (filePath == null) return null;

        SentenceTypeRegistry registry = getRegistry();
        Map<String, SentenceDefinition> definitions = registry.getSentenceTypeSpec().getDefinitions();

        // 1. Erst Pfad-Vergleich (startsWith)
        for (Map.Entry<String, SentenceDefinition> entry : definitions.entrySet()) {
            SentenceMeta meta = entry.getValue().getMeta();
            if (meta != null && meta.getPaths() != null) {
                for (String path : meta.getPaths()) {
                    if (filePath.contains(path)) {
                        return entry.getKey();
                    }
                }
            }
        }

        // 2. Danach Pattern (Regex)
        for (Map.Entry<String, SentenceDefinition> entry : definitions.entrySet()) {
            SentenceMeta meta = entry.getValue().getMeta();
            if (meta != null) {
                String pattern = meta.getPathPattern();
                if (pattern != null && !pattern.trim().isEmpty()) {
                    try {
                        if (filePath.matches(pattern)) {
                            return entry.getKey();
                        }
                    } catch (Exception e) {
                        System.err.println("⚠ Ungültiges Satzart-Pfadmuster für '" + entry.getKey() + "': " + pattern);
                    }
                }
            }
        }

        return null;
    }
}
