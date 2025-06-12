package de.bund.zrb.ui.lock;

import de.bund.zrb.login.LoginCredentials;
import de.bund.zrb.login.LoginManager;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.function.Predicate;

public class DefaultLocker implements LockerUi{
    private final Frame parentFrame;
    private final LoginManager loginManager;

    public DefaultLocker(Frame parentFrame, LoginManager loginManager) {
        this.parentFrame = parentFrame;
        this.loginManager = loginManager;
    }

    @Override
    public LoginCredentials init() {
        JTextField hostField = new JTextField(20);
        JTextField userField = new JTextField(20);
        JPasswordField passField = new JPasswordField(20);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Host:"));
        panel.add(hostField);
        panel.add(new JLabel("Benutzer:"));
        panel.add(userField);
        panel.add(new JLabel("Passwort:"));
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(parentFrame, panel, "Anmeldung erforderlich",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return new LoginCredentials(
                    hostField.getText().trim(),
                    userField.getText().trim(),
                    new String(passField.getPassword())
            );
        }

        return null;
    }

    @Override
    public LoginCredentials logOn(LoginCredentials loginCredentials) {
        JPasswordField passField = new JPasswordField(20);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("🔒 Passwort für " + loginCredentials.getUsername() +
                "@" + loginCredentials.getHost() + ":"));
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(parentFrame, panel, "Passwort eingeben",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return new LoginCredentials(
                    loginCredentials.getHost(),
                    loginCredentials.getUsername(),
                    new String(passField.getPassword())
            );
        }

        return null;
    }

    @Override
    public void lock(Predicate<char[]> passwordValidator) {
        final JDialog lockDialog = new JDialog(parentFrame, "Sperre", true);
        lockDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        lockDialog.setUndecorated(true);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(Color.BLACK);

        JLabel label = new JLabel("🔒 Zugriff gesperrt – Passwort eingeben:");
        label.setForeground(Color.WHITE);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));

        final JPasswordField passField = new JPasswordField(20);
        passField.setPreferredSize(new Dimension(200, 24));
        passField.setFont(passField.getFont().deriveFont(Font.PLAIN, 16f));
        passField.setForeground(Color.WHITE);
        passField.setBackground(Color.DARK_GRAY);
        passField.setCaretColor(Color.WHITE);
        passField.setBorder(BorderFactory.createLineBorder(Color.ORANGE, 2, true));

        JButton unlock = new JButton("Entsperren");
        unlock.setFocusPainted(false);

        unlock.addActionListener(e -> {
            char[] input = passField.getPassword();
            if (passwordValidator.test(input)) {
                Arrays.fill(input, '\0');
                lockDialog.dispose();
            } else {
                passField.setText("");
                Arrays.fill(input, '\0');
            }
        });

        lockDialog.getRootPane().setDefaultButton(unlock);

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(Color.BLACK);
        centerPanel.add(label, BorderLayout.NORTH);
        centerPanel.add(passField, BorderLayout.CENTER);
        centerPanel.add(unlock, BorderLayout.SOUTH);

        panel.add(centerPanel, BorderLayout.CENTER);
        lockDialog.setContentPane(panel);
        lockDialog.setSize(360, 160);
        lockDialog.setLocationRelativeTo(parentFrame);
        lockDialog.setVisible(true);
    }
}
