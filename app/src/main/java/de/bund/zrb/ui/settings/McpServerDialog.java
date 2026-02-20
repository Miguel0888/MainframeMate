package de.bund.zrb.ui.settings;

import de.bund.zrb.mcp.registry.McpServerConfig;
import de.bund.zrb.mcp.registry.McpServerManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dialog to manage external MCP servers – add, edit, remove, enable/disable, start/stop.
 * Similar to GitHub Copilot's MCP server registry.
 */
public class McpServerDialog {

    public static void show(Component parent) {
        McpServerManager manager = McpServerManager.getInstance();
        List<McpServerConfig> configs = new ArrayList<>(manager.loadConfigs());

        ServerModel model = new ServerModel(configs, manager);
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);   // Aktiv
        table.getColumnModel().getColumn(1).setPreferredWidth(150);  // Name
        table.getColumnModel().getColumn(2).setPreferredWidth(300);  // Command
        table.getColumnModel().getColumn(3).setPreferredWidth(80);   // Status
        table.getColumnModel().getColumn(4).setPreferredWidth(60);   // Tools

        // Status column color renderer
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                if ("🟢 Aktiv".equals(value)) {
                    c.setForeground(new Color(0, 140, 0));
                } else {
                    c.setForeground(Color.GRAY);
                }
                return c;
            }
        });

        table.setRowHeight(24);


        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        center.add(new JScrollPane(table), BorderLayout.CENTER);

        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton addBtn = new JButton("➕ Hinzufügen");
        JButton editBtn = new JButton("✏️ Bearbeiten");
        JButton removeBtn = new JButton("🗑️ Entfernen");
        JButton startBtn = new JButton("▶ Starten");
        JButton stopBtn = new JButton("⏹ Stoppen");

        actionPanel.add(addBtn);
        actionPanel.add(editBtn);
        actionPanel.add(removeBtn);
        actionPanel.add(Box.createHorizontalStrut(16));
        actionPanel.add(startBtn);
        actionPanel.add(stopBtn);

        center.add(actionPanel, BorderLayout.SOUTH);

        // Enable/disable buttons based on selection
        Runnable updateButtons = () -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                editBtn.setEnabled(false);
                removeBtn.setEnabled(false);
                startBtn.setEnabled(false);
                stopBtn.setEnabled(false);
                return;
            }
            int modelRow = table.convertRowIndexToModel(row);
            McpServerConfig cfg = configs.get(modelRow);
            editBtn.setEnabled(true);
            removeBtn.setEnabled(true);
            startBtn.setEnabled(cfg.isEnabled() && !manager.isRunning(cfg.getName()));
            stopBtn.setEnabled(manager.isRunning(cfg.getName()));
        };
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateButtons.run();
        });
        // Initial state
        editBtn.setEnabled(false);
        removeBtn.setEnabled(false);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);

        JDialog dialog = new JDialog((Frame) null, "MCP Server verwalten", true);
        dialog.setLayout(new BorderLayout(4, 4));
        dialog.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Schließen");
        buttons.add(closeBtn);
        dialog.add(buttons, BorderLayout.SOUTH);

        // ── Actions ─────────────────────────────────────────────────

        addBtn.addActionListener(e -> {
            McpServerConfig newConfig = showEditDialog(dialog, null);
            if (newConfig != null) {
                configs.add(newConfig);
                manager.saveConfigs(configs);
                model.fireTableDataChanged();
            }
        });

        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            int modelRow = table.convertRowIndexToModel(row);
            McpServerConfig cfg = configs.get(modelRow);
            McpServerConfig edited = showEditDialog(dialog, cfg);
            if (edited != null) {
                // Stop old server if running
                if (manager.isRunning(cfg.getName())) {
                    manager.stopServer(cfg.getName());
                }
                configs.set(modelRow, edited);
                manager.saveConfigs(configs);
                model.fireTableDataChanged();
            }
        });

        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            int modelRow = table.convertRowIndexToModel(row);
            McpServerConfig cfg = configs.get(modelRow);
            int choice = JOptionPane.showConfirmDialog(dialog,
                    "Server '" + cfg.getName() + "' wirklich entfernen?",
                    "Entfernen", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                if (manager.isRunning(cfg.getName())) {
                    manager.stopServer(cfg.getName());
                }
                configs.remove(modelRow);
                manager.saveConfigs(configs);
                model.fireTableDataChanged();
            }
        });

        startBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            int modelRow = table.convertRowIndexToModel(row);
            McpServerConfig cfg = configs.get(modelRow);
            if (!manager.isRunning(cfg.getName())) {
                new Thread(() -> {
                    manager.startServer(cfg);
                    SwingUtilities.invokeLater(() -> {
                        model.fireTableDataChanged();
                        updateButtons.run();
                    });
                }, "mcp-start-" + cfg.getName()).start();
            }
        });

        stopBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            int modelRow = table.convertRowIndexToModel(row);
            McpServerConfig cfg = configs.get(modelRow);
            if (manager.isRunning(cfg.getName())) {
                manager.stopServer(cfg.getName());
                model.fireTableDataChanged();
                updateButtons.run();
            }
        });

        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.setSize(750, 380);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /**
     * Show an edit/add dialog for a single MCP server config.
     *
     * @param parent the parent dialog
     * @param existing existing config to edit, or null for a new server
     * @return the edited config, or null if cancelled
     */
    public static McpServerConfig showEditDialog(Window parent, McpServerConfig existing) {
        JTextField nameField = new JTextField(existing != null ? existing.getName() : "", 20);
        JTextField commandField = new JTextField(existing != null ? existing.getCommand() : "", 30);
        JTextField argsField = new JTextField(
                existing != null && existing.getArgs() != null ? String.join(" ", existing.getArgs()) : "", 30);
        JCheckBox enabledCheck = new JCheckBox("Aktiviert", existing == null || existing.isEnabled());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Befehl:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(commandField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Argumente:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(argsField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        form.add(enabledCheck, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        form.add(new JLabel("<html><small>Beispiel: java -jar pfad/zum/mcp-server.jar</small></html>"), gbc);

        int result = JOptionPane.showConfirmDialog(parent, form,
                existing != null ? "MCP Server bearbeiten" : "MCP Server hinzufügen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String name = nameField.getText().trim();
        String command = commandField.getText().trim();
        if (name.isEmpty() || command.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Name und Befehl sind Pflichtfelder.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        String argsStr = argsField.getText().trim();
        List<String> args = argsStr.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(argsStr.split("\\s+")));
        return new McpServerConfig(name, command, args, enabledCheck.isSelected());
    }

    // ── Table model ─────────────────────────────────────────────────

    private static class ServerModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Aktiv", "Name", "Befehl", "Status", "Tools"};
        private final List<McpServerConfig> configs;
        private final McpServerManager manager;

        ServerModel(List<McpServerConfig> configs, McpServerManager manager) {
            this.configs = configs;
            this.manager = manager;
        }

        @Override
        public int getRowCount() { return configs.size(); }

        @Override
        public int getColumnCount() { return COLUMNS.length; }

        @Override
        public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0; // Only "Aktiv" checkbox
        }

        @Override
        public Object getValueAt(int row, int col) {
            McpServerConfig cfg = configs.get(row);
            switch (col) {
                case 0: return cfg.isEnabled();
                case 1: return cfg.getName();
                case 2: {
                    String cmd = cfg.getCommand();
                    if (cfg.getArgs() != null && !cfg.getArgs().isEmpty()) {
                        cmd += " " + String.join(" ", cfg.getArgs());
                    }
                    return cmd;
                }
                case 3: return manager.isRunning(cfg.getName()) ? "🟢 Aktiv" : "⚪ Inaktiv";
                case 4: {
                    List<String> tools = manager.getToolNames(cfg.getName());
                    return tools.isEmpty() ? "-" : String.valueOf(tools.size());
                }
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object val, int row, int col) {
            if (col == 0) {
                McpServerConfig cfg = configs.get(row);
                boolean enabled = Boolean.TRUE.equals(val);
                cfg.setEnabled(enabled);
                manager.saveConfigs(configs);

                if (!enabled && manager.isRunning(cfg.getName())) {
                    manager.stopServer(cfg.getName());
                } else if (enabled && !manager.isRunning(cfg.getName())) {
                    new Thread(() -> {
                        manager.startServer(cfg);
                        SwingUtilities.invokeLater(() -> fireTableDataChanged());
                    }, "mcp-toggle-" + cfg.getName()).start();
                }

                fireTableRowsUpdated(row, row);
            }
        }
    }
}

