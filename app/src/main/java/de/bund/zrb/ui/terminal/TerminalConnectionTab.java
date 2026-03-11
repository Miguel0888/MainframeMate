 package de.bund.zrb.ui.terminal;

import com.ascert.open.term.core.Host;
import com.ascert.open.term.core.Terminal;
import com.ascert.open.term.core.TerminalFactoryRegistrar;
import com.ascert.open.term.gui.JTerminalScreen;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import java.awt.*;
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

    private final JPanel mainPanel;
    private final JLabel statusLabel;
    private final JToolBar connectionToolbar;
    private final JToolBar functionKeyToolbar;

    private JComponent centerComponent;
    private volatile Terminal terminal;
    private volatile JTerminalScreen terminalScreen;
    private volatile boolean connected;

    public TerminalConnectionTab(String host, int port, String termType, boolean tls, int keepAliveTimeout) {
        this.host = host;
        this.port = port;
        this.termType = termType != null ? termType : "IBM-3278-2";
        this.tls = tls;
        this.keepAliveTimeout = keepAliveTimeout;

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

                // Also grab focus when the mainPanel itself is clicked
                mainPanel.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        screen.requestFocusInWindow();
                    }
                });

                setCenterComponent(screen);
            }
        });
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
