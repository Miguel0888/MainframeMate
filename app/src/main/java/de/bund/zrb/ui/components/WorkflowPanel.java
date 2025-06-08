package de.bund.zrb.ui.components;

import de.bund.zrb.helper.WorkflowStorage;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowMcpData;
import de.zrb.bund.newApi.workflow.WorkflowStepContainer;
import de.zrb.bund.newApi.workflow.WorkflowTemplate;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;

public class WorkflowPanel extends JPanel {

    private WorkflowTemplate currentTemplate;

    private final JComboBox<String> workflowSelector;
    private final JPanel stepListPanel;
    private final WorkflowRunner runner;
    private final ToolRegistry registry;

    private final DefaultTableModel variableModel;

    public WorkflowPanel(WorkflowRunner runner, ToolRegistry registry) {
        this.runner = runner;
        this.registry = registry;

        setLayout(new BorderLayout(8, 8));

        JPanel headerPanel = new JPanel(new BorderLayout());
        JPanel selectorPanel = new JPanel(new BorderLayout(4, 0));

        JButton runWorkflow = new JButton("▶");
        runWorkflow.setToolTipText("Workflow ausführen");
        runWorkflow.setBackground(new Color(76, 175, 80));
        runWorkflow.setForeground(Color.WHITE);
        selectorPanel.add(runWorkflow, BorderLayout.EAST);

        workflowSelector = new JComboBox<>();
        reloadWorkflowNames();
        selectorPanel.add(workflowSelector, BorderLayout.CENTER);

        headerPanel.add(selectorPanel, BorderLayout.CENTER);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton renameWorkflow = new JButton("✏");
        JButton saveWorkflow = new JButton("💾");
        JButton deleteWorkflow = new JButton("🗑");

        headerButtons.add(renameWorkflow);
        headerButtons.add(saveWorkflow);
        headerButtons.add(deleteWorkflow);

        headerPanel.add(headerButtons, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        stepListPanel = new JPanel();
        stepListPanel.setLayout(new BoxLayout(stepListPanel, BoxLayout.Y_AXIS));

        JScrollPane stepScrollPane = new JScrollPane(stepListPanel);
        stepScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(stepScrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JButton addStep = new JButton("📦");
        buttonPanel.add(addStep, BorderLayout.WEST);
        JButton envVarsButton = new JButton("📝 Variablen");
        buttonPanel.add(envVarsButton, BorderLayout.EAST);
        add(buttonPanel, BorderLayout.SOUTH);

        variableModel = new DefaultTableModel(new String[]{"Name", "Wert"}, 0);

        workflowSelector.addActionListener(e -> {
            onSelectWorkflow();
        });

        addStep.addActionListener(e -> addStepPanel(new WorkflowMcpData("", new LinkedHashMap<>())));

        runWorkflow.addActionListener(e -> {
            onRun();
        });

        saveWorkflow.addActionListener(e -> {
            onSave();
        });

        renameWorkflow.addActionListener(e -> {
            onRename();
        });

        deleteWorkflow.addActionListener(e -> {
            onDelete();
        });

        envVarsButton.addActionListener(e -> showVariableDialog());
        onSelectWorkflow(); //refresh stepPanel
    }

    private void onDelete() {
        String current = (String) workflowSelector.getSelectedItem();
        if (current != null && JOptionPane.showConfirmDialog(this,
                "Workflow \"" + current + "\" wirklich löschen?",
                "Löschen bestätigen", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            WorkflowStorage.deleteWorkflow(current);
            reloadWorkflowNames();
            setWorkflowTemplate(new WorkflowTemplate());
        }
    }

    private void onRename() {
        String current = (String) workflowSelector.getSelectedItem();
        if (current != null) {
            String newName = JOptionPane.showInputDialog(this, "Neuer Name für Workflow:", current);
            if (newName != null && !newName.trim().isEmpty()) {
                WorkflowStorage.renameWorkflow(current, newName.trim());
                reloadWorkflowNames();
                workflowSelector.setSelectedItem(newName.trim());
            }
        }
    }

    private void onSave() {
        String currentName = (String) workflowSelector.getSelectedItem();
        JTextField input = new JTextField(currentName != null ? currentName : "");
        if (currentName != null) {
            input.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    SwingUtilities.invokeLater(input::selectAll);
                }
            });
        }
        int result = JOptionPane.showConfirmDialog(this, input, "Workflowname eingeben:", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String name = input.getText().trim();
            if (!name.isEmpty()) {
                updateCurrentTemplateFromUI(); // ← WICHTIG!
                WorkflowStorage.saveWorkflow(name, currentTemplate);
                reloadWorkflowNames();
                workflowSelector.setSelectedItem(name);
            }
        }
    }

