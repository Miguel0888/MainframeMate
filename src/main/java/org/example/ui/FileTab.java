package org.example.ui;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.service.FileContentService;
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
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.UndoManager;
import javax.swing.undo.CannotUndoException;

public class FileTab implements FtpTab {

    private final FtpManager ftpManager;
    private final FileContentService fileContentService;
    private FtpFileBuffer buffer;
    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final RSyntaxTextArea textArea = new RSyntaxTextArea();
    private final TabbedPaneManager tabbedPaneManager;

    private final UndoManager undoManager = new UndoManager();
    private final JButton undoButton = new JButton("↶");
    private final JButton redoButton = new JButton("↷");

    public FileTab(TabbedPaneManager tabbedPaneManager, String content) {
        this(tabbedPaneManager, null, null);
        if(content != null) {
            textArea.setText(content);
        }
    }

    public FileTab(TabbedPaneManager tabbedPaneManager, FtpManager ftpManager, FtpFileBuffer buffer) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.fileContentService = new FileContentService(ftpManager);
        this.buffer = buffer;

        initEditorSettings(textArea, SettingsManager.load());
        if(buffer != null)
        {
            textArea.setText(fileContentService.decodeWith(buffer));
        }
        textArea.getDocument().addUndoableEditListener(undoManager);
        RTextScrollPane scroll = new RTextScrollPane(textArea);

        JPanel statusBar = createStatusBar();

        mainPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
    }

    @Override
    public String getTitle() {
        String title = "[Neu]";
        if( buffer != null) {
            title = "📄 " + buffer.getMeta().getName();
        }
        return title;
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void saveIfApplicable() {
        if (buffer == null) {
            createNewBuffer();
        }

        try {
            // Aktuellen Inhalt aus dem Editor holen
            InputStream newContent = fileContentService.createCommitStream(textArea.getText(), buffer.hasRecordStructure());

            // Neuen Buffer aus aktuellem Inhalt erzeugen
            FtpFileBuffer altered = buffer.withContent(newContent);

            // Versuche, die Änderungen zu speichern (Commit prüft auf Konflikte)
            Optional<FtpFileBuffer> conflict = ftpManager.commit(buffer, altered);

            if (conflict.isPresent()) {
                JOptionPane.showMessageDialog(mainPanel,
                        "⚠️ Die Datei wurde auf dem Server geändert!\nSpeichern wurde abgebrochen.",
                        "Speicherkonflikt", JOptionPane.WARNING_MESSAGE);
            } else {
                // Reload implicit server changes, otherwise next save operation will fail, since hash has changed implicitly
//                buffer = ftpManager.open(buffer.getRemotePath());
//                textArea.setText(fileContentService.decodeWith(buffer));
                buffer = altered;
                resetUndoHistory(); // Optional: Undo-Historie nach erfolgreichem Speichern leeren
                changed = false;    // Optional: internen "dirty"-Status zurücksetzen
                updateTabTitle();   // Optional: Stern entfernen
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Fehler beim Speichern:\n" + e.getMessage(),
                    "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Diese Methode wird bei Save-Versuchen aufgerufen, wenn kein Buffer existiert.
     * In einer späteren Version könnte hier ein neuer Buffer erstellt werden.
     */
    private void createNewBuffer() {
        // throw new UnsupportedOperationException("Speichern ist für diesen Tab nicht möglich (kein FTP-Buffer vorhanden).");
        JOptionPane.showMessageDialog(mainPanel,
                "Speichern neuer Dateien ist hier aktuell nicht möglich. Bitte zunächst eine leere Datei im Verbindungs-Tab anlegen.",
                "Nicht unterstützte Operation", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void onClose() {
        // evtl. Änderungen prüfen, Ressourcen freigeben
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
//        JLabel encodingLabel = new JLabel("Encoding:");
//        JComboBox<String> encodingBox = new JComboBox<>(
//                SettingsManager.SUPPORTED_ENCODINGS.toArray(new String[0])
//        );
//        encodingBox.setSelectedItem(SettingsManager.load().encoding);
//        encodingBox.addActionListener(e -> {
//            String selected = (String) encodingBox.getSelectedItem();
//            if (selected != null) {
//                ftpManager.setCharset(Charset.forName(selected));
//            }
//            textArea.setText();
//        });
//        rightPanel.add(encodingLabel);
//        rightPanel.add(encodingBox);

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
