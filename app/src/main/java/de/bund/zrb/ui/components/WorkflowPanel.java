package de.bund.zrb.ui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.helper.WorkflowStorage;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowStep;

import javax.swing.*;
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
        stepTable.getColumnModel().getColumn(1)
                .setCellEditor(new ParameterCellEditor(stepTable, tableModel, registry, this));

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

    private JComboBox<String> createToolComboBox() {
        return new JComboBox<>(registry.getAllTools().stream()
                .map(t -> t.getSpec().getName())
                .sorted().toArray(String[]::new));
    }

}
