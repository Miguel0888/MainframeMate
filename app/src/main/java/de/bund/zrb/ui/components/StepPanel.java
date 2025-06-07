package de.bund.zrb.ui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.workflow.WorkflowStep;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

public class StepPanel extends JPanel {

    private final JComboBox<String> toolSelector;
    private final RSyntaxTextArea jsonEditor;
    private final JButton paramEditorButton;
    private final JButton deleteButton;
    private final ToolRegistry registry;

    private ActionListener deleteListener;

    public StepPanel(ToolRegistry registry, WorkflowStep initialStep) {
        this.registry = registry;
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
                BorderFactory.createLineBorder(Color.LIGHT_GRAY)));

        // Tool Dropdown & Delete Button
        toolSelector = new JComboBox<>(registry.getAllTools().stream()
                .map(t -> t.getSpec().getName())
                .sorted().toArray(String[]::new));
        toolSelector.setSelectedItem(initialStep.getToolName());

        deleteButton = new JButton("✖");
        deleteButton.setMargin(new Insets(2, 8, 2, 8));
        deleteButton.addActionListener(e -> {
            if (deleteListener != null) deleteListener.actionPerformed(e);
        });

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.add(toolSelector, BorderLayout.CENTER);
        topRow.add(deleteButton, BorderLayout.EAST);
        add(topRow, BorderLayout.NORTH);

        // JSON Editor & Parameter Dialog Button
        jsonEditor = new RSyntaxTextArea(6, 40);
        jsonEditor.setSyntaxEditingStyle("text/json");
        jsonEditor.setCodeFoldingEnabled(true);
        jsonEditor.setAntiAliasingEnabled(true);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        jsonEditor.setText(gson.toJson(initialStep.getParameters()));

        paramEditorButton = new JButton("...");
        paramEditorButton.setMargin(new Insets(2, 10, 2, 10));
        paramEditorButton.setToolTipText("Parameter bearbeiten");
        paramEditorButton.addActionListener(e -> openParamEditor());

        JPanel editorPanel = new JPanel(new BorderLayout(4, 4));
        editorPanel.add(new RTextScrollPane(jsonEditor), BorderLayout.CENTER);
        editorPanel.add(paramEditorButton, BorderLayout.EAST);

        add(editorPanel, BorderLayout.CENTER);
    }

    private void openParamEditor() {
        String toolName = (String) toolSelector.getSelectedItem();
        McpTool tool = registry.getToolByName(toolName);
        if (tool == null) return;

        try {
            Map<String, Object> currentParams = new Gson().fromJson(jsonEditor.getText(), Map.class);
            Map<String, Object> result = ParameterEditorDialog.showDialog(this, tool, currentParams);
            if (result != null) {
                jsonEditor.setText(new GsonBuilder().setPrettyPrinting().create().toJson(result));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ungültiges JSON in Parameterfeld: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    public WorkflowStep toWorkflowStep() {
        String toolName = (String) toolSelector.getSelectedItem();
        try {
            Map<String, Object> params = new Gson().fromJson(jsonEditor.getText(), Map.class);
            return new WorkflowStep(toolName, params);
        } catch (Exception ex) {
            return new WorkflowStep(toolName, new LinkedHashMap<>());
        }
    }

    public void setDeleteListener(ActionListener listener) {
        this.deleteListener = listener;
    }
}
