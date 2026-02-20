package de.bund.zrb.ui.settings;

import de.bund.zrb.mcp.registry.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * "App Store"-style dialog for browsing the MCP Registry,
 * viewing server details, and installing/enabling MCP servers.
 */
public class McpRegistryBrowserDialog {

    public static void show(Component parent) {
        McpRegistrySettings settings = McpRegistrySettings.load();
        McpRegistryApiClient apiClient = new McpRegistryApiClient(settings);
        McpServerManager manager = McpServerManager.getInstance();

        JDialog dialog = new JDialog(
                parent instanceof Frame ? (Frame) parent : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent),
                "MCP Registry Browser", true);
        dialog.setLayout(new BorderLayout(4, 4));

        // ── Top bar: search + refresh + settings ────────────────────
        JPanel topBar = new JPanel(new BorderLayout(4, 4));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JTextField searchField = new JTextField(20);
        searchField.setToolTipText("Server suchen...");
        JButton searchBtn = new JButton("Suchen");
        JButton refreshBtn = new JButton("\u21BB");
        refreshBtn.setToolTipText("Cache leeren & neu laden");

        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.add(new JLabel("Suche: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtons.add(searchBtn);
        rightButtons.add(refreshBtn);

        topBar.add(searchPanel, BorderLayout.CENTER);
        topBar.add(rightButtons, BorderLayout.EAST);
        dialog.add(topBar, BorderLayout.NORTH);

        // ── Server list table ───────────────────────────────────────
        List<McpRegistryServerInfo> servers = new ArrayList<>();

        String[] COLUMNS = {"Name", "Beschreibung", "Status", "Aktion"};
        AbstractTableModel model = new AbstractTableModel() {
            @Override public int getRowCount() { return servers.size(); }
            @Override public int getColumnCount() { return COLUMNS.length; }
            @Override public String getColumnName(int col) { return COLUMNS[col]; }
            @Override public boolean isCellEditable(int r, int c) { return false; }

            @Override
            public Object getValueAt(int row, int col) {
                McpRegistryServerInfo info = servers.get(row);
                switch (col) {
                    case 0: {
                        String prefix = "";
                        if (info.isOfficial() && info.isKnownPublisher()) prefix = "\u2605 "; // ★
                        else if (info.isKnownPublisher()) prefix = "\u2606 "; // ☆
                        else if (info.isOfficial()) prefix = "\u2713 "; // ✓
                        return prefix + info.getTitle();
                    }
                    case 1: {
                        String d = info.getDescription();
                        return d.length() > 80 ? d.substring(0, 77) + "..." : d;
                    }
                    case 2: {
                        String s = info.getStatus();
                        if ("deprecated".equalsIgnoreCase(s)) return "\u26A0 deprecated";
                        if ("deleted".equalsIgnoreCase(s)) return "\u274C deleted";
                        return "\u2713 active";
                    }
                    case 3: return isInstalled(info, manager) ? "Deinstallieren" : "Installieren";
                    default: return "";
                }
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(350);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);

        // Status column coloring
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                String v = String.valueOf(value);
                if (v.contains("deprecated")) c.setForeground(new Color(180, 130, 0));
                else if (v.contains("deleted")) c.setForeground(Color.RED);
                else c.setForeground(new Color(0, 140, 0));
                return c;
            }
        });

        // Action column as clickable link style
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                c.setForeground(new Color(0, 100, 200));
                if (c instanceof JLabel) ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // ── Bottom bar: pagination + details ────────────────────────
        JPanel bottomBar = new JPanel(new BorderLayout(4, 4));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton prevBtn = new JButton("\u25C0 Zur\u00FCck");
        JButton nextBtn = new JButton("Weiter \u25B6");
        JLabel pageLabel = new JLabel(" ");
        prevBtn.setEnabled(false); // first page
        nextBtn.setEnabled(false);
        navPanel.add(prevBtn);
        navPanel.add(nextBtn);
        navPanel.add(pageLabel);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton detailsBtn = new JButton("Details...");
        JButton installBtn = new JButton("Installieren");
        detailsBtn.setEnabled(false);
        installBtn.setEnabled(false);
        actionPanel.add(detailsBtn);
        actionPanel.add(installBtn);

