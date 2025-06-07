package de.bund.zrb.ui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.helper.WorkflowStorage;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowStep;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.List;

public class WorkflowPanel extends JPanel {

    private final JComboBox<String> workflowSelector;
    private final JPanel stepListPanel;
    private final WorkflowRunner runner;
    private final ToolRegistry registry;

    public WorkflowPanel(WorkflowRunner runner, ToolRegistry registry) {
        this.runner = runner;
        this.registry = registry;

        setLayout(new BorderLayout(8, 8));

        JPanel headerPanel = new JPanel(new BorderLayout());
        JPanel selectorPanel = new JPanel(new BorderLayout(4, 0));

        JButton runWorkflow = new JButton("▶");
        runWorkflow.setToolTipText("Workflow ausführen");
        runWorkflow.setBackground(new Color(76, 175, 80)); // Saftiges Grün
        runWorkflow.setForeground(Color.WHITE);
        selectorPanel.add(runWorkflow, BorderLayout.EAST);

        workflowSelector = new JComboBox<>();
        reloadWorkflowNames();
        selectorPanel.add(workflowSelector, BorderLayout.CENTER);

        headerPanel.add(selectorPanel, BorderLayout.CENTER);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton renameWorkflow = new JButton("✏");
        renameWorkflow.setToolTipText("Umbenennen");
        JButton saveWorkflow = new JButton("💾");
        saveWorkflow.setToolTipText("Speichern unter...");
        JButton deleteWorkflow = new JButton("🗑");
        deleteWorkflow.setToolTipText("Löschen");

        headerButtons.add(renameWorkflow);
        headerButtons.add(saveWorkflow);
        headerButtons.add(deleteWorkflow);

        headerPanel.add(headerButtons, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        stepListPanel = new JPanel();
        stepListPanel.setLayout(new BoxLayout(stepListPanel, BoxLayout.Y_AXIS));
        stepListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane stepScrollPane = new JScrollPane(stepListPanel);
        stepScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(stepScrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JButton addStep = new JButton("<<< Schritt hinzufügen >>>");
        addStep.setToolTipText("Schritt hinzufügen");
        addStep.setPreferredSize(new Dimension(100, 28));
        buttonPanel.add(addStep, BorderLayout.CENTER);
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
            runner.execute(steps);
        });

        saveWorkflow.addActionListener(e -> {
            String currentName = (String) workflowSelector.getSelectedItem();
            JTextField input = new JTextField(currentName != null ? currentName : "");
            if (currentName != null) {
                if (currentName != null) {
                    input.addFocusListener(new FocusAdapter() {
                        @Override
                        public void focusGained(FocusEvent e) {
                            SwingUtilities.invokeLater(() -> {
                                input.selectAll();
                            });
                        }
                    });
                }
            }
            if (currentName != null) {
                input.selectAll(); // Markiert den bestehenden Text
            }

            int result = JOptionPane.showConfirmDialog(this, input, "Workflowname eingeben:", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String name = input.getText().trim();
                if (!name.isEmpty()) {
                    WorkflowStorage.saveWorkflow(name, getWorkflowSteps());
                    reloadWorkflowNames();
                    workflowSelector.setSelectedItem(name);
                }
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
