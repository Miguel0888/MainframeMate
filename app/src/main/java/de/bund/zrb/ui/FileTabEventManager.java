package de.bund.zrb.ui;

import de.bund.zrb.ui.filetab.event.*;
import de.zrb.bund.api.SentenceTypeRegistry;

public class FileTabEventManager {

    private final FileTabImpl fileTab;

    public FileTabEventManager(FileTabImpl fileTab) {
        this.fileTab = fileTab;
    }

    public void bindAll() {
        fileTab.dispatcher.subscribe(SentenceTypeChangedEvent.class, event -> {
            fileTab.model.setSentenceType(event.newType);

            if (event.newType == null || event.newType.trim().isEmpty()) {
                // Satzart wurde "abgewählt"
                fileTab.highlighter.clearHighlights(fileTab.editorPanel.getTextArea());
                fileTab.highlighter.clearHighlights(fileTab.comparePanel.getOriginalTextArea());
                fileTab.legendController.clearLegend(); // optional
                return;
            }

            getRegistry().findDefinition(event.newType).ifPresent(def -> {
                int schemaLines = def.getRowCount() != null ? def.getRowCount() : 1;
                fileTab.highlighter.highlightFields(fileTab.editorPanel.getTextArea(), def.getFields(), schemaLines);
                fileTab.legendController.setDefinition(def);
                fileTab.legendController.updateLegendForCaret(0);
                fileTab.highlighter.highlightFields(fileTab.comparePanel.getOriginalTextArea(), def.getFields(), schemaLines);
            });
        });

        fileTab.dispatcher.subscribe(CaretMovedEvent.class, event -> {
            fileTab.legendController.updateLegendForCaret(event.editorLine);
        });

        fileTab.dispatcher.subscribe(RegexFilterChangedEvent.class, event -> {
            fileTab.filterCoordinator.applyFilter();
        });

        fileTab.dispatcher.subscribe(EditorContentChangedEvent.class, event -> {
            if (!fileTab.model.isChanged()) {
                fileTab.model.markChanged();
                fileTab.updateTitle(); // 👈 direkte Methode auf FileTabImpl
            }
        });

        fileTab.dispatcher.subscribe(AppendChangedEvent.class, event -> {
            fileTab.model.setAppend(event.append);
        });

        fileTab.dispatcher.subscribe(CloseComparePanelEvent.class, event -> {
            fileTab.comparePanel.setVisible(false);
            fileTab.statusBarPanel.getCompareButton().setVisible(true);
        });

        fileTab.dispatcher.subscribe(ShowComparePanelEvent.class, event -> {
            fileTab.showComparePanel(); // 👈 zentrales Verhalten
        });

        fileTab.dispatcher.subscribe(UndoRequestedEvent.class, e -> fileTab.editorPanel.undo());
        fileTab.dispatcher.subscribe(RedoRequestedEvent.class, e -> fileTab.editorPanel.redo());

        fileTab.dispatcher.subscribe(EditorContentChangedEvent.class, event -> {
            if(event.changed)
            {
                if (!fileTab.model.isChanged()) {
                    fileTab.model.markChanged();
                }
            } else {
                if (fileTab.model.isChanged()) {
                    fileTab.model.resetChanged();
                }
            }

            fileTab.updateTitle(); // 👈 Immer aufrufen
        });

    }

    private SentenceTypeRegistry getRegistry() {
        return fileTab.tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
    }
}
