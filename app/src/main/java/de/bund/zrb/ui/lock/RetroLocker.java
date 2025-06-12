package de.bund.zrb.ui.lock;

import de.bund.zrb.login.LoginCredentials;
import de.bund.zrb.login.LoginManager;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.function.Predicate;

public class RetroLocker implements LockerUi {

    private final Frame parentFrame;
    private final LoginManager loginManager;

    public RetroLocker(Frame parentFrame, LoginManager loginManager) {
        this.parentFrame = parentFrame;
        this.loginManager = loginManager;
    }

    @Override
    public LoginCredentials init() {
        final JDialog dialog = new JDialog(parentFrame, "Anmeldung erforderlich", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setUndecorated(true);

        JTextField hostField = new JTextField(20);
        JTextField userField = new JTextField(20);
        JPasswordField passField = new JPasswordField(20);

        Font mono = new Font("Monospaced", Font.PLAIN, 14);
        hostField.setFont(mono);
        userField.setFont(mono);
        passField.setFont(mono);

        hostField.setBackground(Color.BLACK);
        userField.setBackground(Color.BLACK);
        passField.setBackground(Color.BLACK);

        hostField.setForeground(Color.GREEN);
        userField.setForeground(Color.GREEN);
        passField.setForeground(Color.GREEN);

        hostField.setCaretColor(Color.GREEN);
        userField.setCaretColor(Color.GREEN);
        passField.setCaretColor(Color.GREEN);

        JPanel inputPanel = new JPanel();
        inputPanel.setOpaque(false);
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        inputPanel.add(Box.createVerticalStrut(10));
        inputPanel.add(new JLabel("HOST:")).setForeground(Color.GREEN);
        inputPanel.add(hostField);
        inputPanel.add(Box.createVerticalStrut(5));
        inputPanel.add(new JLabel("BENUTZER:")).setForeground(Color.GREEN);
        inputPanel.add(userField);
        inputPanel.add(Box.createVerticalStrut(5));
        inputPanel.add(new JLabel("PASSWORT:")).setForeground(Color.GREEN);
        inputPanel.add(passField);

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("ABBRUCH");

        ok.setFont(mono);
        cancel.setFont(mono);
        ok.setBackground(Color.BLACK);
        cancel.setBackground(Color.BLACK);
        ok.setForeground(Color.GREEN);
        cancel.setForeground(Color.GREEN);
        ok.setFocusPainted(false);
        cancel.setFocusPainted(false);

        final boolean[] confirmed = {false};

        ok.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.add(cancel);
        buttonPanel.add(ok);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.BLACK);
        wrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        wrapper.add(inputPanel, BorderLayout.CENTER);
        wrapper.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(wrapper);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        dialog.getRootPane().setDefaultButton(ok);
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
        final JDialog dialog = new JDialog(parentFrame, "Passwort erforderlich", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setUndecorated(true);

        JPasswordField passField = new JPasswordField(20);
        passField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        passField.setForeground(Color.GREEN);
        passField.setBackground(Color.BLACK);
        passField.setCaretColor(Color.GREEN);

        JLabel label = new JLabel("🔐 Passwort für " + loginCredentials.getUsername() + "@" + loginCredentials.getHost());
        label.setForeground(Color.GREEN);
        label.setFont(new Font("Monospaced", Font.BOLD, 14));

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        inputPanel.setOpaque(false);
        inputPanel.add(label);
        inputPanel.add(Box.createVerticalStrut(10));
        inputPanel.add(passField);

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("ABBRUCH");
        ok.setFont(new Font("Monospaced", Font.PLAIN, 12));
        cancel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        ok.setForeground(Color.GREEN);
        cancel.setForeground(Color.GREEN);
        ok.setBackground(Color.BLACK);
        cancel.setBackground(Color.BLACK);

        final boolean[] confirmed = {false};

        ok.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.add(cancel);
        buttonPanel.add(ok);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.BLACK);
        wrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        wrapper.add(inputPanel, BorderLayout.CENTER);
        wrapper.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(wrapper);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        dialog.getRootPane().setDefaultButton(ok);
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

        // Container mit OverlayLayout
        JPanel overlayPanel = new JPanel();
        overlayPanel.setLayout(new OverlayLayout(overlayPanel));
        overlayPanel.setBackground(Color.BLACK);

        // ASCII-Art-Hintergrund
        JTextArea asciiBackground = new JTextArea();
        asciiBackground.setEditable(false);
        asciiBackground.setFocusable(false);
        asciiBackground.setFont(new Font("Monospaced", Font.PLAIN, 14));
        asciiBackground.setForeground(Color.GREEN);
        asciiBackground.setBackground(Color.BLACK);
        asciiBackground.setText(
                "╔═══════════════════════╗\n" +
                "║   Zugang gesperrt!                     ║\n" +
                "║   Passwort eingeben:                   ║\n" +
                "║                                        ║\n" +
                "║                                        ║\n" +
                "║                                        ║\n" +
                "║                                        ║\n" +
                "╚═══════════════════════╝"
        );
        asciiBackground.setAlignmentX(0.5f);
        asciiBackground.setAlignmentY(0.5f);

        // Zentrum mit Passwort und Button
        JPanel inputPanel = new JPanel();
        inputPanel.setOpaque(false);
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        inputPanel.setAlignmentX(0.5f);
        inputPanel.setAlignmentY(0.5f);

        final JPasswordField passField = new JPasswordField(20);
        passField.setMaximumSize(new Dimension(200, 24));
        passField.setFont(new Font("Monospaced", Font.BOLD, 14));
        passField.setForeground(Color.GREEN);
        passField.setBackground(Color.BLACK);
        passField.setCaretColor(Color.GREEN);
        passField.setBorder(BorderFactory.createLineBorder(Color.GREEN));

        JButton unlock = new JButton("ENTSPERR");
        unlock.setBackground(Color.BLACK);
        unlock.setForeground(Color.GREEN);
        unlock.setFocusPainted(false);
        unlock.setFont(new Font("Monospaced", Font.BOLD, 12));
        unlock.setAlignmentX(Component.CENTER_ALIGNMENT);

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

        inputPanel.add(Box.createVerticalStrut(20));
        inputPanel.add(passField);
        inputPanel.add(Box.createVerticalStrut(10));
        inputPanel.add(unlock);

        // Baue die Schichten im Overlay
        overlayPanel.add(inputPanel);
        overlayPanel.add(asciiBackground);

        // Rahmen um alles
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.BLACK);
        wrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        wrapper.add(overlayPanel, BorderLayout.CENTER);

        lockDialog.setContentPane(wrapper);
        lockDialog.setSize(370, 185);
        lockDialog.setLocationRelativeTo(parentFrame);
        lockDialog.setVisible(true);
    }
}
