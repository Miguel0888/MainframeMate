package de.bund.zrb.ui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.helper.WorkflowStorage;
import de.bund.zrb.workflow.WorkflowRunnerImpl;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowStep;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public class WorkflowPanel extends JPanel {

    private final JComboBox<String> workflowSelector;
    private final JTable stepTable;
    private final StepTableModel tableModel;
    private final JTextArea previewArea;
    private final WorkflowRunner runner;
    private final ToolRegistry registry;

    public WorkflowPanel(WorkflowRunner runner, ToolRegistry registry) {
        this.runner = runner;
        this.registry = registry;

        setLayout(new BorderLayout(8, 8));

        workflowSelector = new JComboBox<>();
        reloadWorkflowNames();
        add(workflowSelector, BorderLayout.NORTH);

        tableModel = new StepTableModel();
        stepTable = new JTable(tableModel);
        stepTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(createToolComboBox()));

        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JTabbedPane contentTabs = new JTabbedPane();
        contentTabs.addTab("📦 Schritte", new JScrollPane(stepTable));
        contentTabs.addTab("\uD83D\uDCC4 Vorschau", new JScrollPane(previewArea));
        add(contentTabs, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addStep = new JButton("+");
        addStep.setToolTipText("Schritt hinzufügen");
        JButton removeStep = new JButton("-");
        removeStep.setToolTipText("Schritt entfernen");
        JButton runWorkflow = new JButton("▶");
        runWorkflow.setToolTipText("Ausführen");
        JButton saveWorkflow = new JButton("💾");
        saveWorkflow.setToolTipText("Speichern unter...");
        JButton renameWorkflow = new JButton("✏");
        renameWorkflow.setToolTipText("Umbenennen");
        JButton deleteWorkflow = new JButton("🗑");
        deleteWorkflow.setToolTipText("Löschen");

        buttonPanel.add(addStep);
        buttonPanel.add(removeStep);
        buttonPanel.add(runWorkflow);
        buttonPanel.add(saveWorkflow);
        buttonPanel.add(renameWorkflow);
        buttonPanel.add(deleteWorkflow);
        add(buttonPanel, BorderLayout.SOUTH);

        workflowSelector.addActionListener(e -> {
            String selected = (String) workflowSelector.getSelectedItem();
            if (selected != null) {
                tableModel.setSteps(WorkflowStorage.loadWorkflow(selected));
            }
        });

        addStep.addActionListener(e -> {
            tableModel.addStep(new WorkflowStep("", new LinkedHashMap<>()));
        });

        removeStep.addActionListener(e -> {
            int row = stepTable.getSelectedRow();
            if (row >= 0) {
                tableModel.removeStep(row);
            }
        });

        runWorkflow.addActionListener(e -> {
            List<WorkflowStep> steps = tableModel.getSteps();
            previewArea.setText(getPreviewJson(steps));
            runner.execute(steps);
        });

        saveWorkflow.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Workflowname eingeben:", "Speichern unter", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                WorkflowStorage.saveWorkflow(name.trim(), tableModel.getSteps());
                reloadWorkflowNames();
                workflowSelector.setSelectedItem(name.trim());
            }
        });

        renameWorkflow.addActionListener(e -> {
            String current = (String) workflowSelector.getSelectedItem();
            if (current != null) {
                String newName = JOptionPane.showInputDialog(this, "Neuer Name für Workflow:", current);
                if (newName != null && !newName.trim().isEmpty()) {
                    WorkflowStorage.renameWorkflow(current, newName.trim());
                    reloadWorkflowNames();
                    workflowSelector.setSelectedItem(newName.trim());
                }
            }
        });

        deleteWorkflow.addActionListener(e -> {
            String current = (String) workflowSelector.getSelectedItem();
            if (current != null && JOptionPane.showConfirmDialog(this,
                    "Workflow \"" + current + "\" wirklich löschen?",
                    "Löschen bestätigen", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                WorkflowStorage.deleteWorkflow(current);
                reloadWorkflowNames();
                tableModel.setSteps(Collections.emptyList());
            }
        });
    }

    private void reloadWorkflowNames() {
        String previous = (String) workflowSelector.getSelectedItem();
        workflowSelector.removeAllItems();
        for (String name : WorkflowStorage.listWorkflowNames()) {
            workflowSelector.addItem(name);
        }
        if (previous != null && WorkflowStorage.workflowExists(previous)) {
            workflowSelector.setSelectedItem(previous);
        }
    }

    private String getPreviewJson(List<WorkflowStep> steps) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(steps);
    }

    private static class StepTableModel extends AbstractTableModel {
        private final List<WorkflowStep> steps = new ArrayList<>();

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
            WorkflowStep step = steps.get(rowIndex);
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
            WorkflowStep old = steps.get(rowIndex);
            if (columnIndex == 0) {
                steps.set(rowIndex, new WorkflowStep(aValue.toString(), old.getParameters()));
            } else {
                try {
                    Map<String, Object> params = new Gson().fromJson(aValue.toString(), Map.class);
                    steps.set(rowIndex, new WorkflowStep(old.getToolName(), params));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Ungültiges JSON: " + ex.getMessage());
                }
            }
        }

        public void addStep(WorkflowStep step) {
            steps.add(step);
            fireTableRowsInserted(steps.size() - 1, steps.size() - 1);
        }

        public void removeStep(int index) {
            steps.remove(index);
            fireTableRowsDeleted(index, index);
        }

        public List<WorkflowStep> getSteps() {
            return new ArrayList<>(steps);
        }

        public void setSteps(List<WorkflowStep> newSteps) {
            steps.clear();
            steps.addAll(newSteps);
            fireTableDataChanged();
        }
    }

    private JComboBox<String> createToolComboBox() {
        return new JComboBox<>(registry.getAllTools().stream()
                .map(t -> t.getSpec().getName())
                .sorted().toArray(String[]::new));
    }

}
