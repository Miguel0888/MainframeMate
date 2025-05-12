package org.example.ui;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Properties;

public class MainFrame extends JFrame {

    private static final File SETTINGS_FILE = new File(System.getProperty("user.home"), ".mainframemate/settings.properties");

    public MainFrame() {
        setTitle("MainframeMate");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // zentrieren

        initUI();

        Properties settings = loadSettings();
        if (Boolean.parseBoolean(settings.getProperty("autoConnect", "false"))) {
            String host = settings.getProperty("host");
            String user = settings.getProperty("user");
            // Passwort absichtlich nicht gespeichert – sicherheitsbewusst
            showConnectDialog();
        }
    }

    private void initUI() {
        // Menüleiste
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Datei");
        JMenuItem connectItem = new JMenuItem("Verbinden...");
        connectItem.addActionListener(e -> showConnectDialog());
        fileMenu.add(connectItem);

        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // Hauptinhalt (Platzhalter für FTP-Dateiansicht)
        JPanel content = new JPanel(new BorderLayout());
        JLabel placeholder = new JLabel("Willkommen bei MainframeMate", SwingConstants.CENTER);
        content.add(placeholder, BorderLayout.CENTER);
        add(content);
    }

    private void showConnectDialog() {
        JTextField hostField = new JTextField();
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        JCheckBox autoConnectBox = new JCheckBox("Automatisch verbinden");

        // 📁 Button neben Checkbox
        JButton openFolderButton = new JButton("\uD83D\uDCC1"); // Unicode-Folder 📁
        openFolderButton.setToolTipText("Einstellungsordner öffnen");
        openFolderButton.setMargin(new Insets(0, 5, 0, 5));
        openFolderButton.setFocusable(false);
        openFolderButton.addActionListener(e -> openSettingsFolder());

        // Checkbox + Button in ein Panel
        JPanel autoConnectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        autoConnectPanel.add(autoConnectBox);
        autoConnectPanel.add(openFolderButton);

        // Vorbelegung aus Datei laden
        Properties settings = loadSettings();
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

        int result = JOptionPane.showConfirmDialog(this, panel, "FTP-Verbindung herstellen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String host = hostField.getText();
            String user = userField.getText();
            String pass = new String(passField.getPassword());
            boolean autoConnect = autoConnectBox.isSelected();

            // TODO: FTP-Verbindung aufbauen
            JOptionPane.showMessageDialog(this, "Verbunden mit " + host + " (noch ohne echte Verbindung)");

            // Einstellungen speichern
            settings.setProperty("host", host);
            settings.setProperty("user", user);
            settings.setProperty("autoConnect", String.valueOf(autoConnect));
            saveSettings(settings);
        }
    }

    private Properties loadSettings() {
        Properties props = new Properties();
        if (SETTINGS_FILE.exists()) {
            try (InputStream in = new FileInputStream(SETTINGS_FILE)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }

    private void saveSettings(Properties props) {
        try {
            SETTINGS_FILE.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(SETTINGS_FILE)) {
                props.store(out, "MainframeMate Einstellungen");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openSettingsFolder() {
        try {
            Desktop.getDesktop().open(SETTINGS_FILE.getParentFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ordner konnte nicht geöffnet werden:\n" + e.getMessage());
        }
    }

}
