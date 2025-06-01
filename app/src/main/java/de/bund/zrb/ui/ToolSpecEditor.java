package de.bund.zrb.ui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import de.zrb.bund.newApi.mcp.ToolSpec;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ToolSpecEditor extends JPanel {

    private final JTextField nameField = new JTextField(30);
    private final JTextField descriptionField = new JTextField(30);
    private final PropertyTableModel propertyModel = new PropertyTableModel();
    private final JTable propertyTable = new JTable(propertyModel);
    private final JTextArea exampleInputArea = new JTextArea(6, 50);

    public ToolSpecEditor() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        topPanel.add(new JLabel("Name:"), gbc); gbc.gridy++;
        topPanel.add(nameField, gbc); gbc.gridy++;

        topPanel.add(new JLabel("Beschreibung:"), gbc); gbc.gridy++;
        topPanel.add(descriptionField, gbc); gbc.gridy++;

        add(topPanel, BorderLayout.NORTH);

        // Eigenschaften-Tabelle
        JPanel propsPanel = new JPanel(new BorderLayout());
        propsPanel.setBorder(BorderFactory.createTitledBorder("Input-Properties"));

        propertyTable.setFillsViewportHeight(true);
        propsPanel.add(new JScrollPane(propertyTable), BorderLayout.CENTER);

        JButton addPropertyButton = new JButton("➕ Eigenschaft");
        JButton removePropertyButton = new JButton("➖");

        addPropertyButton.addActionListener(e -> propertyModel.addRow());
        removePropertyButton.addActionListener(e -> {
            int selectedRow = propertyTable.getSelectedRow();
            if (selectedRow >= 0) {
                propertyModel.removeRow(selectedRow);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addPropertyButton);
        buttonPanel.add(removePropertyButton);
        propsPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(propsPanel, BorderLayout.CENTER);

        // Example input
        JPanel examplePanel = new JPanel(new BorderLayout());
        examplePanel.setBorder(BorderFactory.createTitledBorder("Beispiel-Eingabe (JSON)"));
        examplePanel.add(new JScrollPane(exampleInputArea), BorderLayout.CENTER);

        add(examplePanel, BorderLayout.SOUTH);
    }

    public void setToolSpec(ToolSpec spec) {
        nameField.setText(spec.getName());
        descriptionField.setText(spec.getDescription());

        Map<String, ToolSpec.Property> properties = spec.getInputSchema().getProperties();
        List<String> required = spec.getInputSchema().getRequired();
        propertyModel.setData(properties, required);

        if (spec.getExampleInput() != null) {
            Gson gson = new Gson();
            exampleInputArea.setText(gson.toJson(spec.getExampleInput()));
        } else {
            exampleInputArea.setText("");
        }
    }

    public ToolSpec getToolSpec() {
        String name = nameField.getText().trim();
        String desc = descriptionField.getText().trim();

        Map<String, ToolSpec.Property> props = propertyModel.getProperties();
        List<String> required = propertyModel.getRequired();

        Map<String, Object> exampleInput = null;
        String text = exampleInputArea.getText().trim();
        if (!text.isEmpty()) {
            try {
                exampleInput = new Gson().fromJson(text, Map.class);
            } catch (JsonSyntaxException e) {
                JOptionPane.showMessageDialog(this, "Fehler im Beispiel-JSON: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }

        return new ToolSpec(name, desc, new ToolSpec.InputSchema(props, required), exampleInput);
    }

    private GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        return gbc;
    }

    // TableModel für Properties
    private static class PropertyTableModel extends AbstractTableModel {
        private final List<String> names = new ArrayList<>();
        private final List<ToolSpec.Property> properties = new ArrayList<>();
        private final Set<String> required = new HashSet<>();

        private static final String[] COLUMNS = {"Name", "Typ", "Beschreibung", "Required"};

        public void setData(Map<String, ToolSpec.Property> props, List<String> requiredFields) {
            names.clear();
            properties.clear();
            required.clear();

            props.forEach((k, v) -> {
                names.add(k);
                properties.add(v);
            });
            if (requiredFields != null) {
                required.addAll(requiredFields);
            }
            fireTableDataChanged();
        }

        public void addRow() {
            names.add("feld");
            properties.add(new ToolSpec.Property("string", "Beschreibung"));
            fireTableRowsInserted(names.size() - 1, names.size() - 1);
        }

        public void removeRow(int index) {
            names.remove(index);
            properties.remove(index);
            fireTableRowsDeleted(index, index);
        }

        public Map<String, ToolSpec.Property> getProperties() {
            Map<String, ToolSpec.Property> result = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) {
                result.put(names.get(i), properties.get(i));
            }
            return result;
        }

        public List<String> getRequired() {
            return new ArrayList<>(required);
        }

        @Override
        public int getRowCount() {
            return names.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int col) {
            return COLUMNS[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ToolSpec.Property prop = properties.get(rowIndex);
            switch (columnIndex) {
                case 0: return names.get(rowIndex);
                case 1: return prop.getType();
                case 2: return prop.getDescription();
                case 3: return required.contains(names.get(rowIndex));
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            ToolSpec.Property prop = properties.get(rowIndex);
            switch (columnIndex) {
                case 0: names.set(rowIndex, String.valueOf(aValue)); break;
                case 1: properties.set(rowIndex, new ToolSpec.Property(String.valueOf(aValue), prop.getDescription())); break;
                case 2: properties.set(rowIndex, new ToolSpec.Property(prop.getType(), String.valueOf(aValue))); break;
                case 3:
                    if ((boolean) aValue) required.add(names.get(rowIndex));
                    else required.remove(names.get(rowIndex));
                    break;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 3 ? Boolean.class : String.class;
        }
    }
}
