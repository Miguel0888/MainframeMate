package org.example.ui;

import org.example.ftp.FtpService;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Properties;

public class ConnectDialog {

    public static boolean show(Component parent, FtpService ftpService) {
        JTextField hostField = new JTextField();
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        JCheckBox autoConnectBox = new JCheckBox("Automatisch verbinden");

        JButton openFolderButton = new JButton("\uD83D\uDCC1");
        openFolderButton.setToolTipText("Einstellungsordner öffnen");
        openFolderButton.setMargin(new Insets(0, 5, 0, 5));
        openFolderButton.setFocusable(false);
        openFolderButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(SettingsManager.getSettingsFolder());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Ordner konnte nicht geöffnet werden:\n" + ex.getMessage());
            }
        });

        JPanel autoConnectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        autoConnectPanel.add(autoConnectBox);
        autoConnectPanel.add(openFolderButton);

        Properties settings = SettingsManager.load();
        hostField.setText(settings.getProperty("host", ""));
        userField.setText(settings.getProperty("user", ""));
        autoConnectBox.setSelected(Boolean.parseBoolean(settings.getProperty("autoConnect", "false")));

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Host:"));
        panel.add(hostField);
        panel.add(new JLabel("Benutzer:"));
        panel.add(userField);
        panel.add(new JLabel("Passwort:"));
        panel.add(passField);
        panel.add(autoConnectPanel);

        int result = JOptionPane.showConfirmDialog(parent, panel, "FTP-Verbindung herstellen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String host = hostField.getText();
            String user = userField.getText();
            String pass = new String(passField.getPassword());
            boolean autoConnect = autoConnectBox.isSelected();

            try {
                ftpService.connect(host, user, pass);
                // Nur speichern, wenn Verbindung erfolgreich war
                settings.setProperty("host", host);
                settings.setProperty("user", user);
                settings.setProperty("autoConnect", String.valueOf(autoConnect));
                SettingsManager.save(settings);
                return true;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Verbindung fehlgeschlagen:\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }

        return false;
    }
}
