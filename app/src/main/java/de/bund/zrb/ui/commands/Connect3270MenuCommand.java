package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.terminal.TerminalConnectionTab;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;

/**
 * Menu command "3270-Terminal…" – opens a TN3270 terminal as a new ConnectionTab.
 * Shows a connection dialog for host/port/termType/TLS before connecting.
 */
public class Connect3270MenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public Connect3270MenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.tn3270";
    }

    @Override
    public String getLabel() {
        return "3270-Terminal\u2026";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();

        // Check if all data is already available – skip dialog if so
        String host = settings.host != null ? settings.host.trim() : "";
        String user = settings.user != null ? settings.user.trim() : "";
        String cachedPassword = !host.isEmpty() && !user.isEmpty()
                ? de.bund.zrb.login.LoginManager.getInstance().getCachedPassword(host, user)
                : null;
        boolean allDataPresent = !host.isEmpty()
                && !user.isEmpty()
                && cachedPassword != null
                && settings.tn3270Port > 0
                && settings.tn3270TermType != null && !settings.tn3270TermType.isEmpty();

        int port;
        String termType;
        boolean tls;

        if (allDataPresent) {
            // All data available – connect directly, no dialog needed
            port = settings.tn3270Port;
            termType = settings.tn3270TermType;
            tls = settings.tn3270Tls;
        } else {
            // Show dialog for missing data
            port = showConnectionDialog(settings);
            if (port < 0) return; // cancelled

            host = dialogHost;
            termType = dialogTermType;
            tls = dialogTls;
            user = settings.user != null ? settings.user.trim() : "";
            cachedPassword = !host.isEmpty() && !user.isEmpty()
                    ? de.bund.zrb.login.LoginManager.getInstance().getCachedPassword(host, user)
                    : null;
        }

        int keepAlive = settings.tn3270KeepAliveTimeout;

        // ── Connect in background ──
        final String fHost = host;
        final int fPort = port;
        final String fTermType = termType;
        final boolean fTls = tls;
        final int fKeepAlive = keepAlive;
        final String fUser = user;
        final String fPassword = cachedPassword;

        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<TerminalConnectionTab, Void>() {
            @Override
            protected TerminalConnectionTab doInBackground() throws Exception {
                TerminalConnectionTab tab = new TerminalConnectionTab(
                        fHost, fPort, fTermType, fTls, fKeepAlive, fUser, fPassword);
                tab.connect();
                return tab;
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    TerminalConnectionTab tab = get();
                    tabManager.addTab(tab);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String msg = cause.getMessage();

                    JOptionPane.showMessageDialog(parent,
                            "3270-Verbindung fehlgeschlagen:\n" + msg,
                            "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                    System.err.println("[Connect3270] Connection failed: " + msg);
                }
            }
        }.execute();
    }

    // Temporary fields to pass dialog results back (avoid complex return types in Java 8)
    private String dialogHost;
    private String dialogTermType;
    private boolean dialogTls;

    /**
     * Show the connection dialog. Returns the port number, or -1 if cancelled.
     * Stores host, termType, tls in temporary fields.
     */
    private int showConnectionDialog(Settings settings) {
        String hostValue = settings.host != null ? settings.host.trim() : "";

        JTextField hostField = new JTextField(hostValue, 25);
        final JSpinner portSpinner = new JSpinner(
                new SpinnerNumberModel(settings.tn3270Port, 1, 65535, 1));
        JTextField termTypeField = new JTextField(
                settings.tn3270TermType != null ? settings.tn3270TermType : "IBM-3278-2", 15);
        JCheckBox tlsBox = new JCheckBox("SSL/TLS verwenden", settings.tn3270Tls);

        // Automatically switch port when TLS checkbox changes
        tlsBox.addActionListener(e -> {
            int currentPort = ((Number) portSpinner.getValue()).intValue();
            if (tlsBox.isSelected()) {
                if (currentPort == 23) portSpinner.setValue(992);
            } else {
                if (currentPort == 992) portSpinner.setValue(23);
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Host:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(portSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Terminal-Typ:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(termTypeField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(tlsBox, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "3270-Terminal verbinden", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return -1;

        dialogHost = hostField.getText().trim();
        if (dialogHost.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Host darf nicht leer sein.",
                    "Eingabefehler", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        int port = ((Number) portSpinner.getValue()).intValue();
        dialogTermType = termTypeField.getText().trim();
        if (dialogTermType.isEmpty()) dialogTermType = "IBM-3278-2";
        dialogTls = tlsBox.isSelected();

        // Persist values
        settings.tn3270Port = port;
        settings.tn3270Tls = dialogTls;
        settings.tn3270TermType = dialogTermType;
        SettingsHelper.save(settings);

        return port;
    }
}
