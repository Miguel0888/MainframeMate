package de.bund.zrb.ui.filetab;

import de.bund.zrb.ui.filetab.event.*;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceMeta;
import de.zrb.bund.newApi.ui.FindBarPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Bottom status bar for FileTabImpl.
 * Contains: FindBarPanel (orange, for regex line filtering) + Legende + Satzart dropdown.
 * Compare / Undo / Redo buttons have been moved to the toolbar (SplitPreviewTab).
 */
public class StatusBarPanel extends JPanel {

    private final FindBarPanel findBar;
    private final JComboBox<String> sentenceComboBox = new JComboBox<>();
    private final JPanel legendWrapper = new JPanel(new BorderLayout());

    private boolean suppressEvents = false;

    public StatusBarPanel() {
        super(new BorderLayout());

        // FindBarPanel (orange) for grep/filter
        findBar = new FindBarPanel("Regulärer Ausdruck\u2026",
                "<html>Regulärer Ausdruck für die Zeilenfilterung<br>"
                        + "<br><b>Beispiele:</b><br>"
                        + "&bull; <code>abc</code> – enthält 'abc'<br>"
                        + "&bull; <code>^abc</code> – beginnt mit 'abc'<br>"
                        + "&bull; <code>abc$</code> – endet mit 'abc'<br>"
                        + "&bull; <code>.*test.*</code> – enthält 'test'<br>"
                        + "&bull; <code>\\d+</code> – Ziffern<br>"
                        + "<br>Nicht groß-/kleinschreibungssensitiv.<br>"
                        + "Alle Zeilen, die nicht passen, werden gefaltet.</html>");

        // Left / Center: FindBarPanel
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(findBar, BorderLayout.CENTER);

        // Right: Legende + Satzart
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
        rightPanel.add(legendWrapper);
        rightPanel.add(Box.createHorizontalStrut(10));
        rightPanel.add(sentenceComboBox);

        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    public JTextField getGrepField() {
        return findBar.getTextField();
    }

    /** Return the FindBarPanel (orange) used for grep/filter. */
    public FindBarPanel getFindBar() {
        return findBar;
    }

    public JComboBox<String> getSentenceComboBox() {
        return sentenceComboBox;
    }

    public JPanel getLegendWrapper() {
        return legendWrapper;
    }

    public void setSentenceTypes(List<String> types) {
        suppressEvents = true;
        try {
            sentenceComboBox.removeAllItems();
            sentenceComboBox.addItem(""); // Leerer Eintrag für "keine Satzart"
            for (String t : types) {
                sentenceComboBox.addItem(t);
            }
        } finally {
            suppressEvents = false;
        }
    }

    /**
     * Fills the combo box with sentence types and file types, visually grouped.
     * Separator items (starting with "──") are rendered differently and not selectable.
     */
    public void setSentenceTypesGrouped(Map<String, SentenceDefinition> definitions) {
        suppressEvents = true;
        try {
            sentenceComboBox.removeAllItems();
            sentenceComboBox.addItem(""); // Leerer Eintrag

            java.util.List<String> sentenceKeys = new java.util.ArrayList<>();
            java.util.List<String> documentKeys = new java.util.ArrayList<>();
            java.util.List<String> languageKeys = new java.util.ArrayList<>();

            for (Map.Entry<String, SentenceDefinition> entry : definitions.entrySet()) {
                if (entry.getValue().isFileType()) {
                    SentenceMeta meta = entry.getValue().getMeta();
                    if (meta != null && meta.hasSyntaxStyle()) {
                        languageKeys.add(entry.getKey());
                    } else {
                        documentKeys.add(entry.getKey());
                    }
                } else {
                    sentenceKeys.add(entry.getKey());
                }
            }

            if (!sentenceKeys.isEmpty()) {
                sentenceComboBox.addItem("── Satzarten ──");
                for (String key : sentenceKeys) {
                    sentenceComboBox.addItem(key);
                }
            }

            if (!documentKeys.isEmpty()) {
                sentenceComboBox.addItem("── Dateitypen ──");
                for (String key : documentKeys) {
                    sentenceComboBox.addItem(key);
                }
            }

            if (!languageKeys.isEmpty()) {
                sentenceComboBox.addItem("── Sprachen ──");
                for (String key : languageKeys) {
                    sentenceComboBox.addItem(key);
                }
            }

            // Custom renderer for separator items
            sentenceComboBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value != null && value.toString().startsWith("──")) {
                        setFont(getFont().deriveFont(Font.BOLD, 11f));
                        setForeground(Color.GRAY);
                        setEnabled(false);
                    }
                    return this;
                }
            });
        } finally {
            suppressEvents = false;
        }
    }

    public void setSelectedSentenceType(String sentenceType) {
        suppressEvents = true;
        try {
            ensureEmptySentenceOption();

            if (sentenceType == null || sentenceType.trim().isEmpty()) {
                sentenceComboBox.setSelectedIndex(0); // Leerer Eintrag
            } else {
                sentenceComboBox.setSelectedItem(sentenceType);
            }
        } finally {
            suppressEvents = false;
        }
    }

    private void ensureEmptySentenceOption() {
        if (sentenceComboBox.getItemCount() == 0) {
            sentenceComboBox.addItem("");
        }
    }

    public void onSentenceTypeChanged(Consumer<String> callback) {
        sentenceComboBox.addActionListener(e -> {
            if (suppressEvents) return;
            Object selected = sentenceComboBox.getSelectedItem();
            if (selected != null) {
                String val = selected.toString();
                // Skip separator items
                if (val.startsWith("──")) {
                    sentenceComboBox.setSelectedItem("");
                    return;
                }
                callback.accept(val);
            }
        });
    }

    public void onRegexChanged(Runnable callback) {
        findBar.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
        });
    }

    /**
     * Bind events. Compare/Undo/Redo are now handled by the toolbar buttons
     * in SplitPreviewTab, so only sentence type and regex events are wired here.
     */
    public void bindEvents(FileTabEventDispatcher dispatcher) {
        onSentenceTypeChanged(type -> dispatcher.publish(new SentenceTypeChangedEvent(type)));
        onRegexChanged(() -> dispatcher.publish(new RegexFilterChangedEvent(findBar.getText())));
    }

}
