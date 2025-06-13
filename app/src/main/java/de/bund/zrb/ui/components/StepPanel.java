package de.bund.zrb.ui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.workflow.WorkflowMcpData;
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
    private final JButton reformatButton;
    private final JButton deleteButton;
    private final ToolRegistry registry;

    private ActionListener deleteListener;

    private final JTextField resultVarField;

    public StepPanel(ToolRegistry registry, WorkflowMcpData initialStep) {
        this.registry = registry;
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
                BorderFactory.createLineBorder(Color.LIGHT_GRAY)));

        // Tool Dropdown
        toolSelector = new JComboBox<>(registry.getAllTools().stream()
                .map(t -> t.getSpec().getName())
                .sorted().toArray(String[]::new));
        toolSelector.setSelectedItem(initialStep.getToolName());

        // Buttons
        paramEditorButton = new JButton("...");
        paramEditorButton.setToolTipText("Parameter bearbeiten");
        paramEditorButton.setMargin(new Insets(2, 6, 2, 6));
        paramEditorButton.addActionListener(e -> openParamEditor());

        reformatButton = new JButton("⭳");
        reformatButton.setToolTipText("Reformat JSON");
        reformatButton.setMargin(new Insets(2, 6, 2, 6));
        reformatButton.addActionListener(e -> reformatJson());

        deleteButton = new JButton("✖");
        deleteButton.setToolTipText("Schritt entfernen");
        deleteButton.setMargin(new Insets(2, 6, 2, 6));
        deleteButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Diesen Schritt wirklich löschen?",
                    "Löschen bestätigen",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION && deleteListener != null) {
                deleteListener.actionPerformed(e);
            }
        });

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.add(toolSelector, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonBar.add(reformatButton);
        buttonBar.add(paramEditorButton);
        buttonBar.add(deleteButton);
        topRow.add(buttonBar, BorderLayout.EAST);

        add(topRow, BorderLayout.NORTH);

        // JSON Editor
        jsonEditor = new RSyntaxTextArea(6, 40);
        jsonEditor.setSyntaxEditingStyle("text/json");
        jsonEditor.setCodeFoldingEnabled(true);
        jsonEditor.setAntiAliasingEnabled(true);
        jsonEditor.setText(prettyJson(initialStep.getParameters()));

        // Editor-Panel mit zusätzlichem Feld für resultVar
        resultVarField = new JTextField(initialStep.getResultVar() != null ? initialStep.getResultVar() : "");
        resultVarField.setToolTipText("Name der Variablen zur Speicherung des Ergebnisses");
        JPanel resultVarPanel = new JPanel(new BorderLayout());
        resultVarPanel.add(new JLabel("↳ "), BorderLayout.WEST);
        resultVarPanel.add(resultVarField, BorderLayout.CENTER);
        add(resultVarPanel, BorderLayout.SOUTH);

        add(new RTextScrollPane(jsonEditor), BorderLayout.CENTER);
    }

    private void openParamEditor() {
        String toolName = (String) toolSelector.getSelectedItem();
        McpTool tool = registry.getToolByName(toolName);
        if (tool == null) return;

        try {
            Map<String, Object> currentParams = new Gson().fromJson(jsonEditor.getText(), Map.class);
            Map<String, Object> result = ParameterEditorDialog.showDialog(this, tool, currentParams);
            if (result != null) {
                jsonEditor.setText(prettyJson(result));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Ungültiges JSON in Parameterfeld: " + ex.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reformatJson() {
        try {
            Map<String, Object> params = new Gson().fromJson(jsonEditor.getText(), Map.class);
            jsonEditor.setText(prettyJson(params));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Kann JSON nicht formatieren: " + ex.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String prettyJson(Map<String, Object> map) {
        return new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()    // avoid ' masking (this security feature is not necessary, here)
                .create().toJson(map);
    }

    public WorkflowMcpData toWorkflowStep() {
        String toolName = (String) toolSelector.getSelectedItem();
        String resultVar = resultVarField.getText().trim();
        try {
            Map<String, Object> params = new Gson().fromJson(jsonEditor.getText(), Map.class);
            return new WorkflowMcpData(toolName, params, resultVar.isEmpty() ? null : resultVar);
        } catch (Exception ex) {
            return new WorkflowMcpData(toolName, new LinkedHashMap<>(), resultVar.isEmpty() ? null : resultVar);
        }
    }


    public void setDeleteListener(ActionListener listener) {
        this.deleteListener = listener;
    }
}
