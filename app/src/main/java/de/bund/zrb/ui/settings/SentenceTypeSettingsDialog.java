package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SentenceTypeSettingsHelper;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
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

        JButton addButton = new JButton("➕ Satzart");
        addButton.addActionListener(e -> {
            SentenceTypeEditor editor = new SentenceTypeEditor();
            if (showEditorDialog(parent, editor, "Neue Satzart")) {
                SentenceDefinition def = editor.getDefinition();
                String key = editor.getKey();
                if (def != null && key != null) {
                    sentenceTypeSpec.getDefinitions().put(key, def);
                    tableModel.fireTableDataChanged();
                    SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
                }
            }
        });

        JButton editButton = new JButton("✏️ Bearbeiten");
        editButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                String key = (String) tableModel.getValueAt(selected, 0);
                SentenceTypeEditor editor = new SentenceTypeEditor();
                editor.setData(key, sentenceTypeSpec.getDefinitions().get(key));
                if (showEditorDialog(parent, editor, "Satzart bearbeiten")) {
                    sentenceTypeSpec.getDefinitions().put(editor.getKey(), editor.getDefinition());
                    tableModel.fireTableDataChanged();
                    SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
                }
            }
        });

        JButton removeButton = new JButton("🗑️ Entfernen");
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
        private final String[] columns = {"Satzart", "Pfad", "Anzahl Felder"};

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
                        case 1: return entry.getValue().getMeta() != null ? entry.getValue().getMeta().getPath() : "";
                        case 2: return entry.getValue().getFields() != null ? entry.getValue().getFields().size() : 0;
                        default: return "";
                    }
                }
                index++;
            }
            return "";
        }

    }

}