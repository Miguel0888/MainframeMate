package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.model.Settings;
import de.bund.zrb.service.FileContentService;
import de.bund.zrb.ui.filetab.*;
import de.bund.zrb.ui.filetab.event.*;
import de.bund.zrb.helper.SettingsHelper;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceMeta;
import de.zrb.bund.newApi.ui.FileTab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public class FileTabImpl implements FileTab {

    public static final double DEFAULT_DIVIDER_LOCATION = 0.7;
    private double currentDividerLocation = DEFAULT_DIVIDER_LOCATION;
    private static final String DIVIDER_LOCATION_KEY = "comparePanel.dividerLocation";
    private final JPanel mainPanel = new JPanel(new BorderLayout());
    final FileTabModel model = new FileTabModel();
    final FileTabEventDispatcher dispatcher = new FileTabEventDispatcher();

    final EditorPanel editorPanel = new EditorPanel();
    final ComparePanel comparePanel;
    final StatusBarPanel statusBarPanel = new StatusBarPanel();

    final SentenceHighlighter highlighter = new SentenceHighlighter();
    final LegendController legendController;
    final FilterCoordinator filterCoordinator;

    private final JSplitPane splitPane;
    final TabbedPaneManager tabbedPaneManager;
    private final FileContentService contentService;
    private final FileTabEventManager eventManager;
    private FtpFileBuffer buffer;

    private VirtualResource resource;


    @Override
    public JPopupMenu createContextMenu(Runnable onClose) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem bookmarkItem = new JMenuItem("📌 Als Lesezeichen merken");
        bookmarkItem.addActionListener(e -> {
            MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
            String link = buffer != null ? buffer.getLink() : (resource != null ? resource.getResolvedPath() : null);
            main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), link);
        });

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onClose.run());

        menu.add(bookmarkItem);
        menu.addSeparator();
        menu.add(closeItem);

        return menu;
    }

    public FileTabImpl(TabbedPaneManager tabbedPaneManager,
                       FtpManager ftpManager,
                       String content,
                       String sentenceType) {
        this(tabbedPaneManager, ftpManager, (FtpFileBuffer) null, sentenceType);
        setContent(content, sentenceType);
    }

    public FileTabImpl(TabbedPaneManager tabbedPaneManager, FtpManager ftpManager, FtpFileBuffer buffer, String sentenceType) {
        this(tabbedPaneManager, ftpManager, buffer, sentenceType, null, null);
    }

    public FileTabImpl(TabbedPaneManager tabbedPaneManager, FtpManager ftpManager, FtpFileBuffer buffer, String sentenceType, String searchPattern, Boolean toCompare) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.contentService = new FileContentService(ftpManager);

        this.buffer = buffer;
        String content = buffer != null ? contentService.decodeWith(buffer) : "";
        model.setBuffer(buffer);
        model.setSentenceType(sentenceType);

        comparePanel = new ComparePanel(model.getFullPath(), content);
        comparePanel.setVisible(false);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(editorPanel);
        splitPane.setBottomComponent(comparePanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(6);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusBarPanel, BorderLayout.SOUTH);

        legendController = new LegendController(statusBarPanel.getLegendWrapper());

        filterCoordinator = new FilterCoordinator(
                editorPanel.getTextArea(),
                comparePanel.getOriginalTextArea(),
                statusBarPanel.getGrepField(),
                SettingsHelper.load().soundEnabled
        );

        statusBarPanel.bindEvents(dispatcher);
        editorPanel.bindEvents(dispatcher);
        comparePanel.bindEvents(dispatcher);

        this.eventManager = new FileTabEventManager(this);
        eventManager.bindAll();

        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        statusBarPanel.setSentenceTypes(new java.util.ArrayList<>(registry.getSentenceTypeSpec().getDefinitions().keySet()));
        statusBarPanel.setSelectedSentenceType(sentenceType);


        setContent(content, sentenceType);

        restoreDividerLocation();

        initDividerStateListener();
        showComparePanelWithDividerOnReady(toCompare);

        if(searchPattern != null && !searchPattern.isEmpty())
        {
            this.searchFor(searchPattern);
        }
    }

    /**
     * New constructor using VirtualResource + content (no FtpManager/Buffer).
     */
    public FileTabImpl(TabbedPaneManager tabbedPaneManager, VirtualResource resource, String content,
                       String sentenceType, String searchPattern, Boolean toCompare) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.contentService = null; // no legacy content service needed
        this.resource = resource;
        this.buffer = null;

        model.setBuffer(null);
        model.setSentenceType(sentenceType);

        comparePanel = new ComparePanel(resource.getResolvedPath(), content);
        comparePanel.setVisible(false);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(editorPanel);
        splitPane.setBottomComponent(comparePanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(6);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusBarPanel, BorderLayout.SOUTH);

        legendController = new LegendController(statusBarPanel.getLegendWrapper());
        filterCoordinator = new FilterCoordinator(
                editorPanel.getTextArea(),
                comparePanel.getOriginalTextArea(),
                statusBarPanel.getGrepField(),
                SettingsHelper.load().soundEnabled
        );
        eventManager = new FileTabEventManager(this);

        setContent(content, sentenceType);

        if (searchPattern != null && !searchPattern.isEmpty()) {
            searchFor(searchPattern);
        }
        if (Boolean.TRUE.equals(toCompare)) {
            toggleComparePanel();
        }
    }

    private void initDividerStateListener() {
        splitPane.addPropertyChangeListener("dividerLocation", evt -> {
            int height = splitPane.getHeight();
            if (height > 0) {
                int rawDivider = splitPane.getDividerLocation();
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
            splitPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (splitPane.getHeight() > 0) {
                        splitPane.removeComponentListener(this); // nur einmal ausführen
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
        splitPane.setDividerLocation(currentDividerLocation);
        statusBarPanel.getCompareButton().setVisible(false);
    }

    public void toggleComparePanel() {
        boolean show = !comparePanel.isVisible();
        comparePanel.setVisible(show);

        if (show) {
            splitPane.setDividerLocation(currentDividerLocation);
            statusBarPanel.getCompareButton().setVisible(false);
        }
    }

    private SentenceTypeRegistry getRegistry() {
        return tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public String getTitle() {
        if (model.getBuffer() == null) {
            if (resource != null) {
                String path = resource.getResolvedPath();
                if (path != null && path.contains("/")) {
                    return path.substring(path.lastIndexOf('/') + 1);
                }
                if (path != null && path.contains("\\")) {
                    return path.substring(path.lastIndexOf('\\') + 1);
                }
                return path == null ? "[Neu]" : path;
            }
            return "[Neu]";
        }
        String name = model.getBuffer().getMeta().getName();
        return model.isChanged() ? name + " *" : name;
    }

    @Override
    public String getTooltip() {
        if (model.getBuffer() == null && resource != null) {
            return resource.getResolvedPath();
        }
        return model.getFullPath();
    }

    @Override
    public void onClose() {
        // Ressourcenfreigabe falls nötig
        saveDividerLocation();
    }

    private double calculateDividerLocation() {
        double relativeLocation = DEFAULT_DIVIDER_LOCATION;
        int height = splitPane.getHeight();
        if (height > 0) {
            relativeLocation = splitPane.getDividerLocation() / (double) height;
        }
        return relativeLocation;
    }

    private void saveDividerLocation() {
        Settings settings = SettingsHelper.load();
        int height = splitPane.getHeight();
        if (height > 0) {
            double relativeLocation = splitPane.getDividerLocation() / (double) height;
            if(relativeLocation > 0) {
                settings.applicationState.put(DIVIDER_LOCATION_KEY, String.valueOf(relativeLocation));
                SettingsHelper.save(settings);
            }
        }
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
        FtpFileBuffer buffer = model.getBuffer();
        if (model.getBuffer() == null) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Speichern über FileService ist noch nicht implementiert.",
                    "Speichern nicht möglich", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String newText = editorPanel.getTextArea().getText();

            if (model.isAppend()) {
                String oldText = contentService.decodeWith(buffer);
                if (!oldText.endsWith("\n") && !newText.startsWith("\n")) {
                    oldText += "\n";
                }
                newText = oldText + newText;
            }

            InputStream stream = contentService.createCommitStream(newText, buffer.hasRecordStructure());
            FtpFileBuffer altered = buffer.withContent(stream);
            Optional<FtpFileBuffer> conflict = contentService.getFtpManager().commit(buffer, altered);

            if (!conflict.isPresent()) {
                model.setBuffer(altered);
                model.resetChanged();
                editorPanel.resetUndoHistory();
                tabbedPaneManager.updateTitleFor(this);
            } else {
                JOptionPane.showMessageDialog(mainPanel, "⚠️ Konflikt beim Speichern: Datei wurde verändert.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Speichern:\n" + e.getMessage(), "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void setContent(String content) {
        editorPanel.setTextSilently(content);
        model.resetChanged(); // Wichtig: Änderungszustand zurücksetzen
    }


    @Override
    public void setContent(String content, String sentenceType) {
        setContent(content);
        if(sentenceType == null) {
            try {
                sentenceType = detectSentenceTypeByPath(model.getFullPath());
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
        if (model.getBuffer() == null && resource != null) {
            return resource.getResolvedPath();
        }
        return model.getPath();
    }

    @Override
    public Type getType() {
        return Type.FILE;
    }

    @Override
    public String getContent() {
        return editorPanel.getTextArea().getText();
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
