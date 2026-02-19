package de.bund.zrb.ui.components;

import de.bund.zrb.helper.WorkflowStorage;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowMcpData;
import de.zrb.bund.newApi.workflow.WorkflowStepContainer;
import de.zrb.bund.newApi.workflow.WorkflowTemplate;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class WorkflowPanel extends JPanel {

    private WorkflowTemplate currentTemplate = new WorkflowTemplate(); //wenn keines vorhanden ist nötig!

    private final JComboBox<String> workflowSelector;
    private final JPanel stepListPanel;
    private final WorkflowRunner runner;
    private final MainframeContext context;

    private final DefaultTableModel variableModel;

    public WorkflowPanel(WorkflowRunner runner, MainframeContext context) {
        this.runner = runner;
        this.context = context;

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

        // Hilfe-Button
        HelpButton helpButton = new HelpButton("Hilfe zu Workflows",
                e -> HelpContentProvider.showHelpPopup(
                        (Component) e.getSource(),
                        HelpContentProvider.HelpTopic.WORKFLOW));
        helpButton.setVisible(de.bund.zrb.helper.SettingsHelper.load().showHelpIcons);
        headerButtons.add(helpButton);

        JButton renameWorkflow = new JButton("✏");
        renameWorkflow.setToolTipText("Workflow umbenennen");
        JButton saveWorkflow = new JButton("💾");
        saveWorkflow.setToolTipText("Workflow unter einem Namen speichern");
        JButton deleteWorkflow = new JButton("🗑");
        deleteWorkflow.setToolTipText("Workflow löschen");

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
        addStep.setToolTipText("Neuen Tooleinsatz einplanen");
        buttonPanel.add(addStep, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new BorderLayout());

        JButton envVarsButton = new JButton("📝 Variablen");
        envVarsButton.setToolTipText("Workflow Variablen setzen");
        rightPanel.add(envVarsButton, BorderLayout.CENTER);
        buttonPanel.add(rightPanel, BorderLayout.EAST);
        add(buttonPanel, BorderLayout.SOUTH);

        JButton envViewerButton = new JButton("✨");
        envViewerButton.setToolTipText("Variablen des letzten Laufs ansehen");
        envViewerButton.setMargin(new Insets(2, 4, 2, 4));
        rightPanel.add(envViewerButton, BorderLayout.EAST);

        variableModel = new DefaultTableModel(new String[]{"Name", "Wert"}, 0);

        String defaultWorkflow = de.bund.zrb.helper.SettingsHelper.load().defaultWorkflow;
        if (defaultWorkflow != null && !defaultWorkflow.trim().isEmpty()) {
            for (int i = 0; i < workflowSelector.getItemCount(); i++) {
                if (defaultWorkflow.equals(workflowSelector.getItemAt(i))) {
                    workflowSelector.setSelectedItem(defaultWorkflow);
                    break;
                }
            }
        }
        workflowSelector.addActionListener(e -> {
            onSelectWorkflow();
        });

        addStep.addActionListener(e -> addStepPanel(new WorkflowMcpData("", new LinkedHashMap<>(), null)));

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

        envViewerButton.addActionListener(e -> showEnvironmentViewer());
        envVarsButton.addActionListener(e -> showVariableDialog());
        onSelectWorkflow(); //refresh stepPanel
    }

    private void showEnvironmentViewer() {
        Map<String, String> vars = context.getVariableRegistry().getAllVariables();

        List<Map.Entry<String, String>> entries = new ArrayList<>(vars.entrySet());

        JTable table = new JTable(new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return entries.size();
            }

            @Override
            public int getColumnCount() {
                return 2;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                return columnIndex == 0 ? entries.get(rowIndex).getKey() : entries.get(rowIndex).getValue();
            }

            @Override
            public String getColumnName(int column) {
                return column == 0 ? "Name" : "Wert";
            }
        });

        // Custom renderer mit "..." Button (optisch)
        table.getColumnModel().getColumn(1).setCellRenderer((tbl, value, isSelected, hasFocus, row, col) -> {
            JPanel panel = new JPanel(new BorderLayout());
            JLabel label = new JLabel(value != null ? value.toString() : "");
            label.setToolTipText(label.getText());

            JButton button = new JButton("...");
            button.setMargin(new Insets(0, 4, 0, 4));
            button.setFocusable(false); // nur zur Darstellung

            panel.add(label, BorderLayout.CENTER);
            panel.add(button, BorderLayout.EAST);

            if (isSelected) {
                panel.setBackground(tbl.getSelectionBackground());
            } else {
                panel.setBackground(tbl.getBackground());
            }

            return panel;
        });

        // Klick-Logik für den Button in Spalte 1
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());

                if (col == 1 && row >= 0 && row < entries.size()) {
                    String value = entries.get(row).getValue();
                    JTextArea area = new JTextArea(value != null ? value : "");
                    area.setLineWrap(true);
                    area.setWrapStyleWord(true);
                    area.setEditable(false);

                    JScrollPane scroll = new JScrollPane(area);
                    scroll.setPreferredSize(new Dimension(500, 300));

                    JOptionPane.showMessageDialog(table, scroll, "Wert anzeigen", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(500, 300));

        JOptionPane.showMessageDialog(this, scrollPane, "Umgebungsvariablen des letzten Laufs..", JOptionPane.INFORMATION_MESSAGE);
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

    private UUID onRun() {
        try {
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

            // Degugging:
//        JOptionPane.showMessageDialog(this,
//                "Workflow gestartet mit ID:\n" + runId,
//                "Ausführung gestartet",
//                JOptionPane.INFORMATION_MESSAGE);
            return runId;
        } catch (IllegalArgumentException e) {
            showError(this.getParent(), "Fehler beim Import:\n" + e.getMessage());
        }
        return null;
    }

    private static void showError(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
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
        StepPanel panel = new StepPanel(context.getToolRegistry(), step);
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
