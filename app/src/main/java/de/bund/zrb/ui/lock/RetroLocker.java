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
        // Asks for HOST & USER
        return new LoginCredentials(null, null, null); // TODO
    }

    @Override
    public LoginCredentials logOn(LoginCredentials loginCredentials) {
        return new LoginCredentials(null, null, null); // TODO
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
