package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.service.FileContentService;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ui.util.RegexFoldParser;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.*;
import de.zrb.bund.newApi.ui.FileTab;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.CannotUndoException;
import java.awt.*;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FileTabImpl implements FileTab {

    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final EditorAreaPanel editorPanel = new EditorAreaPanel();
    private final CompareAddonPanel comparePanel;
    private final JSplitPane splitPane;
    private final JPanel statusBar;

    private final JButton undoButton;
    private final JButton redoButton;
    private final JTextField grepField = new JTextField();
    private final JComboBox<String> sentenceComboBox = new JComboBox<>();
    private final JPanel legendWrapper = new JPanel(new BorderLayout());
    private final JButton toggleCompare = new JButton("\u21BB");

    private final TabbedPaneManager tabbedPaneManager;
    private final FtpManager ftpManager;
    private final FileContentService fileContentService;

    private FtpFileBuffer buffer;
    private boolean compareVisible = false;
    private boolean changed = false;
    private boolean append = false;
    private int currentLegendRowIndex = 0;
    private int currentMaxRows = 1;

    public FileTabImpl(TabbedPaneManager tabbedPaneManager,
                       @NotNull FtpManager ftpManager,
                       @NotNull String content,
                       @NotNull String sentenceType) {

        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.fileContentService = new FileContentService(ftpManager);
        this.buffer = null; // kein Buffer bei manuellem Content

        // Inhalt setzen
        editorPanel.getTextArea().setText(content);
        String path = "[Lokaler Inhalt]";

        comparePanel = new CompareAddonPanel(path, content);
        comparePanel.setVisible(false);
        comparePanel.setCloseAction(this::hideComparePanel);

        undoButton = editorPanel.getUndoButton();
        redoButton = editorPanel.getRedoButton();

        initUndoRedoActions();
        initSentenceComboBox(sentenceType);
        initGrepFilter();

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(editorPanel);
        splitPane.setBottomComponent(comparePanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(6);

        statusBar = createStatusBar();

        toggleCompare.setToolTipText("Vergleich anzeigen");
        toggleCompare.addActionListener(e -> showComparePanel());
        statusBar.add(toggleCompare, BorderLayout.WEST);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
    }


    public FileTabImpl(TabbedPaneManager tabbedPaneManager, @NotNull FtpManager ftpManager, @Nullable FtpFileBuffer buffer, String sentenceType) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.fileContentService = new FileContentService(ftpManager);
        this.buffer = buffer;

        String content = buffer != null ? fileContentService.decodeWith(buffer) : "";
        String path = buffer != null ? buffer.getLink() : "[Unbekannter Pfad]";

        editorPanel.getTextArea().setText(content);
        comparePanel = new CompareAddonPanel(path, content);
        comparePanel.setVisible(false);
        comparePanel.setCloseAction(this::hideComparePanel);

        undoButton = editorPanel.getUndoButton();
        redoButton = editorPanel.getRedoButton();

        initUndoRedoActions();
        initSentenceComboBox(sentenceType);
        initGrepFilter();

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(editorPanel);
        splitPane.setBottomComponent(comparePanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(6);

        statusBar = createStatusBar();

        toggleCompare.setToolTipText("Vergleich anzeigen");
        toggleCompare.addActionListener(e -> showComparePanel());
        statusBar.add(toggleCompare, BorderLayout.WEST);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
    }

    private void initUndoRedoActions() {
        undoButton.setToolTipText("Rückgängig");
        redoButton.setToolTipText("Wiederholen");
        undoButton.addActionListener(e -> {
            editorPanel.undo();
            updateUndoRedoState();
        });
        redoButton.addActionListener(e -> {
            editorPanel.redo();
            updateUndoRedoState();
        });
        editorPanel.getTextArea().getDocument().addUndoableEditListener(e -> updateUndoRedoState());
    }

    private void updateUndoRedoState() {
        undoButton.setEnabled(editorPanel.getUndoManager().canUndo());
        redoButton.setEnabled(editorPanel.getUndoManager().canRedo());
        markAsChanged();
    }

    private void initSentenceComboBox(String selectedType) {
        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        registry.getSentenceTypeSpec().getDefinitions().keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(sentenceComboBox::addItem);

        sentenceComboBox.setSelectedItem(selectedType);
        sentenceComboBox.addActionListener(e -> {
            String selected = (String) sentenceComboBox.getSelectedItem();
            highlight(selected);
        });
        highlight(selectedType);
    }

    private void initGrepFilter() {
        grepField.setToolTipText("Regulärer Ausdruck zur Filterung der Anzeige");
        grepField.getDocument().addDocumentListener(new DocumentListener() {
            private void applyFilter() {
                String input = grepField.getText();
                editorPanel.applyRegexFilter(input);
                onFilterApply(input);
            }
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(undoButton);
        left.add(redoButton);

        JPanel center = new JPanel(new BorderLayout());
        center.add(new JLabel("🔎 ", JLabel.RIGHT), BorderLayout.WEST);
        center.add(grepField, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(legendWrapper);
        right.add(Box.createHorizontalStrut(10));
        right.add(sentenceComboBox);

        panel.add(left, BorderLayout.WEST);
        panel.add(center, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private void highlight(String sentenceType) {
        SentenceTypeRegistry registry = tabbedPaneManager.getMainframeContext().getSentenceTypeRegistry();
        registry.findDefinition(sentenceType).ifPresent(def -> {
            int schemaLines = def.getRowCount() != null ? def.getRowCount() : 1;
            currentMaxRows = schemaLines;
            highlightFields(editorPanel.getTextArea(), def.getFields(), schemaLines);
            updateLegendPanel(def, sentenceType);
        });
    }

    private void updateLegendPanel(SentenceDefinition def, String sentenceType) {
        legendWrapper.removeAll();
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (Map.Entry<FieldCoordinate, SentenceField> entry : def.getFields().entrySet()) {
            FieldCoordinate coord = entry.getKey();
            SentenceField field = entry.getValue();
            if (coord.getRow() - 1 != currentLegendRowIndex) continue;
            JLabel label = new JLabel(field.getName());
            label.setOpaque(true);
            label.setBackground(getColorFor(field));
            label.setForeground(Color.BLACK);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.DARK_GRAY),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)
            ));
            legendPanel.add(label);
        }
        legendWrapper.add(legendPanel, BorderLayout.CENTER);
        legendWrapper.revalidate();
        legendWrapper.repaint();
    }

    private Color getColorFor(SentenceField field) {
        String override = SettingsHelper.load().fieldColorOverrides.get(field.getName().toUpperCase());
        try {
            if (override != null) return Color.decode(override);
            if (field.getColor() != null && !field.getColor().isEmpty()) return Color.decode(field.getColor());
        } catch (NumberFormatException ignored) {}
        int hash = Math.abs(field.getName().hashCode());
        float hue = (hash % 360) / 360f;
        return Color.getHSBColor(hue, 0.5f, 0.85f);
    }

    private void highlightFields(RSyntaxTextArea area, FieldMap fields, int schemaLines) {
        Highlighter highlighter = area.getHighlighter();
        highlighter.removeAllHighlights();

        String[] lines = area.getText().split("\n");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            int schemaRow = lineIndex % schemaLines;
            int lineOffset = getLineStartOffset(lines, lineIndex);
            String line = lines[lineIndex];
            for (Map.Entry<FieldCoordinate, SentenceField> entry : fields.entrySet()) {
                FieldCoordinate coord = entry.getKey();
                SentenceField field = entry.getValue();
                if (coord.getRow() - 1 != schemaRow) continue;
                int start = coord.getPosition() - 1;
                int len = field.getLength() != null ? field.getLength() : 0;
                int end = Math.min(line.length(), start + len);
                try {
                    highlighter.addHighlight(lineOffset + start, lineOffset + end,
                            new DefaultHighlighter.DefaultHighlightPainter(getColorFor(field)));
                } catch (Exception ignored) {}
            }
        }
    }

    private int getLineStartOffset(String[] lines, int index) {
        int offset = 0;
        for (int i = 0; i < index; i++) {
            offset += lines[i].length() + 1;
        }
        return offset;
    }

    private void showComparePanel() {
        compareVisible = true;
        comparePanel.setVisible(true);
        comparePanel.setAppendSelected(append);
        splitPane.setDividerLocation(0.7);
        toggleCompare.setVisible(false);
    }

    private void hideComparePanel() {
        compareVisible = false;
        comparePanel.setVisible(false);
        toggleCompare.setVisible(true);
        append = comparePanel.isAppendSelected();
    }

    @Override
    public String getTitle() {
        if (buffer == null) return "[Neu]";
        String name = buffer.getMeta().getName();
        return changed ? name + " *" : name;
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
            JOptionPane.showMessageDialog(mainPanel, "Kein FTP-Buffer vorhanden.", "Speichern nicht möglich", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String newText = editorPanel.getTextArea().getText();
            if (append) {
                String old = fileContentService.decodeWith(buffer);
                if (!old.endsWith("\n") && !newText.startsWith("\n")) old += "\n";
                newText = old + newText;
            }
            FtpFileBuffer altered = buffer.withContent(fileContentService.createCommitStream(newText, buffer.hasRecordStructure()));
            Optional<FtpFileBuffer> conflict = ftpManager.commit(buffer, altered);
            if (!conflict.isPresent()) {
                buffer = altered;
                changed = false;
                editorPanel.resetUndoHistory();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Dateikonflikt: Datei wurde auf dem Server verändert.", "Konflikt", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Speichern: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public boolean isAppendEnabled() {
        return append;
    }

    @Override
    public void setContent(String content, String sentenceType) {
        editorPanel.getTextArea().setText(content);
        highlight(sentenceType);
    }

    @Override
    public void markAsChanged() {
        if (!changed) {
            changed = true;
            tabbedPaneManager.updateTitleFor(this);
        }
    }

    @Override
    public String getPath() {
        return buffer != null ? buffer.getRemotePath() : null;
    }

    @Override
    public Type getType() {
        return Type.FILE;
    }

    @Override
    public String getContent() {
        return editorPanel.getTextArea().getText();
    }

    @Override
    public void setContent(String content) {
        editorPanel.getTextArea().setText(content);
    }

    @Override
    public void setAppend(boolean append) {
        this.append = append;
        comparePanel.setAppendSelected(append);
    }

    protected void onFilterApply(String input) {
        // optional override hook
    }

    @Override
    public void onClose() {
        // optional clean-up
    }
}