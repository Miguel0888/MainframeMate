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
import de.zrb.bund.newApi.ui.FindBarPanel;
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
import de.bund.zrb.files.impl.ftp.jes.JesFtpJobSubmitter;
import de.bund.zrb.files.impl.ftp.jes.JesSubmitException;
import de.bund.zrb.ui.commands.ConnectJesMenuCommand;
import de.bund.zrb.files.impl.ftp.jes.JobSubmitResult;

/**
 * File editor tab that extends SplitPreviewTab to provide:
 * - RAW/RENDERED/SPLIT view modes with syntax highlighting (from SplitPreviewTab)
 * - Search/Filter functionality
 * - Compare panel for diff viewing
 * - Sentence typSchluessel highlighting
 * - File save operations
 */
public class FileTabImpl extends SplitPreviewTab implements FileTab {

    public static final double DEFAULT_DIVIDER_LOCATION = 0.6;
    private static final int MIN_COMPARE_HEIGHT = 80; // minimum pixels for compare panel
    private double currentDividerLocation = DEFAULT_DIVIDER_LOCATION;
    private static final String DIVIDER_LOCATION_KEY = "comparePanel.dividerLocation";

    final FileTabModel model = new FileTabModel();
    final FileTabEventDispatcher dispatcher = new FileTabEventDispatcher();

    public FileTabModel getModel() {
        return model;
    }

    final ComparePanel comparePanel;
    final StatusBarPanel statusBarPanel = new StatusBarPanel();

    final SentenceHighlighter highlighter = new SentenceHighlighter();
    final LegendController legendController;
    final FilterCoordinator filterCoordinator;

    private final JSplitPane compareSplitPane;
    final TabbedPaneManager tabbedPaneManager;
    private final FileTabEventManager eventManager;

    private VirtualResource resource;

