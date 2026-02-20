package de.bund.zrb.ui.settings;

import de.bund.zrb.mcp.registry.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "App Store"-style dialog for browsing the MCP Registry,
 * viewing server details, and installing/enabling MCP servers.
 * <p>
 * Two tabs: <b>Marketplace</b> (full catalogue with search) and
 * <b>Installiert</b> (installed servers with start/stop/remove).
 * </p>
 */
public class McpRegistryBrowserDialog {

    public static void show(Component parent) {
        McpRegistrySettings settings = McpRegistrySettings.load();
        McpRegistryApiClient apiClient = new McpRegistryApiClient(settings);
        McpServerManager manager = McpServerManager.getInstance();

        JDialog dialog = new JDialog(
                parent instanceof Frame ? (Frame) parent
                        : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent),
                "MCP Registry", true);
        dialog.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();

        // ════════════════════════════════════════════════════════════
        //  Tab 1: Marketplace
        // ════════════════════════════════════════════════════════════
        JPanel marketplaceTab = buildMarketplaceTab(apiClient, manager, dialog);
        tabs.addTab("Marketplace", marketplaceTab);

        // ════════════════════════════════════════════════════════════
        //  Tab 2: Installiert
        // ════════════════════════════════════════════════════════════
        InstalledTab installedTab = new InstalledTab(manager, dialog, apiClient);
        tabs.addTab("Installiert", installedTab.panel);

