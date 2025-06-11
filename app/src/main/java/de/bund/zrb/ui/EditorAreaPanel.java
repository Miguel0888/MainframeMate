package de.bund.zrb.ui;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.util.RegexFoldParser;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Highlighter;
import javax.swing.text.DefaultHighlighter;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;

public class EditorAreaPanel extends JPanel {

    private final RSyntaxTextArea textArea;
    private final UndoManager undoManager;
    private final JButton undoButton;
    private final JButton redoButton;
    private boolean soundEnabled = true;

    public EditorAreaPanel() {
        super(new BorderLayout());
        this.textArea = new RSyntaxTextArea();
        this.undoManager = new UndoManager();
        this.undoButton = new JButton("\u21B6");
        this.redoButton = new JButton("\u21B7");

        Settings settings = SettingsHelper.load();
        initEditorSettings(settings);
        this.soundEnabled = settings.soundEnabled;

        textArea.getDocument().addUndoableEditListener(undoManager);
        textArea.getDocument().addDocumentListener(createChangeListener());

        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);
        add(scrollPane, BorderLayout.CENTER);
    }

    private DocumentListener createChangeListener() {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markAsChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { markAsChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { markAsChanged(); }
        };
    }

    // Placeholder for tab controller logic
    private void markAsChanged() {
        // Can be connected to FileTabImpl via callback or observer
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

    public void applyRegexFilter(String input) {
        RegexFoldParser parser = new RegexFoldParser(input, hasMatch -> {
            if (!hasMatch && !input.trim().isEmpty()) {
                getTextArea().setBackground(new Color(255, 220, 220));
                if (soundEnabled) {
                    Toolkit.getDefaultToolkit().beep();
                }
            } else {
                getTextArea().setBackground(UIManager.getColor("TextArea.background"));
            }
        });
        textArea.getFoldManager().setFolds(parser.getFolds(textArea));
        textArea.repaint();
    }

    private void initEditorSettings(Settings settings) {
        Font editorFont = new Font(settings.editorFont, Font.PLAIN, settings.editorFontSize);
        textArea.setFont(editorFont);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setTabSize(4);
        textArea.setHighlightCurrentLine(true);
        textArea.setMarkOccurrences(false);

        if (settings.marginColumn > 0) {
            textArea.setMarginLineEnabled(true);
            textArea.setMarginLinePosition(settings.marginColumn);
        } else {
            textArea.setMarginLineEnabled(false);
        }
        textArea.setMarginLineColor(Color.RED);
        textArea.setLineWrap(false);
        textArea.setPaintTabLines(true);
    }

    public void undo() {
        try {
            if (undoManager.canUndo()) undoManager.undo();
        } catch (CannotUndoException ignored) {}
    }

    public void redo() {
        try {
            if (undoManager.canRedo()) undoManager.redo();
        } catch (CannotUndoException ignored) {}
    }

    public void resetUndoHistory() {
        undoManager.discardAllEdits();
    }

    public void highlightText(int startOffset, int endOffset, Color color) {
        try {
            Highlighter highlighter = textArea.getHighlighter();
            highlighter.addHighlight(startOffset, endOffset,
                    new DefaultHighlighter.DefaultHighlightPainter(color));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