        bottomBar.add(navPanel, BorderLayout.WEST);
        bottomBar.add(actionPanel, BorderLayout.EAST);
        dialog.add(bottomBar, BorderLayout.SOUTH);

        // ── State ───────────────────────────────────────────────────
        // cursorStack holds the cursors used to load previous pages (for back-navigation)
        // cursorHolder[0] = cursor for the CURRENT page (null = first page)
        // nextCursorHolder[0] = cursor returned by API for the NEXT page
        List<String> cursorStack = new ArrayList<>();
        String[] cursorHolder = {null};
        String[] nextCursorHolder = {null};
        boolean[] showDeleted = {false};

        // ── Load function ───────────────────────────────────────────
        Runnable[] loadFn = new Runnable[1];
        loadFn[0] = () -> {
            pageLabel.setText("Lade...");
            searchBtn.setEnabled(false);

            String query = searchField.getText().trim();
            String cursor = cursorHolder[0];

            new Thread(() -> {
                try {
                    McpRegistryApiClient.ListResult result = apiClient.listServers(
                            query.isEmpty() ? null : query, cursor, 50);
                    SwingUtilities.invokeLater(() -> {
                        servers.clear();
                        for (McpRegistryServerInfo s : result.servers) {
                            if (!showDeleted[0] && s.isDeleted()) continue;
                            servers.add(s);
                        }
                        model.fireTableDataChanged();
                        nextCursorHolder[0] = result.nextCursor;
                        nextBtn.setEnabled(result.nextCursor != null);
                        prevBtn.setEnabled(!cursorStack.isEmpty());
                        int page = cursorStack.size() + 1;
                        pageLabel.setText("Seite " + page + " \u2014 " + servers.size() + " Server");
                        searchBtn.setEnabled(true);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        pageLabel.setText("Fehler: " + e.getMessage());
                        searchBtn.setEnabled(true);
                    });
                }
            }, "mcp-registry-load").start();
        };

        // ── Actions ─────────────────────────────────────────────────
        searchBtn.addActionListener(e -> {
            cursorStack.clear();
            cursorHolder[0] = null;
            nextCursorHolder[0] = null;
            loadFn[0].run();
        });

        searchField.addActionListener(e -> searchBtn.doClick());

        refreshBtn.addActionListener(e -> {
            apiClient.invalidateCache();
            cursorStack.clear();
            cursorHolder[0] = null;
            nextCursorHolder[0] = null;
            loadFn[0].run();
        });

        nextBtn.addActionListener(e -> {
            if (nextCursorHolder[0] != null) {
                // Save current page cursor so we can go back
                cursorStack.add(cursorHolder[0]);
                cursorHolder[0] = nextCursorHolder[0];
                loadFn[0].run();
            }
        });

