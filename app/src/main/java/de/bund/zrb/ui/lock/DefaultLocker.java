package de.bund.zrb.ui.lock;

import de.bund.zrb.login.LoginCredentials;
import de.bund.zrb.login.LoginManager;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.function.Predicate;

public class DefaultLocker implements LockerUi {
    private final Frame parentFrame;
    private final LoginManager loginManager;

    public DefaultLocker(Frame parentFrame, LoginManager loginManager) {
        this.parentFrame = parentFrame;
        this.loginManager = loginManager;
    }

    @Override
    public LoginCredentials init() {
        final JDialog dialog = createDialog("Anmeldung erforderlich");

        JTextField hostField = styledTextField();
        JTextField userField = styledTextField();
        JPasswordField passField = styledPasswordField();

        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.setBackground(Color.BLACK);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        panel.add(label("Host:"));
        panel.add(hostField);
        panel.add(label("Benutzer:"));
        panel.add(userField);
        panel.add(label("Passwort:"));
        panel.add(passField);

        JButton ok = styledButton("OK");
        JButton cancel = styledButton("Abbrechen");

        final boolean[] confirmed = {false};

        ok.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });

        cancel.addActionListener(e -> dialog.dispose());

        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.BLACK);
        buttonPanel.add(cancel);
        buttonPanel.add(ok);

        dialog.setContentPane(wrap(panel, buttonPanel));
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);

        if (!confirmed[0]) return null;

        return new LoginCredentials(
                hostField.getText().trim(),
                userField.getText().trim(),
                new String(passField.getPassword())
        );
    }

    @Override
    public LoginCredentials logOn(LoginCredentials loginCredentials) {
        final JDialog dialog = createDialog("Passwort erforderlich");

        JPasswordField passField = styledPasswordField();
        JLabel label = label("🔒 Passwort für " + loginCredentials.getUsername() + "@" + loginCredentials.getHost() + ":");

        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.setBackground(Color.BLACK);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(label);
        panel.add(passField);

        JButton ok = styledButton("OK");
        JButton cancel = styledButton("Abbrechen");

        final boolean[] confirmed = {false};

        ok.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });

        cancel.addActionListener(e -> dialog.dispose());

        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.BLACK);
        buttonPanel.add(cancel);
        buttonPanel.add(ok);

        dialog.setContentPane(wrap(panel, buttonPanel));
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);

        if (!confirmed[0]) return null;

        return new LoginCredentials(
                loginCredentials.getHost(),
                loginCredentials.getUsername(),
                new String(passField.getPassword())
        );
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

    // ----------------------
    // Hilfsmethoden
    // ----------------------

    private JDialog createDialog(String title) {
        final JDialog dialog = new JDialog(parentFrame, title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setUndecorated(true);
        return dialog;
    }

    private JTextField styledTextField() {
        JTextField field = new JTextField(20);
        styleInput(field);
        return field;
    }

    private JPasswordField styledPasswordField() {
        JPasswordField field = new JPasswordField(20);
        styleInput(field);
        return field;
    }

    private void styleInput(JTextField field) {
        field.setBackground(Color.DARK_GRAY);
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createLineBorder(Color.ORANGE, 2));
    }

    private JButton styledButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(Color.ORANGE, 2));
        return button;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        return label;
    }

    private JPanel wrap(JPanel center, JPanel buttons) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.BLACK);
        wrapper.add(center, BorderLayout.CENTER);
        wrapper.add(buttons, BorderLayout.SOUTH);
        return wrapper;
    }
}
