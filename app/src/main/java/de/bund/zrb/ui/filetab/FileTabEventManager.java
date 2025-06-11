package de.bund.zrb.ui.filetab;

import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.filetab.event.*;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.SentenceDefinition;

import java.util.Optional;

public class FileTabEventManager {

    private final FileTabModel model;
    private final FileTabEventDispatcher dispatcher;
    private final SentenceHighlighter highlighter;
    private final LegendController legendController;
    private final FilterCoordinator filterCoordinator;
    private final ComparePanel comparePanel;
    private final StatusBarPanel statusBarPanel;
    private final EditorPanel editorPanel;
    private final TabbedPaneManager tabbedPaneManager;
    private final Runnable updateTitleCallback;

    public FileTabEventManager(
            FileTabModel model,
            FileTabEventDispatcher dispatcher,
            SentenceHighlighter highlighter,
            LegendController legendController,
            FilterCoordinator filterCoordinator,
            ComparePanel comparePanel,
            StatusBarPanel statusBarPanel,
            EditorPanel editorPanel,
            TabbedPaneManager tabbedPaneManager,
            Runnable updateTitleCallback
    ) {
        this.model = model;
        this.dispatcher = dispatcher;
        this.highlighter = highlighter;
        this.legendController = legendController;
        this.filterCoordinator = filterCoordinator;
        this.comparePanel = comparePanel;
        this.statusBarPanel = statusBarPanel;
        this.editorPanel = editorPanel;
        this.tabbedPaneManager = tabbedPaneManager;
        this.updateTitleCallback = updateTitleCallback;
    }

    public void bindAll() {
        dispatcher.subscribe(SentenceTypeChangedEvent.class, event -> {
            model.setSentenceType(event.newType);
            getRegistry().findDefinition(event.newType).ifPresent(def -> {
                int schemaLines = def.getRowCount() != null ? def.getRowCount() : 1;
                highlighter.highlightFields(editorPanel.getTextArea(), def.getFields(), schemaLines);
                legendController.setDefinition(def);
                legendController.updateLegendForCaret(0); // erste Zeile anzeigen
            });
        });

        dispatcher.subscribe(CaretMovedEvent.class, event -> {
            legendController.updateLegendForCaret(event.editorLine);
        });

        dispatcher.subscribe(RegexFilterChangedEvent.class, event -> {
            filterCoordinator.applyFilter();
        });

        dispatcher.subscribe(EditorContentChangedEvent.class, event -> {
            if (!model.isChanged()) {
                model.markChanged();
                updateTitleCallback.run();
            }
        });

        dispatcher.subscribe(AppendChangedEvent.class, event -> {
            model.setAppend(event.append);
        });

        dispatcher.subscribe(CloseComparePanelEvent.class, event -> {
            comparePanel.setVisible(false);
            statusBarPanel.getCompareButton().setVisible(true);
        });

        dispatcher.subscribe(ShowComparePanelEvent.class, event -> {
            comparePanel.setVisible(true);
            comparePanel.getParent().revalidate();
            statusBarPanel.getCompareButton().setVisible(false);
        });
    }

    private SentenceTypeRegistry getRegistry() {
        return tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
    }
}
