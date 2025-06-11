package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.service.FileContentService;
import de.bund.zrb.ui.filetab.*;
import de.bund.zrb.ui.filetab.event.*;
import de.bund.zrb.helper.SettingsHelper;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.ui.FileTab;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.Optional;

public class FileTabImpl implements FileTab {

    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final FileTabModel model = new FileTabModel();
    private final FileTabEventDispatcher dispatcher = new FileTabEventDispatcher();

    private final EditorPanel editorPanel = new EditorPanel();
    private final ComparePanel comparePanel;
    private final StatusBarPanel statusBarPanel = new StatusBarPanel();

    private final SentenceHighlighter highlighter = new SentenceHighlighter();
    private final LegendRenderer legendRenderer = new LegendRenderer();
    private final FilterCoordinator filterCoordinator;

    private final JSplitPane splitPane;
    private final TabbedPaneManager tabbedPaneManager;
    private final FileContentService contentService;

    public FileTabImpl(TabbedPaneManager tabbedPaneManager,
                       FtpManager ftpManager,
                       String content,
                       String sentenceType) {
        this(tabbedPaneManager, ftpManager, (FtpFileBuffer) null, sentenceType);
        setContent(content, sentenceType);
    }

    public FileTabImpl(TabbedPaneManager tabbedPaneManager, FtpManager ftpManager, FtpFileBuffer buffer, String initialType) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.contentService = new FileContentService(ftpManager);

        String content = buffer != null ? contentService.decodeWith(buffer) : "";
        model.setBuffer(buffer);
        model.setSentenceType(initialType);

        editorPanel.getTextArea().setText(content);
        comparePanel = new ComparePanel(model.getFullPath(), content);
        comparePanel.setVisible(false);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(editorPanel);
        splitPane.setBottomComponent(comparePanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(6);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusBarPanel, BorderLayout.SOUTH);

        // Filter Setup
        filterCoordinator = new FilterCoordinator(
                editorPanel.getTextArea(),
                comparePanel.getOriginalTextArea(),
                statusBarPanel.getGrepField(),
                SettingsHelper.load().soundEnabled
        );

        // Init Satztypen
        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        statusBarPanel.setSentenceTypes(new java.util.ArrayList<>(registry.getSentenceTypeSpec().getDefinitions().keySet()));
        statusBarPanel.setSelectedSentenceType(initialType);

        // Bind Events
        statusBarPanel.bindEvents(dispatcher);
        editorPanel.bindEvents(dispatcher);
        comparePanel.bindEvents(dispatcher);

        bindDispatcherEvents();
        dispatcher.publish(new SentenceTypeChangedEvent(initialType)); // initial auslösen
    }

    private void bindDispatcherEvents() {
        dispatcher.subscribe(SentenceTypeChangedEvent.class, event -> {
            model.setSentenceType(event.newType);
            Optional<SentenceDefinition> defOpt = getRegistry().findDefinition(event.newType);
            if (!defOpt.isPresent()) return;

            SentenceDefinition def = defOpt.get();
            int schemaLines = def.getRowCount() != null ? def.getRowCount() : 1;

            highlighter.highlightFields(editorPanel.getTextArea(), def.getFields(), schemaLines);
            statusBarPanel.getLegendWrapper().removeAll();
            statusBarPanel.getLegendWrapper().add(legendRenderer.renderLegend(def, 0), BorderLayout.CENTER);
            statusBarPanel.getLegendWrapper().revalidate();
            statusBarPanel.getLegendWrapper().repaint();
        });

        dispatcher.subscribe(RegexFilterChangedEvent.class, event -> {
            filterCoordinator.applyFilter();
        });

        dispatcher.subscribe(EditorContentChangedEvent.class, event -> {
            if (!model.isChanged()) {
                model.markChanged();
                tabbedPaneManager.updateTitleFor(this);
            }
        });

        dispatcher.subscribe(AppendChangedEvent.class, event -> {
            model.setAppend(event.append);
        });

        dispatcher.subscribe(CloseComparePanelEvent.class, event -> {
            comparePanel.setVisible(false);
        });

        dispatcher.subscribe(ShowComparePanelEvent.class, event -> {
            showComparePanel();
        });

        dispatcher.subscribe(CloseComparePanelEvent.class, event -> {
            comparePanel.setVisible(false);
            statusBarPanel.getCompareButton().setVisible(true);
        });
    }

    private void showComparePanel() {
        comparePanel.setVisible(true);
        splitPane.setDividerLocation(0.7); // z. B. 70% oben, 30% unten
        statusBarPanel.getCompareButton().setVisible(false);
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
}
