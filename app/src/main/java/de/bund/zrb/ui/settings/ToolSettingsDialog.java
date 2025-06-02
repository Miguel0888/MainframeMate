package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.ToolSettingsHelper;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.zrb.bund.newApi.mcp.ToolSpec;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.List;

public class ToolSettingsDialog {

    private static List<ToolSpec> tools;
    private static ToolTableModel tableModel;

    public static void show(Component parent) {
        tools = ToolRegistryImpl.getInstance().getRegisteredToolSpecs();
        tableModel = new ToolTableModel();

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(600, 300));

        JButton addButton = new JButton("➕ Tool");
        addButton.addActionListener(e -> {
            ToolSpecEditor editor = new ToolSpecEditor();
            if (showEditorDialog(parent, editor, "Neues Tool")) {
                ToolSpec newTool = editor.getToolSpec();
                if (newTool != null) {
                    tools.add(newTool);
                    tableModel.fireTableDataChanged();
                    ToolSettingsHelper.saveTools(tools);
                }
            }
        });

        JButton editButton = new JButton("✏️ Bearbeiten");
        editButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                ToolSpec original = tools.get(selected);
                ToolSpecEditor editor = new ToolSpecEditor();
                editor.setToolSpec(original);
                if (showEditorDialog(parent, editor, "Tool bearbeiten")) {
                    ToolSpec updated = editor.getToolSpec();
                    if (updated != null) {
                        tools.set(selected, updated);
                        tableModel.fireTableDataChanged();
                        ToolSettingsHelper.saveTools(tools);
                    }
                }
            }
        });

        JButton removeButton = new JButton("🗑️ Entfernen");
        removeButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                tools.remove(selected);
                tableModel.fireTableDataChanged();
                ToolSettingsHelper.saveTools(tools);
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

        int result = JOptionPane.showConfirmDialog(parent, container, "Verwaltung der Werkzeugdefinitionen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            ToolSettingsHelper.saveTools(tools);
        }
    }

    private static boolean showEditorDialog(Component parent, ToolSpecEditor editor, String title) {
        int result = JOptionPane.showConfirmDialog(
                parent,
                editor,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        return result == JOptionPane.OK_OPTION;
    }

    private static class ToolTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "Beschreibung"};

        @Override
        public int getRowCount() {
            return tools.size();
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
            ToolSpec tool = tools.get(row);
            return column == 0 ? tool.getName() : tool.getDescription();
        }
    }
}
