package de.bund.zrb.ui.components;

import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ToolSpec;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ParameterEditorDialog {

    public static Map<String, Object> showDialog(Component parent, McpTool tool, Map<String, Object> existing) {
        if (tool == null || tool.getSpec() == null) return existing;

        ToolSpec.InputSchema schema = tool.getSpec().getInputSchema();
        if (schema == null) return existing;

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        Map<String, JTextField> textFields = new HashMap<>();
        Map<String, JCheckBox> checkboxes = new HashMap<>();
        Map<String, JComboBox<String>> typeSelectors = new HashMap<>();

        int row = 0;
        for (Map.Entry<String, ToolSpec.Property> entry : schema.getProperties().entrySet()) {
            String key = entry.getKey();
            ToolSpec.Property prop = entry.getValue();

            JLabel label = new JLabel(key + (schema.getRequired().contains(key) ? " *" : ""));

            JTextField textField = new JTextField(20);
            JCheckBox variableCheckBox = new JCheckBox("Variable");

            Object existingValue = existing != null ? existing.get(key) : null;
            String detectedType = "string";

            if (existingValue instanceof String) {
                String valueStr = (String) existingValue;
                if (valueStr.matches("\\{\\{[^{}]+}}")) {
                    variableCheckBox.setSelected(true);
                    textField.setText(valueStr.substring(2, valueStr.length() - 2)); // unwrap
                } else {
                    textField.setText(valueStr);
                }
            } else if (existingValue != null) {
                textField.setText(existingValue.toString());
                if (existingValue instanceof Number) {
                    detectedType = "number";
                } else if (existingValue instanceof Boolean) {
                    detectedType = "boolean";
                }
            }

            JPanel fieldPanel = new JPanel(new BorderLayout());
            fieldPanel.add(textField, BorderLayout.CENTER);
            fieldPanel.add(variableCheckBox, BorderLayout.EAST);

            JComboBox<String> typeBox = new JComboBox<>(new String[]{"string", "number", "boolean"});

            String typeFromSpec = prop.getType() != null ? prop.getType() : "string";
            if (!typeFromSpec.equals("string") && !typeFromSpec.equals("number") && !typeFromSpec.equals("boolean")) {
                typeFromSpec = "string";
            }

            typeBox.setSelectedItem(detectedType != null ? detectedType : typeFromSpec);
            typeSelectors.put(key, typeBox);

            gbc.gridx = 0;
            gbc.gridy = row;
            formPanel.add(label, gbc);

            gbc.gridx = 1;
            formPanel.add(typeBox, gbc);

            gbc.gridx = 2;
            formPanel.add(fieldPanel, gbc);

            textFields.put(key, textField);
            checkboxes.put(key, variableCheckBox);
            row++;
        }

        int result = JOptionPane.showConfirmDialog(parent, formPanel, "Parameter bearbeiten", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            Map<String, Object> resultMap = new HashMap<>();
            for (String key : textFields.keySet()) {
                String value = textFields.get(key).getText().trim();
                boolean isVariable = checkboxes.get(key).isSelected();
                String selectedType = (String) typeSelectors.get(key).getSelectedItem();

                if (!value.isEmpty()) {
                    if (isVariable && !value.matches("\\{\\{[^{}]+}}")) {
                        resultMap.put(key, "{{" + value + "}}");
                        continue;
                    }

                    try {
                        switch (selectedType) {
                            case "number":
                                resultMap.put(key, Double.parseDouble(value));
                                break;
                            case "boolean":
                                resultMap.put(key, Boolean.parseBoolean(value));
                                break;
                            default:
                                resultMap.put(key, value);
                        }
                    } catch (Exception ex) {
                        resultMap.put(key, value); // fallback auf String
                    }
                }
            }
            return resultMap;
        }

        return null; // cancelled
    }
}
