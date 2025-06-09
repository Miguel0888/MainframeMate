package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.service.FileContentService;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ui.util.RegexFoldParser;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.api.TabAdapter;
import de.zrb.bund.api.TabType;
import de.zrb.bund.newApi.sentence.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    // Aktuelle Satzart, falls bekannt
    private JComboBox<String> sentenceComboBox;
    private JPanel legendWrapper;

    private int currentLegendRowIndex = 0;
    private int currentMaxRows = 1;
    private boolean soundEnabled = true;

    public FileTab(TabbedPaneManager tabbedPaneManager, @Nullable FtpManager ftpManager, String content, String sentenceType) {
        this(tabbedPaneManager, ftpManager, (FtpFileBuffer) null, sentenceType);
        soundEnabled = SettingsHelper.load().soundEnabled;
        if(content != null) {
            textArea.setText(content);
        }
        highlight(sentenceType);
    }

    public FileTab(TabbedPaneManager tabbedPaneManager, @NotNull FtpManager ftpManager, FtpFileBuffer buffer, String sentenceType) {
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

        textArea.addCaretListener(e -> updateLegendByCaret()); // set the corresponding legend automatically
        textArea.setCodeFoldingEnabled(true);
        highlight(sentenceType);
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

        // Undo-Panel links
        JPanel leftPanel = createUndoPanel();

        // Satzart-Panel rechts (Legende & ComboBox)
        JPanel rightPanel = createSentencePanel();

        // RegGrep-Suchleiste in der Mitte
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        JTextField grepField = new JTextField(30);
        grepField.setToolTipText("Regulärer Ausdruck für Zeilenfilterung");
        grepField.setToolTipText(
                "<html>" +
                        "Regulärer Ausdruck für die Zeilenfilterung<br>" +
                        "<br>" +
                        "<b>Beispiele:</b><br>" +
                        "&bull; <code>abc</code> – enthält 'abc'<br>" +
                        "&bull; <code>^abc</code> – beginnt mit 'abc'<br>" +
                        "&bull; <code>abc$</code> – endet mit 'abc'<br>" +
                        "&bull; <code>.*test.*</code> – enthält 'test' (beliebiger Kontext)<br>" +
                        "&bull; <code>\\d+</code> – enthält eine oder mehrere Ziffern<br>" +
                        "&bull; <code>[A-Z]{3}</code> – genau drei Großbuchstaben<br>" +
                        "<br>" +
                        "Hinweis: Die Suche ist <b>nicht</b> groß-/kleinschreibungssensitiv.<br>" +
                        "Alle Zeilen, die nicht passen, werden gefaltet." +
                        "</html>"
        );
//        centerPanel.add(new JLabel("🔍 Grep:"));
        centerPanel.add(grepField);

        grepField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyGrepFilter(grepField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyGrepFilter(grepField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyGrepFilter(grepField.getText());
            }

            private void applyGrepFilter(String input) {
                RegexFoldParser parser = new RegexFoldParser(input, hasMatch -> {
                    if (!hasMatch && !input.trim().isEmpty()) {
                        grepField.setBackground(new Color(255, 200, 200));
                        if(soundEnabled)
                        {
                            Toolkit.getDefaultToolkit().beep(); // einfacher Standardsound
                        }
                    } else {
                        grepField.setBackground(UIManager.getColor("TextField.background"));
                    }
                });

                textArea.getFoldManager().setFolds(parser.getFolds(textArea));
                textArea.repaint();
            }
        });

        textArea.getDocument().addUndoableEditListener(e -> updateUndoRedoState());

        statusBar.add(leftPanel, BorderLayout.WEST);
        statusBar.add(centerPanel, BorderLayout.CENTER);
        statusBar.add(rightPanel, BorderLayout.EAST);

        return statusBar;
    }


    @NotNull
    private JPanel createUndoPanel() {
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
        return leftPanel;
    }

    @NotNull
    private JPanel createSentencePanel() {
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));

        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();

        // Initialsatzart bestimmen
        String initialType = getCurrentSentenceTypeGuess(textArea.getText());
        SentenceDefinition initialDef = registry.findDefinition(initialType).orElse(null);
        currentMaxRows = initialDef != null && initialDef.getRowCount() != null ? initialDef.getRowCount() : 1;
        currentLegendRowIndex = 0;

        legendWrapper = new JPanel(new BorderLayout());
        legendWrapper.add(createLegendPanelForRow(initialType, currentLegendRowIndex), BorderLayout.CENTER);

        sentenceComboBox = new JComboBox<>();
        sentenceComboBox.addItem("");
        registry.getSentenceTypeSpec().getDefinitions().keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(sentenceComboBox::addItem);
        sentenceComboBox.setSelectedItem(initialType);

        sentenceComboBox.addActionListener(e -> {
            String selected = (String) sentenceComboBox.getSelectedItem();
            if (selected != null && !selected.trim().isEmpty()) {
                highlight(selected);
                Optional<SentenceDefinition> defOpt = registry.findDefinition(selected);
                if (defOpt.isPresent()) {
                    SentenceDefinition def = defOpt.get();
                    currentMaxRows = def.getRowCount() != null ? def.getRowCount() : 1;
                    currentLegendRowIndex = 0;
                    updateLegend(legendWrapper);
                }
            } else {
                currentMaxRows = 1;
                currentLegendRowIndex = 0;
                legendWrapper.removeAll();
                legendWrapper.revalidate();
                legendWrapper.repaint();
            }
        });

        rightPanel.add(legendWrapper);
        rightPanel.add(Box.createHorizontalStrut(10));
