package org.example.ui;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("MainframeMate");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // zentrieren

        initUI();
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

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Host:"));
        panel.add(hostField);
        panel.add(new JLabel("Benutzer:"));
        panel.add(userField);
        panel.add(new JLabel("Passwort:"));
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(this, panel, "FTP-Verbindung herstellen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String host = hostField.getText();
            String user = userField.getText();
            String pass = new String(passField.getPassword());

            // TODO: FTP-Verbindung aufbauen
            JOptionPane.showMessageDialog(this, "Verbunden mit " + host + " (noch ohne echte Verbindung)");
        }
    }
}
