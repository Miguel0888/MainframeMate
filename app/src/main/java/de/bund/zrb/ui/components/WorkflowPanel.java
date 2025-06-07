package de.bund.zrb.ui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.helper.WorkflowStorage;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowStep;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class WorkflowPanel extends JPanel {

    private final JComboBox<String> workflowSelector;
    private final JPanel stepListPanel;
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

        stepListPanel = new JPanel();
        stepListPanel.setLayout(new BoxLayout(stepListPanel, BoxLayout.Y_AXIS));
        JScrollPane stepScrollPane = new JScrollPane(stepListPanel);

        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JTabbedPane contentTabs = new JTabbedPane();
        contentTabs.addTab("📦 Schritte", stepScrollPane);
        contentTabs.addTab("📄 Vorschau", new JScrollPane(previewArea));
        add(contentTabs, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addStep = new JButton("+");
        addStep.setToolTipText("Schritt hinzufügen");
        JButton runWorkflow = new JButton("▶");
        runWorkflow.setToolTipText("Ausführen");
        JButton saveWorkflow = new JButton("💾");
        saveWorkflow.setToolTipText("Speichern unter...");
        JButton renameWorkflow = new JButton("✏");
        renameWorkflow.setToolTipText("Umbenennen");
        JButton deleteWorkflow = new JButton("🗑");
        deleteWorkflow.setToolTipText("Löschen");

        buttonPanel.add(addStep);
        buttonPanel.add(runWorkflow);
        buttonPanel.add(saveWorkflow);
        buttonPanel.add(renameWorkflow);
        buttonPanel.add(deleteWorkflow);
        add(buttonPanel, BorderLayout.SOUTH);

        workflowSelector.addActionListener(e -> {
            String selected = (String) workflowSelector.getSelectedItem();
            if (selected != null) {
                setWorkflowSteps(WorkflowStorage.loadWorkflow(selected));
            }
        });

        addStep.addActionListener(e -> {
            addStepPanel(new WorkflowStep("", new java.util.LinkedHashMap<>()));
        });

        runWorkflow.addActionListener(e -> {
            List<WorkflowStep> steps = getWorkflowSteps();
            previewArea.setText(getPreviewJson(steps));
            runner.execute(steps);
        });

        saveWorkflow.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Workflowname eingeben:", "Speichern unter", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                WorkflowStorage.saveWorkflow(name.trim(), getWorkflowSteps());
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
                setWorkflowSteps(java.util.Collections.emptyList());
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

    private void addStepPanel(WorkflowStep step) {
        StepPanel panel = new StepPanel(registry, step);
        panel.setDeleteListener(e -> {
            stepListPanel.remove(panel);
            stepListPanel.revalidate();
            stepListPanel.repaint();
        });
        stepListPanel.add(panel);
        stepListPanel.revalidate();
        stepListPanel.repaint();
    }

    private void setWorkflowSteps(List<WorkflowStep> steps) {
        stepListPanel.removeAll();
        for (WorkflowStep step : steps) {
            addStepPanel(step);
        }
    }

    private List<WorkflowStep> getWorkflowSteps() {
        List<WorkflowStep> steps = new java.util.ArrayList<>();
        for (Component comp : stepListPanel.getComponents()) {
            if (comp instanceof StepPanel) {
                steps.add(((StepPanel) comp).toWorkflowStep());
            }
        }
        return steps;
    }
}
