package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.service.FileContentService;
import de.bund.zrb.ui.filetab.*;
import de.bund.zrb.ui.filetab.event.*;
import de.bund.zrb.helper.SettingsHelper;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.ui.FileTab;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.Optional;

public class FileTabImpl implements FileTab {

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


    @Override
    public JPopupMenu createContextMenu(Runnable onClose) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem bookmarkItem = new JMenuItem("📌 Als Lesezeichen merken");
        bookmarkItem.addActionListener(e -> {
            MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
            main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), (buffer != null ? buffer.getLink() : null));
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

        setContent(content);

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

        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        statusBarPanel.setSentenceTypes(new java.util.ArrayList<>(registry.getSentenceTypeSpec().getDefinitions().keySet()));
        statusBarPanel.setSelectedSentenceType(sentenceType);

        statusBarPanel.bindEvents(dispatcher);
        editorPanel.bindEvents(dispatcher);
        comparePanel.bindEvents(dispatcher);

        this.eventManager = new FileTabEventManager(this);
        eventManager.bindAll();

        dispatcher.publish(new SentenceTypeChangedEvent(sentenceType));

        if(toCompare != null && toCompare) {
            this.toggleComparePanel();
        }
        if(searchPattern != null && !searchPattern.isEmpty())
        {
            this.searchFor(searchPattern);
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
        splitPane.setDividerLocation(0.7);
        statusBarPanel.getCompareButton().setVisible(false);
    }

    public void toggleComparePanel() {
        boolean show = !comparePanel.isVisible();
        comparePanel.setVisible(show);

        if (show) {
            splitPane.setDividerLocation(0.7);
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
        if (model.getBuffer() == null) return "[Neu]";
        String name = model.getBuffer().getMeta().getName();
        return model.isChanged() ? name + " *" : name;
    }

    @Override
    public String getTooltip() {
        return model.getFullPath();
    }

    @Override
    public void onClose() {
        // Ressourcenfreigabe falls nötig
    }

    @Override
    public void saveIfApplicable() {
        FtpFileBuffer buffer = model.getBuffer();
        if (model.getBuffer() == null) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Diese Datei wurde noch nicht gespeichert.\nBitte 'Speichern unter' verwenden.",
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
        editorPanel.getTextArea().setText(content);
    }

    @Override
    public void setContent(String content, String sentenceType) {
        setContent(content);
        dispatcher.publish(new SentenceTypeChangedEvent(sentenceType));
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
}
