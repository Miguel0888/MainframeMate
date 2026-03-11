 package de.bund.zrb.ui.terminal;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private final JToolBar functionKeyToolbar;

    private JComponent centerComponent;
    private volatile Terminal terminal;
    private volatile JTerminalScreen terminalScreen;
    private volatile boolean connected;

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
        this.functionKeyToolbar = createFunctionKeyToolbar();

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(connectionToolbar, BorderLayout.NORTH);
        northPanel.add(functionKeyToolbar, BorderLayout.CENTER);

        this.centerComponent = createMessageLabel("Verbinde mit " + host + ":" + port + " …");

        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(centerComponent, BorderLayout.CENTER);
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
                    if (terminalScreen != null) {
                        // Ensure the screen component is fully visible before requesting focus.
                        // A short timer ensures the layout has completed.
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
                JTerminalScreen screen = new JTerminalScreen(createdTerminal, functionKeyToolbar);
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
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                functionKeyToolbar.removeAll();
                functionKeyToolbar.revalidate();
                functionKeyToolbar.repaint();
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
     * Sequence: position cursor → type user → CLEAR → wait → type password → ENTER.
     * Runs on a background thread.
     */
    private void autoLogin(final Terminal term) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1) Wait for the host to send the login screen
                    if (!waitForKeyboardUnlock(term, 10_000)) {
                        LOG.warning("[3270] Auto-login: keyboard did not unlock within timeout");
                        return;
                    }
                    Thread.sleep(500);

                    // 2) Position cursor at the first unprotected (input) field
                    short firstField = term.getNextUnprotectedField(0);
                    if (firstField >= 0) {
                        term.setCursorPosition(firstField);
                    }

                    // 3) Type username
                    typeString(term, user);
                    Thread.sleep(100);

                    // 4) Send CLEAR to advance to the password field
                    term.Fkey(AID_CLEAR);

                    // 5) Wait for host to process CLEAR and unlock keyboard again
                    if (!waitForKeyboardUnlock(term, 10_000)) {
                        LOG.warning("[3270] Auto-login: keyboard did not unlock after CLEAR");
                        return;
                    }
                    Thread.sleep(500);

                    // 6) Position cursor at the (now first) unprotected field = password
                    firstField = term.getNextUnprotectedField(0);
                    if (firstField >= 0) {
                        term.setCursorPosition(firstField);
                    }

                    // 7) Type password
                    typeString(term, password);
                    Thread.sleep(100);

                    // 8) Send ENTER
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

        toolbar.addSeparator(new Dimension(16, 0));
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statusLabel);

        return toolbar;
    }

    private JToolBar createFunctionKeyToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        return toolbar;
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
