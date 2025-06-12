package de.bund.zrb.ui.lock;

import de.bund.zrb.login.LoginCredentials;
import de.bund.zrb.login.LoginManager;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.function.Predicate;

public class SwingLocker implements LockerUi {
    private final Frame parentFrame;
    private final LoginManager loginManager;

    public SwingLocker(Frame parentFrame, LoginManager loginManager) {
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

        int result = JOptionPane.showConfirmDialog(
                parentFrame,
                panel,
                "Anmeldung erforderlich",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

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
        panel.add(new JLabel("🔒 Passwort für " + loginCredentials.getUsername() + "@" + loginCredentials.getHost() + ":"));
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(
                parentFrame,
                panel,
                "Passwort eingeben",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

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
        JPasswordField passField = new JPasswordField(20);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("🔒 Zugriff gesperrt – Passwort eingeben:"));
        panel.add(passField);

        while (true) {
            int result = JOptionPane.showConfirmDialog(
                    parentFrame,
                    panel,
                    "Sperre",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (result != JOptionPane.OK_OPTION) {
                break;
            }

            char[] input = passField.getPassword();
            boolean valid = passwordValidator.test(input);
            Arrays.fill(input, '\0');

            if (valid) {
                break;
            } else {
                passField.setText("");
            }
        }
    }
}