//        rightPanel.add(new JLabel("Satzart:"));
        rightPanel.add(sentenceComboBox);

        return rightPanel;
    }

    private void styleAsClickable(JLabel label) {
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setForeground(Color.BLUE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                label.setForeground(Color.RED);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                label.setForeground(Color.BLUE);
            }
        });
    }

    private void updateLegend(JPanel legendWrapper) {
        String selectedType = (String) sentenceComboBox.getSelectedItem();
        if (selectedType == null || selectedType.trim().isEmpty()) return;

        legendWrapper.removeAll();
        legendWrapper.add(createLegendPanelForRow(selectedType, currentLegendRowIndex), BorderLayout.CENTER);
        legendWrapper.revalidate();
        legendWrapper.repaint();
    }

    private void updateLegendByCaret() {
        String sentenceType = (String) sentenceComboBox.getSelectedItem();
        if (sentenceType == null || sentenceType.trim().isEmpty()) return;

        Optional<SentenceDefinition> defOpt = tabbedPaneManager
                .getMainframeContext()
                .getSentenceTypeRegistry()
                .findDefinition(sentenceType);

        if (!defOpt.isPresent()) return;

        int rowCount = defOpt.get().getRowCount() != null ? defOpt.get().getRowCount() : 1;

        try {
            int caretPos = textArea.getCaretPosition();
            int line = textArea.getLineOfOffset(caretPos);
            int effectiveRow = line % rowCount;

            if (effectiveRow != currentLegendRowIndex) {
                currentLegendRowIndex = effectiveRow;
                updateLegend(legendWrapper);
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
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

    /** Adds Content with Undo support
     *
     * @param newText
     */
    public void setContent(String newText) {
        textArea.selectAll();
        textArea.replaceSelection(""); // erlaubt Undo des Löschens
        textArea.append(newText);      // erlaubt Undo des Einfügens
        updateUndoRedoState();
    }

    public void setContent(String text, String sentenceType) {
        sentenceComboBox.setSelectedItem(sentenceType);
        setContent(text); // Undo etc.
        highlight(sentenceType);
    }

    public void highlight(@Nullable String sentenceType) {
        tabbedPaneManager.getMainframeContext()
                .getSentenceTypeRegistry()
                .findDefinition(sentenceType)
                .ifPresent(def -> {
                    int schemaLines = def.getRowCount() != null ? def.getRowCount() : 1;
                    highlightFields(def.getFields(), schemaLines);
                    sentenceComboBox.setSelectedItem(sentenceType);
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

    private void highlightFields(FieldMap fields, int schemaLines) {
        Highlighter highlighter = textArea.getHighlighter();
        highlighter.removeAllHighlights();

        String[] lines = textArea.getText().split("\n");

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            int schemaRow = lineIndex % schemaLines;
            int lineOffset = getLineStartOffset(lines, lineIndex);
            String line = lines[lineIndex];

            for (Map.Entry<FieldCoordinate, SentenceField> entry : fields.entrySet()) {
                FieldCoordinate coord = entry.getKey();
                SentenceField field = entry.getValue();

                int fieldRow = coord.getRow() - 1;
                if (fieldRow != schemaRow) continue;

                int start = coord.getPosition() - 1;
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

    private JPanel createLegendPanelForRow(String sentenceType, int rowIndex) {
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        Optional<SentenceDefinition> defOpt = registry.findDefinition(sentenceType);
        if (!defOpt.isPresent()) return legendPanel;

        for (Map.Entry<FieldCoordinate, SentenceField> entry : defOpt.get().getFields().entrySet()) {
            FieldCoordinate coord = entry.getKey();
            SentenceField field = entry.getValue();

            if (coord.getRow() - 1 != rowIndex) continue;

            String name = field.getName();
            if (name == null || name.trim().isEmpty()) continue;

            Color color = getColorFor(name, field.getColor());
            JLabel label = new JLabel(name);
            label.setOpaque(true);
            label.setBackground(color);
            label.setForeground(Color.BLACK);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.DARK_GRAY),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)
            ));
            legendPanel.add(label);
        }

        return legendPanel;
    }

    public String getCurrentSentenceType() {
        return Objects.requireNonNull(sentenceComboBox.getSelectedItem()).toString();
    }

}
