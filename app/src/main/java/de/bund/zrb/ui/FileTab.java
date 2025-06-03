package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.service.FileContentService;
import de.bund.zrb.helper.SettingsHelper;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.api.TabAdapter;
import de.zrb.bund.api.TabType;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceField;
import de.zrb.bund.newApi.sentence.SentenceMeta;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.UndoManager;
import javax.swing.undo.CannotUndoException;

public class FileTab implements FtpTab, TabAdapter {

    private final FtpManager ftpManager;
    private final FileContentService fileContentService;
    private FtpFileBuffer buffer;
    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final RSyntaxTextArea textArea = new RSyntaxTextArea();
    private final TabbedPaneManager tabbedPaneManager;

    private final UndoManager undoManager = new UndoManager();
    private final JButton undoButton = new JButton("↶");
    private final JButton redoButton = new JButton("↷");

    //ToDo: Mit Hashing kombinieren
    private boolean changed = false; // wird aber sowieso beim speichern geprüft mittels hashWert
    private String currentSentenceType = null; // Aktuelle Satzart, falls bekannt

    public FileTab(TabbedPaneManager tabbedPaneManager, String content, String sentenceType) {
        this(tabbedPaneManager, (FtpManager) null, (FtpFileBuffer) null);
        this.currentSentenceType = sentenceType; // Aktuelle Satzart speichern
        if(content != null) {
            textArea.setText(content);
        }
    }

    public FileTab(TabbedPaneManager tabbedPaneManager, FtpManager ftpManager, FtpFileBuffer buffer) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.fileContentService = new FileContentService(ftpManager);
        this.buffer = buffer;

        initEditorSettings(textArea, SettingsHelper.load());
        if(buffer != null)
        {
            textArea.setText(fileContentService.decodeWith(buffer));
        }
        textArea.getDocument().addUndoableEditListener(undoManager);
        RTextScrollPane scroll = new RTextScrollPane(textArea);

        JPanel statusBar = createStatusBar();

        mainPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markAsChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markAsChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markAsChanged();
            }
        });
    }

    @Override
    public String getTitle() {
        String title = "[Neu]";
        if (buffer != null) {
            title = "📄 " + buffer.getMeta().getName();
        }

        if (changed && !title.endsWith(" *")) {
            title += " *";
        }

        return title;
    }

    @Override
    public String getTooltip() {
        return buffer != null ? buffer.getLink() : "Wird nicht gespeichert";
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

        JMenuItem bookmarkItem = new JMenuItem("🕮 Bookmark setzen");
        bookmarkItem.addActionListener(e -> {
            if (buffer != null) {
                MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
                main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), buffer.getLink());
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Kein FTP-Pfad verfügbar für Bookmark.");
            }
        });

        JMenuItem saveItem = new JMenuItem("💾 Speichern");
        saveItem.addActionListener(e -> saveIfApplicable());

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(bookmarkItem);
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

        // Rechts: Satzart-Auswahl
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel sentenceLabel = new JLabel("Satzart:");

        JComboBox<String> sentenceBox = new JComboBox<>();
        // Erst den leeren Eintrag hinzufügen
        sentenceBox.addItem(""); // entspricht "Keine Auswahl"
        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        registry.getSentenceTypeSpec().getDefinitions()
                .keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(sentenceBox::addItem);

        // Optional: aktuelle Satzart setzen (wenn bekannt)
        sentenceBox.setSelectedItem(getCurrentSentenceTypeGuess(textArea.getText()));

        sentenceBox.addActionListener(e -> {
            String selected = (String) sentenceBox.getSelectedItem();
            if (selected != null && !selected.trim().isEmpty()) {
                highlight(selected);
            } else {
                // Satzart wurde abgewählt → Highlighting entfernen
                textArea.getHighlighter().removeAllHighlights();
                currentSentenceType = null;
            }
        });

        rightPanel.add(sentenceLabel);
        rightPanel.add(sentenceBox);


        // Listener registrieren, um Buttons aktuell zu halten
        textArea.getDocument().addUndoableEditListener(e -> updateUndoRedoState());

        statusBar.add(leftPanel, BorderLayout.WEST);
        statusBar.add(rightPanel, BorderLayout.EAST);
        return statusBar;
    }

    /**
     * Versucht, die aktuelle Satzart anhand des Remote-Pfads (falls vorhanden) zu erraten.
     * Zuerst wird gegen die bekannten Pfade (`paths`) geprüft, dann gegen das Pfad-Pattern (`pathPattern`).
     * Erst wenn nichts passt, wird der Inhalt als Fallback herangezogen.
     *
     * @param content Der aktuelle Dateiinhalt (nur sekundär genutzt).
     * @return Der Name der vermuteten Satzart oder null, wenn keine passende gefunden wurde.
     */
    private String getCurrentSentenceTypeGuess(String content) {
        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();

        String filePath = getPath();     // z. B. /zrb/data/abc/SA300.DAT
        String fullPath = getFullPath(); // z. B. ftp://server/zrb/data/abc/SA300.DAT

        if (filePath == null && (fullPath == null || fullPath.isEmpty())) {
            return null; // Kein Pfad vorhanden, keine sinnvolle Prüfung möglich
        }

        for (Map.Entry<String, SentenceDefinition> entry : registry.getSentenceTypeSpec().getDefinitions().entrySet()) {
            String name = entry.getKey();
            SentenceDefinition def = entry.getValue();
            SentenceMeta meta = def.getMeta();
            if (meta == null) continue;

            List<String> paths = meta.getPaths();
            String pattern = meta.getPathPattern();

            // 1. Prüfe feste Pfade (case-insensitive enthält)
            if (paths != null && !paths.isEmpty()) {
                for (String path : paths) {
                    if (path != null && !path.trim().isEmpty()) {
                        String normalized = path.trim().toLowerCase();
                        if ((filePath != null && filePath.toLowerCase().contains(normalized)) ||
                                (fullPath != null && fullPath.toLowerCase().contains(normalized))) {
                            return name;
                        }
                    }
                }
            }

            // 2. Prüfe Regex-Pattern
            if (pattern != null && !pattern.trim().isEmpty()) {
                try {
                    if ((filePath != null && filePath.matches(pattern)) ||
                            (fullPath != null && fullPath.matches(pattern))) {
                        return name;
                    }
                } catch (Exception e) {
                    // Logge fehlerhafte Patterns (nicht abbrechen)
                    System.err.println("⚠️ Ungültiges Pfad-Pattern bei Satzart '" + name + "': " + pattern);
                }
            }
        }

        // 3. Fallback: kein Match anhand des Pfads – evtl. später: heuristische Content-Prüfung
        return null;
    }

    private void updateUndoRedoState() {
        undoButton.setEnabled(undoManager.canUndo());
        redoButton.setEnabled(undoManager.canRedo());

        // ToDo: May compare hash too
        if(!undoManager.canUndo() && undoManager.canRedo()) { // Hack to avoid reset after first alteration
            changed = false;
            updateTabTitle();
        }
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

    public void setContent(String text, String sentenceType) {
        this.currentSentenceType = sentenceType;
        setContent(text); // Undo etc.
        highlight(sentenceType);
    }

    public void highlight(String sentenceType) {
        this.currentSentenceType = sentenceType;
        tabbedPaneManager.getMainframeContext()
                .getSentenceTypeRegistry()
                .findDefinition(sentenceType)
                .ifPresent(def -> {
                    int schemaLines = def.getRowCount() != null ? def.getRowCount() : 1;
                    highlightFields(def.getFields(), schemaLines);
                });
    }

    public void resetUndoHistory() {
        undoManager.discardAllEdits();
        updateUndoRedoState();
    }

    public void markAsChanged() {
        if(!this.changed) {
            this.changed = true;
            // Optional: Tab-Titel mit Stern markieren
            updateTabTitle();
        }
    }

    /**
     * Liefert den Pfad des Remote-Objekts, falls vorhanden.
     * Andernfalls wird null zurückgegeben.
     *
     * @return Der Pfad des Remote-Objekts oder null.
     */
    @Override
    public String getPath() {
        return (buffer != null) ? buffer.getRemotePath() : null;
    }

    /**
     * Liefert den vollständigen Pfad des Remote-Objekts inclusive Dateinamen.
     * Andernfalls wird ein leerer String zurückgegeben.
     *
     * @return Der vollständige Pfad oder ein leerer String.
     */
    public String getFullPath() {
        return buffer != null ? buffer.getLink() : "";
    }

    @Override
    public TabType getType() {
        return TabType.FILE;
    }

    private void updateTabTitle() {
        tabbedPaneManager.updateTitleFor(this);
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

    private void highlightFields(List<SentenceField> fields, int schemaLines) {
        Highlighter highlighter = textArea.getHighlighter();
        highlighter.removeAllHighlights();

        String[] lines = textArea.getText().split("\n");

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            int schemaRow = lineIndex % schemaLines;
            int lineOffset = getLineStartOffset(lines, lineIndex);
            String line = lines[lineIndex];

            for (SentenceField field : fields) {
                int fieldRow = field.getRow() != null ? field.getRow() - 1 : 0;
                if (fieldRow != schemaRow) continue;

                int start = field.getPosition() != null ? field.getPosition() - 1 : 0;
                int len = field.getLength() != null ? field.getLength() : 0;
                if (start >= line.length()) continue;

                int end = Math.min(line.length(), start + len);

                try {
                    highlighter.addHighlight(
                            lineOffset + start,
                            lineOffset + end,
                            new DefaultHighlighter.DefaultHighlightPainter(
                                    getColorFor(field.getName(), field.getColor()))
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


    private Color getColorFor(String fieldName, String overrideColor) {
        Settings settings = SettingsHelper.load();

        if (fieldName == null && overrideColor != null) {
            // Konstante ohne Namen, Farbe aus Settings versuchen (z. B. CONST_<VALUE>)
            String valueKey = "CONST_" + overrideColor.toUpperCase();
            String hex = settings.fieldColorOverrides.get(valueKey);
            return hex != null ? Color.decode(hex) : Color.GRAY;
        }

        if (fieldName == null) {
            return Color.GRAY;
        }

        // Settings-Override prüfen
        String hex = settings.fieldColorOverrides.get(fieldName.toUpperCase());
        if (hex != null) {
            try {
                return Color.decode(hex);
            } catch (NumberFormatException e) {
                System.err.println("⚠️ Ungültige Farbdefinition für " + fieldName + ": " + hex);
            }
        }

        // Dynamisch berechnete Farbe
        int hash = Math.abs(fieldName.hashCode());
        float hue = (hash % 360) / 360f;
        return Color.getHSBColor(hue, 0.5f, 0.85f);
    }

    public FtpFileBuffer getBuffer() {
        return buffer;
    }
}
