package de.bund.zrb.ui.filetab;

import de.bund.zrb.model.Settings;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ui.filetab.event.EditorContentChangedEvent;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.undo.UndoManager;

import java.awt.*;

public class EditorPanel extends JPanel {

    private final RSyntaxTextArea textArea = new RSyntaxTextArea();
    private final UndoManager undoManager = new UndoManager();

    private final JButton undoButton = new JButton("↶");
    private final JButton redoButton = new JButton("↷");

    public EditorPanel() {
        super(new BorderLayout());

        initTextArea();
        textArea.getDocument().addUndoableEditListener(undoManager);

        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setFoldIndicatorEnabled(true);

        add(scrollPane, BorderLayout.CENTER);
    }

    private void initTextArea() {
        Settings settings = SettingsHelper.load();
        Font font = new Font(settings.editorFont, Font.PLAIN, settings.editorFontSize);
        textArea.setFont(font);
        textArea.setSyntaxEditingStyle("text/plain");
        textArea.setAntiAliasingEnabled(true);
        textArea.setCodeFoldingEnabled(true);
        textArea.setHighlightCurrentLine(true);
        textArea.setTabSize(4);
        textArea.setLineWrap(false);
        textArea.setPaintTabLines(true);
        if (settings.marginColumn > 0) {
            textArea.setMarginLineEnabled(true);
            textArea.setMarginLinePosition(settings.marginColumn);
        }
    }

    public RSyntaxTextArea getTextArea() {
        return textArea;
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public JButton getUndoButton() {
        return undoButton;
    }

    public JButton getRedoButton() {
        return redoButton;
    }

    public void undo() {
        if (undoManager.canUndo()) undoManager.undo();
    }

    public void redo() {
        if (undoManager.canRedo()) undoManager.redo();
    }

    public void resetUndoHistory() {
        undoManager.discardAllEdits();
    }

    public void bindEvents(FileTabEventDispatcher dispatcher) {
        getTextArea().getDocument().addUndoableEditListener(e ->
                dispatcher.publish(new EditorContentChangedEvent(true))
        );
    }

}