        // Refresh installed list when switching to that tab
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) installedTab.refresh();
        });

        dialog.add(tabs, BorderLayout.CENTER);
        dialog.setSize(920, 580);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // ════════════════════════════════════════════════════════════════
    //  Marketplace tab
    // ════════════════════════════════════════════════════════════════

    private static JPanel buildMarketplaceTab(McpRegistryApiClient apiClient,
                                               McpServerManager manager, JDialog dialog) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));

        // ── Top bar ─────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(4, 4));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JTextField searchField = new JTextField(20);
        searchField.setToolTipText("Server suchen (z.B. 'github', 'filesystem', 'slack')...");
        JButton refreshBtn = new JButton("\u21BB");
        refreshBtn.setToolTipText("Cache leeren & neu laden");

        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.add(new JLabel("Suche: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(refreshBtn, BorderLayout.EAST);
        topBar.add(searchPanel, BorderLayout.CENTER);
        panel.add(topBar, BorderLayout.NORTH);

        // ── Table ───────────────────────────────────────────────────
        List<McpRegistryServerInfo> allServers = new ArrayList<>();
        List<McpRegistryServerInfo> displayServers = new ArrayList<>();

        String[] COLUMNS = {"Name", "Herausgeber", "Beschreibung", "Status"};
        AbstractTableModel model = new AbstractTableModel() {
            @Override public int getRowCount() { return displayServers.size(); }
            @Override public int getColumnCount() { return COLUMNS.length; }
            @Override public String getColumnName(int col) { return COLUMNS[col]; }

            @Override
            public Object getValueAt(int row, int col) {
                McpRegistryServerInfo info = displayServers.get(row);
                switch (col) {
                    case 0: {
                        String prefix = "";
                        if (info.isOfficial() && info.isKnownPublisher()) prefix = "\u2605 ";
                        else if (info.isKnownPublisher()) prefix = "\u2606 ";
                        else if (info.isOfficial()) prefix = "\u2713 ";
                        return prefix + McpRegistryApiClient.getShortName(info.getName());
                    }
                    case 1:
                        return McpRegistryApiClient.getPublisher(info.getName());
                    case 2: {
                        String d = info.getDescription();
                        return d.length() > 100 ? d.substring(0, 97) + "..." : d;
                    }
                    case 3: {
                        if (isInstalled(info, manager)) return "\u2713 Installiert";
                        String s = info.getStatus();
                        if ("deprecated".equalsIgnoreCase(s)) return "\u26A0 deprecated";
                        if ("deleted".equalsIgnoreCase(s)) return "\u274C deleted";
                        return "active";
                    }
                    default: return "";
                }
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(380);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);

        // Status column coloring
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                String v = String.valueOf(value);
                if (v.contains("Installiert")) c.setForeground(new Color(0, 100, 200));
                else if (v.contains("deprecated")) c.setForeground(new Color(180, 130, 0));
                else if (v.contains("deleted")) c.setForeground(Color.RED);
                else c.setForeground(new Color(0, 140, 0));
                return c;
            }
        });

        // Name column: bold for known publishers
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                if (row < displayServers.size()) {
                    McpRegistryServerInfo info = displayServers.get(row);
                    if (info.isKnownPublisher() || info.isOfficial()) {
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    }
                }
                return c;
            }
        });

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // ── Bottom bar ──────────────────────────────────────────────
        JPanel bottomBar = new JPanel(new BorderLayout(4, 4));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        JLabel statusLabel = new JLabel("Lade Katalog...");
        bottomBar.add(statusLabel, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton detailsBtn = new JButton("Details...");
        JButton installBtn = new JButton("Installieren");
        detailsBtn.setEnabled(false);
        installBtn.setEnabled(false);
        actionPanel.add(detailsBtn);
        actionPanel.add(installBtn);
        bottomBar.add(actionPanel, BorderLayout.EAST);
        panel.add(bottomBar, BorderLayout.SOUTH);

        // ── Filter ──────────────────────────────────────────────────
        Runnable applyFilter = () -> {
            String query = searchField.getText().trim();
            displayServers.clear();
            List<McpRegistryServerInfo> filtered = McpRegistryApiClient.filterServers(allServers, query);
            for (McpRegistryServerInfo s : filtered) {
                if (!s.isDeleted()) displayServers.add(s);
            }
            model.fireTableDataChanged();
            if (query.isEmpty()) {
                statusLabel.setText(displayServers.size() + " Server im Katalog");
            } else {
                statusLabel.setText(displayServers.size() + " Treffer f\u00FCr \"" + query + "\"");
            }
        };

        // Live search with debounce
        javax.swing.Timer[] filterTimer = {null};
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void schedule() {
                if (filterTimer[0] != null) filterTimer[0].stop();
                filterTimer[0] = new javax.swing.Timer(250, e -> applyFilter.run());
                filterTimer[0].setRepeats(false);
                filterTimer[0].start();
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { schedule(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { schedule(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { schedule(); }
        });

        // ── Table selection ─────────────────────────────────────────
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            boolean sel = row >= 0 && row < displayServers.size();
            detailsBtn.setEnabled(sel);
            installBtn.setEnabled(sel);
            if (sel) {
                McpRegistryServerInfo info = displayServers.get(row);
                installBtn.setText(isInstalled(info, manager) ? "Deinstallieren" : "Installieren");
            }
        });

        detailsBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= displayServers.size()) return;
            showDetailsDialog(dialog, apiClient, displayServers.get(row), manager);
        });

        installBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= displayServers.size()) return;
            McpRegistryServerInfo info = displayServers.get(row);

            if (isInstalled(info, manager)) {
                manager.stopServer(info.getName());
                List<McpServerConfig> configs = manager.loadConfigs();
                configs.removeIf(c -> c.getName().equals(info.getName()));
                manager.saveConfigs(configs);
                installBtn.setText("Installieren");
            } else {
                doInstall(dialog, apiClient, info, manager);
                installBtn.setText("Deinstallieren");
            }
            model.fireTableDataChanged();
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) detailsBtn.doClick();
            }
        });

        // ── Refresh ─────────────────────────────────────────────────
        Runnable loadAll = () -> {
            allServers.clear();
            displayServers.clear();
            model.fireTableDataChanged();
            statusLabel.setText("Lade Katalog...");
            searchField.setEnabled(false);
            refreshBtn.setEnabled(false);

            new Thread(() -> {
                try {
                    List<McpRegistryServerInfo> loaded = apiClient.loadAllServers(
                            (total, page) -> SwingUtilities.invokeLater(
                                    () -> statusLabel.setText("Lade... " + total + " Server (Seite " + page + ")")
                            ));
                    SwingUtilities.invokeLater(() -> {
                        allServers.addAll(loaded);
                        searchField.setEnabled(true);
                        refreshBtn.setEnabled(true);
                        applyFilter.run();
                        searchField.requestFocusInWindow();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Fehler: " + ex.getMessage());
                        searchField.setEnabled(true);
                        refreshBtn.setEnabled(true);
                    });
                }
            }, "mcp-registry-load").start();
        };

        refreshBtn.addActionListener(e -> {
            apiClient.invalidateCache();
            loadAll.run();
        });

        // Initial load
        loadAll.run();

        return panel;
    }

    // ════════════════════════════════════════════════════════════════
    //  Installed tab (inner helper class)
    // ════════════════════════════════════════════════════════════════

    private static class InstalledTab {
        final JPanel panel;
        private final McpServerManager manager;
        private final JDialog dialog;
        private final McpRegistryApiClient apiClient;

        private final List<McpServerConfig> configs = new ArrayList<>();
        private final AbstractTableModel model;
        private final JTable table;

        InstalledTab(McpServerManager manager, JDialog dialog, McpRegistryApiClient apiClient) {
            this.manager = manager;
            this.dialog = dialog;
            this.apiClient = apiClient;

            panel = new JPanel(new BorderLayout(4, 4));

            // ── Info ────────────────────────────────────────────────
            JLabel info = new JLabel(
                    "<html><b>Installierte MCP-Server</b> \u2014 Starten, stoppen oder entfernen.</html>");
            info.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
            panel.add(info, BorderLayout.NORTH);

            // ── Table ───────────────────────────────────────────────
            String[] COLS = {"Name", "Befehl", "Aktiv", "Status"};
            model = new AbstractTableModel() {
                @Override public int getRowCount() { return configs.size(); }
                @Override public int getColumnCount() { return COLS.length; }
                @Override public String getColumnName(int col) { return COLS[col]; }

                @Override
                public Object getValueAt(int row, int col) {
                    McpServerConfig c = configs.get(row);
                    switch (col) {
                        case 0: return McpRegistryApiClient.getShortName(c.getName());
                        case 1: return c.getCommand() + " " + String.join(" ", c.getArgs());
                        case 2: return c.isEnabled() ? "Ja" : "Nein";
                        case 3: return manager.isRunning(c.getName()) ? "\u25CF L\u00E4uft" : "\u25CB Gestoppt";
                        default: return "";
                    }
                }
            };

            table = new JTable(model);
            table.setRowHeight(26);
            table.getColumnModel().getColumn(0).setPreferredWidth(200);
            table.getColumnModel().getColumn(1).setPreferredWidth(300);
            table.getColumnModel().getColumn(2).setPreferredWidth(60);
            table.getColumnModel().getColumn(3).setPreferredWidth(100);

            // Status column coloring
            table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                               boolean focus, int row, int col) {
                    Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                    String v = String.valueOf(value);
                    c.setForeground(v.contains("L\u00E4uft") ? new Color(0, 140, 0) : Color.GRAY);
                    return c;
                }
            });

            panel.add(new JScrollPane(table), BorderLayout.CENTER);

            // ── Buttons ─────────────────────────────────────────────
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
            btnPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

            JButton startBtn = new JButton("Starten");
            JButton stopBtn = new JButton("Stoppen");
            JButton removeBtn = new JButton("Entfernen");
            JButton toggleBtn = new JButton("Aktivieren/Deaktivieren");
            startBtn.setEnabled(false);
            stopBtn.setEnabled(false);
            removeBtn.setEnabled(false);
            toggleBtn.setEnabled(false);
            btnPanel.add(toggleBtn);
            btnPanel.add(startBtn);
            btnPanel.add(stopBtn);
            btnPanel.add(removeBtn);
            panel.add(btnPanel, BorderLayout.SOUTH);

            // ── Selection ───────────────────────────────────────────
            table.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int row = table.getSelectedRow();
                boolean sel = row >= 0 && row < configs.size();
                removeBtn.setEnabled(sel);
                if (sel) {
                    McpServerConfig cfg = configs.get(row);
                    boolean running = manager.isRunning(cfg.getName());
                    startBtn.setEnabled(!running && cfg.isEnabled());
                    stopBtn.setEnabled(running);
                    toggleBtn.setEnabled(true);
                    toggleBtn.setText(cfg.isEnabled() ? "Deaktivieren" : "Aktivieren");
                } else {
                    startBtn.setEnabled(false);
                    stopBtn.setEnabled(false);
                    toggleBtn.setEnabled(false);
                }
            });

            startBtn.addActionListener(e -> {
                int row = table.getSelectedRow();
                if (row < 0) return;
                McpServerConfig cfg = configs.get(row);
                new Thread(() -> {
                    manager.startServer(cfg);
                    SwingUtilities.invokeLater(this::refresh);
                }, "mcp-start-" + cfg.getName()).start();
            });

            stopBtn.addActionListener(e -> {
                int row = table.getSelectedRow();
                if (row < 0) return;
                manager.stopServer(configs.get(row).getName());
                refresh();
            });

            removeBtn.addActionListener(e -> {
                int row = table.getSelectedRow();
                if (row < 0) return;
                McpServerConfig cfg = configs.get(row);
                int confirm = JOptionPane.showConfirmDialog(dialog,
                        "Server \"" + McpRegistryApiClient.getShortName(cfg.getName())
                                + "\" wirklich entfernen?",
                        "Entfernen", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) return;
                manager.stopServer(cfg.getName());
                List<McpServerConfig> all = manager.loadConfigs();
                all.removeIf(c -> c.getName().equals(cfg.getName()));
                manager.saveConfigs(all);
                refresh();
            });

            toggleBtn.addActionListener(e -> {
                int row = table.getSelectedRow();
                if (row < 0) return;
                McpServerConfig cfg = configs.get(row);
                List<McpServerConfig> all = manager.loadConfigs();
                for (McpServerConfig c : all) {
                    if (c.getName().equals(cfg.getName())) {
                        c.setEnabled(!c.isEnabled());
                        if (!c.isEnabled()) manager.stopServer(c.getName());
                    }
                }
                manager.saveConfigs(all);
                refresh();
            });

            refresh();
        }

        void refresh() {
            configs.clear();
            configs.addAll(manager.loadConfigs());
            model.fireTableDataChanged();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Details dialog
    // ════════════════════════════════════════════════════════════════

    private static void showDetailsDialog(Window parent, McpRegistryApiClient apiClient,
                                          McpRegistryServerInfo listInfo, McpServerManager manager) {
        JDialog detail = new JDialog(parent,
                "Details: " + McpRegistryApiClient.getShortName(listInfo.getName()),
                Dialog.ModalityType.APPLICATION_MODAL);
        detail.setLayout(new BorderLayout(8, 8));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel loading = new JLabel("Lade Details...");
        content.add(loading);
        detail.add(new JScrollPane(content), BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton installBtn = new JButton(isInstalled(listInfo, manager) ? "Konfigurieren" : "Installieren");
        JButton closeBtn = new JButton("Schliessen");
        buttonBar.add(installBtn);
        buttonBar.add(closeBtn);
        detail.add(buttonBar, BorderLayout.SOUTH);

        closeBtn.addActionListener(e -> detail.dispose());

        detail.setSize(620, 480);
        detail.setLocationRelativeTo(parent);

        new Thread(() -> {
            try {
                McpRegistryServerInfo info = apiClient.getServerDetails(listInfo.getName());
                SwingUtilities.invokeLater(() -> {
                    content.removeAll();
                    buildDetailContent(content, info);
                    content.revalidate();
                    content.repaint();

                    installBtn.addActionListener(e -> {
                        if (info.isDeprecated()) {
                            int confirm = JOptionPane.showConfirmDialog(detail,
                                    "Dieser Server ist als 'deprecated' markiert.\nTrotzdem installieren?",
                                    "Deprecated", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                            if (confirm != JOptionPane.YES_OPTION) return;
                        }
                        doInstall(detail, apiClient, info, manager);
                        detail.dispose();
                    });
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> loading.setText("Fehler: " + ex.getMessage()));
            }
        }, "mcp-detail-load").start();

        detail.setVisible(true);
    }

    private static void buildDetailContent(JPanel content, McpRegistryServerInfo info) {
        Font boldFont = new Font("Dialog", Font.BOLD, 13);
        Font normalFont = new Font("Dialog", Font.PLAIN, 12);
        Font monoFont = new Font("Monospaced", Font.PLAIN, 12);

        // Short name as title
        String shortName = McpRegistryApiClient.getShortName(info.getName());
        JLabel titleLabel = new JLabel(shortName);
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(2));

        // Full name + publisher
        JLabel nameLabel = new JLabel(info.getName() + "  \u2022  "
                + McpRegistryApiClient.getPublisher(info.getName()));
        nameLabel.setFont(monoFont);
        nameLabel.setForeground(Color.GRAY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(nameLabel);
        content.add(Box.createVerticalStrut(6));

        // Trust badges
        StringBuilder badges = new StringBuilder();
        if (info.isOfficial()) badges.append("\u2605 Offiziell gepr\u00FCft  ");
        if (info.isKnownPublisher()) badges.append("\u2606 Bekannter Herausgeber  ");
        if (badges.length() > 0) {
            JLabel badgeLabel = new JLabel(badges.toString().trim());
            badgeLabel.setFont(normalFont);
            badgeLabel.setForeground(new Color(0, 120, 60));
            badgeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(badgeLabel);
            content.add(Box.createVerticalStrut(4));
        }

        // Status + Version
        String statusText = info.getStatus();
        if (info.getLatestVersion() != null) statusText += "  |  v" + info.getLatestVersion();
        JLabel statusLabel = new JLabel(statusText);
        statusLabel.setFont(normalFont);
        if (info.isDeprecated()) statusLabel.setForeground(new Color(180, 130, 0));
        else if (info.isDeleted()) statusLabel.setForeground(Color.RED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(statusLabel);
        content.add(Box.createVerticalStrut(4));

        // Repository URL
        if (info.getRepositoryUrl() != null && !info.getRepositoryUrl().isEmpty()) {
            JLabel repoLabel = new JLabel("Repository: " + info.getRepositoryUrl());
            repoLabel.setFont(monoFont);
            repoLabel.setForeground(new Color(0, 100, 200));
            repoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(repoLabel);
        }
        content.add(Box.createVerticalStrut(10));

        // Description
        if (!info.getDescription().isEmpty()) {
            JTextArea desc = new JTextArea(info.getDescription());
            desc.setLineWrap(true);
            desc.setWrapStyleWord(true);
            desc.setEditable(false);
            desc.setFont(normalFont);
            desc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(desc);
            content.add(Box.createVerticalStrut(12));
        }

        // Packages
        if (info.hasPackages()) {
            JLabel pkgTitle = new JLabel("Packages (stdio):");
            pkgTitle.setFont(boldFont);
            pkgTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(pkgTitle);
            for (McpRegistryServerInfo.PackageInfo pkg : info.getPackages()) {
                JLabel pkgLabel = new JLabel("  " + pkg.toString());
                pkgLabel.setFont(monoFont);
                pkgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(pkgLabel);
            }
            content.add(Box.createVerticalStrut(8));
        }

        // Remotes
        if (info.hasRemotes()) {
            JLabel remTitle = new JLabel("Remote Endpoints:");
            remTitle.setFont(boldFont);
            remTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(remTitle);
            for (McpRegistryServerInfo.RemoteInfo rem : info.getRemotes()) {
                JLabel remLabel = new JLabel("  " + rem.toString());
                remLabel.setFont(monoFont);
                remLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(remLabel);
            }
            content.add(Box.createVerticalStrut(8));
        }

        // Variables
        if (!info.getVariables().isEmpty()) {
            JLabel varTitle = new JLabel("Variablen:");
            varTitle.setFont(boldFont);
            varTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(varTitle);
            for (McpRegistryServerInfo.VariableInfo v : info.getVariables()) {
                String vText = "  " + v.name
                        + (v.required ? " (required)" : "")
                        + (v.isSecret ? " [secret]" : "")
                        + (v.description != null ? " \u2014 " + v.description : "");
                JLabel vLabel = new JLabel(vText);
                vLabel.setFont(normalFont);
                vLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(vLabel);
            }
            content.add(Box.createVerticalStrut(8));
        }

        // Headers
        if (!info.getHeaders().isEmpty()) {
            JLabel hdrTitle = new JLabel("Headers:");
            hdrTitle.setFont(boldFont);
            hdrTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(hdrTitle);
            for (McpRegistryServerInfo.HeaderInfo h : info.getHeaders()) {
                String hText = "  " + h.name
                        + (h.required ? " (required)" : "")
                        + (h.isSecret ? " [secret]" : "")
                        + (h.description != null ? " \u2014 " + h.description : "");
                JLabel hLabel = new JLabel(hText);
                hLabel.setFont(normalFont);
                hLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(hLabel);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Install flow
    // ════════════════════════════════════════════════════════════════

    private static void doInstall(Window parent, McpRegistryApiClient apiClient,
                                  McpRegistryServerInfo info, McpServerManager manager) {
        McpRegistryServerInfo detail = info;
        if (info.getLatestVersion() == null) {
            try {
                detail = apiClient.getServerDetails(info.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parent,
                        "Details konnten nicht geladen werden:\n" + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        if (detail.isDeprecated()) {
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "Dieser Server ist als 'deprecated' markiert.\nTrotzdem installieren?",
                    "Deprecated", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        if (detail.isDeleted()) {
            JOptionPane.showMessageDialog(parent,
                    "Dieser Server wurde gel\u00F6scht und kann nicht installiert werden.",
                    "Gel\u00F6scht", JOptionPane.ERROR_MESSAGE);
            return;
        }

        McpServerConfig config = showInstallConfigDialog(parent, detail);
        if (config == null) return;

        if (!"remote".equals(config.getCommand())) {
            int trust = JOptionPane.showConfirmDialog(parent,
                    "Sicherheitshinweis:\n\n"
                            + "Server: " + detail.getName() + "\n"
                            + "Befehl: " + config.getCommand() + " "
                            + String.join(" ", config.getArgs()) + "\n\n"
                            + "Dieser Befehl f\u00FChrt lokalen Code aus.\n"
                            + "Vertrauen Sie diesem Server?",
                    "Vertrauensbest\u00E4tigung", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (trust != JOptionPane.YES_OPTION) return;
        }

        List<McpServerConfig> configs = manager.loadConfigs();
        configs.removeIf(c -> c.getName().equals(config.getName()));
        configs.add(config);
        manager.saveConfigs(configs);

        new Thread(() -> manager.startServer(config), "mcp-install-" + config.getName()).start();
    }

    private static McpServerConfig showInstallConfigDialog(Window parent, McpRegistryServerInfo info) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Server-Name:"), gbc);
        JTextField nameField = new JTextField(info.getName(), 30);
        nameField.setEditable(false);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Verbindungsart:"), gbc);

        boolean hasRemote = info.hasRemotes();
        boolean hasPackage = info.hasPackages();
        JComboBox<String> modeBox = new JComboBox<>();
        if (hasRemote) modeBox.addItem("Remote (HTTP/SSE)");
        if (hasPackage) modeBox.addItem("Lokal (stdio)");
        if (!hasRemote && !hasPackage) modeBox.addItem("Manuell konfigurieren");
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(modeBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Befehl:"), gbc);
        JTextField cmdField = new JTextField(30);
        if (hasPackage) {
            McpRegistryServerInfo.PackageInfo pkg = info.getPackages().get(0);
            if ("npm".equals(pkg.registryType)) cmdField.setText("npx");
            else if ("pypi".equals(pkg.registryType)) cmdField.setText("uvx");
        }
        gbc.gridx = 1;
        form.add(cmdField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        form.add(new JLabel("Argumente:"), gbc);
        JTextField argsField = new JTextField(30);
        if (hasPackage) {
            McpRegistryServerInfo.PackageInfo pkg = info.getPackages().get(0);
            argsField.setText(pkg.name != null ? pkg.name : "");
        }
        gbc.gridx = 1;
        form.add(argsField, gbc);

        int varRow = 4;
        for (McpRegistryServerInfo.VariableInfo v : info.getVariables()) {
            gbc.gridx = 0; gbc.gridy = varRow; gbc.weightx = 0;
            form.add(new JLabel(v.name + (v.required ? " *" : "") + ":"), gbc);
            JTextField vf = v.isSecret ? new JPasswordField(30)
                    : new JTextField(v.defaultValue != null ? v.defaultValue : "", 30);
            gbc.gridx = 1;
            form.add(vf, gbc);
            varRow++;
        }

        JCheckBox enabledBox = new JCheckBox("Sofort aktivieren", true);
        gbc.gridx = 0; gbc.gridy = varRow; gbc.gridwidth = 2;
        form.add(enabledBox, gbc);

        Runnable updateFields = () -> {
            String mode = (String) modeBox.getSelectedItem();
            boolean isStdio = mode != null && mode.contains("stdio");
            cmdField.setEnabled(isStdio || (mode != null && mode.contains("Manuell")));
            argsField.setEnabled(isStdio || (mode != null && mode.contains("Manuell")));
        };
        modeBox.addActionListener(e -> updateFields.run());
        updateFields.run();

        int result = JOptionPane.showConfirmDialog(parent, form,
                "MCP Server installieren: " + McpRegistryApiClient.getShortName(info.getName()),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;

        String cmd = cmdField.getText().trim();
        String argsStr = argsField.getText().trim();
        if (cmd.isEmpty() && modeBox.getSelectedItem() != null
                && ((String) modeBox.getSelectedItem()).contains("stdio")) {
            JOptionPane.showMessageDialog(parent,
                    "Befehl ist erforderlich f\u00FCr stdio-Server.",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        List<String> args = new ArrayList<>();
        if (!argsStr.isEmpty()) Collections.addAll(args, argsStr.split("\\s+"));
        if (cmd.isEmpty()) cmd = "echo";

        return new McpServerConfig(info.getName(), cmd, args, enabledBox.isSelected());
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════

    private static boolean isInstalled(McpRegistryServerInfo info, McpServerManager manager) {
        for (McpServerConfig c : manager.loadConfigs()) {
            if (c.getName().equals(info.getName())) return true;
        }
        return false;
    }
}

