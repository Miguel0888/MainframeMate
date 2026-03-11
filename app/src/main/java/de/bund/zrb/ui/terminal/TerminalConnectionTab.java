 package de.bund.zrb.ui.terminal;

import com.ascert.open.ohio.Ohio;
import com.ascert.open.term.core.Host;
import com.ascert.open.term.core.Terminal;
import com.ascert.open.term.core.TerminalFactoryRegistrar;
import com.ascert.open.term.gui.JTerminalScreen;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embed a TN3270 terminal screen as a regular connection tab.
 * <p>
 * Uses {@link Terminal} + {@link JTerminalScreen} directly (not EmulatorPanel)
 * to avoid the double-connect / swallowed-error problems of EmulatorPanel.
 */
public class TerminalConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(TerminalConnectionTab.class.getName());
    private static final AtomicBoolean FACTORY_INITIALIZED = new AtomicBoolean(false);

    private final String host;
    private final int port;
    private final String termType;
    private final boolean tls;
    private final int keepAliveTimeout;
    private final String user;
    private final String password;

    private final JPanel mainPanel;
    private final JLabel statusLabel;
    private final JToolBar connectionToolbar;
    private final JPanel fkeyPanel;

    private JComponent centerComponent;
    private volatile Terminal terminal;
    private volatile JTerminalScreen terminalScreen;
    private volatile boolean connected;

    /** Timer that periodically reads the last screen line to update F-key buttons. */
    private Timer fkeyRefreshTimer;

    public TerminalConnectionTab(String host, int port, String termType, boolean tls, int keepAliveTimeout) {
        this(host, port, termType, tls, keepAliveTimeout, null, null);
    }

    public TerminalConnectionTab(String host, int port, String termType, boolean tls, int keepAliveTimeout,
                                 String user, String password) {
        this.host = host;
        this.port = port;
        this.termType = termType != null ? termType : "IBM-3278-2";
        this.tls = tls;
        this.keepAliveTimeout = keepAliveTimeout;
        this.user = user;
        this.password = password;

        ensureFactoryInitialized();

        this.mainPanel = new JPanel(new BorderLayout());
        this.statusLabel = new JLabel("  Bereit");
        this.connectionToolbar = createConnectionToolbar();
        this.fkeyPanel = createFkeyPanel();

        this.centerComponent = createMessageLabel("Verbinde mit " + host + ":" + port + " …");

        mainPanel.add(connectionToolbar, BorderLayout.NORTH);
        mainPanel.add(centerComponent, BorderLayout.CENTER);
        mainPanel.add(fkeyPanel, BorderLayout.SOUTH);
    }

    private static void ensureFactoryInitialized() {
        if (FACTORY_INITIALIZED.compareAndSet(false, true)) {
            try {
                TerminalFactoryRegistrar.initTermTypeFactories(null);
                LOG.info("[3270] Terminal factories registered.");
            } catch (Exception e) {
                FACTORY_INITIALIZED.set(false);
                LOG.log(Level.WARNING, "[3270] Failed to register terminal factories", e);
            }
        }
    }

    /**
     * Create a Terminal via the factory, build the JTerminalScreen on the EDT,
     * then connect. Must be called on a background thread.
     */
    public void connect() throws Exception {
        final Host terminalHost = new Host(host, port, termType, tls, keepAliveTimeout);
        final Terminal createdTerminal = TerminalFactoryRegistrar.createTerminal(terminalHost);
        final AtomicReference<JTerminalScreen> screenRef = new AtomicReference<JTerminalScreen>();

        updateStatus("  ⏳ Verbinde…");

        // Build screen component on EDT (Swing requirement)
        buildScreenOnEdt(createdTerminal, screenRef);

        try {
            createdTerminal.connect();

            this.terminal = createdTerminal;
            this.terminalScreen = screenRef.get();
            this.connected = true;

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    statusLabel.setText("  ✅ Verbunden");
                    startFkeyRefreshTimer();
                    if (terminalScreen != null) {
                        terminalScreen.setFocusable(true);
                        terminalScreen.requestFocusInWindow();
                        Timer focusTimer = new Timer(200, e -> {
                            if (terminalScreen != null) {
                                terminalScreen.requestFocusInWindow();
                            }
                        });
                        focusTimer.setRepeats(false);
                        focusTimer.start();
                    }
                }
            });

            // Auto-login if credentials are available
            if (user != null && !user.isEmpty() && password != null && !password.isEmpty()) {
                autoLogin(createdTerminal);
            }
        } catch (Exception ex) {
            disconnectQuietly(createdTerminal);
            clearTerminalState();
            showDisconnectedMessage("Verbindung zu " + host + ":" + port + " fehlgeschlagen.");
            updateStatus("  ❌ Fehler");
            throw ex;
        }
    }

    private void buildScreenOnEdt(final Terminal createdTerminal,
                                  final AtomicReference<JTerminalScreen> screenRef)
            throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                // Pass a hidden toolbar to JTerminalScreen (it needs one internally)
                // We parse the F-key legend ourselves and show it in the bottom panel.
                JToolBar dummyToolbar = new JToolBar();
                dummyToolbar.setVisible(false);
                JTerminalScreen screen = new JTerminalScreen(createdTerminal, dummyToolbar);
                screenRef.set(screen);

                // Make the screen focusable and request focus on click
                screen.setFocusable(true);
                screen.setRequestFocusEnabled(true);
                screen.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        screen.requestFocusInWindow();
                    }
                });

                // Wrap the screen in a panel that scales the font to fill the available space
                JPanel scalingWrapper = new JPanel(new BorderLayout());
                scalingWrapper.setBackground(Color.BLACK);
                scalingWrapper.add(screen, BorderLayout.CENTER);

                // Also grab focus when the wrapper is clicked
                scalingWrapper.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        screen.requestFocusInWindow();
                    }
                });

                // Dynamically scale the terminal font when the container is resized
                scalingWrapper.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        scaleTerminalFont(screen, scalingWrapper);
                    }
                });

                setCenterComponent(scalingWrapper);
            }
        });
    }

    /**
     * Scale the terminal font so that the 80×24 (or 80×43) character grid
     * fills as much of the available container space as possible.
     */
    private void scaleTerminalFont(JTerminalScreen screen, JComponent container) {
        if (screen == null || container == null) return;

        int availableWidth = container.getWidth();
        int availableHeight = container.getHeight();
        if (availableWidth <= 0 || availableHeight <= 0) return;

        // Standard 3270 screen dimensions
        int cols = 80;
        int rows = 24;
        // Model 3 and 4 use 80×32 or 80×43 respectively, but 80×24 is most common
        if ("IBM-3278-3".equalsIgnoreCase(termType) || "IBM-3279-3".equalsIgnoreCase(termType)) {
            rows = 32;
        } else if ("IBM-3278-4".equalsIgnoreCase(termType) || "IBM-3279-4".equalsIgnoreCase(termType)) {
            rows = 43;
        } else if ("IBM-3278-5".equalsIgnoreCase(termType) || "IBM-3279-5".equalsIgnoreCase(termType)) {
            rows = 27;
            cols = 132;
        }

        // Calculate max font size that fits
        // Use monospaced font — character width ≈ 0.6 × font-size, height ≈ font-size + line-spacing
        // We test actual metrics to be precise
        Font currentFont = screen.getFont();
        String fontFamily = currentFont != null ? currentFont.getFamily() : "Monospaced";

        // Binary search for best font size
        int bestSize = 8;
        for (int size = 8; size <= 40; size++) {
            Font testFont = new Font(fontFamily, Font.PLAIN, size);
            FontMetrics fm = screen.getFontMetrics(testFont);
            int totalWidth = fm.charWidth('M') * cols;
            int totalHeight = fm.getHeight() * rows;

            if (totalWidth <= availableWidth && totalHeight <= availableHeight) {
                bestSize = size;
            } else {
                break;
            }
        }

        Font currentScreenFont = screen.getFont();
        if (currentScreenFont == null || currentScreenFont.getSize() != bestSize) {
            screen.setFont(new Font(fontFamily, Font.PLAIN, bestSize));
            screen.revalidate();
            screen.repaint();
        }
    }

    public void disconnect() {
        Terminal currentTerminal = this.terminal;
        clearTerminalState();
        disconnectQuietly(currentTerminal);
        clearFunctionKeyToolbar();
        showDisconnectedMessage("Verbindung getrennt.");
        updateStatus("  ⛔ Getrennt");
    }

    private void clearTerminalState() {
        this.connected = false;
        this.terminal = null;
        this.terminalScreen = null;
    }

    private void disconnectQuietly(Terminal terminalToDisconnect) {
        if (terminalToDisconnect == null) return;
        try {
            terminalToDisconnect.disconnect();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[3270] Disconnect error", e);
        }
    }

    private void clearFunctionKeyToolbar() {
        if (fkeyRefreshTimer != null) {
            fkeyRefreshTimer.stop();
            fkeyRefreshTimer = null;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                fkeyPanel.removeAll();
                fkeyPanel.revalidate();
                fkeyPanel.repaint();
            }
        });
    }

    // ── Auto-Login ──────────────────────────────────────────────

    private static final com.ascert.open.ohio.Ohio.OHIO_AID AID_ENTER =
            com.ascert.open.ohio.Ohio.OHIO_AID.OHIO_AID_3270_ENTER;
    private static final com.ascert.open.ohio.Ohio.OHIO_AID AID_CLEAR =
            com.ascert.open.ohio.Ohio.OHIO_AID.OHIO_AID_3270_CLEAR;

    /**
     * Automatically log in after connect.
     * <p>
     * Sequence: CLEAR → wait → type user into 1st field → move cursor to 2nd field → type password → ENTER.
     * Both userid and password are on the same screen; no host round-trip between them.
     * Runs on a background thread.
     */
    private void autoLogin(final Terminal term) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1) Wait for the host to send the initial screen
                    if (!waitForKeyboardUnlock(term, 10_000)) {
                        LOG.warning("[3270] Auto-login: keyboard did not unlock within timeout");
                        return;
                    }
                    Thread.sleep(500);

                    // 2) Send CLEAR to reset the screen to a clean login prompt
                    term.Fkey(AID_CLEAR);
                    if (!waitForKeyboardUnlock(term, 10_000)) {
                        LOG.warning("[3270] Auto-login: keyboard did not unlock after CLEAR");
                        return;
                    }
                    Thread.sleep(500);

                    // 3) Find the first unprotected field = userid
                    short useridField = term.getNextUnprotectedField(0);
                    if (useridField < 0) {
                        LOG.warning("[3270] Auto-login: no unprotected field found for userid");
                        return;
                    }
                    term.setCursorPosition(useridField);

                    // 4) Type username
                    typeString(term, user);
                    Thread.sleep(100);

                    // 5) Find the SECOND unprotected field = password
                    //    (jump from userid field position to the next one)
                    short passwordField = term.getNextUnprotectedField(useridField + 1);
                    if (passwordField < 0 || passwordField == useridField) {
                        LOG.warning("[3270] Auto-login: no second unprotected field found for password");
                        return;
                    }
                    term.setCursorPosition(passwordField);

                    // 6) Type password
                    typeString(term, password);
                    Thread.sleep(100);

                    // 7) Send ENTER to submit both userid and password
                    term.Fkey(AID_ENTER);

                    LOG.info("[3270] Auto-login: credentials sent");
                    updateStatus("  ✅ Verbunden (angemeldet)");
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[3270] Auto-login failed", e);
                }
            }
        }, "3270-AutoLogin").start();
    }

    /**
     * Poll until the terminal keyboard is unlocked or the timeout expires.
     */
    private boolean waitForKeyboardUnlock(Terminal term, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!term.isKeyboardLocked()) {
                return true;
            }
            Thread.sleep(100);
        }
        return !term.isKeyboardLocked();
    }

    /**
     * Type a string character by character into the terminal.
     */
    private void typeString(Terminal term, String text) throws Exception {
        com.ascert.open.term.core.InputCharHandler charHandler = term.getCharHandler();
        for (int i = 0; i < text.length(); i++) {
            charHandler.type(text.charAt(i));
        }
    }

    private void showDisconnectedMessage(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setCenterComponent(createMessageLabel(message));
            }
        });
    }

    private void updateStatus(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(text);
            }
        });
    }

    private void setCenterComponent(JComponent component) {
        if (centerComponent != null) {
            mainPanel.remove(centerComponent);
        }
        centerComponent = component;
        mainPanel.add(centerComponent, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private JLabel createMessageLabel(String text) {
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setFont(label.getFont().deriveFont(Font.ITALIC, 14f));
        return label;
    }

    private void reconnect() {
        updateStatus("  ⏳ Verbinde…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (connected) disconnect();
                connect();
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    updateStatus("  ❌ Fehler");
                    JOptionPane.showMessageDialog(mainPanel,
                            "Verbindung fehlgeschlagen:\n" + cause.getMessage(),
                            "3270-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private JToolBar createConnectionToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JButton connectBtn = new JButton("🔌 Verbinden");
        connectBtn.setToolTipText("Verbindung (wieder-)herstellen");
        connectBtn.addActionListener(e -> reconnect());
        toolbar.add(connectBtn);

        JButton disconnectBtn = new JButton("⛔ Trennen");
        disconnectBtn.setToolTipText("Terminalsession trennen");
        disconnectBtn.addActionListener(e -> disconnect());
        toolbar.add(disconnectBtn);

        toolbar.addSeparator(new Dimension(12, 0));

        // Special keys
        toolbar.add(makeSpecialButton("ENT", AID_ENTER));
        toolbar.add(makeSpecialButton("CLR", AID_CLEAR));
        toolbar.add(makeSpecialButton("PA1", Ohio.OHIO_AID.OHIO_AID_3270_PA1));
        toolbar.add(makeSpecialButton("PA2", Ohio.OHIO_AID.OHIO_AID_3270_PA2));
        toolbar.add(makeSpecialButton("PA3", Ohio.OHIO_AID.OHIO_AID_3270_PA3));
        toolbar.add(makeSpecialButton("SYS", Ohio.OHIO_AID.OHIO_AID_3270_SYSREQ));

        toolbar.addSeparator(new Dimension(16, 0));
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statusLabel);

        return toolbar;
    }

    private JButton makeSpecialButton(String label, final Ohio.OHIO_AID aid) {
        JButton btn = new JButton(label);
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setFocusable(false);
        btn.addActionListener(e -> {
            Terminal t = terminal;
            if (t != null && connected) {
                t.Fkey(aid);
                if (terminalScreen != null) terminalScreen.requestFocusInWindow();
            }
        });
        return btn;
    }

    private JPanel createFkeyPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        return panel;
    }

    // ── F-Key Legend Parsing ─────────────────────────────────────

    /** Pattern matching entries like "1=Help", "3=End", "10=Left", "12=Cancel" */
    private static final Pattern FKEY_PATTERN = Pattern.compile("(\\d{1,2})\\s*[=]\\s*(\\S+)");

    /** Last parsed legend text – avoid rebuilding buttons if unchanged. */
    private String lastFkeyLegend = "";

    private void startFkeyRefreshTimer() {
        if (fkeyRefreshTimer != null) fkeyRefreshTimer.stop();
        fkeyRefreshTimer = new Timer(500, e -> refreshFkeyLegend());
        fkeyRefreshTimer.setRepeats(true);
        fkeyRefreshTimer.start();
    }

    /**
     * Read the last line(s) of the terminal screen, parse F-key assignments,
     * and rebuild the bottom button panel if anything changed.
     */
    private void refreshFkeyLegend() {
        Terminal t = terminal;
        if (t == null || !connected) return;

        int cols = t.getCols();
        int rows = t.getRows();
        if (cols <= 0 || rows <= 0) return;

        // Read the last two rows (F-key legend often spans 2 lines)
        String lastLine = "";
        try {
            int startPos = (rows - 2) * cols;
            lastLine = t.getCharString(startPos, cols * 2);
        } catch (Exception ex) {
            return;
        }

        if (lastLine == null) return;
        String trimmed = lastLine.trim();
        if (trimmed.equals(lastFkeyLegend)) return;
        lastFkeyLegend = trimmed;

        // Parse: find patterns like "1=Help", "3=End", "10=Actions"
        List<int[]> fkeys = new ArrayList<int[]>();  // [fkeyNumber]
        List<String> labels = new ArrayList<String>();

        Matcher m = FKEY_PATTERN.matcher(trimmed);
        while (m.find()) {
            int num = Integer.parseInt(m.group(1));
            if (num >= 1 && num <= 24) {
                fkeys.add(new int[]{num});
                labels.add(m.group(2));
            }
        }

        // Rebuild button panel on EDT
        fkeyPanel.removeAll();
        for (int i = 0; i < fkeys.size(); i++) {
            int fnum = fkeys.get(i)[0];
            String label = labels.get(i);
            fkeyPanel.add(makeFkeyButton(fnum, label));
        }
        fkeyPanel.revalidate();
        fkeyPanel.repaint();
    }

    private JButton makeFkeyButton(final int fkeyNumber, String label) {
        JButton btn = new JButton(fkeyNumber + "-" + label);
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 11f));
        btn.setFocusable(false);
        btn.setToolTipText("F" + fkeyNumber);

        Color bg = getFkeyColor(fkeyNumber);
        btn.setBackground(bg);
        btn.setOpaque(true);
        // Use dark text for light backgrounds, white for dark ones
        float brightness = (bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114) / 255000f;
        btn.setForeground(brightness > 0.55f ? Color.BLACK : Color.WHITE);

        btn.addActionListener(e -> {
            Terminal t = terminal;
            if (t != null && connected) {
                t.Fkey(fkeyNumber);
                if (terminalScreen != null) terminalScreen.requestFocusInWindow();
            }
        });
        return btn;
    }

    /**
     * Compute a background color for an F-key button.
     * <p>
     * F1–F12 (standard keys):
     *   F1–F4:  yellow group, slight gradient toward orange
     *   F5–F8:  orange group, slight gradient toward red
     *   F9–F12: red group
     * <p>
     * F13–F24 (extended keys):
     *   F13–F16: green group, slight gradient toward teal
     *   F17–F20: teal group, slight gradient toward blue
     *   F21–F24: blue group
     */
    private static Color getFkeyColor(int fkey) {
        // Group base colors (standard F1-F12)
        //                  yellow         orange          red
        Color yellow = new Color(255, 230, 80);
        Color orange = new Color(255, 165, 50);
        Color red    = new Color(220, 70, 60);

        // Group base colors (extended F13-F24)
        //                  green          teal            blue
        Color green  = new Color(90, 200, 90);
        Color teal   = new Color(60, 190, 190);
        Color blue   = new Color(80, 120, 210);

        if (fkey >= 1 && fkey <= 4) {
            // Yellow group: position 0..3, blend up to 25% toward orange
            float t = (fkey - 1) / 3f * 0.25f;
            return blend(yellow, orange, t);
        } else if (fkey >= 5 && fkey <= 8) {
            // Orange group: position 0..3, blend up to 25% toward red
            float t = (fkey - 5) / 3f * 0.25f;
            return blend(orange, red, t);
        } else if (fkey >= 9 && fkey <= 12) {
            // Red group: position 0..3, stays red (slight lighten for first)
            float t = (fkey - 9) / 3f * 0.15f;
            return blend(red, new Color(180, 50, 50), t);
        } else if (fkey >= 13 && fkey <= 16) {
            // Green group
            float t = (fkey - 13) / 3f * 0.25f;
            return blend(green, teal, t);
        } else if (fkey >= 17 && fkey <= 20) {
            // Teal group
            float t = (fkey - 17) / 3f * 0.25f;
            return blend(teal, blue, t);
        } else if (fkey >= 21 && fkey <= 24) {
            // Blue group
            float t = (fkey - 21) / 3f * 0.15f;
            return blend(blue, new Color(60, 80, 180), t);
        }
        return new Color(200, 200, 200); // fallback gray
    }

    private static Color blend(Color a, Color b, float t) {
        float u = 1f - t;
        return new Color(
                Math.round(a.getRed() * u + b.getRed() * t),
                Math.round(a.getGreen() * u + b.getGreen() * t),
                Math.round(a.getBlue() * u + b.getBlue() * t)
        );
    }

    // ── ConnectionTab interface ─────────────────────────────────

    @Override
    public String getTitle() {
        return "🖥️ 3270 " + host + ":" + port;
    }

    @Override
    public String getTooltip() {
        return "TN3270 → " + host + ":" + port + " (" + termType + ", TLS=" + tls + ")";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        if (fkeyRefreshTimer != null) {
            fkeyRefreshTimer.stop();
            fkeyRefreshTimer = null;
        }
        disconnect();
    }

    @Override
    public void saveIfApplicable() {
        // nothing to save
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());
        menu.add(closeItem);
        return menu;
    }

    @Override
    public void focusSearchField() {
        // When this tab is selected/focused, give keyboard focus to the terminal screen
        if (terminalScreen != null && connected) {
            terminalScreen.requestFocusInWindow();
        }
    }

    @Override
    public void searchFor(String searchPattern) {
        // not applicable for terminal
    }

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {
        // not applicable
    }

    @Override
    public String getPath() {
        return "tn3270://" + host + ":" + port + "?tls=" + tls + "&termType=" + termType;
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }
}
