package de.bund.zrb.ui.settings;

import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.tools.ToolAccessType;
import de.bund.zrb.tools.ToolPolicy;
import de.bund.zrb.tools.ToolPolicyRepository;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Manage enabled/approval/access policies for tools. */
public class ToolPolicyDialog {

    public static void show(Component parent) {
        ToolPolicyRepository repository = new ToolPolicyRepository();
        List<ToolPolicy> policies = new ArrayList<>(repository.loadAll());
        Map<String, ToolPolicy> byName = new HashMap<>();
        for (ToolPolicy p : policies) {
            byName.put(p.getToolName(), p);
        }

        List<Row> rows = new ArrayList<>();
        for (McpTool tool : ToolRegistryImpl.getInstance().getAllTools()) {
            String name = tool.getSpec().getName();
            ToolPolicy policy = byName.get(name);
            if (policy == null) {
                continue;
            }
            rows.add(new Row(policy, tool.getSpec().getDescription()));
        }

        Model model = new Model(rows);
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.getColumnModel().getColumn(4).setPreferredWidth(380);

        JComboBox<ToolAccessType> accessEditor = new JComboBox<>(ToolAccessType.values());
        table.getColumnModel().getColumn(2).setCellEditor(new javax.swing.DefaultCellEditor(accessEditor));

        TableRowSorter<Model> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        JTextField filterField = new JTextField(24);
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            private void apply() {
                String text = filterField.getText();
                if (text == null || text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                    return;
                }
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text.trim()), 3, 4));
            }
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Suche:"));
        searchPanel.add(filterField);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(searchPanel, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);

        JDialog dialog = new JDialog((Frame) null, "Tool-Konfiguration", true);
        dialog.setLayout(new BorderLayout(4, 4));
        dialog.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        javax.swing.JButton cancel = new javax.swing.JButton("Abbrechen");
        javax.swing.JButton ok = new javax.swing.JButton("Speichern");
        buttons.add(cancel);
        buttons.add(ok);
        dialog.add(buttons, BorderLayout.SOUTH);

        cancel.addActionListener(e -> dialog.dispose());
        ok.addActionListener(e -> {
            repository.saveAll(model.toPolicies());
            dialog.dispose();
        });

        dialog.setSize(920, 460);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static class Row {
        private final ToolPolicy policy;
        private final String description;

        private Row(ToolPolicy policy, String description) {
            this.policy = policy;
            this.description = description;
        }
    }

    private static class Model extends AbstractTableModel {
        private final String[] columns = {"Aktiv", "Nachfragen", "Typ", "Tool", "Beschreibung"};
        private final List<Row> rows;

        private Model(List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 1) {
                return Boolean.class;
            }
            if (columnIndex == 2) {
                return ToolAccessType.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex <= 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            if (columnIndex == 0) {
                return row.policy.isEnabled();
            }
            if (columnIndex == 1) {
                return row.policy.isAskBeforeUse();
            }
            if (columnIndex == 2) {
                return row.policy.getAccessType();
            }
            if (columnIndex == 3) {
                return row.policy.getToolName();
            }
            return Objects.toString(row.description, "");
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            if (columnIndex == 0) {
                row.policy.setEnabled(Boolean.TRUE.equals(aValue));
            } else if (columnIndex == 1) {
                row.policy.setAskBeforeUse(Boolean.TRUE.equals(aValue));
            } else if (columnIndex == 2 && aValue instanceof ToolAccessType) {
                row.policy.setAccessType((ToolAccessType) aValue);
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
        }

        public List<ToolPolicy> toPolicies() {
            List<ToolPolicy> out = new ArrayList<>();
            for (Row row : rows) {
                out.add(row.policy);
            }
            return out;
        }
    }
}
