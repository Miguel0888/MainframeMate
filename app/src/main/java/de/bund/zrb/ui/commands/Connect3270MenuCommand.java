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
        return "file.tn3270";
    }

    @Override
    public String getLabel() {
        return "3270-Terminal\u2026";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();

        // ── Build connection dialog ──
        JTextField hostField = new JTextField(
                settings.host != null ? settings.host.trim() : "", 25);
        JSpinner portSpinner = new JSpinner(
                new SpinnerNumberModel(settings.tn3270Port, 1, 65535, 1));
        JTextField termTypeField = new JTextField(
                settings.tn3270TermType != null ? settings.tn3270TermType : "IBM-3278-2", 15);
        JCheckBox tlsBox = new JCheckBox("SSL/TLS verwenden", settings.tn3270Tls);

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

        if (result != JOptionPane.OK_OPTION) return;

        // ── Validate ──
        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Host darf nicht leer sein.",
                    "Eingabefehler", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int port = ((Number) portSpinner.getValue()).intValue();
        String termType = termTypeField.getText().trim();
        if (termType.isEmpty()) termType = "IBM-3278-2";
        boolean tls = tlsBox.isSelected();
        int keepAlive = settings.tn3270KeepAliveTimeout;

        // ── Persist last used values ──
        settings.tn3270Port = port;
        settings.tn3270Tls = tls;
        settings.tn3270TermType = termType;
        SettingsHelper.save(settings);

        // ── Connect in background ──
        final String fHost = host;
        final int fPort = port;
        final String fTermType = termType;
        final boolean fTls = tls;
        final int fKeepAlive = keepAlive;

        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<TerminalConnectionTab, Void>() {
            @Override
            protected TerminalConnectionTab doInBackground() throws Exception {
                TerminalConnectionTab tab = new TerminalConnectionTab(
                        fHost, fPort, fTermType, fTls, fKeepAlive);
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
}

