package org.example.ui;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpFileObserver;
import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.util.SettingsManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.UndoManager;
import javax.swing.undo.CannotUndoException;

public class FileTab implements FtpTab, FtpFileObserver {

    private final FtpManager ftpManager;
    private final FtpFileBuffer buffer;
    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final RSyntaxTextArea textArea = new RSyntaxTextArea();
    private final TabbedPaneManager tabbedPaneManager;

    private final UndoManager undoManager = new UndoManager();
    private final JButton undoButton = new JButton("↶");
    private final JButton redoButton = new JButton("↷");

    public FileTab(FtpManager ftpManager, TabbedPaneManager tabbedPaneManager, FtpFileBuffer buffer) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.buffer = buffer;

        initEditorSettings(textArea, SettingsManager.load());
        textArea.setText(buffer.getContent());
        textArea.getDocument().addUndoableEditListener(undoManager);
        RTextScrollPane scroll = new RTextScrollPane(textArea);

        JPanel statusBar = createStatusBar();

        mainPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        ftpManager.addFileObserver(this); // ⬅ Registrierung
    }

    @Override
    public String getTitle() {
        return "📄 " + buffer.getMeta().getName();
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void saveIfApplicable() {
        try {
            boolean ok = ftpManager.storeFile(buffer, textArea.getText());
            if (!ok) {
                JOptionPane.showMessageDialog(mainPanel, "Datei wurde verändert!\nSpeichern abgebrochen.", "Konflikt", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Speichern:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onClose() {
        ftpManager.removeFileObserver(this);
        // evtl. weitere Änderungen prüfen, Ressourcen freigeben
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem saveItem = new JMenuItem("💾 Speichern");
        saveItem.addActionListener(e -> saveIfApplicable());

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(saveItem);
        menu.add(closeItem);
        return menu;
    }

    @Override
    public void onFileReloaded(String remotePath, String newContent) {
        if (buffer.getRemotePath().equals(remotePath)) {
            setContent(newContent);
            resetUndoHistory();
            changed = false;
            updateTabTitle();
        }
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        // Links: Undo/Redo Buttons
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        undoButton.setToolTipText("Änderung rückgängig machen");
        undoButton.setEnabled(false);
        undoButton.addActionListener(e -> {
            try {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            } catch (CannotUndoException ex) {
                // ignorieren
            }
            updateUndoRedoState();
        });

        redoButton.setToolTipText("Wiederherstellen");
        redoButton.setEnabled(false);
        redoButton.addActionListener(e -> {
            try {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                }
            } catch (CannotUndoException ex) {
                // ignorieren
            }
            updateUndoRedoState();
        });

        leftPanel.add(undoButton);
        leftPanel.add(redoButton);

        // Rechts: Encoding-Auswahl
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel encodingLabel = new JLabel("Encoding:");
        JComboBox<String> encodingBox = new JComboBox<>(
                SettingsManager.SUPPORTED_ENCODINGS.toArray(new String[0])
        );
        encodingBox.setSelectedItem(SettingsManager.load().encoding);
        encodingBox.addActionListener(e -> {
            String selected = (String) encodingBox.getSelectedItem();
            ftpManager.getClient().setControlEncoding(selected);
            Charset charset = Charset.forName(selected);
            textArea.setText(buffer.decodeWith(charset));
        });
        rightPanel.add(encodingLabel);
        rightPanel.add(encodingBox);

        // Listener registrieren, um Buttons aktuell zu halten
        textArea.getDocument().addUndoableEditListener(e -> updateUndoRedoState());

        statusBar.add(leftPanel, BorderLayout.WEST);
        statusBar.add(rightPanel, BorderLayout.EAST);
        return statusBar;
    }

    private void updateUndoRedoState() {
        undoButton.setEnabled(undoManager.canUndo());
        redoButton.setEnabled(undoManager.canRedo());
    }




    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Plugin-Management
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void setContent(String newText) {
        textArea.selectAll();
        textArea.replaceSelection(""); // erlaubt Undo des Löschens
        textArea.append(newText);      // erlaubt Undo des Einfügens
        updateUndoRedoState();
    }

    public void setContent(String text, List<Map<String, Object>> feldDefinitionen, int zeilenSchema) {
        setContent(text); // Basismethode aufrufen
        highlightStructuredContent(text, feldDefinitionen, zeilenSchema);
    }

    public void setStructuredContent(String text, List<Map<String, Object>> feldDefinitionen, int zeilenSchema) {
        setContent(text); // Inhalt setzen (inkl. Undo)
        highlightStructuredContent(text, feldDefinitionen, zeilenSchema);
    }

    public void resetUndoHistory() {
        undoManager.discardAllEdits();
        updateUndoRedoState();
    }

    //ToDo: Mit Hashing kombinieren
    private boolean changed = false; // wird aber sowieso beim speichern geprüft mittels hashWert

    public void markAsChanged() {
        this.changed = true;
        // Optional: Tab-Titel mit Stern markieren
        updateTabTitle();
    }

    private void updateTabTitle() {
        int index = ((JTabbedPane) mainPanel.getParent()).indexOfComponent(mainPanel);
        if (index >= 0) {
            String title = getTitle();
            if (changed && !title.endsWith("*")) {
                title += " *";
            }
            ((JTabbedPane) mainPanel.getParent()).setTitleAt(index, title);
        }
    }

    public String getContent() {
        return textArea.getText();
    }

    private void initEditorSettings(RSyntaxTextArea editor, Settings settings) {
        // Font aus Settings setzen
        Font editorFont = new Font(settings.editorFont, Font.PLAIN, settings.editorFontSize);
        editor.setFont(editorFont);

        // Syntax und Verhalten
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        editor.setCodeFoldingEnabled(false);
        editor.setAntiAliasingEnabled(true);
        editor.setTabSize(4);
        editor.setHighlightCurrentLine(true);
        editor.setMarkOccurrences(false);

        // Vertikale Begrenzung bei 80 Zeichen
        editor.setMarginLineEnabled(true);
        if (settings.marginColumn > 0) {
            editor.setMarginLineEnabled(true);
            editor.setMarginLinePosition(settings.marginColumn);
        } else {
            editor.setMarginLineEnabled(false);
        }
        editor.setMarginLineColor(Color.RED);

        // Optional: Tabs sichtbar machen
        editor.setPaintTabLines(true);
    }

    private void highlightStructuredContent(String content, List<Map<String, Object>> felder, int schemaLines) {
        Highlighter highlighter = textArea.getHighlighter();
        highlighter.removeAllHighlights();

        String[] lines = content.split("\n");

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            int schemaRow = lineIndex % schemaLines;
            int lineOffset = getLineStartOffset(lines, lineIndex);
            String line = lines[lineIndex];

            for (Map<String, Object> feld : felder) {
                int feldRow = feld.containsKey("row") ? getIntValue(feld.get("row")) - 1 : 0;
                if (feldRow != schemaRow) continue;

                int start = getIntValue(feld.get("pos")) - 1;
                int len = getIntValue(feld.get("len"));
                if (start >= line.length()) continue;

                int end = Math.min(line.length(), start + len);

                try {
                    highlighter.addHighlight(
                            lineOffset + start,
                            lineOffset + end,
                            new DefaultHighlighter.DefaultHighlightPainter(getColorFor((String) feld.get("name"), feld))
                    );
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int getLineStartOffset(String[] lines, int lineIndex) {
        int offset = 0;
        for (int i = 0; i < lineIndex; i++) {
            offset += lines[i].length() + 1; // \n
        }
        return offset;
    }

    private int getIntValue(Object obj) {
        return (obj instanceof Number) ? ((Number) obj).intValue() : Integer.parseInt(obj.toString());
    }


    private Color getColorFor(String name, Map<String, Object> feld) {
        Settings settings = SettingsManager.load();

        if (name == null && feld != null && feld.containsKey("value")) {
            // Konstante, aber ohne Namen → grau, außer Settings-Override
            String valueKey = "CONST_" + String.valueOf(feld.get("value")).toUpperCase();
            String hex = settings.fieldColorOverrides.get(valueKey);
            return hex != null ? Color.decode(hex) : Color.GRAY;
        }

        if (name == null) {
            return Color.GRAY;
        }

        String hex = settings.fieldColorOverrides.get(name.toUpperCase());
        if (hex != null) {
            try {
                return Color.decode(hex);
            } catch (NumberFormatException e) {
                System.err.println("⚠️ Ungültige Farbdefinition für " + name + ": " + hex);
            }
        }

        // Dynamische Farbgenerierung für Felder mit Namen
        int hash = Math.abs(name.hashCode());
        float hue = (hash % 360) / 360f;
        return Color.getHSBColor(hue, 0.5f, 0.85f);
    }


    public FtpFileBuffer getBuffer() {
        return buffer;
    }
}
