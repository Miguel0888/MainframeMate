package de.bund.zrb.ui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zrb.bund.newApi.workflow.WorkflowMcpData;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StepTableModel extends AbstractTableModel {

    private final List<WorkflowMcpData> steps = new ArrayList<>();

    @Override
    public int getRowCount() {
        return steps.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        WorkflowMcpData step = steps.get(rowIndex);
        return columnIndex == 0 ? step.getToolName() : new GsonBuilder().setPrettyPrinting().create().toJson(step.getParameters());
    }

    @Override
    public String getColumnName(int column) {
        return column == 0 ? "Tool" : "Parameter (JSON)";
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        WorkflowMcpData old = steps.get(rowIndex);
        if (columnIndex == 0) {
            steps.set(rowIndex, new WorkflowMcpData(aValue.toString(), old.getParameters()));
        } else {
            try {
                Map<String, Object> params = new Gson().fromJson(aValue.toString(), Map.class);
                steps.set(rowIndex, new WorkflowMcpData(old.getToolName(), params));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Ungültiges JSON: " + ex.getMessage());
            }
        }
    }

    public void addStep(WorkflowMcpData step) {
        steps.add(step);
        fireTableRowsInserted(steps.size() - 1, steps.size() - 1);
    }

    public void removeStep(int index) {
        steps.remove(index);
        fireTableRowsDeleted(index, index);
    }

    public List<WorkflowMcpData> getSteps() {
        return new ArrayList<>(steps);
    }

    public void setSteps(List<WorkflowMcpData> newSteps) {
        steps.clear();
        steps.addAll(newSteps);
        fireTableDataChanged();
    }
}
