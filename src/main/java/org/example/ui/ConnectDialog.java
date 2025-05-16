package org.example.ui;

import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class ConnectDialog {

    public static boolean connectIfNeeded(Component parent, FtpManager ftpManager) {
        Settings settings = SettingsManager.load();

        if (settings.autoConnect && settings.host != null && settings.user != null) {
            return show(parent, ftpManager, settings);
        }

        return show(parent, ftpManager, settings);
    }

    public static boolean show(Component parent, FtpManager ftpManager) {
        return show(parent, ftpManager, SettingsManager.load());
    }

    private static boolean show(Component parent, FtpManager ftpManager, Settings settings) {
        JTextField hostField = new JTextField(settings.host != null ? settings.host : "");
        JTextField userField = new JTextField(settings.user != null ? settings.user : "");
        JPasswordField passField = new JPasswordField();
        JCheckBox autoConnectBox = new JCheckBox("Automatisch verbinden");
        autoConnectBox.setSelected(settings.autoConnect);

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
                ftpManager.connect(host, user, pass);
                settings.host = host;
                settings.user = user;
                settings.autoConnect = autoConnect;
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
