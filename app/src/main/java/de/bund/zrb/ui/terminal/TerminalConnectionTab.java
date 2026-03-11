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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final boolean autoLoginEnabled;
    private final String autoCommand;  // null or empty = no auto-command

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

    /** Map from F-key number (1–24) to the corresponding button in the bottom panel. */
    private final Map<Integer, JButton> fkeyButtons = new HashMap<Integer, JButton>();

    /** Records all user interactions for macro bookmarks. Always active. */
    private final TerminalMacroRecorder macroRecorder = new TerminalMacroRecorder();

    /** Steps to replay after connect+login (null = no replay). */
    private final List<Map<String, String>> replaySteps;

    public TerminalConnectionTab(String host, int port, String termType, boolean tls, int keepAliveTimeout) {
        this(host, port, termType, tls, keepAliveTimeout, null, null, true, null, null);
    }

    public TerminalConnectionTab(String host, int port, String termType, boolean tls, int keepAliveTimeout,
                                 String user, String password) {
        this(host, port, termType, tls, keepAliveTimeout, user, password, true, null, null);
    }

    public TerminalConnectionTab(String host, int port, String termType, boolean tls, int keepAliveTimeout,
                                 String user, String password, boolean autoLoginEnabled, String autoCommand) {
        this(host, port, termType, tls, keepAliveTimeout, user, password, autoLoginEnabled, autoCommand, null);
    }

    /**
     * @param replaySteps macro steps to replay after connect+login (null = interactive session)
     */
    public TerminalConnectionTab(String host, int port, String termType, boolean tls, int keepAliveTimeout,
                                 String user, String password, boolean autoLoginEnabled, String autoCommand,
                                 List<Map<String, String>> replaySteps) {
        this.host = host;
        this.port = port;
        this.termType = termType != null ? termType : "IBM-3278-2";
        this.tls = tls;
        this.keepAliveTimeout = keepAliveTimeout;
        this.user = user;
        this.password = password;
        this.autoLoginEnabled = autoLoginEnabled;
        this.autoCommand = autoCommand;
        this.replaySteps = replaySteps;

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

            // Auto-login if enabled and credentials are available
            if (autoLoginEnabled && user != null && !user.isEmpty() && password != null && !password.isEmpty()) {
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

                // Make the screen focusable and request focus on click.
                // If the click lands on a menu/action-bar item, auto-send ENTER.
                screen.setFocusable(true);
                screen.setRequestFocusEnabled(true);
                screen.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        screen.requestFocusInWindow();
                    }

                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        if (e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
                        if (e.getClickCount() != 1) return;
                        // Short delay so JTerminalScreen has finished positioning the cursor
                        Timer autoEnter = new Timer(50, evt -> autoEnterIfMenuItem(createdTerminal));
                        autoEnter.setRepeats(false);
                        autoEnter.start();
                    }
                });

                // Visual feedback: press/release the F-key button when a keyboard F-key is used.
                // Also record all keyboard input for macro bookmarks.
                screen.addKeyListener(new java.awt.event.KeyAdapter() {
                    @Override
                    public void keyPressed(java.awt.event.KeyEvent e) {
                        // Record F-keys as AID
                        int fnum = fkeyNumberFromEvent(e);
                        if (fnum > 0) {
                            JButton btn = fkeyButtons.get(fnum);
                            if (btn != null) {
                                btn.getModel().setArmed(true);
                                btn.getModel().setPressed(true);
                            }
                            Ohio.OHIO_AID aid = pfKeyToAid(fnum);
                            if (aid != null) macroRecorder.recordAid(aid);
                        }
                        // Record Enter as AID
                        if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                            macroRecorder.recordAid(AID_ENTER);
                        }
                    }

                    @Override
                    public void keyReleased(java.awt.event.KeyEvent e) {
                        int fnum = fkeyNumberFromEvent(e);
                        if (fnum > 0) {
                            JButton btn = fkeyButtons.get(fnum);
                            if (btn != null) {
                                btn.getModel().setPressed(false);
                                btn.getModel().setArmed(false);
                            }
                        }
                    }

                    @Override
                    public void keyTyped(java.awt.event.KeyEvent e) {
                        char c = e.getKeyChar();
                        // Record printable characters (ignore control chars)
                        if (c != java.awt.event.KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c)) {
                            macroRecorder.recordChar(c);
                        }
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

    // ── Menu / Action-Bar click detection ─────────────────────

    /** Max row (0-based) to consider as action-bar area. */
    private static final int ACTION_BAR_MAX_ROW = 3;

    /**
     * Called shortly after a mouse click on the terminal screen.
     * If the cursor is positioned on a selectable menu item (= an unprotected
     * field in the top rows that contains visible text), send ENTER automatically.
     */
    private void autoEnterIfMenuItem(Terminal term) {
        if (term == null || !connected) return;
        if (term.isKeyboardLocked()) return;

        int cols = term.getCols();
        if (cols <= 0) return;

        int cursorPos = term.getCursorPosition();
        int cursorRow = cursorPos / cols;   // 0-based

        // Only action-bar area (top few rows)
        if (cursorRow > ACTION_BAR_MAX_ROW) return;

        // Check if the cursor is in an unprotected field with text
        try {
            com.ascert.open.term.core.TermField field = term.getField(cursorPos);
            if (field == null) return;
            if (field.isProtected()) return;

            // Read the field text — if it has any visible non-space chars, it's a menu item
            int begin = field.getBeginBA() + 1; // skip attribute byte
            int end = field.getEndBA();
            int len = end - begin + 1;
            if (len <= 0 || len > cols) return;

            String fieldText = term.getCharString(begin, len);
            if (fieldText != null && fieldText.trim().length() > 0) {
                LOG.fine("[3270] Menu click detected at row " + cursorRow
                        + ", field='" + fieldText.trim() + "' → sending ENTER");
                term.Fkey(AID_ENTER);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[3270] autoEnterIfMenuItem error", e);
        }
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

                    // 8) Auto-command after login (e.g. "a" + ENTER to skip welcome screen)
                    if (autoCommand != null && !autoCommand.isEmpty()) {
                        if (!waitForKeyboardUnlock(term, 10_000)) {
                            LOG.warning("[3270] Auto-command: keyboard did not unlock after login");
                            return;
                        }
                        Thread.sleep(500);

                        typeString(term, autoCommand);
                        Thread.sleep(100);
                        term.Fkey(AID_ENTER);
                        LOG.info("[3270] Auto-command sent: '" + autoCommand + "'");
                    }

                    // 9) Replay macro steps if this is a bookmark session
                    if (replaySteps != null && !replaySteps.isEmpty()) {
                        if (!waitForKeyboardUnlock(term, 10_000)) {
                            LOG.warning("[3270] Macro replay: keyboard did not unlock");
                            return;
                        }
                        Thread.sleep(500);
                        new TerminalMacroPlayer(term, replaySteps).play();
                        LOG.info("[3270] Macro replay complete (" + replaySteps.size() + " steps)");
                    }
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
                macroRecorder.recordAid(aid);
                t.Fkey(aid);
                if (terminalScreen != null) terminalScreen.requestFocusInWindow();
            }
        });
        return btn;
    }

    private JPanel createFkeyPanel() {
        JPanel outer = new JPanel(new GridLayout(0, 1, 0, 0));
        outer.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        return outer;
    }


    // ── F-Key Legend Parsing ─────────────────────────────────────

    /**
     * Pattern matching F-key legend entries in all common formats:
     *   "F1=Help"  "F3:End"  "F10-Actions"  "1=Help"  "PF1=Help"  "PF3:End"
     * Group 1 = key number, Group 2 = label text.
     */
    private static final Pattern FKEY_PATTERN = Pattern.compile(
            "(?:PF|F)?(\\d{1,2})\\s*[=:\\-]\\s*(\\S+)");

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
     * and rebuild the bottom status bar with grouped buttons.
     * <p>
     * Row 1 (standard):  F1–F4 left │ F5–F8 center │ F9–F12 right
     * Row 2 (extended, only shown when used): F13–F16 left │ F17–F20 center │ F21–F24 right
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

        // Parse F-key assignments
        Map<Integer, String> parsed = new LinkedHashMap<Integer, String>();
        Matcher m = FKEY_PATTERN.matcher(trimmed);
        while (m.find()) {
            int num = Integer.parseInt(m.group(1));
            if (num >= 1 && num <= 24) {
                parsed.put(num, m.group(2));
            }
        }

        // Build sub-panels for standard keys (row 1)
        JPanel leftStd   = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        JPanel centerStd = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 1));
        JPanel rightStd  = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 1));

        // Build sub-panels for extended keys (row 2)
        JPanel leftExt   = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        JPanel centerExt = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 1));
        JPanel rightExt  = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 1));

        boolean hasExtended = false;
        fkeyButtons.clear();

        for (Map.Entry<Integer, String> entry : parsed.entrySet()) {
            int fnum = entry.getKey();
            JButton btn = makeFkeyButton(fnum, entry.getValue());
            fkeyButtons.put(fnum, btn);

            if (fnum >= 1 && fnum <= 4)        leftStd.add(btn);
            else if (fnum >= 5 && fnum <= 8)   centerStd.add(btn);
            else if (fnum >= 9 && fnum <= 12)  rightStd.add(btn);
            else if (fnum >= 13 && fnum <= 16) { leftExt.add(btn); hasExtended = true; }
            else if (fnum >= 17 && fnum <= 20) { centerExt.add(btn); hasExtended = true; }
            else if (fnum >= 21 && fnum <= 24) { rightExt.add(btn); hasExtended = true; }
        }

        // Assemble rows
        JPanel row1 = new JPanel(new BorderLayout(8, 0));
        row1.add(leftStd, BorderLayout.WEST);
        row1.add(centerStd, BorderLayout.CENTER);
        row1.add(rightStd, BorderLayout.EAST);

        fkeyPanel.removeAll();
        fkeyPanel.add(row1);

        if (hasExtended) {
            JPanel row2 = new JPanel(new BorderLayout(8, 0));
            row2.add(leftExt, BorderLayout.WEST);
            row2.add(centerExt, BorderLayout.CENTER);
            row2.add(rightExt, BorderLayout.EAST);
            fkeyPanel.add(row2);
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

        final Ohio.OHIO_AID aid = pfKeyToAid(fkeyNumber);
        btn.addActionListener(e -> {
            Terminal t = terminal;
            if (t != null && connected && aid != null) {
                macroRecorder.recordAid(aid);
                t.Fkey(aid);
                if (terminalScreen != null) terminalScreen.requestFocusInWindow();
            }
        });
        return btn;
    }

    /** Map PF key number (1–24) to the corresponding OHIO_AID enum. */
    private static Ohio.OHIO_AID pfKeyToAid(int pfNumber) {
        switch (pfNumber) {
            case 1:  return Ohio.OHIO_AID.OHIO_AID_3270_PF1;
            case 2:  return Ohio.OHIO_AID.OHIO_AID_3270_PF2;
            case 3:  return Ohio.OHIO_AID.OHIO_AID_3270_PF3;
            case 4:  return Ohio.OHIO_AID.OHIO_AID_3270_PF4;
            case 5:  return Ohio.OHIO_AID.OHIO_AID_3270_PF5;
            case 6:  return Ohio.OHIO_AID.OHIO_AID_3270_PF6;
            case 7:  return Ohio.OHIO_AID.OHIO_AID_3270_PF7;
            case 8:  return Ohio.OHIO_AID.OHIO_AID_3270_PF8;
            case 9:  return Ohio.OHIO_AID.OHIO_AID_3270_PF9;
            case 10: return Ohio.OHIO_AID.OHIO_AID_3270_PF10;
            case 11: return Ohio.OHIO_AID.OHIO_AID_3270_PF11;
            case 12: return Ohio.OHIO_AID.OHIO_AID_3270_PF12;
            case 13: return Ohio.OHIO_AID.OHIO_AID_3270_PF13;
            case 14: return Ohio.OHIO_AID.OHIO_AID_3270_PF14;
            case 15: return Ohio.OHIO_AID.OHIO_AID_3270_PF15;
            case 16: return Ohio.OHIO_AID.OHIO_AID_3270_PF16;
            case 17: return Ohio.OHIO_AID.OHIO_AID_3270_PF17;
            case 18: return Ohio.OHIO_AID.OHIO_AID_3270_PF18;
            case 19: return Ohio.OHIO_AID.OHIO_AID_3270_PF19;
            case 20: return Ohio.OHIO_AID.OHIO_AID_3270_PF20;
            case 21: return Ohio.OHIO_AID.OHIO_AID_3270_PF21;
            case 22: return Ohio.OHIO_AID.OHIO_AID_3270_PF22;
            case 23: return Ohio.OHIO_AID.OHIO_AID_3270_PF23;
            case 24: return Ohio.OHIO_AID.OHIO_AID_3270_PF24;
            default: return null;
        }
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

    /**
     * Map a KeyEvent to an F-key number (1–24), or 0 if not an F-key.
     */
    private static int fkeyNumberFromEvent(java.awt.event.KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= java.awt.event.KeyEvent.VK_F1 && code <= java.awt.event.KeyEvent.VK_F12) {
            return code - java.awt.event.KeyEvent.VK_F1 + 1;
        }
        if (code >= java.awt.event.KeyEvent.VK_F13 && code <= java.awt.event.KeyEvent.VK_F24) {
            return code - java.awt.event.KeyEvent.VK_F13 + 13;
        }
        return 0;
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
        return host + ":" + port;
    }

    /** Get the recorded macro steps as a JSON string for bookmark storage. */
    public String getMacroStepsJson() {
        return macroRecorder.toJson();
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }
}
