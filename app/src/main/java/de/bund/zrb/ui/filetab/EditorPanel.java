package de.bund.zrb.ui.filetab;

import de.bund.zrb.model.Settings;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ui.filetab.event.CaretMovedEvent;
import de.bund.zrb.ui.filetab.event.EditorContentChangedEvent;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.undo.UndoManager;

import java.awt.*;

public class EditorPanel extends JPanel {

    private boolean suppressChangeEvents = false;
    private int originalContentHash;

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

    private int getCaretLine() {
        try {
            int pos = textArea.getCaretPosition();
            return textArea.getLineOfOffset(pos);
        } catch (Exception ex) {
            return 0;
        }
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
        // Ergänzter DocumentListener für direkte Textänderungserkennung (inkl. Undo)
        textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                handleChange();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                handleChange();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                // Ignorieren, da nicht relevant für plain text
            }

            private void handleChange() {
                if (!suppressChangeEvents) {
                    boolean changed = textArea.getText().hashCode() != originalContentHash;
                    dispatcher.publish(new EditorContentChangedEvent(changed));
                }
            }
        });

        getTextArea().addCaretListener(e -> {
            int caretLine = getCaretLine();
            dispatcher.publish(new CaretMovedEvent(caretLine));
        });

    }

    public void setTextSilently(String content) {
        suppressChangeEvents = true;
        try {
            textArea.setText(content);
            resetUndoHistory(); // damit "Neu" auch wirklich "unverändert" ist
            originalContentHash = content != null ? content.hashCode() : 0;
        } finally {
            suppressChangeEvents = false;
        }
    }


}
