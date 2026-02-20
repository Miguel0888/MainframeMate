package de.bund.zrb.ui.commands;

import de.bund.zrb.ndv.NdvClient;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.NdvConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;

/**
 * Menu command "NDV-Verbindung..." to connect to a Natural Development Server.
 */
public class ConnectNdvMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public ConnectNdvMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.ndv";
    }

    @Override
    public String getLabel() {
        return "NDV-Verbindung...";
    }

    @Override
    public void perform() {
        // Show connection dialog
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField hostField = new JTextField(20);
        JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(8011, 1, 65535, 1));
        JTextField userField = new JTextField(20);
        JPasswordField passField = new JPasswordField(20);
        JTextField libraryField = new JTextField(20);

        // Pre-fill from last known settings if available
        hostField.setText("");
        userField.setText("");

        // Row 0: Host
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("Host:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(hostField, gbc);

        // Row 1: Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(portSpinner, gbc);

        // Row 2: User
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("Benutzer:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(userField, gbc);

        // Row 3: Password
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(passField, gbc);

        // Row 4: Library (optional)
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        panel.add(new JLabel("Bibliothek (optional):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(libraryField, gbc);

        // Row 5: Info
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("<html><small>Verbindet zum Natural Development Server (NATSPOD-Protokoll).</small></html>");
        infoLabel.setForeground(Color.GRAY);
        panel.add(infoLabel, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "NDV-Verbindung herstellen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String host = hostField.getText().trim();
        int port = (Integer) portSpinner.getValue();
        String user = userField.getText().trim();
        String password = new String(passField.getPassword());
        String library = libraryField.getText().trim();

        // Validate
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Bitte Host angeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (user.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Bitte Benutzer angeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Bitte Passwort angeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Connect in background
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<NdvConnectionTab, Void> worker = new SwingWorker<NdvConnectionTab, Void>() {
            @Override
            protected NdvConnectionTab doInBackground() throws Exception {
                NdvClient client = new NdvClient();
                client.connect(host, port, user, password);

                // Logon to library if specified
                if (!library.isEmpty()) {
                    client.logon(library.toUpperCase());
                }

                return new NdvConnectionTab(tabManager, client);
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    NdvConnectionTab tab = get();
                    tabManager.addTab(tab);
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg = e.getCause().getMessage();
                    }
                    JOptionPane.showMessageDialog(parent,
                            "NDV-Verbindung fehlgeschlagen:\n" + msg,
                            "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                    System.err.println("[ConnectNdvMenuCommand] Connection failed: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }
}

