package de.bund.zrb.ui.lock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ApplicationLocker {

    private final JFrame parentFrame;
    private final int timeoutMillis;
    private final int warningPhaseMillis;
    private final byte[] passwordHash;

    private Timer inactivityTimer;
    private Timer countdownTimer;
    private JWindow countdownWindow;

    private boolean retroDesign = false;

    public ApplicationLocker(JFrame parentFrame, int timeoutMillis, int warningPhaseMillis, String plainPassword) {
        this.parentFrame = parentFrame;
        this.timeoutMillis = timeoutMillis;
        this.warningPhaseMillis = warningPhaseMillis;
        this.passwordHash = hashPassword(plainPassword);
    }

    public void start() {
        inactivityTimer = new Timer(timeoutMillis, e -> startWarningCountdown());
        inactivityTimer.setRepeats(false);
        inactivityTimer.start();

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof InputEvent || event instanceof KeyEvent) {
                resetAllTimers(true); // true = evtl. Countdown abbrechen
            }
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private void resetAllTimers(boolean mayCancelCountdown) {
        if (inactivityTimer != null) {
            inactivityTimer.restart();
        }
        if (mayCancelCountdown && countdownTimer != null) {
            cancelCountdown("Sperre abgebrochen durch Benutzeraktion.");
        }
    }

    private void startWarningCountdown() {
        final int totalSeconds = warningPhaseMillis / 1000;
        final JLabel timerLabel = new JLabel(String.valueOf(totalSeconds), SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 48));
        timerLabel.setForeground(Color.WHITE);

        final AnimatedTimerCircle circle = new AnimatedTimerCircle(timerLabel);
        int diameter = timerLabel.getPreferredSize().height + 60;
        circle.setPreferredSize(new Dimension(diameter + 40, diameter + 40));

        countdownWindow = new JWindow(parentFrame);
        countdownWindow.setBackground(new Color(0, 0, 0, 0)); // transparenter Hintergrund
        countdownWindow.getContentPane().add(circle);
        countdownWindow.pack();
        countdownWindow.setLocationRelativeTo(parentFrame);
        countdownWindow.setAlwaysOnTop(true);
        countdownWindow.setVisible(true);

        countdownTimer = new Timer(1000, new ActionListener() {
            int timeLeft = totalSeconds;

            public void actionPerformed(ActionEvent e) {
                timeLeft--;
                float progress = timeLeft / (float) totalSeconds;
                timerLabel.setText(String.valueOf(timeLeft));
                circle.setProgress(progress);

                if (timeLeft <= 0) {
                    countdownTimer.stop();
                    countdownWindow.dispose();
                    lock();
                }
            }
        });
        countdownTimer.start();
    }

    private void cancelCountdown(String message) {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        if (countdownWindow != null) {
            countdownWindow.dispose();
        }
    }

    public void lock() {
        final JDialog lockDialog = new JDialog(parentFrame, "Sperre", true);
        lockDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        lockDialog.setUndecorated(true);

        if (retroDesign) {
            retroLocker(lockDialog);
        } else {
            modernLocker(lockDialog);
        }
        inactivityTimer.stop();
    }

    private void modernLocker(JDialog lockDialog) {
        // Modernes Design (wie gehabt)
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
            if (verifyPassword(input)) {
                Arrays.fill(input, '\0');
                lockDialog.dispose();
                inactivityTimer.start();
                resetAllTimers(false);
            } else {
                passField.setText("");
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

    private void retroLocker(JDialog lockDialog) {
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
            if (verifyPassword(input)) {
                Arrays.fill(input, '\0');
                lockDialog.dispose();
                resetAllTimers(false);
            } else {
                passField.setText("");
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


    private byte[] hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(password.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Hashen des Passworts", e);
        }
    }

    private boolean verifyPassword(char[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] inputHash = digest.digest(new String(input).getBytes("UTF-8"));
            return Arrays.equals(inputHash, passwordHash);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            return false;
        }
    }

    public void setRetroDesign(boolean retro) {
        this.retroDesign = retro;
    }
}
