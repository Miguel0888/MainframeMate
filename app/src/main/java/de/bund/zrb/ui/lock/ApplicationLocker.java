package de.bund.zrb.ui.lock;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginCredentials;
import de.bund.zrb.login.LoginCredentialsProvider;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Predicate;

public class ApplicationLocker implements LoginCredentialsProvider {

    private final LockerUi lockerUi;
    private final LoginManager loginManager;

    private final JFrame parentFrame;
    private final int timeoutMillis;
    private final int warningPhaseMillis;

    private Timer inactivityTimer;
    private Timer countdownTimer;
    private JWindow countdownWindow;

    private LoginCredentials loginCredentials;

    private volatile boolean locked = false;
    private volatile boolean warningActive = false;

    public ApplicationLocker(JFrame parentFrame, LoginManager loginManager) {
        this.parentFrame = parentFrame;
        this.loginManager = loginManager;

        Settings settings = SettingsHelper.load();
        this.timeoutMillis = settings.lockDelay;
        this.warningPhaseMillis = settings.lockPrenotification;

        // Enum nutzt Settings-Index (z. B. aus int settings.lockStyle)
        int styleIndex = Math.max(0, Math.min(LockerStyle.values().length - 1, settings.lockStyle));
        LockerStyle style = LockerStyle.values()[styleIndex];
        this.lockerUi = style.createUi(parentFrame, loginManager);
    }

    @Override
    public LoginCredentials requestCredentials(String host, String user) {

        if (isBlank(host) || isBlank(user)) {
            loginCredentials = lockerUi.init();
        } else {
            loginCredentials = lockerUi.logOn(new LoginCredentials(host, user));
        }

        return loginCredentials;
    }

    public void start() {
        // ApplicationLocker is always started - it will only lock when there's an active session
        // The actual locking decision is made in userNotLoggedIn() which checks shouldLockApplication()

        inactivityTimer = new Timer(timeoutMillis, e -> startWarningCountdown());
        inactivityTimer.setRepeats(false);
        inactivityTimer.start();

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof InputEvent || event instanceof KeyEvent) {
                resetAllTimers(true);
            }
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    public void lock() {
        if (locked || !shouldLock()) return;

        locked = true;
        warningActive = false;
        inactivityTimer.stop();

        Predicate<char[]> validator = input -> {
            if (loginCredentials == null) {
                loginCredentials = new LoginCredentials(
                        SettingsHelper.load().host,
                        SettingsHelper.load().user,
                        loginManager.getPassword(SettingsHelper.load().host, SettingsHelper.load().user)
                );
            }

            return loginManager.verifyPassword(
                    loginCredentials.getHost(),
                    loginCredentials.getUsername(),
                    new String(input)
            );
        };

        lockerUi.lock(validator);
        locked = false;
        resetAllTimers(false);
    }

    /**
     * Check if application should be locked.
     * Returns true if:
     * - Lock is enabled in settings AND
     * - There's an active session (password cached/stored or successful login occurred)
     */
    private boolean shouldLock() {
        Settings settings = SettingsHelper.load();
        if (!settings.lockEnabled) {
            return false;
        }
        return loginManager.shouldLockApplication();
    }

    private void resetAllTimers(boolean mayCancelCountdown) {
        if (inactivityTimer != null) inactivityTimer.restart();
        if (mayCancelCountdown && countdownTimer != null) cancelCountdown();
    }

    private void cancelCountdown() {
        if (countdownTimer != null) countdownTimer.stop();
        if (countdownWindow != null) countdownWindow.dispose();
        warningActive = false;
    }

    private void startWarningCountdown() {
        if (locked || warningActive || !shouldLock()) return;

        warningActive = true;

        final int totalSeconds = warningPhaseMillis / 1000;
        final JLabel timerLabel = new JLabel(String.valueOf(totalSeconds), SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 48));
        timerLabel.setForeground(Color.WHITE);

        final AnimatedTimerCircle circle = new AnimatedTimerCircle(timerLabel);
        int diameter = timerLabel.getPreferredSize().height + 60;
        circle.setPreferredSize(new Dimension(diameter + 40, diameter + 40));

        countdownWindow = new JWindow(parentFrame);
        countdownWindow.setBackground(new Color(0, 0, 0, 0));
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