    private void onRun() {
        updateCurrentTemplateFromUI(); // sicherstellen, dass UI-Daten im Template sind

        Map<String, String> overrides = new LinkedHashMap<>();
        for (int i = 0; i < variableModel.getRowCount(); i++) {
            String key = variableModel.getValueAt(i, 0).toString().trim();
            String value = variableModel.getValueAt(i, 1).toString().trim();
            if (!key.isEmpty()) {
                overrides.put(key, value);
            }
        }

        UUID runId = runner.execute(currentTemplate, overrides);

        JOptionPane.showMessageDialog(this,
                "Workflow gestartet mit ID:\n" + runId,
                "Ausführung gestartet",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void onSelectWorkflow() {
        String selected = (String) workflowSelector.getSelectedItem();
        if (selected != null) {
            setWorkflowTemplate(WorkflowStorage.loadWorkflow(selected));
        }
    }

    private void reloadWorkflowNames() {
        String previous = (String) workflowSelector.getSelectedItem();
        workflowSelector.removeAllItems();
        for (String name : WorkflowStorage.listWorkflowNames()) {
            workflowSelector.addItem(name);
        }
        if (previous != null && WorkflowStorage.workflowExists(previous)) {
            workflowSelector.setSelectedItem(previous);
        } else if (workflowSelector.getItemCount() > 0) {
            workflowSelector.setSelectedIndex(0); // ← neue Zeile!
        }
    }

    private void addStepPanel(WorkflowMcpData step) {
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

    public void setWorkflowTemplate(WorkflowTemplate template) {
        this.currentTemplate = template;
        stepListPanel.removeAll();
        variableModel.setRowCount(0);
        if (template.getMeta() != null && template.getMeta().getVariables() != null) {
            for (Map.Entry<String, String> entry : template.getMeta().getVariables().entrySet()) {
                variableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
            }
        }
        for (WorkflowStepContainer container : template.getData()) {
            if (container.getMcp() != null) {
                addStepPanel(container.getMcp());
            }
        }
    }

    private void showVariableDialog() {
        if (currentTemplate == null || currentTemplate.getMeta() == null) {
            JOptionPane.showMessageDialog(this, "Kein Workflow geladen.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Variablen bearbeiten", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        variableModel.setRowCount(0); // vorher aufräumen
        Map<String, String> variables = currentTemplate.getMeta().getVariables();
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                variableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
            }
        }

        JTable table = new JTable(variableModel) {
            @Override
            public TableCellEditor getCellEditor(int row, int column) {
                if (column == 0) {
                    List<String> suggestions = getUsedVariablePlaceholders();
                    JComboBox<String> comboBox = new JComboBox<>(suggestions.toArray(new String[0]));
                    comboBox.setEditable(true);
                    return new DefaultCellEditor(comboBox);
                } else {
                    return super.getCellEditor(row, column);
                }
            }
        };

        JScrollPane scrollPane = new JScrollPane(table);

        JButton addRowButton = new JButton("+");
        addRowButton.addActionListener(e -> variableModel.addRow(new Object[]{"", ""}));

        JButton deleteRowButton = new JButton("−");
        deleteRowButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected != -1) variableModel.removeRow(selected);
        });

        JButton saveButton = new JButton("Anwenden");
        saveButton.addActionListener(e -> {
            onVarApply(table, variableModel, dialog);
        });

        JButton cancelButton = new JButton("Verwerfen");
        cancelButton.addActionListener(e -> dialog.dispose());

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.add(addRowButton);
        leftPanel.add(deleteRowButton);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.add(saveButton);
        rightPanel.add(cancelButton);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.add(leftPanel, BorderLayout.WEST);
        bottomBar.add(rightPanel, BorderLayout.EAST);

        JPanel contentPanel = new JPanel(new BorderLayout(4, 4));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(bottomBar, BorderLayout.SOUTH);

        dialog.setContentPane(contentPanel);
        dialog.setVisible(true);
    }
    private void onVarApply(JTable table, DefaultTableModel variableModel, JDialog dialog) {
        if (table.isEditing()) {
            int editingRow = table.getEditingRow();
            int editingCol = table.getEditingColumn();
            if (editingRow >= 0 && editingRow < variableModel.getRowCount()
                    && editingCol >= 0 && editingCol < variableModel.getColumnCount()) {
                table.getCellEditor().stopCellEditing();
            } else {
                table.editingCanceled(null);
            }
        }

        Map<String, String> newVars = new LinkedHashMap<>();
        Set<String> seenKeys = new HashSet<>();

        for (int i = 0; i < variableModel.getRowCount(); i++) {
            String key = variableModel.getValueAt(i, 0).toString().trim();
            String value = variableModel.getValueAt(i, 1).toString().trim();

            if (!key.isEmpty()) {
                if (seenKeys.contains(key)) {
                    JOptionPane.showMessageDialog(dialog,
                            "Der Variablenname \"" + key + "\" ist mehrfach vorhanden.",
                            "Fehlerhafte Eingabe",
                            JOptionPane.ERROR_MESSAGE);
                    return; // Abbruch bei Duplikat
                }
                seenKeys.add(key);
                newVars.put(key, value);
            }
        }

        currentTemplate.getMeta().setVariables(newVars);
        dialog.dispose();
    }



    private List<String> getUsedVariablePlaceholders() {
        Set<String> vars = new TreeSet<>();
        for (Component comp : stepListPanel.getComponents()) {
            if (comp instanceof StepPanel) {
                WorkflowMcpData step = ((StepPanel) comp).toWorkflowStep();
                for (Object val : step.getParameters().values()) {
                    if (val instanceof String) {
                        String s = (String) val;
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{([^}]+)}}").matcher(s);
                        while (matcher.find()) {
                            vars.add(matcher.group(1));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(vars);
    }

    private void updateCurrentTemplateFromUI() {
        List<WorkflowStepContainer> containers = new ArrayList<>();

        for (Component comp : stepListPanel.getComponents()) {
            if (comp instanceof StepPanel) {
                WorkflowMcpData step = ((StepPanel) comp).toWorkflowStep();
                WorkflowStepContainer container = new WorkflowStepContainer();
                container.setMcp(step);
                containers.add(container);
            }
        }

        currentTemplate.setData(containers);

        // Falls du sicherstellen willst, dass Variablen auch aktuell sind:
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i < variableModel.getRowCount(); i++) {
            String key = variableModel.getValueAt(i, 0).toString().trim();
            String value = variableModel.getValueAt(i, 1).toString().trim();
            if (!key.isEmpty()) {
                vars.put(key, value);
            }
        }
        if (currentTemplate.getMeta() != null) {
            currentTemplate.getMeta().setVariables(vars);
        }
    }

}
