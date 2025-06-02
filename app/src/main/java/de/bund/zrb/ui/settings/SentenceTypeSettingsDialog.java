package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SentenceTypeSettingsHelper;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;

public class SentenceTypeSettingsDialog {

    private static SentenceTypeSpec sentenceTypeSpec;
    private static SentenceTableModel tableModel;

    public static void show(Component parent) {
        sentenceTypeSpec = SentenceTypeSettingsHelper.loadSentenceTypes();
        tableModel = new SentenceTableModel();

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(600, 300));
        table.getTableHeader().setToolTipText("Pfade: durch Semikolon getrennt. Pattern: regulärer Ausdruck.");

        JButton addButton = new JButton("➕ Satzart");
        addButton.addActionListener(e -> {
            SentenceTypeEditor editor = new SentenceTypeEditor();
            editor.originalKey = ""; // wichtig beim Neuanlegen

            if (showEditorDialog(parent, editor, "Neue Satzart")) {
                SentenceDefinition def = editor.getDefinition();
                String newKey = editor.getKey();

                if (def != null && newKey != null && !newKey.isEmpty()) {
                    boolean exists = sentenceTypeSpec.getDefinitions().keySet().stream()
                            .anyMatch(existingKey -> existingKey.equalsIgnoreCase(newKey));

                    if (exists) {
                        JOptionPane.showMessageDialog(parent,
                                "Eine Satzart mit dem Namen \"" + newKey + "\" existiert bereits (Groß-/Kleinschreibung ignoriert).",
                                "Fehler beim Speichern", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    sentenceTypeSpec.getDefinitions().put(newKey, def);
                    tableModel.fireTableDataChanged();
                    SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
                }
            }
        });

        JButton editButton = new JButton("✏ Bearbeiten");
        editButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                String key = (String) tableModel.getValueAt(selected, 0);
                SentenceTypeEditor editor = new SentenceTypeEditor();
                editor.setData(key, sentenceTypeSpec.getDefinitions().get(key));
                editor.originalKey = key; // Originalschlüssel merken

                if (showEditorDialog(parent, editor, "Satzart bearbeiten")) {
                    String newKey = editor.getKey();
                    SentenceDefinition def = editor.getDefinition();

                    if (def != null && newKey != null && !newKey.isEmpty()) {
                        // Falls umbenannt, alten Key entfernen
                        if (!newKey.equals(editor.originalKey)) {
                            sentenceTypeSpec.getDefinitions().remove(editor.originalKey);
                        }

                        sentenceTypeSpec.getDefinitions().put(newKey, def);
                        tableModel.fireTableDataChanged();
                        SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
                    }
                }
            }
        });

        JButton removeButton = new JButton("❌ Entfernen");
        removeButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                String key = (String) tableModel.getValueAt(selected, 0);
                sentenceTypeSpec.getDefinitions().remove(key);
                tableModel.fireTableDataChanged();
                SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        JPanel container = new JPanel(new BorderLayout());
        container.add(new JScrollPane(table), BorderLayout.CENTER);
        container.add(buttonPanel, BorderLayout.SOUTH);
        container.setPreferredSize(new Dimension(700, 400));

        int result = JOptionPane.showConfirmDialog(parent, container, "Verwaltung der Satzarten",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
        }
    }

    private static boolean showEditorDialog(Component parent, SentenceTypeEditor editor, String title) {
        int result = JOptionPane.showConfirmDialog(
                parent,
                editor,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        return result == JOptionPane.OK_OPTION;
    }

    private static class SentenceTableModel extends AbstractTableModel {
        private final String[] columns = {"Satzart", "Pfade", "Pattern", "Feldzahl"};

        @Override
        public int getRowCount() {
            if (sentenceTypeSpec == null || sentenceTypeSpec.getDefinitions() == null) {
                return 0;
            }
            return sentenceTypeSpec.getDefinitions().size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (sentenceTypeSpec == null || sentenceTypeSpec.getDefinitions() == null) {
                return "";
            }

            int index = 0;
            for (Map.Entry<String, SentenceDefinition> entry : sentenceTypeSpec.getDefinitions().entrySet()) {
                if (index == row) {
                    switch (column) {
                        case 0: return entry.getKey();
                        case 1:
                            List<String> paths = entry.getValue().getMeta() != null
                                    ? entry.getValue().getMeta().getPaths()
                                    : null;
                            return paths != null ? String.join(";", paths) : "";
                        case 2:
                            return entry.getValue().getMeta() != null
                                    ? entry.getValue().getMeta().getPathPattern()
                                    : "";
                        case 3:
                            return entry.getValue().getFields() != null
                                    ? entry.getValue().getFields().size()
                                    : 0;
                        default: return "";
                    }
                }
                index++;
            }
            return "";
        }

    }

}