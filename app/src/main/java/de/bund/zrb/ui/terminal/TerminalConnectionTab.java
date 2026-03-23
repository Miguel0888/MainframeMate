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
    private volatile int actionDelayMs;   // delay in ms after AID keys during auto-login/macro
    private volatile int fkeyOverlayOpacity; // 0..100 percent opacity for F-key overlay

    private final JPanel mainPanel;
    private final JLabel statusDot;
    private final JToolBar connectionToolbar;
    private final JPanel fkeyPanel;

    private JComponent centerComponent;
    private volatile Terminal terminal;
    private volatile JTerminalScreen terminalScreen;
    private volatile boolean connected;

    /** Timer that periodically reads the last screen line to update F-key buttons. */
    private Timer fkeyRefreshTimer;

    /** Reference to the cosmic clock panel (if enabled) so we can stop it on disconnect. */
    private volatile de.bund.zrb.ui.terminal.cosmicclock.CosmicClockPanel cosmicClockInstance;

    /** Current mouse→F-key bindings (mutable, updated live from settings). */
    private volatile java.util.List<de.bund.zrb.model.MouseFkeyBinding> mouseBindings;

    /** Reference to the background panel (cosmic clock or plain black) in the overlay hierarchy. */
    private volatile JPanel backgroundPanelRef;

    /** Reference to the overlay container that holds backgroundPanel + fkeyPanel. */
    private volatile JPanel overlayContainerRef;

    /** Settings change listener – stored so we can unregister on close. */
    private final java.util.function.Consumer<de.bund.zrb.model.Settings> settingsListener;

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

        // Load action delay and overlay opacity from settings
        int delayFromSettings = 1000;
        int opacityFromSettings = 50;
        try {
            de.bund.zrb.model.Settings s = de.bund.zrb.helper.SettingsHelper.load();
            delayFromSettings = s.tn3270ActionDelayMs;
            opacityFromSettings = s.tn3270FkeyOverlayOpacity;
            this.mouseBindings = s.tn3270MouseFkeyBindings;
        } catch (Exception ignored) { }
        this.actionDelayMs = Math.max(delayFromSettings, 0);
        this.fkeyOverlayOpacity = Math.max(0, Math.min(100, opacityFromSettings));
        if (this.mouseBindings == null) this.mouseBindings = de.bund.zrb.model.MouseFkeyBinding.getDefaults();

        // Register for live settings updates
        this.settingsListener = this::onSettingsChanged;
        de.bund.zrb.helper.SettingsHelper.addChangeListener(settingsListener);

        ensureFactoryInitialized();

        this.mainPanel = new JPanel(new BorderLayout());
        this.statusDot = createStatusDot();
        setStatus(Status.IDLE);
        this.connectionToolbar = createConnectionToolbar();
        this.fkeyPanel = createFkeyPanel();

        this.centerComponent = createMessageLabel("Verbinde mit " + host + ":" + port + " …");

        mainPanel.add(connectionToolbar, BorderLayout.NORTH);
        mainPanel.add(centerComponent, BorderLayout.CENTER);
        // fkeyPanel is NOT added to BorderLayout.SOUTH — it floats as a
        // translucent overlay inside the terminal area (see buildScreenOnEdt).
    }

    // ── Live settings application ───────────────────────────────

    /**
     * Called by the {@link de.bund.zrb.helper.SettingsHelper} listener whenever
     * any settings are saved.  Applies relevant terminal settings immediately
     * to this tab without requiring an application restart.
     */
    private void onSettingsChanged(de.bund.zrb.model.Settings s) {
        SwingUtilities.invokeLater(() -> {
            // ── Action delay ──
            actionDelayMs = Math.max(s.tn3270ActionDelayMs, 0);

            // ── EBCDIC codepage (takes effect immediately for the running session) ──
            patchEbcdicCodePage();

            // ── F-key overlay opacity ──
            int newOpacity = Math.max(0, Math.min(100, s.tn3270FkeyOverlayOpacity));
            if (newOpacity != fkeyOverlayOpacity) {
                fkeyOverlayOpacity = newOpacity;
                fkeyPanel.repaint();
            }

            // ── Mouse bindings ──
            mouseBindings = s.tn3270MouseFkeyBindings != null
                    ? s.tn3270MouseFkeyBindings
                    : de.bund.zrb.model.MouseFkeyBinding.getDefaults();

            // ── Cosmic clock time factor ──
            if (cosmicClockInstance != null) {
                cosmicClockInstance.getTimeModel().setTimeFactor(s.cosmicClockTimeFactor);
                cosmicClockInstance.setUseGermanNames(s.cosmicClockGermanNames);
            }

            // ── Cosmic clock enable/disable toggle ──
            boolean wantCosmic = s.cosmicClockEnabled;
            boolean haveCosmic = cosmicClockInstance != null;
            JTerminalScreen screen = terminalScreen;
            JPanel overlay = overlayContainerRef;
            JPanel oldBg = backgroundPanelRef;

            if (wantCosmic != haveCosmic && screen != null && overlay != null && oldBg != null) {
                // Detach screen from current background
                oldBg.remove(screen);

                // Stop old cosmic clock if active
                if (cosmicClockInstance != null) {
                    cosmicClockInstance.stop();
                    cosmicClockInstance = null;
                }

                // Create new background
                JPanel newBg;
                if (wantCosmic) {
                    de.bund.zrb.ui.terminal.cosmicclock.CosmicClockPanel cc =
                            new de.bund.zrb.ui.terminal.cosmicclock.CosmicClockPanel(s.cosmicClockTimeFactor);
                    cc.setUseGermanNames(s.cosmicClockGermanNames);
                    cc.setLayout(new GridBagLayout());
                    cc.add(screen, new GridBagConstraints());
                    cc.start();
                    cc.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override public void mousePressed(java.awt.event.MouseEvent e) {
                            JTerminalScreen ts = terminalScreen;
                            if (ts != null) ts.requestFocusInWindow();
                        }
                    });
                    cosmicClockInstance = cc;
                    newBg = cc;
                } else {
                    JPanel blackBg = new JPanel(new GridBagLayout());
                    blackBg.setOpaque(true);
                    blackBg.setBackground(Color.BLACK);
                    blackBg.add(screen, new GridBagConstraints());
                    blackBg.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override public void mousePressed(java.awt.event.MouseEvent e) {
                            JTerminalScreen ts = terminalScreen;
                            if (ts != null) ts.requestFocusInWindow();
                        }
                    });
                    newBg = blackBg;
                }

                // Swap in overlay container: remove old background, add new one
                overlay.remove(oldBg);
                overlay.add(newBg);   // added last → painted first (behind fkeyPanel)
                backgroundPanelRef = newBg;

                // Re-attach resize listener for font scaling
                overlay.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        scaleTerminalFont(terminalScreen, backgroundPanelRef);
                    }
                });

                overlay.revalidate();
                overlay.repaint();

                // Trigger font re-scaling with the new background
                scaleTerminalFont(screen, newBg);
            }
        });
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
     * Patch the EBCDIC↔Unicode translation tables in {@code Tn3270StreamParser}
     * to match the configured codepage (e.g. Cp273 for German).
     * <p>
     * OpenTerm ships with a hardcoded US EBCDIC (CP037) table.  German mainframes
     * use CP273 where, for example, EBCDIC 0xC0 = ä (not '{'), 0xD0 = ö (not '}'),
     * and 0x5A = Ü (not '!').  This method replaces the static {@code ebc2asc} and
     * {@code asc2ebc} arrays at runtime using Java's built-in Charset support.
     */
    private void patchEbcdicCodePage() {
        String cpName = "Cp273"; // default
        try {
            de.bund.zrb.model.Settings s = de.bund.zrb.helper.SettingsHelper.load();
            if (s.tn3270CodePage != null && !s.tn3270CodePage.trim().isEmpty()) {
                cpName = s.tn3270CodePage.trim();
            }
        } catch (Exception ignored) { }

        try {
            java.nio.charset.Charset cs = java.nio.charset.Charset.forName(cpName);

            // Build ebc2asc: for each EBCDIC byte 0x00..0xFF, decode to Unicode
            int[] newEbc2Asc = new int[256];
            for (int i = 0; i < 256; i++) {
                byte[] b = { (byte) i };
                String decoded = new String(b, cs);
                newEbc2Asc[i] = decoded.charAt(0);
            }

            // Build asc2ebc: for each Unicode code point 0x00..0xFF, encode to EBCDIC
            int[] newAsc2Ebc = new int[256];
            java.nio.charset.CharsetEncoder enc = cs.newEncoder()
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE);
            for (int i = 0; i < 256; i++) {
                char c = (char) i;
                try {
                    java.nio.ByteBuffer bb = enc.encode(java.nio.CharBuffer.wrap(new char[]{c}));
                    newAsc2Ebc[i] = bb.get(0) & 0xFF;
                } catch (Exception e) {
                    newAsc2Ebc[i] = 0; // unmappable → null
                }
            }

            // Patch the static arrays in Tn3270StreamParser
            int[] ebc2asc = com.ascert.open.term.i3270.Tn3270StreamParser.ebc2asc;
            int[] asc2ebc = com.ascert.open.term.i3270.Tn3270StreamParser.asc2ebc;
            System.arraycopy(newEbc2Asc, 0, ebc2asc, 0, 256);
            System.arraycopy(newAsc2Ebc, 0, asc2ebc, 0, 256);

            LOG.info("[3270] EBCDIC codepage patched to " + cpName);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[3270] Failed to patch EBCDIC codepage '" + cpName + "', "
                    + "falling back to built-in CP037 tables", e);
        }
    }

    /**
     * Create a Terminal via the factory, build the JTerminalScreen on the EDT,
     * then connect. Must be called on a background thread.
     */
    public void connect() throws Exception {
        // Patch EBCDIC translation tables BEFORE creating the terminal, so
        // the parser uses the correct codepage for display and keyboard input.
        patchEbcdicCodePage();

        final Host terminalHost = new Host(host, port, termType, tls, keepAliveTimeout);
        final Terminal createdTerminal = TerminalFactoryRegistrar.createTerminal(terminalHost);
        final AtomicReference<JTerminalScreen> screenRef = new AtomicReference<JTerminalScreen>();

        setStatus(Status.CONNECTING);

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
                    setStatus(Status.CONNECTED);
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
            setStatus(Status.ERROR);
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
                dummyToolbar.setPreferredSize(new Dimension(0, 0));
                dummyToolbar.setMaximumSize(new Dimension(0, 0));
                JTerminalScreen screen = new JTerminalScreen(createdTerminal, dummyToolbar);
                screenRef.set(screen);

                // OpenTerm may have added the dummyToolbar into JTerminalScreen's
                // layout — detach it so it can never become visible as a ghost bar.
                if (dummyToolbar.getParent() != null) {
                    dummyToolbar.getParent().remove(dummyToolbar);
                }

                // JTerminalScreen.paintComponent() only draws a fixed-size
                // frameBuff image at (0,0) — it does NOT fill its entire
                // bounds and does NOT call super.paintComponent().  By
                // default it is opaque, so Swing trusts it to paint its
                // whole area, but it doesn't — leaving stale pixels from
                // the back-buffer visible (ghost images of the toolbar and
                // F-key bar).  Setting it non-opaque forces Swing to paint
                // the parent's black background before drawing the terminal.
                screen.setOpaque(false);

                // Make the screen focusable and request focus on click.
                // Disable Swing focus-traversal so Tab etc. reach OpenTerm.
                screen.setFocusable(true);
                screen.setRequestFocusEnabled(true);
                screen.setFocusTraversalKeysEnabled(false);

                // Arrow keys are handled in the KeyListener below.
                // We do NOT modify OpenTerm's InputMap/ActionMap because
                // init() (called during connect) replaces them entirely,
                // discarding any pre-connect customizations.

                screen.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        screen.requestFocusInWindow();
                        // Right-click context menu
                        if (e.isPopupTrigger()) {
                            showTerminalContextMenu(screen, createdTerminal, e.getX(), e.getY());
                        }
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        // Windows fires popup trigger on release
                        if (e.isPopupTrigger()) {
                            showTerminalContextMenu(screen, createdTerminal, e.getX(), e.getY());
                        }
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
                // Handle Ctrl+V (paste) and Ctrl+C (copy) before OpenTerm processes the keys.
                screen.addKeyListener(new java.awt.event.KeyAdapter() {
                    @Override
                    public void keyPressed(java.awt.event.KeyEvent e) {
                        // Ctrl+V → paste from clipboard
                        if (e.getKeyCode() == java.awt.event.KeyEvent.VK_V && e.isControlDown() && !e.isAltDown()) {
                            pasteFromClipboard(createdTerminal);
                            e.consume();
                            return;
                        }
                        // Ctrl+C → copy selection to clipboard
                        if (e.getKeyCode() == java.awt.event.KeyEvent.VK_C && e.isControlDown() && !e.isAltDown()) {
                            copySelectionToClipboard(screen, createdTerminal);
                            e.consume();
                            return;
                        }

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
                        // Arrow keys → move 3270 cursor
                        int akc = e.getKeyCode();
                        if (akc == java.awt.event.KeyEvent.VK_LEFT
                                || akc == java.awt.event.KeyEvent.VK_RIGHT
                                || akc == java.awt.event.KeyEvent.VK_UP
                                || akc == java.awt.event.KeyEvent.VK_DOWN) {
                            moveCursorByArrowKey(createdTerminal, akc);
                            screen.refresh();
                            e.consume();
                            return;
                        }
                    }

                    @Override
                    public void keyReleased(java.awt.event.KeyEvent e) {
                        int fnum = fkeyNumberFromEvent(e);
                        if (fnum > 0) {
                            JButton btn = fkeyButtons.get(fnum);
                            if (btn != null) {
                                // IMPORTANT: disarm BEFORE unpress!
                                // DefaultButtonModel.setPressed(false) fires the ActionListener
                                // if the button is still armed.  That would send the F-key to
                                // the host a second time (OpenTerm already handles the keyboard
                                // F-key event itself).
                                btn.getModel().setArmed(false);
                                btn.getModel().setPressed(false);
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

                // ── Build overlay: terminal centred, F-key bar at the bottom ──
                // Load settings for cosmic clock
                boolean cosmicEnabled = true;
                double clockFactor = 120;
                boolean germanNames = true;
                try {
                    de.bund.zrb.model.Settings cs = de.bund.zrb.helper.SettingsHelper.load();
                    cosmicEnabled = cs.cosmicClockEnabled;
                    clockFactor = cs.cosmicClockTimeFactor;
                    germanNames = cs.cosmicClockGermanNames;
                } catch (Exception ignored) { }

                // Background panel: either animated cosmic clock or plain black
                final JPanel backgroundPanel;
                de.bund.zrb.ui.terminal.cosmicclock.CosmicClockPanel cosmicClockRef = null;
                if (cosmicEnabled) {
                    de.bund.zrb.ui.terminal.cosmicclock.CosmicClockPanel cc =
                            new de.bund.zrb.ui.terminal.cosmicclock.CosmicClockPanel(clockFactor);
                    cc.setUseGermanNames(germanNames);
                    cc.setLayout(new GridBagLayout());
                    cc.add(screen, new GridBagConstraints());
                    cc.start();
                    backgroundPanel = cc;
                    cosmicClockRef = cc;
                } else {
                    JPanel blackBg = new JPanel(new GridBagLayout());
                    blackBg.setOpaque(true);
                    blackBg.setBackground(Color.BLACK);
                    blackBg.add(screen, new GridBagConstraints());
                    backgroundPanel = blackBg;
                }
                final de.bund.zrb.ui.terminal.cosmicclock.CosmicClockPanel cosmicClock = cosmicClockRef;
                cosmicClockInstance = cosmicClockRef;
                backgroundPanelRef = backgroundPanel;

                // Also grab focus when the background is clicked
                backgroundPanel.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        screen.requestFocusInWindow();
                    }
                });

                // ── Mouse → F-key bindings (buttons + scroll wheel) ─────────
                final long[] lastMouseFkeySentAt = {0};
                final int MOUSE_DEBOUNCE_MS = 150;

                screen.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        // Buttons 4 (back) and 5 (forward) on extended mice
                        de.bund.zrb.model.MouseFkeyBinding.MouseAction action = null;
                        if (e.getButton() == 4) action = de.bund.zrb.model.MouseFkeyBinding.MouseAction.BACK;
                        else if (e.getButton() == 5) action = de.bund.zrb.model.MouseFkeyBinding.MouseAction.FORWARD;
                        if (action == null) return;

                        java.util.List<de.bund.zrb.model.MouseFkeyBinding> currentBindings = mouseBindings;
                        boolean shift = e.isShiftDown(), ctrl = e.isControlDown(), alt = e.isAltDown();
                        for (de.bund.zrb.model.MouseFkeyBinding b : currentBindings) {
                            if (b.mouseAction == action && b.modifierMatches(shift, ctrl, alt)) {
                                sendFkeyFromMouse(createdTerminal, b.fkey);
                                e.consume();
                                return;
                            }
                        }
                    }
                });
                screen.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
                    @Override
                    public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                        int rotation = e.getWheelRotation();
                        if (rotation == 0) return;

                        // Debounce: prevent flooding the host with rapid scroll events
                        long now = System.currentTimeMillis();
                        if (now - lastMouseFkeySentAt[0] < MOUSE_DEBOUNCE_MS) { e.consume(); return; }

                        de.bund.zrb.model.MouseFkeyBinding.MouseAction action =
                                rotation > 0 ? de.bund.zrb.model.MouseFkeyBinding.MouseAction.SCROLL_DOWN
                                             : de.bund.zrb.model.MouseFkeyBinding.MouseAction.SCROLL_UP;
                        java.util.List<de.bund.zrb.model.MouseFkeyBinding> currentBindings = mouseBindings;
                        boolean shift = e.isShiftDown(), ctrl = e.isControlDown(), alt = e.isAltDown();
                        for (de.bund.zrb.model.MouseFkeyBinding b : currentBindings) {
                            if (b.mouseAction == action && b.modifierMatches(shift, ctrl, alt)) {
                                lastMouseFkeySentAt[0] = now;
                                sendFkeyFromMouse(createdTerminal, b.fkey);
                                e.consume();
                                return;
                            }
                        }
                    }
                });

                // ── Overlay container with a custom LayoutManager ──────────
                // The terminal (backgroundPanel) always fills the full area;
                // the F-key panel is anchored at the bottom edge.
                final JPanel overlayContainer = new JPanel() {
                    @Override
                    public boolean isOptimizedDrawingEnabled() {
                        return false; // children overlap
                    }
                };
                overlayContainer.setOpaque(true);
                overlayContainer.setBackground(Color.BLACK);
                overlayContainer.setLayout(new LayoutManager() {
                    @Override public void addLayoutComponent(String name, Component comp) { }
                    @Override public void removeLayoutComponent(Component comp) { }
                    @Override public Dimension preferredLayoutSize(Container parent) {
                        return backgroundPanel.getPreferredSize();
                    }
                    @Override public Dimension minimumLayoutSize(Container parent) {
                        return new Dimension(0, 0);
                    }
                    @Override
                    public void layoutContainer(Container parent) {
                        int w = parent.getWidth();
                        int h = parent.getHeight();
                        backgroundPanel.setBounds(0, 0, w, h);
                        int fkeyH = fkeyPanel.getPreferredSize().height;
                        if (fkeyH <= 0) fkeyH = 30;
                        fkeyPanel.setBounds(0, h - fkeyH, w, fkeyH);
                    }
                });

                overlayContainer.add(fkeyPanel);
                overlayContainer.add(backgroundPanel);
                overlayContainerRef = overlayContainer;

                // Scale terminal font when the container is resized
                overlayContainer.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        scaleTerminalFont(screen, backgroundPanel);
                    }
                });

                setCenterComponent(overlayContainer);
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

        // JTerminalScreen draws into a frameBuff that includes margins around the grid
        int marginX = JTerminalScreen.MARGIN_X;  // 10
        int marginY = JTerminalScreen.MARGIN_Y;  //  6

        // Calculate max font size that fits (including margins)
        Font currentFont = screen.getFont();
        String fontFamily = currentFont != null ? currentFont.getFamily() : "Monospaced";

        int bestSize = 8;
        for (int size = 8; size <= 40; size++) {
            Font testFont = new Font(fontFamily, Font.PLAIN, size);
            FontMetrics fm = screen.getFontMetrics(testFont);
            int totalWidth  = fm.charWidth('M') * cols + 2 * marginX;
            int totalHeight = fm.getHeight() * rows     + 2 * marginY;

            if (totalWidth <= availableWidth && totalHeight <= availableHeight) {
                bestSize = size;
            } else {
                break;
            }
        }

        Font currentScreenFont = screen.getFont();
        if (currentScreenFont == null || currentScreenFont.getSize() != bestSize) {
            Font bestFont = new Font(fontFamily, Font.PLAIN, bestSize);
            screen.setFont(bestFont);

            // Set the preferred size to the exact frameBuff pixel dimensions
            // so GridBagLayout centres the terminal in the available space.
            FontMetrics fm = screen.getFontMetrics(bestFont);
            int prefW = fm.charWidth('M') * cols + 2 * marginX;
            int prefH = fm.getHeight() * rows     + 2 * marginY;
            screen.setPreferredSize(new Dimension(prefW, prefH));

            screen.revalidate();
            screen.repaint();
        }
    }

    public void disconnect() {
        Terminal currentTerminal = this.terminal;
        clearTerminalState();
        disconnectQuietly(currentTerminal);
        // Stop the cosmic clock animation timer
        if (cosmicClockInstance != null) {
            cosmicClockInstance.stop();
            cosmicClockInstance = null;
        }
        clearFunctionKeyToolbar();
        showDisconnectedMessage("Verbindung getrennt.");
        setStatus(Status.DISCONNECTED);
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
                Container parent = fkeyPanel.getParent();
                if (parent != null) {
                    parent.revalidate();
                    parent.repaint();
                } else {
                    fkeyPanel.repaint();
                }
            }
        });
    }

    // ── Menu / Action-Bar click detection ─────────────────────

    /**
     * Called shortly after a mouse click on the terminal screen.
     * If the cursor is positioned on a selectable menu item (= a <b>protected</b>
     * field that contains visible text), send ENTER automatically.
     * <p>
     * Detection strategy:
     * <ul>
     *   <li><b>Rows 0–1</b> (action bar): always auto-enter for protected fields with text.</li>
     *   <li><b>Rows 2 … (rows−4)</b> (pulldown / popup area): auto-enter when
     *       <ul>
     *         <li>the field uses CUA-style highlighting (reverse video, alt-intensity, underscore), <b>OR</b></li>
     *         <li>the screen line looks like a pulldown menu item — bordered by {@code |}
     *             with a leading number (e.g. {@code | 3  Save   |}).</li>
     *       </ul>
     *   </li>
     *   <li><b>Bottom 3 rows</b> (command line, message line, PF-key legend): never auto-enter.</li>
     * </ul>
     */
    private void autoEnterIfMenuItem(Terminal term) {
        if (term == null || !connected) return;
        if (term.isKeyboardLocked()) return;

        int cols = term.getCols();
        int rows = term.getRows();
        if (cols <= 0 || rows <= 0) return;

        int cursorPos = term.getCursorPosition();
        int cursorRow = cursorPos / cols;   // 0-based

        // Bottom 3 rows are command line / message / PF-key legend → never
        if (cursorRow >= rows - 3) return;

        try {
            com.ascert.open.term.core.TermField field = term.getField(cursorPos);
            if (field == null) return;

            // Only auto-enter for PROTECTED fields.
            // Unprotected fields are input areas → don't interfere.
            if (!field.isProtected()) return;

            // Read the field text — if it has any visible non-space chars, it's a candidate
            int begin = field.getBeginBA() + 1; // skip attribute byte
            int end = field.getEndBA();
            int len = end - begin + 1;
            if (len <= 0 || len > cols) return;

            String fieldText = term.getCharString(begin, len);
            if (fieldText == null || fieldText.trim().length() == 0) return;

            // ── Action bar area (rows 0–1): always selectable ───
            if (cursorRow <= 1) {
                LOG.fine("[3270] Action-bar click at row " + cursorRow
                        + ", field='" + fieldText.trim() + "' → sending ENTER");
                term.Fkey(AID_ENTER);
                return;
            }

            // ── Pulldown / popup area (rows 2+) ────────────────

            // Check 1: CUA visual attributes (reverse video, highlight, underscore)
            if (fieldHasMenuStyle(field)) {
                LOG.fine("[3270] Menu click at row " + cursorRow
                        + ", field='" + fieldText.trim() + "' (highlighted) → sending ENTER");
                term.Fkey(AID_ENTER);
                return;
            }

            // Check 2: Line looks like a pipe-bordered pulldown menu item
            //           e.g.  "  | 3  Save        |  "
            if (looksLikePulldownMenuLine(term, cursorRow, cols)) {
                LOG.fine("[3270] Pulldown menu click at row " + cursorRow
                        + ", field='" + fieldText.trim() + "' (pipe-bordered) → sending ENTER");
                term.Fkey(AID_ENTER);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[3270] autoEnterIfMenuItem error", e);
        }
    }

    /**
     * Check whether the screen line at {@code row} looks like a pulldown menu item.
     * <p>
     * Typical 3270 pulldown format:
     * <pre>
     *   | 1  New        |
     *   | 2  Open       |
     *   | 3  Save       |
     *   | 10 Long name  |
     * </pre>
     * Detection heuristic:
     * <ol>
     *   <li>The line contains at least two vertical-border characters
     *       ({@code |}, {@code │}, {@code ║}).</li>
     *   <li>The content between the outermost borders starts (after optional spaces)
     *       with one or more digits — the menu-item selection number.</li>
     * </ol>
     */
    private static boolean looksLikePulldownMenuLine(Terminal term, int row, int cols) {
        String line;
        try {
            line = term.getCharString(row * cols, cols);
        } catch (Exception e) {
            return false;
        }
        if (line == null || line.isEmpty()) return false;

        // Find leftmost and rightmost border character
        int firstBorder = -1;
        int lastBorder = -1;
        for (int i = 0; i < line.length(); i++) {
            if (isBorderChar(line.charAt(i))) {
                if (firstBorder < 0) firstBorder = i;
                lastBorder = i;
            }
        }

        // Need at least two border chars with content between them
        if (firstBorder < 0 || lastBorder - firstBorder < 2) return false;

        // Extract content between the outermost borders
        String between = line.substring(firstBorder + 1, lastBorder);
        String trimmed = between.trim();
        if (trimmed.isEmpty()) return false;

        // First non-space character must be a digit (menu-item number)
        return Character.isDigit(trimmed.charAt(0));
    }

    /** Characters used as vertical borders in 3270 pulldown menus. */
    private static boolean isBorderChar(char c) {
        return c == '|' || c == '│' || c == '┃' || c == '║' || c == '¦';
    }

    /**
     * Check whether a protected field has the visual styling typical of
     * selectable CUA menu items: <b>reverse video</b>, <b>alternate intensity</b>
     * (high-intensity / bold), or <b>underscore</b>.
     */
    private static boolean fieldHasMenuStyle(com.ascert.open.term.core.TermField field) {
        // 1) Check data characters for video attributes
        com.ascert.open.term.core.TermChar[] chars = field.getChars();
        if (chars != null) {
            for (com.ascert.open.term.core.TermChar c : chars) {
                if (c == null || c.isStartField()) continue;  // skip attribute byte
                return c.isReverse() || c.isAltIntensity() || c.isUnderscore();
            }
        }
        // 2) Fallback: check the field-attribute character itself
        com.ascert.open.term.core.TermChar fa = field.getFAChar();
        if (fa != null) {
            return fa.isReverse() || fa.isAltIntensity();
        }
        return false;
    }

    // ── Arrow-key cursor movement ─────────────────────────────────

    /**
     * Move the 3270 cursor by one position in the direction indicated by the
     * given {@code VK_*} arrow key code.  The cursor wraps around at screen
     * boundaries (standard 3270 behaviour).
     */
    private void moveCursorByArrowKey(Terminal term, int keyCode) {
        if (term == null || !connected) return;

        int cols = term.getCols();
        int rows = term.getRows();
        if (cols <= 0 || rows <= 0) return;

        int totalCells = rows * cols;
        int pos = term.getCursorPosition();

        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_LEFT:
                pos = (pos - 1 + totalCells) % totalCells;
                break;
            case java.awt.event.KeyEvent.VK_RIGHT:
                pos = (pos + 1) % totalCells;
                break;
            case java.awt.event.KeyEvent.VK_UP:
                pos = (pos - cols + totalCells) % totalCells;
                break;
            case java.awt.event.KeyEvent.VK_DOWN:
                pos = (pos + cols) % totalCells;
                break;
            default:
                return;
        }

        term.setCursorPosition((short) pos);
    }

    // ── Clipboard: Paste / Copy ─────────────────────────────────

    /**
     * Paste text from the system clipboard into the terminal at the current cursor position.
     * Each character is typed via InputCharHandler. Newlines are ignored (3270 fields are single-line).
     */
    private void pasteFromClipboard(Terminal term) {
        if (term == null || !connected) return;
        if (term.isKeyboardLocked()) return;

        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) return;

            String text = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            if (text == null || text.isEmpty()) return;

            // Strip newlines/tabs — 3270 fields don't support them
            text = text.replace("\r\n", " ").replace("\r", " ").replace("\n", " ").replace("\t", " ");

            com.ascert.open.term.core.InputCharHandler charHandler = term.getCharHandler();
            for (int i = 0; i < text.length(); i++) {
                charHandler.type(text.charAt(i));
            }
            macroRecorder.recordText(text);

            LOG.fine("[3270] Pasted " + text.length() + " characters");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[3270] Paste failed", e);
        }
    }

    /**
     * Copy the current terminal screen selection (or entire screen if no selection) to the clipboard.
     */
    private void copySelectionToClipboard(JTerminalScreen screen, Terminal term) {
        if (term == null || !connected) return;

        try {
            String text = null;

            // Try to get selection from JTerminalScreen
            int selStart = screen.getSelectionStartPos();
            int selEnd = screen.getSelectionEndPos();
            if (selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
                int from = Math.min(selStart, selEnd);
                int to = Math.max(selStart, selEnd);
                int len = to - from + 1;
                text = term.getCharString(from, len);
            }

            // Fallback: copy entire screen
            if (text == null || text.isEmpty()) {
                char[] display = term.getDisplay();
                if (display != null) {
                    int cols = term.getCols();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < display.length; i++) {
                        sb.append(display[i]);
                        if (cols > 0 && (i + 1) % cols == 0 && i + 1 < display.length) {
                            sb.append('\n');
                        }
                    }
                    text = sb.toString();
                }
            }

            if (text != null && !text.isEmpty()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(text), null);
                LOG.fine("[3270] Copied " + text.length() + " characters to clipboard");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[3270] Copy failed", e);
        }
    }

    /**
     * Show a right-click context menu on the terminal screen with Paste, Copy, Select All.
     */
    private void showTerminalContextMenu(JTerminalScreen screen, Terminal term, int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem pasteItem = new JMenuItem("📋 Einfügen");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        pasteItem.addActionListener(e -> {
            pasteFromClipboard(term);
            screen.requestFocusInWindow();
        });
        pasteItem.setEnabled(connected && term != null && !term.isKeyboardLocked());
        menu.add(pasteItem);

        JMenuItem copyItem = new JMenuItem("📄 Kopieren");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        copyItem.addActionListener(e -> {
            copySelectionToClipboard(screen, term);
            screen.requestFocusInWindow();
        });
        copyItem.setEnabled(connected && term != null);
        menu.add(copyItem);

        menu.addSeparator();

        JMenuItem selectAllItem = new JMenuItem("🔲 Alles auswählen");
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        selectAllItem.addActionListener(e -> {
            screen.selectAll();
            screen.requestFocusInWindow();
        });
        selectAllItem.setEnabled(connected);
        menu.add(selectAllItem);

        menu.show(screen, x, y);
    }

    // ── Auto-Login ──────────────────────────────────────────────

    private static final com.ascert.open.ohio.Ohio.OHIO_AID AID_ENTER =
            com.ascert.open.ohio.Ohio.OHIO_AID.OHIO_AID_3270_ENTER;
    private static final com.ascert.open.ohio.Ohio.OHIO_AID AID_CLEAR =
            com.ascert.open.ohio.Ohio.OHIO_AID.OHIO_AID_3270_CLEAR;

    /**
     * Automatically log in after connect.
     * <p>
     * Sequence: CLEAR → wait → type user → Tab to next field → type password → ENTER.
     * Uses OpenTerm's field navigation (same as Tab key) to move between fields.
     * Runs on a background thread.
     */
    private void autoLogin(final Terminal term) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int delay = actionDelayMs;

                    // 1) Wait for the host to send the initial screen
                    if (!waitForKeyboardUnlock(term, 10_000)) {
                        LOG.warning("[3270] Auto-login: keyboard did not unlock within timeout");
                        return;
                    }
                    Thread.sleep(delay);

                    // 2) Send CLEAR to reset the screen to a clean login prompt
                    term.Fkey(AID_CLEAR);
                    if (!waitForKeyboardUnlock(term, 10_000)) {
                        LOG.warning("[3270] Auto-login: keyboard did not unlock after CLEAR");
                        return;
                    }
                    Thread.sleep(delay);

                    // 3) Find the first unprotected field = userid
                    short useridField = term.getNextUnprotectedField(0);
                    if (useridField < 0) {
                        LOG.warning("[3270] Auto-login: no unprotected field found for userid");
                        return;
                    }
                    LOG.info("[3270] Auto-login: userid field at position " + useridField);
                    term.setCursorPosition(useridField);

                    // 4) Type username
                    typeString(term, user);
                    Thread.sleep(delay);

                    // 5) Tab to password field — use the current cursor position
                    //    (same mechanism as OpenTerm's TabAction: find the next
                    //    unprotected field AFTER where the cursor is right now)
                    int cursorAfterUser = term.getCursorPosition();
                    short passwordField = term.getNextUnprotectedField(cursorAfterUser);
                    LOG.info("[3270] Auto-login: cursor after user=" + cursorAfterUser
                            + ", password field at position " + passwordField);

                    if (passwordField < 0 || passwordField == useridField) {
                        LOG.warning("[3270] Auto-login: no second unprotected field found for password"
                                + " (useridField=" + useridField + ", passwordField=" + passwordField + ")");
                        return;
                    }
                    term.setCursorPosition(passwordField);

                    // 6) Type password
                    typeString(term, password);
                    Thread.sleep(delay);

                    // 7) Send ENTER to submit both userid and password
                    term.Fkey(AID_ENTER);

                    LOG.info("[3270] Auto-login: credentials sent");
                    setStatus(Status.LOGGED_IN);

                    // 8) Auto-command after login (e.g. "a" + ENTER to skip welcome screen)
                    if (autoCommand != null && !autoCommand.isEmpty()) {
                        if (!waitForKeyboardUnlock(term, 10_000)) {
                            LOG.warning("[3270] Auto-command: keyboard did not unlock after login");
                            return;
                        }
                        Thread.sleep(delay);

                        typeString(term, autoCommand);
                        Thread.sleep(delay);
                        term.Fkey(AID_ENTER);
                        LOG.info("[3270] Auto-command sent: '" + autoCommand + "'");
                    }

                    // 9) Replay macro steps if this is a bookmark session
                    if (replaySteps != null && !replaySteps.isEmpty()) {
                        if (!waitForKeyboardUnlock(term, 10_000)) {
                            LOG.warning("[3270] Macro replay: keyboard did not unlock");
                            return;
                        }
                        Thread.sleep(delay);
                        new TerminalMacroPlayer(term, replaySteps, delay).play();
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

    // ── Status indicator (colored dot) ────────────────────────

    private enum Status {
        IDLE       (new Color(160, 160, 160), "Bereit"),          // gray
        CONNECTING (new Color(240, 200,  40), "Verbinde…"),       // yellow
        CONNECTED  (new Color( 40, 200,  60), "Verbunden"),       // green
        LOGGED_IN  (new Color( 40, 200,  60), "Angemeldet"),      // green
        ERROR      (new Color(220,  50,  50), "Fehler"),          // red
        DISCONNECTED(new Color(220, 50,  50), "Getrennt");        // red

        final Color color;
        final String tooltip;
        Status(Color color, String tooltip) { this.color = color; this.tooltip = tooltip; }
    }

    private JLabel createStatusDot() {
        JLabel dot = new JLabel("●");
        dot.setFont(dot.getFont().deriveFont(Font.PLAIN, 16f));
        dot.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 6));
        return dot;
    }

    private void setStatus(final Status status) {
        SwingUtilities.invokeLater(() -> {
            statusDot.setForeground(status.color);
            statusDot.setToolTipText(status.tooltip);
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
        setStatus(Status.CONNECTING);
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
                    setStatus(Status.ERROR);
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

        // Status dot at the very left
        toolbar.add(statusDot);

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

        // History dropdown button
        JButton historyBtn = new JButton("📜");
        historyBtn.setToolTipText("Eingabe-Historie (Makro-Aufzeichnung)");
        historyBtn.setMargin(new Insets(2, 6, 2, 6));
        historyBtn.setFocusable(false);
        historyBtn.addActionListener(e -> showHistoryPopup(historyBtn));
        toolbar.add(historyBtn);


        return toolbar;
    }

    /**
     * Show a popup with the recorded macro history.
     * Each step is a row with text + a copy button.
     * A "clear" button at the top resets the recorder.
     */
    private void showHistoryPopup(JComponent anchor) {
        List<Map<String, String>> steps = macroRecorder.getSteps();

        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());

        // Header with clear button
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JLabel titleLabel = new JLabel("📜 Eingabe-Historie (" + steps.size() + ")");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        header.add(titleLabel, BorderLayout.WEST);

        JButton clearBtn = new JButton("🗑 Leeren");
        clearBtn.setMargin(new Insets(1, 6, 1, 6));
        clearBtn.setFont(clearBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearBtn.setFocusable(false);
        clearBtn.addActionListener(e -> {
            macroRecorder.clear();
            popup.setVisible(false);
        });
        header.add(clearBtn, BorderLayout.EAST);
        popup.add(header, BorderLayout.NORTH);

        // Steps list in a scrollable panel
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        if (steps.isEmpty()) {
            JLabel emptyLabel = new JLabel("  (keine Eingaben aufgezeichnet)");
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 11f));
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            listPanel.add(emptyLabel);
        } else {
            for (int i = 0; i < steps.size(); i++) {
                Map<String, String> step = steps.get(i);
                listPanel.add(createHistoryRow(i + 1, step));
            }
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setPreferredSize(new Dimension(350, Math.min(steps.size() * 28 + 20, 400)));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        popup.add(scrollPane, BorderLayout.CENTER);

        popup.show(anchor, 0, anchor.getHeight());
    }

    /**
     * Create a single row in the history popup: "[#] TYPE: value  [📋]"
     */
    private JPanel createHistoryRow(int index, Map<String, String> step) {
        String type = step.getOrDefault("type", "?");
        String value = step.getOrDefault("value", "");

        // Format display text
        String displayText;
        Color typeColor;
        if ("TEXT".equals(type)) {
            displayText = "\"" + value + "\"";
            typeColor = new Color(0, 100, 0);
        } else {
            // AID — make it more readable
            displayText = formatAidName(value);
            typeColor = new Color(0, 50, 150);
        }

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        // Index label
        JLabel indexLabel = new JLabel(String.valueOf(index));
        indexLabel.setFont(indexLabel.getFont().deriveFont(Font.PLAIN, 10f));
        indexLabel.setForeground(Color.GRAY);
        indexLabel.setPreferredSize(new Dimension(24, 20));
        indexLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(indexLabel, BorderLayout.WEST);

        // Content label
        JLabel contentLabel = new JLabel(displayText);
        contentLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        contentLabel.setForeground(typeColor);
        contentLabel.setToolTipText(type + ": " + value);
        row.add(contentLabel, BorderLayout.CENTER);

        // Copy button
        JButton copyBtn = new JButton("📋");
        copyBtn.setMargin(new Insets(0, 2, 0, 2));
        copyBtn.setPreferredSize(new Dimension(24, 20));
        copyBtn.setFont(copyBtn.getFont().deriveFont(10f));
        copyBtn.setFocusable(false);
        copyBtn.setToolTipText("In Zwischenablage kopieren");
        final String copyValue = value;
        copyBtn.addActionListener(e -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(copyValue), null);
            copyBtn.setText("✓");
            Timer reset = new Timer(1000, evt -> copyBtn.setText("📋"));
            reset.setRepeats(false);
            reset.start();
        });
        row.add(copyBtn, BorderLayout.EAST);

        return row;
    }

    /** Format an OHIO_AID enum name to a human-readable short form. */
    private static String formatAidName(String aidName) {
        if (aidName == null) return "?";
        // OHIO_AID_3270_ENTER → ⏎ Enter
        // OHIO_AID_3270_PF3 → F3
        // OHIO_AID_3270_CLEAR → ⌧ Clear
        // OHIO_AID_3270_PA1 → PA1
        if (aidName.contains("ENTER")) return "⏎ Enter";
        if (aidName.contains("CLEAR")) return "⌧ Clear";
        if (aidName.contains("SYSREQ")) return "⚡ SysReq";
        if (aidName.contains("PA1")) return "PA1";
        if (aidName.contains("PA2")) return "PA2";
        if (aidName.contains("PA3")) return "PA3";
        // PF keys
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("PF(\\d+)").matcher(aidName);
        if (m.find()) return "F" + m.group(1);
        return aidName;
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
        // The F-key panel is drawn as a translucent overlay on top of the
        // terminal screen.  We override paintComponent to fill a semi-
        // transparent background whose alpha is controlled by the user
        // setting tn3270FkeyOverlayOpacity (0–100%).
        // The opacity field is volatile and updated live from settings.
        JPanel outer = new JPanel(new GridLayout(0, 1, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                int alpha = (int) Math.round(fkeyOverlayOpacity / 100.0 * 255);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                // do NOT call super — we painted the background ourselves
            }
        };
        outer.setOpaque(false); // let the custom paintComponent handle it
        outer.setBackground(UIManager.getColor("Panel.background") != null
                ? UIManager.getColor("Panel.background") : new Color(240, 240, 240));
        outer.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        return outer;
    }


    // ── F-Key Legend Parsing ─────────────────────────────────────

    /**
     * Pattern matching F-key legend entries in standard inline formats:
     *   "F1=Help"  "F3:End"  "F10-Actions"  "1=Help"  "PF1=Help"  "PF3:End"
     * Group 1 = key number, Group 2 = label text.
     * <p>
     * The label capture {@code ([A-Za-z][\\w./#@]*)} requires labels to start
     * with a letter.  This prevents false matches on Natural-style positional
     * legends where keys are separated by dashes: {@code PF1---PF2---PF3---}.
     */
    private static final Pattern FKEY_PATTERN = Pattern.compile(
            "(?:PF|F)?(\\d{1,2})\\s*[=:\\-]\\s*([A-Za-z][\\w./#@]*)");

    /**
     * Pattern matching multi-key legend entries where two (or more) keys share
     * the same label, separated by {@code /}:
     *   "PF1/PF13:Help"  "F3/F15=Exit"  "1/13-Actions"
     * Group 1 = first key, Group 2 = second key, Group 3 = label.
     */
    private static final Pattern FKEY_MULTI_PATTERN = Pattern.compile(
            "(?:PF|F)?(\\d{1,2})/(?:PF|F)?(\\d{1,2})\\s*[=:\\-]\\s*([A-Za-z][\\w./#@]*)");

    /**
     * Detects Natural-style positional F-key legend.
     * Line 1: {@code Enter-PF1---PF2---PF3---PF4---...---PF12---}
     * Line 2: labels aligned underneath the corresponding keys.
     */
    private static final Pattern NATURAL_FKEY_LINE = Pattern.compile(
            "PF\\d+---");

    /** Pattern to find individual PF key positions on a Natural legend line. */
    private static final Pattern NATURAL_PF_KEY = Pattern.compile(
            "PF(\\d{1,2})");

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
     * F1–F12 are <b>always</b> visible.  Assigned keys use the full group
     * color; unassigned keys use a pale/desaturated accent of that color so
     * the user can gauge the key positions at a glance.
     * <p>
     * F13–F24 (extended) are shown <b>only</b> when at least one of them is
     * assigned on the current screen.  When shown, all twelve buttons appear
     * (again, unassigned ones are pale).
     * <p>
     * Row 1 (standard):  F1–F4 left │ F5–F8 center │ F9–F12 right
     * Row 2 (extended):  F13–F16 left │ F17–F20 center │ F21–F24 right
     */
    private void refreshFkeyLegend() {
        Terminal t = terminal;
        if (t == null || !connected) return;

        int cols = t.getCols();
        int rows = t.getRows();
        if (cols <= 0 || rows <= 0) return;

        // Read the last two rows (F-key legend often spans 2 lines)
        String lastTwoLines = "";
        try {
            int startPos = (rows - 2) * cols;
            lastTwoLines = t.getCharString(startPos, cols * 2);
        } catch (Exception ex) {
            return;
        }

        if (lastTwoLines == null || lastTwoLines.length() < cols) return;
        String trimmed = lastTwoLines.trim();
        if (trimmed.equals(lastFkeyLegend)) return;
        lastFkeyLegend = trimmed;

        // Split into the two physical screen lines
        String line1 = lastTwoLines.substring(0, cols);
        String line2 = lastTwoLines.length() >= cols * 2
                ? lastTwoLines.substring(cols, cols * 2) : "";

        // Parse F-key assignments from the legend text
        Map<Integer, String> parsed = new LinkedHashMap<Integer, String>();

        // ── Try Natural-style positional format first ──────────────
        // Detected by "PF<n>---" on either line (keys separated by
        // dashes, labels on the OTHER line aligned by column position).
        boolean naturalOnLine1 = NATURAL_FKEY_LINE.matcher(line1).find();
        boolean naturalOnLine2 = NATURAL_FKEY_LINE.matcher(line2).find();

        if (naturalOnLine1) {
            parseNaturalFkeyLegend(line1, line2, parsed);
        } else if (naturalOnLine2) {
            parseNaturalFkeyLegend(line2, line1, parsed);
        }

        // ── Fall back to standard inline format ────────────────────
        // "F1=Help  F3=Exit  F12=Cancel"  /  "PF1:Help  PF3:End"
        // Also handles multi-key entries: "PF1/PF13:Help"
        if (parsed.isEmpty()) {
            // First pass: multi-key entries like "PF1/PF13:Help"
            Matcher mm = FKEY_MULTI_PATTERN.matcher(trimmed);
            while (mm.find()) {
                String label = mm.group(3);
                int num1 = Integer.parseInt(mm.group(1));
                int num2 = Integer.parseInt(mm.group(2));
                if (num1 >= 1 && num1 <= 24) parsed.put(num1, label);
                if (num2 >= 1 && num2 <= 24) parsed.put(num2, label);
            }
            // Second pass: single-key entries like "F3=Exit"
            Matcher m = FKEY_PATTERN.matcher(trimmed);
            while (m.find()) {
                int num = Integer.parseInt(m.group(1));
                if (num >= 1 && num <= 24 && !parsed.containsKey(num)) {
                    parsed.put(num, m.group(2));
                }
            }
        }

        // ── Build sub-panels for standard keys (row 1): always all F1–F12 ──
        JPanel leftStd   = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        JPanel centerStd = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 1));
        JPanel rightStd  = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 1));
        leftStd.setOpaque(false);
        centerStd.setOpaque(false);
        rightStd.setOpaque(false);

        fkeyButtons.clear();

        for (int fnum = 1; fnum <= 12; fnum++) {
            JButton btn;
            if (parsed.containsKey(fnum)) {
                btn = makeFkeyButton(fnum, parsed.get(fnum));
            } else {
                btn = makeFkeyPlaceholder(fnum);
            }
            fkeyButtons.put(fnum, btn);

            if (fnum <= 4)       leftStd.add(btn);
            else if (fnum <= 8)  centerStd.add(btn);
            else                 rightStd.add(btn);
        }

        // ── Extended keys (row 2): only if at least one F13–F24 is assigned ──
        boolean hasExtended = false;
        for (int fnum = 13; fnum <= 24; fnum++) {
            if (parsed.containsKey(fnum)) { hasExtended = true; break; }
        }

        JPanel leftExt   = null;
        JPanel centerExt = null;
        JPanel rightExt  = null;

        if (hasExtended) {
            leftExt   = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
            centerExt = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 1));
            rightExt  = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 1));
            leftExt.setOpaque(false);
            centerExt.setOpaque(false);
            rightExt.setOpaque(false);

            for (int fnum = 13; fnum <= 24; fnum++) {
                JButton btn;
                if (parsed.containsKey(fnum)) {
                    btn = makeFkeyButton(fnum, parsed.get(fnum));
                } else {
                    btn = makeFkeyPlaceholder(fnum);
                }
                fkeyButtons.put(fnum, btn);

                if (fnum <= 16)      leftExt.add(btn);
                else if (fnum <= 20) centerExt.add(btn);
                else                 rightExt.add(btn);
            }
        }

        // ── Assemble rows ──
        JPanel row1 = new JPanel(new BorderLayout(8, 0));
        row1.setOpaque(false);
        row1.add(leftStd, BorderLayout.WEST);
        row1.add(centerStd, BorderLayout.CENTER);
        row1.add(rightStd, BorderLayout.EAST);

        fkeyPanel.removeAll();
        fkeyPanel.add(row1);

        if (hasExtended) {
            JPanel row2 = new JPanel(new BorderLayout(8, 0));
            row2.setOpaque(false);
            row2.add(leftExt, BorderLayout.WEST);
            row2.add(centerExt, BorderLayout.CENTER);
            row2.add(rightExt, BorderLayout.EAST);
            fkeyPanel.add(row2);
        }

        fkeyPanel.revalidate();
        fkeyPanel.repaint();

        // Trigger parent relayout so the overlay height adjusts to the new content
        Container parent = fkeyPanel.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    /**
     * Parse Natural-style positional F-key legend.
     * <p>
     * {@code keysLine} contains the PF key names separated by dashes:
     * <pre>Enter-PF1---PF2---PF3---PF4---...---PF12---</pre>
     * {@code labelsLine} contains the labels aligned by column position:
     * <pre>      Help        Exit                   Canc</pre>
     * <p>
     * Each label word on {@code labelsLine} is assigned to the PF key whose
     * column zone contains the label's start position.  A key's zone runs
     * from the start of its name to just before the next key's name.
     */
    private void parseNaturalFkeyLegend(String keysLine, String labelsLine,
                                         Map<Integer, String> parsed) {
        // 1) Find all PF key positions on the keys line
        Matcher km = NATURAL_PF_KEY.matcher(keysLine);
        List<int[]> keys = new ArrayList<int[]>(); // {keyNumber, startColumn}
        while (km.find()) {
            int keyNum = Integer.parseInt(km.group(1));
            if (keyNum >= 1 && keyNum <= 24) {
                keys.add(new int[]{keyNum, km.start()});
            }
        }
        if (keys.isEmpty()) return;

        // 2) Find all label words on the labels line and assign each
        //    to the PF key whose zone contains the label's start column.
        //    A key's zone extends from its start column to the start of
        //    the next key (or end of line for the last key).
        Matcher wm = Pattern.compile("\\S+").matcher(labelsLine);
        while (wm.find()) {
            String label = wm.group();
            int labelCol = wm.start();

            for (int i = 0; i < keys.size(); i++) {
                int zoneStart = keys.get(i)[1];
                int zoneEnd = (i + 1 < keys.size())
                        ? keys.get(i + 1)[1] : labelsLine.length();
                if (labelCol >= zoneStart && labelCol < zoneEnd) {
                    parsed.put(keys.get(i)[0], label);
                    break;
                }
            }
        }
    }

    private JButton makeFkeyButton(final int fkeyNumber, String label) {
        JButton btn = new JButton("F" + fkeyNumber + "-" + label);
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 11f));
        btn.setFocusable(false);
        btn.setToolTipText("F" + fkeyNumber + " – " + label);

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

    /**
     * Create a pale placeholder button for an F-key that is not assigned
     * on the current screen.  The button is still functional (sends the
     * PF key to the host when clicked), but its washed-out colour signals
     * that no legend label is active for it.
     */
    private JButton makeFkeyPlaceholder(final int fkeyNumber) {
        JButton btn = new JButton("F" + fkeyNumber);
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 11f));
        btn.setFocusable(false);
        btn.setToolTipText("F" + fkeyNumber + " (nicht belegt)");

        Color bg = getFkeyColorPale(fkeyNumber);
        btn.setBackground(bg);
        btn.setOpaque(true);
        btn.setForeground(new Color(160, 160, 160)); // muted gray text

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
     * Send an F-key from a mouse action (button click or scroll wheel).
     * Animates the corresponding F-key button and records the AID for macros.
     */
    private void sendFkeyFromMouse(Terminal t, int fkeyNumber) {
        if (t == null || !connected) return;
        Ohio.OHIO_AID aid = pfKeyToAid(fkeyNumber);
        if (aid == null) return;

        macroRecorder.recordAid(aid);
        t.Fkey(aid);

        // Visual feedback on the F-key button
        JButton btn = fkeyButtons.get(fkeyNumber);
        if (btn != null) {
            btn.getModel().setArmed(true);
            btn.getModel().setPressed(true);
            Timer release = new Timer(120, e -> {
                // Disarm BEFORE unpress so the ActionListener is not fired again
                btn.getModel().setArmed(false);
                btn.getModel().setPressed(false);
            });
            release.setRepeats(false);
            release.start();
        }

        if (terminalScreen != null) terminalScreen.requestFocusInWindow();
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

    /**
     * Compute a pale/washed-out background colour for an unassigned F-key.
     * Takes the full group colour from {@link #getFkeyColor(int)} and blends
     * it 75 % toward white, producing a light pastel that still hints at
     * the group (pale yellow, pale orange, pale red, etc.).
     */
    private static Color getFkeyColorPale(int fkey) {
        Color full = getFkeyColor(fkey);
        Color white = new Color(255, 255, 255);
        return blend(full, white, 0.75f);
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
        de.bund.zrb.helper.SettingsHelper.removeChangeListener(settingsListener);
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