    public VirtualResource getResource() {
        return resource;
    }
    @Override
    public JPopupMenu createContextMenu(Runnable onClose) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem bookmarkItem = new JMenuItem("📌 Als Lesezeichen merken");
        bookmarkItem.addActionListener(e -> {
            MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
            String link = resource != null ? resource.getResolvedPath() : null;
            String backend = resource != null ? resource.getBackendType().name() : null;
            main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), link, backend);
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
                resource.getBackendType() == VirtualBackendType.FTP || resource.getBackendType() == VirtualBackendType.NDV,
                resource.getBackendType().name());

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

        // Wire step-search navigation on the status bar's FindBarPanel
        statusBarPanel.getFindBar().setPrevAction(e -> navigateFindMatch(-1));
        statusBarPanel.getFindBar().setNextAction(e -> navigateFindMatch(+1));
        statusBarPanel.getFindBar().setStepSearchEnabled(restoreStepSearchPreference());
        statusBarPanel.getFindBar().setStepSearchModeListener(new FindBarPanel.StepSearchModeListener() {
            @Override
            public void onStepSearchModeChanged(boolean stepSearch) {
                persistStepSearchPreference(stepSearch);
            }
        });

        // Wire toolbar buttons (Compare toggle / Undo / Redo) to event dispatcher
        compareButton.addActionListener(e -> {
            if (compareButton.isSelected()) {
                dispatcher.publish(new ShowComparePanelEvent());
            } else {
                dispatcher.publish(new CloseComparePanelEvent());
            }
        });
        undoButton.addActionListener(e -> dispatcher.publish(new UndoRequestedEvent()));
        redoButton.addActionListener(e -> dispatcher.publish(new RedoRequestedEvent()));

        eventManager.bindAll();

        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        statusBarPanel.setSentenceTypesGrouped(registry.getSentenceTypeSpec().getDefinitions());

        restoreDividerLocation();
        initDividerStateListener();
        showComparePanelWithDividerOnReady(toCompare);

        // Content already set via super constructor, but we need to handle sentence type
        // Auto-detect sentence type or file type by path if not explicitly provided
        if (sentenceType == null) {
            try {
                String filePath = resource != null ? resource.getResolvedPath() : model.getFullPath();
                sentenceType = detectSentenceTypeByPath(filePath);
                // If path-based detection failed, try content-based detection
                if (sentenceType == null && content != null && !content.isEmpty()) {
                    sentenceType = detectSentenceTypeByContent(content);
                }
                System.out.println("[FileTabImpl] Auto-detected sentenceType: " + sentenceType
                        + " for path: " + filePath);
            } catch (Exception e) {
                System.err.println("[FileTabImpl] Auto-detect sentence type failed: " + e.getMessage());
            }
        }
        if (sentenceType != null) {
            dispatcher.publish(new SentenceTypeChangedEvent(sentenceType));
        }
        statusBarPanel.setSelectedSentenceType(sentenceType);

        if (searchPattern != null && !searchPattern.isEmpty()) {
            searchFor(searchPattern);
        }

        // Record initial version in local history (so we have the opened state for comparison)
        recordInitialVersion(content);

        // Add JES Submit button for FTP+MVS+JCL tabs
        initJesSubmitButton(content, sentenceType);
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
            if (!comparePanel.isVisible()) return; // ignore layout events when hidden
            int height = compareSplitPane.getHeight();
            if (height > 0) {
                int rawDivider = compareSplitPane.getDividerLocation();
                double relative = rawDivider / (double) height;

                if (relative >= 0.10 && relative <= 0.95) {
                    currentDividerLocation = relative;
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
        statusBarPanel.getFindBar().setText(searchPattern.trim());
        filterCoordinator.applyFilter(); // Zeilenfilterung im rawPane

        // For binary/rendered documents, also highlight in HTML pane
        if (needsHtmlRendering) {
            highlightFindMatches();
        }
    }

    /**
     * Override: use the statusBarPanel's FindBarPanel text (the SplitPreviewTab findBar
     * is replaced by statusBarPanel in the layout and is therefore hidden).
     */
    @Override
    protected void highlightFindMatches() {
        String query = statusBarPanel.getFindBar().getText().trim();
        boolean stepMode = statusBarPanel.getFindBar().isStepSearchEnabled();

        if (!isTextFile && needsHtmlRendering) {
            // Binary document: rawPane has garbage, only search htmlRenderedPane
            highlightInTextComponent(rawPane, "", false); // clear old highlights
            highlightInTextComponent(htmlRenderedPane, query, stepMode);
        } else if (needsHtmlRendering) {
            // Text-based HTML — search both panes
            highlightInTextComponent(rawPane, query, stepMode);
            highlightInTextComponent(htmlRenderedPane, query, stepMode);
        } else {
            // Plain text / source code — only rawPane
            highlightInTextComponent(rawPane, query, stepMode);
        }

        // In step mode, show the first result
        if (stepMode && !findMatchPositions.isEmpty()) {
            findMatchIndex = 0;
            showCurrentMatch();
        }
    }

    public void updateTitle() {
        tabbedPaneManager.updateTitleFor(this);
    }

    /** Package-visible: whether this tab currently uses HTML rendering. */
    boolean isHtmlRendered() {
        return needsHtmlRendering;
    }

    /** Package-visible: select/deselect the compare toggle button in the toolbar. */
    void setCompareButtonSelected(boolean selected) {
        compareButton.setSelected(selected);
    }

    public void showComparePanel() {
        comparePanel.setVisible(true);
        compareButton.setSelected(true);

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
        compareButton.setSelected(show);

        if (show) {
            SwingUtilities.invokeLater(this::applyDividerLocation);
        }
    }

    private SentenceTypeRegistry getRegistry() {
        return tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
    }

    /**
     * Request that the outline panel is refreshed for this tab.
     * Delegates to TabbedPaneManager which has access to the RightDrawer.
     */
    void refreshOutline() {
        if (tabbedPaneManager != null) {
            tabbedPaneManager.refreshOutlineForActiveTab();
        }
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
                hasUnsavedChanges = false;
                tabbedPaneManager.updateTitleFor(this);

                // Re-index saved content in RAG
                reindexTextContent(resolvedPath, newText);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Speichern (FileService):\n" + e.getMessage(),
                    "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Re-index text content in the RAG Lucene index after save.
     * Runs asynchronously to avoid blocking the EDT or save operation.
     */
    private void reindexTextContent(final String docId, final String text) {
        if (docId == null || text == null || text.trim().isEmpty()) return;
        new Thread(() -> {
            try {
                de.bund.zrb.rag.service.RagService rag = de.bund.zrb.rag.service.RagService.getInstance();
                if (rag.isIndexed(docId)) {
                    rag.removeDocument(docId);
                }
                de.bund.zrb.ingestion.model.document.DocumentMetadata meta =
                        de.bund.zrb.ingestion.model.document.DocumentMetadata.builder()
                                .sourceName(sourceName)
                                .mimeType(metadata != null ? metadata.getMimeType() : "text/plain")
                                .build();
                de.bund.zrb.ingestion.model.document.Document doc =
                        de.bund.zrb.ingestion.model.document.Document.fromText(text, meta);
                rag.indexDocument(docId, sourceName != null ? sourceName : docId, doc, false);
                System.out.println("[FileTabImpl] Re-indexed after save: " + docId);
                SwingUtilities.invokeLater(() -> sidebar.refreshIndexStatus());
            } catch (Exception e) {
                System.err.println("[FileTabImpl] Re-index after save failed: " + e.getMessage());
            }
        }, "Reindex-Save-" + docId).start();
    }

    /**
     * Re-index binary content (PDF, DOCX, etc.) in the RAG Lucene index after upload.
     * Extracts text via the ingestion pipeline and indexes it.
     * Must be called from a background thread.
     */
    private void reindexBinaryContent(final String docId, final byte[] binaryData, final String fileName) {
        if (docId == null || binaryData == null || binaryData.length == 0) return;
        de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase extractor = null;
        try {
            de.bund.zrb.rag.service.RagService rag = de.bund.zrb.rag.service.RagService.getInstance();
            if (rag.isIndexed(docId)) {
                rag.removeDocument(docId);
            }

            de.bund.zrb.ingestion.config.IngestionConfig config = new de.bund.zrb.ingestion.config.IngestionConfig()
                    .setMaxFileSizeBytes(50 * 1024 * 1024)
                    .setTimeoutPerExtractionMs(60000)
                    .setEnableFallbackOnExtractorFailure(true);
            extractor = new de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase(config);

            de.bund.zrb.ingestion.model.DocumentSource source =
                    de.bund.zrb.ingestion.model.DocumentSource.fromBytes(binaryData, fileName);
            de.bund.zrb.ingestion.model.ExtractionResult result = extractor.executeSync(source);

            if (result.isSuccess() && result.getPlainText() != null && !result.getPlainText().trim().isEmpty()) {
                de.bund.zrb.ingestion.model.document.DocumentMetadata meta =
                        de.bund.zrb.ingestion.model.document.DocumentMetadata.builder()
                                .sourceName(fileName)
                                .mimeType(metadata != null ? metadata.getMimeType() : "application/octet-stream")
                                .build();
                de.bund.zrb.ingestion.model.document.Document doc =
                        de.bund.zrb.ingestion.model.document.Document.fromText(result.getPlainText(), meta);
                rag.indexDocument(docId, fileName, doc, false);
                System.out.println("[FileTabImpl] Re-indexed after upload: " + docId);
                SwingUtilities.invokeLater(() -> sidebar.refreshIndexStatus());
            }
        } catch (Exception e) {
            System.err.println("[FileTabImpl] Re-index after upload failed: " + e.getMessage());
        } finally {
            if (extractor != null) {
                extractor.shutdown();
            }
        }
    }

    /**
     * Uploads a local file to replace the remote binary file (PDF, DOCX, etc.).
     * Opens a file chooser, reads the selected file, and writes it to the remote path in binary mode.
     */
    @Override
    protected void uploadContent() {
        if (resource == null) {
            JOptionPane.showMessageDialog(this,
                    "Hochladen ist nicht möglich (keine Resource).",
                    "Nicht möglich", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String resolvedPath = resource.getResolvedPath();
        if (resolvedPath == null || resolvedPath.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Hochladen ist nicht möglich: Kein Zielpfad bekannt.",
                    "Nicht möglich", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Datei zum Hochladen auswählen");
        if (activeFileType != null) {
            String ext = getUploadExtension(activeFileType);
            if (ext != null) {
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        activeFileType.toUpperCase() + " Dateien (*." + ext + ")", ext));
            }
        }

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File selected = chooser.getSelectedFile();
        if (selected == null || !selected.exists()) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Datei \"" + selected.getName() + "\" (" + formatFileSize(selected.length()) + ")\n" +
                        "hochladen und Remote-Datei ersetzen?\n\n" +
                        "Ziel: " + resolvedPath,
                "Hochladen bestätigen", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() ->
                        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)));

                byte[] fileBytes = java.nio.file.Files.readAllBytes(selected.toPath());

                CredentialsProvider credentialsProvider = new InteractiveCredentialsProvider(
                        (host, user) -> LoginManager.getInstance().getPassword(host, user)
                );

                try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
                    FilePayload payload = FilePayload.fromBytes(fileBytes, null, false);
                    fs.writeFileBinary(resolvedPath, payload);
                }

                // Update local state with new bytes
                this.rawBytes = fileBytes;

                // Re-render the preview with new content
                renderBinaryContent(fileBytes, resolvedPath, activeFileType != null ? activeFileType : "BINARY");

                SwingUtilities.invokeLater(() -> {
                    // Brief confirmation on the upload button
                    String original = uploadButton.getText();
                    uploadButton.setText("✓ Hochgeladen!");
                    Timer timer = new Timer(2000, evt -> uploadButton.setText(original));
                    timer.setRepeats(false);
                    timer.start();
                });

                System.out.println("[FileTabImpl] Upload successful: " + selected.getName() +
                        " (" + fileBytes.length + " bytes) → " + resolvedPath);

                // Re-index uploaded binary content in RAG
                reindexBinaryContent(resolvedPath, fileBytes, selected.getName());

            } catch (Exception e) {
                System.err.println("[FileTabImpl] Upload failed: " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                                "Fehler beim Hochladen:\n" + e.getMessage(),
                                "Upload-Fehler", JOptionPane.ERROR_MESSAGE));
            } finally {
                SwingUtilities.invokeLater(() ->
                        setCursor(java.awt.Cursor.getDefaultCursor()));
            }
        }, "Upload-" + resolvedPath).start();
    }

    private String getUploadExtension(String fileType) {
        if (fileType == null) return null;
        switch (fileType.toUpperCase()) {
            case "PDF": return "pdf";
            case "WORD": return "docx";
            case "EXCEL": return "xlsx";
            case "OUTLOOK MAIL": return "eml";
            default: return null;
        }
    }

    @Override
    public void setContent(String content) {
        // Use inherited setContent from SplitPreviewTab which sets rawContent and rawPane
        super.setContent(content);
        model.resetChanged();
    }

    /**
     * Reloads the current file in BINARY transfer mode (for PDF, DOCX, etc.).
     * This is required because binary files get corrupted when transferred as ASCII.
     * The binary bytes are stored for download, and the extracted/rendered content
     * is shown in the preview via the ingestion pipeline (PDFBox, POI, etc.).
     *
     * @param fileType the file type key (e.g., "PDF", "WORD")
     */
    public void reloadAsBinary(String fileType) {
        if (resource == null) {
            System.err.println("[FileTabImpl] Cannot reload as binary: no resource");
            return;
        }

        String resolvedPath = resource.getResolvedPath();
        if (resolvedPath == null || resolvedPath.trim().isEmpty()) {
            System.err.println("[FileTabImpl] Cannot reload as binary: no resolved path");
            return;
        }

        // Run in background to avoid blocking EDT
        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() ->
                        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)));

                CredentialsProvider credentialsProvider = new InteractiveCredentialsProvider(
                        (host, user) -> LoginManager.getInstance().getPassword(host, user)
                );

                byte[] binaryData;
                try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
                    FilePayload payload = fs.readFileBinary(resolvedPath);
                    binaryData = payload.getBytes();
                }

                // Store raw binary bytes for download
                this.rawBytes = binaryData;

                System.out.println("[FileTabImpl] Reloaded as binary: " + resolvedPath +
                        " (" + binaryData.length + " bytes)");

                // Use ingestion pipeline to extract text and render
                renderBinaryContent(binaryData, resolvedPath, fileType);

            } catch (Exception e) {
                System.err.println("[FileTabImpl] Failed to reload as binary: " + e.getMessage());
                SwingUtilities.invokeLater(() ->
                        htmlRenderedPane.setText("<html><body><p style='color:red;'>⚠ Fehler beim binären Laden:<br>" +
                                escapeHtml(e.getMessage()) + "</p>" +
                                "<p>Der Inhalt wurde möglicherweise als Text (ASCII) übertragen " +
                                "und ist daher beschädigt.</p></body></html>"));
            } finally {
                SwingUtilities.invokeLater(() ->
                        setCursor(java.awt.Cursor.getDefaultCursor()));
            }
        }, "BinaryReload-" + resolvedPath).start();
    }

    /**
     * Renders binary document content (PDF, DOCX, XLSX, etc.) using the ingestion pipeline.
     * Extracts text via PDFBox/POI, builds a Document model, renders to HTML.
     */
    private void renderBinaryContent(byte[] binaryData, String path, String fileType) {
        String fileName = path;
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSep >= 0) fileName = path.substring(lastSep + 1);
        String sizeStr = formatFileSize(binaryData.length);
        final String displayName = fileName;

        // For PDF files, render actual pages instead of extracted text
        if ("PDF".equalsIgnoreCase(fileType)) {
            SwingUtilities.invokeLater(() -> renderPdfPages(binaryData));
            return;
        }

        // Show loading indicator immediately
        SwingUtilities.invokeLater(() ->
                htmlRenderedPane.setText("<html><body style='font-family: Segoe UI, sans-serif; padding: 20px;'>" +
                        "<p>⏳ Extrahiere Text aus <b>" + escapeHtml(displayName) + "</b> (" + sizeStr + ")...</p>" +
                        "</body></html>"));

        de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase extractUseCase = null;
        try {
            // Use ingestion pipeline: detect → extract → build → render
            // Use executeSync to avoid nested timeout issues
            de.bund.zrb.ingestion.config.IngestionConfig config = new de.bund.zrb.ingestion.config.IngestionConfig()
                    .setMaxFileSizeBytes(50 * 1024 * 1024)   // 50 MB
                    .setTimeoutPerExtractionMs(60000)          // 60 seconds
                    .setEnableFallbackOnExtractorFailure(true);

            extractUseCase = new de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase(config);

            de.bund.zrb.ingestion.model.DocumentSource source =
                    de.bund.zrb.ingestion.model.DocumentSource.fromBytes(binaryData, displayName);

            // Use executeSync — we're already in a background thread, so no need for the
            // internal timeout executor (which was causing TimeoutException)
            de.bund.zrb.ingestion.model.ExtractionResult result = extractUseCase.executeSync(source);

            if (!result.isSuccess()) {
                final String errorMsg = result.getErrorMessage();
                SwingUtilities.invokeLater(() ->
                        htmlRenderedPane.setText("<html><body style='font-family: Segoe UI, sans-serif; padding: 20px;'>" +
                                "<p style='color:orange;'>⚠ Text-Extraktion fehlgeschlagen: " +
                                escapeHtml(errorMsg) + "</p>" +
                                "<p>Verwenden Sie <b>📥 Herunterladen</b> um die Originaldatei zu speichern.</p>" +
                                "</body></html>"));
                return;
            }

            String extractedText = result.getPlainText();

            // Render extracted text as HTML
            String html = markdownFormatter.renderToHtml(extractedText);

            // Build header with document info
            String header = "<div style='background:#f0f0f0; padding:8px 12px; margin-bottom:12px; " +
                    "border-radius:4px; font-size:11px; color:#666;'>" +
                    "📄 <b>" + escapeHtml(displayName) + "</b> | " + sizeStr + " | " +
                    fileType.toUpperCase() + " | ✅ Binär geladen</div>";

            String fullHtml = "<html><body style='font-family: Segoe UI, sans-serif;'>" + header + html + "</body></html>";

            SwingUtilities.invokeLater(() -> {
                htmlRenderedPane.setText(fullHtml);
                htmlRenderedPane.setCaretPosition(0);
            });

        } catch (Exception e) {
            System.err.println("[FileTabImpl] renderBinaryContent failed: " + e.getMessage());
            e.printStackTrace();
            SwingUtilities.invokeLater(() ->
                    htmlRenderedPane.setText("<html><body style='font-family: Segoe UI, sans-serif; padding: 20px;'>" +
                            "<p style='color:red;'>⚠ Rendering fehlgeschlagen: " +
                            escapeHtml(e.getMessage()) + "</p>" +
                            "<p>Verwenden Sie <b>📥 Herunterladen</b> um die Originaldatei zu speichern.</p>" +
                            "</body></html>"));
        } finally {
            if (extractUseCase != null) {
                extractUseCase.shutdown();
            }
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " Bytes";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
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
        statusBarPanel.getFindBar().focusAndSelectAll();
    }

    private String detectSentenceTypeByPath(String filePath) {
        if (filePath == null) return null;

        SentenceTypeRegistry registry = getRegistry();
        Map<String, SentenceDefinition> definitions = registry.getSentenceTypeSpec().getDefinitions();

        // 0. Erst Extension-Matching (für Dateitypen mit definierten Endungen)
        String fileExt = null;
        int dotIdx = filePath.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < filePath.length() - 1) {
            fileExt = filePath.substring(dotIdx + 1).toLowerCase();
        }
        if (fileExt != null && !fileExt.isEmpty()) {
            for (Map.Entry<String, SentenceDefinition> entry : definitions.entrySet()) {
                SentenceMeta meta = entry.getValue().getMeta();
                if (meta != null && meta.getExtensions() != null) {
                    for (String ext : meta.getExtensions()) {
                        if (ext.equalsIgnoreCase(fileExt)) {
                            System.out.println("[SentenceDetect] Extension match: '" + fileExt + "' → " + entry.getKey() + " (path: " + filePath + ")");
                            return entry.getKey();
                        }
                    }
                }
            }
        }

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

        System.out.println("[SentenceDetect] No match for path: " + filePath
                + " (ext: " + fileExt + ", definitions: " + definitions.size() + ")");
        return null;
    }

    /**
     * Detect sentence type by running configured detection scripts against the file content.
     * Each definition can have a detectionScript (Java source) that receives the content
     * as args[0] and returns "true" or "false".
     *
     * @param content the file content to check
     * @return the matching sentence type key, or null if no script matched
     */
    private String detectSentenceTypeByContent(String content) {
        if (content == null || content.isEmpty()) return null;

        SentenceTypeRegistry registry = getRegistry();
        Map<String, SentenceDefinition> definitions = registry.getSentenceTypeSpec().getDefinitions();

        // Limit content passed to scripts (first ~4KB should be enough for language detection)
        String snippet = content.length() > 4096 ? content.substring(0, 4096) : content;

        for (Map.Entry<String, SentenceDefinition> entry : definitions.entrySet()) {
            SentenceMeta meta = entry.getValue().getMeta();
            if (meta == null || !meta.hasDetectionScript()) continue;

            try {
                de.bund.zrb.runtime.ExpressionCompiler compiler =
                        de.bund.zrb.runtime.ExpressionCompilerFactory.getCompiler();
                String result = compiler.compileAndExecute(
                        "Detect_" + entry.getKey().replaceAll("[^a-zA-Z0-9_]", "_"),
                        meta.getDetectionScript(),
                        java.util.Collections.singletonList(snippet)
                );
                if ("true".equalsIgnoreCase(result != null ? result.trim() : "")) {
                    System.out.println("[SentenceDetect] Content detection match: " + entry.getKey());
                    return entry.getKey();
                }
            } catch (Exception e) {
                System.err.println("[SentenceDetect] Detection script failed for '"
                        + entry.getKey() + "': " + e.getMessage());
            }
        }

        return null;
    }

    // ── JES Submit ──────────────────────────────────────────────────────────────

    private JButton jesSubmitButton;

    /**
     * Adds a "▶ Ausführen" button to the toolbar when the tab is FTP + MVS + JCL.
     */
    private void initJesSubmitButton(String content, String resolvedSentenceType) {
        if (!isJesSubmitEligible(content, resolvedSentenceType)) {
            return;
        }

        jesSubmitButton = new JButton("▶ Ausführen");
        jesSubmitButton.setToolTipText("JCL als Job per FTP JES submitten");

        jesSubmitButton.addActionListener(e -> handleJesSubmit());

        // Insert after uploadButton in the toolbar (index search)
        if (toolbar != null) {
            int insertIndex = -1;
            for (int i = 0; i < toolbar.getComponentCount(); i++) {
                if (toolbar.getComponent(i) == uploadButton) {
                    insertIndex = i + 1;
                    break;
                }
            }
            if (insertIndex >= 0 && insertIndex <= toolbar.getComponentCount()) {
                toolbar.add(jesSubmitButton, insertIndex);
            } else {
                // Fallback: add before the glue
                toolbar.add(jesSubmitButton, 5);
            }
            toolbar.revalidate();
            toolbar.repaint();
        }
    }

    /**
     * Check if JES submit is eligible: FTP backend + MVS mode + JCL content.
     */
    private boolean isJesSubmitEligible(String content, String resolvedSentenceType) {
        if (resource == null) return false;
        if (resource.getBackendType() != VirtualBackendType.FTP) return false;

        // Check MVS mode
        FtpResourceState ftpState = resource.getFtpState();
        if (ftpState == null || !Boolean.TRUE.equals(ftpState.getMvsMode())) return false;

        // Check JCL: either by sentence type or content detection
        if (resolvedSentenceType != null) {
            String upper = resolvedSentenceType.toUpperCase();
            if (upper.contains("JCL")) return true;
        }
        // Check file extension
        String path = resource.getResolvedPath();
        if (path != null) {
            String lower = path.toLowerCase();
            if (lower.endsWith(".jcl") || lower.endsWith(".proc") || lower.endsWith(".prc")) return true;
        }
        // Check content
        if (content != null && isJclContent(content)) return true;

        return false;
    }

    /**
     * Handle JES submit button click:
     * 1) Check unsaved changes
     * 2) Show confirmation dialog
     * 3) Submit asynchronously
     */
    private void handleJesSubmit() {
        String jclContent = getRawPane().getText();
        if (jclContent == null || jclContent.trim().isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "JCL-Inhalt ist leer.",
                    "Ausführen nicht möglich", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── Step 1: Handle unsaved changes ──
        if (hasUnsavedChanges) {
            String[] options = {"Speichern & Ausführen", "Nur Ausführen", "Abbrechen"};
            int choice = JOptionPane.showOptionDialog(mainPanel,
                    "Es gibt ungespeicherte Änderungen.\nWas soll passieren?",
                    "Ungespeicherte Änderungen",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);

            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return; // Cancel
            if (choice == 0) {
                // Save first
                saveIfApplicable();
                // Re-read content after save
                jclContent = getRawPane().getText();
            }
            // choice == 1: proceed with current content
        }

        // ── Step 2: Confirmation dialog ──
        FtpResourceState ftpState = resource.getFtpState();
        String host = ftpState != null && ftpState.getConnectionId() != null
                ? ftpState.getConnectionId().getHost() : "(unbekannt)";
        String user = ftpState != null && ftpState.getConnectionId() != null
                ? ftpState.getConnectionId().getUsername() : "(unbekannt)";
        String path = resource.getResolvedPath();

        String message = "Soll dieser JCL wirklich als Job submitted werden?\n\n"
                + "Host: " + host + "\n"
                + "User: " + user + "\n"
                + (path != null ? "Datei: " + path + "\n" : "");

        int confirm = JOptionPane.showConfirmDialog(mainPanel,
                message,
                "Job submitten?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        // ── Step 3: Submit asynchronously ──
        jesSubmitButton.setEnabled(false);
        jesSubmitButton.setText("⏳ Submitting…");

        final String finalJclContent = jclContent;
        new SwingWorker<JobSubmitResult, Void>() {
            @Override
            protected JobSubmitResult doInBackground() throws Exception {
                CredentialsProvider credentialsProvider = new InteractiveCredentialsProvider(
                        (h, u) -> LoginManager.getInstance().getPassword(h, u)
                );
                return JesFtpJobSubmitter.submit(
                        ftpState.getConnectionId(),
                        credentialsProvider,
                        finalJclContent
                );
            }

            @Override
            protected void done() {
                jesSubmitButton.setEnabled(true);
                jesSubmitButton.setText("▶ Ausführen");
                try {
                    JobSubmitResult result = get();
                    showSubmitSuccess(result);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showSubmitError(cause);
                }
            }
        }.execute();
    }

    /**
     * Show success dialog with Job-ID and copy button.
     */
    private void showSubmitSuccess(JobSubmitResult result) {
        String jobId = result.getJobId();
        String jobName = result.getJobName();
        String info = "Job submitted!\n\n"
                + "Job-ID: " + jobId + "\n"
                + (jobName != null ? "Jobname: " + jobName + "\n" : "")
                + "Host: " + result.getHost() + "\n"
                + "User: " + result.getUser() + "\n"
                + "Zeit: " + result.getSubmittedAt().toString().replace('T', ' ');

        String[] options = {"Job-ID kopieren", "📋 In JES-Jobs öffnen", "Schließen"};
        int choice = JOptionPane.showOptionDialog(mainPanel,
                info,
                "Job submitted",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null, options, options[2]);

        if (choice == 0) {
            // Copy Job-ID to clipboard
            java.awt.datatransfer.StringSelection sel =
                    new java.awt.datatransfer.StringSelection(jobId);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        } else if (choice == 1) {
            // Open JES-Jobs tab with jobId as search pattern
            ConnectJesMenuCommand.openJesTab(mainPanel, tabbedPaneManager, jobId);
        }
    }

    /**
     * Show error dialog for failed submit.
     */
    private void showSubmitError(Throwable cause) {
        String title;
        String message;

        if (cause instanceof JesSubmitException) {
            // Check if it's a JES permission issue
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("FILETYPE=JES") || msg.contains("JES-Submit nicht möglich"))) {
                title = "JES-Submit nicht möglich";
            } else {
                title = "Job submit fehlgeschlagen";
            }
            message = cause.getMessage();
        } else {
            title = "Job submit fehlgeschlagen";
            message = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }

        JOptionPane.showMessageDialog(mainPanel,
                message,
                title,
                JOptionPane.ERROR_MESSAGE);
    }
}

