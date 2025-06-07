package de.bund.zrb.ui.components;

import com.google.gson.Gson;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ToolSpec;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ParameterEditorDialog {

    public static Map<String, Object> showDialog(Component parent, McpTool tool, Map<String, Object> existing) {
        if (tool == null || tool.getSpec() == null) return existing;

        ToolSpec spec = tool.getSpec();
        ToolSpec.InputSchema schema = spec.getInputSchema();
        if (schema == null) return existing;

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        Map<String, JComponent> fieldMap = new HashMap<>();

        int row = 0;
        for (Map.Entry<String, ToolSpec.Property> entry : schema.getProperties().entrySet()) {
            String key = entry.getKey();
            ToolSpec.Property prop = entry.getValue();

            JLabel label = new JLabel(key + (schema.getRequired().contains(key) ? " *" : ""));
            gbc.gridx = 0;
            gbc.gridy = row;
            formPanel.add(label, gbc);

            JTextField field = new JTextField(20);
            Object existingValue = existing != null ? existing.get(key) : null;
            if (existingValue != null) field.setText(existingValue.toString());

            gbc.gridx = 1;
            formPanel.add(field, gbc);
            fieldMap.put(key, field);

            row++;
        }

        int result = JOptionPane.showConfirmDialog(parent, formPanel, "Parameter bearbeiten", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            Map<String, Object> resultMap = new HashMap<>();
            for (Map.Entry<String, JComponent> entry : fieldMap.entrySet()) {
                String key = entry.getKey();
                JComponent comp = entry.getValue();
                if (comp instanceof JTextField) {
                    resultMap.put(key, ((JTextField) comp).getText());
                }
            }
            return resultMap;
        }

        return null; // Cancelled
    }
}
