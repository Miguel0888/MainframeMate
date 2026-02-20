package de.bund.zrb.ui.settings;

import de.bund.zrb.mcp.registry.McpServerConfig;
import de.bund.zrb.mcp.registry.McpServerManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings panel for the MCP Registry – manage external MCP servers
 * (like GitHub MCP Server for Issues etc.).
 *
 * <p>External MCP servers are started as separate processes communicating
 * via stdio JSON-RPC. Their tools are dynamically discovered and registered
 * in the ToolRegistry.</p>
 */
public class McpRegistryPanel extends JPanel {

    private static final String[] COLUMNS = {"Aktiv", "Name", "Befehl", "Status", "Tools"};

    private final McpServerManager manager = McpServerManager.getInstance();
    private final List<McpServerConfig> configs;
    private final RegistryTableModel model;
    private final JTable table;

    public McpRegistryPanel() {
        configs = new ArrayList<>(manager.loadConfigs());
        model = new RegistryTableModel();
        table = new JTable(model);

        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Info label
        JLabel info = new JLabel(
                "<html><b>MCP Registry</b> — Externe MCP-Server einbinden (z. B. GitHub MCP Server).<br>"
                + "Die Tools der Server werden automatisch in der Tool-Registry registriert.</html>");
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(info, BorderLayout.NORTH);

        // Table
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(3).setMaxWidth(100);
        table.getColumnModel().getColumn(4).setMaxWidth(60);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton addBtn = new JButton("➕ Hinzufügen");
        JButton editBtn = new JButton("✏️ Bearbeiten");
        JButton removeBtn = new JButton("🗑️ Entfernen");
        JButton startBtn = new JButton("▶ Starten");
        JButton stopBtn = new JButton("⏹ Stoppen");

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(Box.createHorizontalStrut(16));
        buttonPanel.add(startBtn);
        buttonPanel.add(stopBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // Button state
        editBtn.setEnabled(false);
        removeBtn.setEnabled(false);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0) {
                editBtn.setEnabled(false);
                removeBtn.setEnabled(false);
                startBtn.setEnabled(false);
                stopBtn.setEnabled(false);
            } else {
                int modelRow = table.convertRowIndexToModel(row);
                McpServerConfig cfg = configs.get(modelRow);
                editBtn.setEnabled(true);
                removeBtn.setEnabled(true);
                startBtn.setEnabled(cfg.isEnabled() && !manager.isRunning(cfg.getName()));
                stopBtn.setEnabled(manager.isRunning(cfg.getName()));
            }
        });

        // Actions
        addBtn.addActionListener(e -> {
            McpServerConfig newCfg = McpServerDialog.showEditDialog(
                    SwingUtilities.getWindowAncestor(this), null);
            if (newCfg != null) {
                configs.add(newCfg);
                manager.saveConfigs(configs);
                model.fireTableDataChanged();
            }
        });

        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            int modelRow = table.convertRowIndexToModel(row);
            McpServerConfig cfg = configs.get(modelRow);
            McpServerConfig edited = McpServerDialog.showEditDialog(
                    SwingUtilities.getWindowAncestor(this), cfg);
            if (edited != null) {
                if (manager.isRunning(cfg.getName())) manager.stopServer(cfg.getName());
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
            int choice = JOptionPane.showConfirmDialog(this,
                    "Server '" + cfg.getName() + "' wirklich entfernen?",
                    "Entfernen", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                if (manager.isRunning(cfg.getName())) manager.stopServer(cfg.getName());
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
            new Thread(() -> {
                manager.startServer(cfg);
                SwingUtilities.invokeLater(model::fireTableDataChanged);
            }, "mcp-start-" + cfg.getName()).start();
        });

        stopBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            int modelRow = table.convertRowIndexToModel(row);
            McpServerConfig cfg = configs.get(modelRow);
            manager.stopServer(cfg.getName());
            model.fireTableDataChanged();
        });
    }

    // ── Table model ─────────────────────────────────────────────────

    private class RegistryTableModel extends AbstractTableModel {

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
            return col == 0;
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

