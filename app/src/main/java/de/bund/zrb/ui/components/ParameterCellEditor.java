package de.bund.zrb.ui.components;

import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowMcpData;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.Map;

class ParameterCellEditor extends AbstractCellEditor implements TableCellEditor {
    private final JTable table;
    private final StepTableModel tableModel;
    private final ToolRegistry registry;
    private final Component parent;

    private final JPanel panel = new JPanel(new BorderLayout());
    private final JTextField textField = new JTextField();
    private final JButton button = new JButton("...");

    private int editingRow;

    public ParameterCellEditor(JTable table, StepTableModel tableModel, ToolRegistry registry, Component parent) {
        this.table = table;
        this.tableModel = tableModel;
        this.registry = registry;
        this.parent = parent;

        panel.add(textField, BorderLayout.CENTER);
        panel.add(button, BorderLayout.EAST);

        button.addActionListener(e -> {
            stopCellEditing(); // commit text
            WorkflowMcpData step = tableModel.getSteps().get(editingRow);
            String toolName = step.getToolName();
            Map<String, Object> edited = ParameterEditorDialog.showDialog(
                    parent,
                    registry.getToolByName(toolName),
                    step.getParameters()
            );
            if (edited != null) {
                step.setParameters(edited);
                tableModel.fireTableRowsUpdated(editingRow, editingRow);
            }
        });
    }

    @Override
    public Object getCellEditorValue() {
        return textField.getText();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        editingRow = row;
        textField.setText(value != null ? value.toString() : "");
        return panel;
    }
}