        prevBtn.addActionListener(e -> {
            if (!cursorStack.isEmpty()) {
                // Pop the previous page cursor
                cursorHolder[0] = cursorStack.remove(cursorStack.size() - 1);
                loadFn[0].run();
            }
        });


        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            boolean sel = row >= 0;
            detailsBtn.setEnabled(sel);
            installBtn.setEnabled(sel);
            if (sel) {
                McpRegistryServerInfo info = servers.get(table.convertRowIndexToModel(row));
                installBtn.setText(isInstalled(info, manager) ? "Deinstallieren" : "Installieren");
            }
        });

        detailsBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            McpRegistryServerInfo info = servers.get(table.convertRowIndexToModel(row));
            showDetailsDialog(dialog, apiClient, info, manager);
        });

        installBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            McpRegistryServerInfo info = servers.get(table.convertRowIndexToModel(row));

            if (isInstalled(info, manager)) {
                // Uninstall
                manager.stopServer(info.getName());
                List<McpServerConfig> configs = manager.loadConfigs();
                configs.removeIf(c -> c.getName().equals(info.getName()));
                manager.saveConfigs(configs);
                installBtn.setText("Installieren");
                model.fireTableDataChanged();
            } else {
                // Install flow
                doInstall(dialog, apiClient, info, manager);
                installBtn.setText("Deinstallieren");
                model.fireTableDataChanged();
            }
        });

        // Table double-click → details
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) detailsBtn.doClick();
            }
        });

        // ── Initial load ────────────────────────────────────────────
        dialog.setSize(850, 520);
        dialog.setLocationRelativeTo(parent);
        loadFn[0].run();
        dialog.setVisible(true);
    }

    // ── Details dialog ──────────────────────────────────────────────

    private static void showDetailsDialog(Window parent, McpRegistryApiClient apiClient,
                                          McpRegistryServerInfo listInfo, McpServerManager manager) {
        JDialog detail = new JDialog(parent, "Details: " + listInfo.getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
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

        detail.setSize(600, 450);
        detail.setLocationRelativeTo(parent);

        // Load details async
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
                SwingUtilities.invokeLater(() -> {
                    loading.setText("Fehler: " + ex.getMessage());
                });
            }
        }, "mcp-detail-load").start();

        detail.setVisible(true);
    }

    private static void buildDetailContent(JPanel content, McpRegistryServerInfo info) {
        Font boldFont = new Font("Dialog", Font.BOLD, 13);
        Font normalFont = new Font("Dialog", Font.PLAIN, 12);
        Font monoFont = new Font("Monospaced", Font.PLAIN, 12);

        // Name / Title
        JLabel titleLabel = new JLabel(info.getTitle());
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(4));

        // Name (technical)
        if (!info.getTitle().equals(info.getName())) {
            JLabel nameLabel = new JLabel(info.getName());
            nameLabel.setFont(monoFont);
            nameLabel.setForeground(Color.GRAY);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(nameLabel);
            content.add(Box.createVerticalStrut(4));
        }

        // Status + Version
        String statusText = info.getStatus();
        if (info.getLatestVersion() != null) statusText += "  |  Version: " + info.getLatestVersion();
        JLabel statusLabel = new JLabel(statusText);
        statusLabel.setFont(normalFont);
        if (info.isDeprecated()) statusLabel.setForeground(new Color(180, 130, 0));
        else if (info.isDeleted()) statusLabel.setForeground(Color.RED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(statusLabel);
        content.add(Box.createVerticalStrut(4));

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

        // Repository URL
        if (info.getRepositoryUrl() != null && !info.getRepositoryUrl().isEmpty()) {
            JLabel repoLabel = new JLabel("Repository: " + info.getRepositoryUrl());
            repoLabel.setFont(monoFont);
            repoLabel.setForeground(new Color(0, 100, 200));
            repoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(repoLabel);
        }
        content.add(Box.createVerticalStrut(8));

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
                        + (v.description != null ? " - " + v.description : "");
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
                        + (h.description != null ? " - " + h.description : "");
                JLabel hLabel = new JLabel(hText);
                hLabel.setFont(normalFont);
                hLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(hLabel);
            }
        }
    }

    // ── Install flow ────────────────────────────────────────────────

    private static void doInstall(Window parent, McpRegistryApiClient apiClient,
                                  McpRegistryServerInfo info, McpServerManager manager) {
        // Load full details if we only have list info
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

        // Deprecated warning
        if (detail.isDeprecated()) {
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "Dieser Server ist als 'deprecated' markiert.\nTrotzdem installieren?",
                    "Deprecated", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        // Deleted → block
        if (detail.isDeleted()) {
            JOptionPane.showMessageDialog(parent,
                    "Dieser Server wurde geloescht und kann nicht installiert werden.",
                    "Geloescht", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Build install config
        McpServerConfig config = showInstallConfigDialog(parent, detail);
        if (config == null) return; // cancelled

        // Security UX for stdio
        if (!"remote".equals(config.getCommand())) {
            int trust = JOptionPane.showConfirmDialog(parent,
                    "Sicherheitshinweis:\n\n"
                            + "Server: " + detail.getName() + "\n"
                            + "Befehl: " + config.getCommand() + " " + String.join(" ", config.getArgs()) + "\n\n"
                            + "Dieser Befehl fuehrt lokalen Code aus.\n"
                            + "Vertrauen Sie diesem Server?",
                    "Vertrauensbestaetigung", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (trust != JOptionPane.YES_OPTION) return;
        }

        // Save and start
        List<McpServerConfig> configs = manager.loadConfigs();
        configs.removeIf(c -> c.getName().equals(config.getName()));
        configs.add(config);
        manager.saveConfigs(configs);

        new Thread(() -> {
            manager.startServer(config);
        }, "mcp-install-" + config.getName()).start();
    }

    private static McpServerConfig showInstallConfigDialog(Window parent, McpRegistryServerInfo info) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Server-Name:"), gbc);
        JTextField nameField = new JTextField(info.getName(), 30);
        nameField.setEditable(false);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(nameField, gbc);

        // Mode selection
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Verbindungsart:"), gbc);

        boolean hasRemote = info.hasRemotes();
        boolean hasPackage = info.hasPackages();
        JComboBox<String> modeBox = new JComboBox<>();
        if (hasRemote) modeBox.addItem("Remote (HTTP/SSE)");
        if (hasPackage) modeBox.addItem("Lokal (stdio)");
        if (!hasRemote && !hasPackage) modeBox.addItem("Manuell konfigurieren");
        // Default: remote if available
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(modeBox, gbc);

        // Command (for stdio)
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Befehl:"), gbc);
        JTextField cmdField = new JTextField(30);
        if (hasPackage) {
            McpRegistryServerInfo.PackageInfo pkg = info.getPackages().get(0);
            if ("npm".equals(pkg.registryType)) {
                cmdField.setText("npx");
            } else if ("pypi".equals(pkg.registryType)) {
                cmdField.setText("uvx");
            } else {
                cmdField.setText("");
            }
        }
        gbc.gridx = 1;
        form.add(cmdField, gbc);

        // Args
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        form.add(new JLabel("Argumente:"), gbc);
        JTextField argsField = new JTextField(30);
        if (hasPackage) {
            McpRegistryServerInfo.PackageInfo pkg = info.getPackages().get(0);
            argsField.setText(pkg.name != null ? pkg.name : "");
        }
        gbc.gridx = 1;
        form.add(argsField, gbc);

        // Variables (dynamic fields)
        int varRow = 4;
        List<JTextField> varFields = new ArrayList<>();
        for (McpRegistryServerInfo.VariableInfo v : info.getVariables()) {
            gbc.gridx = 0; gbc.gridy = varRow; gbc.weightx = 0;
            String label = v.name + (v.required ? " *" : "") + ":";
            form.add(new JLabel(label), gbc);

            JTextField vf;
            if (v.choices != null && !v.choices.isEmpty()) {
                vf = new JTextField(v.choices.get(0), 30);
            } else {
                vf = v.isSecret ? new JPasswordField(30) : new JTextField(v.defaultValue != null ? v.defaultValue : "", 30);
            }
            gbc.gridx = 1;
            form.add(vf, gbc);
            varFields.add(vf);
            varRow++;
        }

        // Enable toggle
        JCheckBox enabledBox = new JCheckBox("Sofort aktivieren", true);
        gbc.gridx = 0; gbc.gridy = varRow; gbc.gridwidth = 2;
        form.add(enabledBox, gbc);

        // Toggle command/args visibility based on mode
        Runnable updateFields = () -> {
            String mode = (String) modeBox.getSelectedItem();
            boolean isStdio = mode != null && mode.contains("stdio");
            cmdField.setEnabled(isStdio || (mode != null && mode.contains("Manuell")));
            argsField.setEnabled(isStdio || (mode != null && mode.contains("Manuell")));
        };
        modeBox.addActionListener(e -> updateFields.run());
        updateFields.run();

        int result = JOptionPane.showConfirmDialog(parent, form,
                "MCP Server installieren: " + info.getTitle(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String cmd = cmdField.getText().trim();
        String argsStr = argsField.getText().trim();
        if (cmd.isEmpty() && modeBox.getSelectedItem() != null
                && ((String) modeBox.getSelectedItem()).contains("stdio")) {
            JOptionPane.showMessageDialog(parent, "Befehl ist erforderlich fuer stdio-Server.",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        // Build env from variables
        List<String> args = new ArrayList<>();
        if (!argsStr.isEmpty()) {
            for (String a : argsStr.split("\\s+")) args.add(a);
        }

        if (cmd.isEmpty()) cmd = "echo"; // placeholder for remote-only

        return new McpServerConfig(info.getName(), cmd, args, enabledBox.isSelected());
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static boolean isInstalled(McpRegistryServerInfo info, McpServerManager manager) {
        for (McpServerConfig c : manager.loadConfigs()) {
            if (c.getName().equals(info.getName())) return true;
        }
        return false;
    }
}

