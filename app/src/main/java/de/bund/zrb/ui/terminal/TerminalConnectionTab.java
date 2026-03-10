package de.bund.zrb.ui.terminal;

import com.ascert.open.term.core.Host;
import com.ascert.open.term.core.TerminalFactoryRegistrar;
import com.ascert.open.term.gui.EmulatorPanel;
import com.ascert.open.term.i3270.Term3270Factory;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ConnectionTab wrapping an OpenTerm 3270 terminal emulator.
 * <p>
 * Embeds an {@link EmulatorPanel} in MainframeMate as a regular tab.
 * The terminal never triggers {@code System.exit()} because we
 * set {@code EmulatorPanel.setEmbeddedUse(true)}.
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
    private EmulatorPanel emulatorPanel;
    private volatile boolean connected;

    public TerminalConnectionTab(String host, int port, String termType, boolean tls, int keepAliveTimeout) {
        this.host = host;
        this.port = port;
        this.termType = termType != null ? termType : "IBM-3278-2";
        this.tls = tls;
        this.keepAliveTimeout = keepAliveTimeout;

        ensureFactoryInitialized();

        mainPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("  Verbinde…");

        JToolBar toolbar = createToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // Placeholder while connecting
        JLabel connecting = new JLabel("Verbinde mit " + host + ":" + port + " …", SwingConstants.CENTER);
        connecting.setFont(connecting.getFont().deriveFont(Font.ITALIC, 14f));
        mainPanel.add(connecting, BorderLayout.CENTER);
    }

    /**
     * Register OpenTerm terminal factories exactly once.
     */
    private static void ensureFactoryInitialized() {
        if (FACTORY_INITIALIZED.compareAndSet(false, true)) {
            try {
                TerminalFactoryRegistrar.initTermTypeFactories(Term3270Factory.class.getName());
                LOG.info("[3270] Terminal factories registered.");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[3270] Failed to register terminal factories", e);
                FACTORY_INITIALIZED.set(false);
            }
        }
    }

    /**
     * Create and connect the EmulatorPanel.
     * Must be called on a background thread; UI updates via SwingUtilities.
     */
    public void connect() throws Exception {
        Host h = new Host(host, port, termType, tls, keepAliveTimeout);

        // Build the emulator panel on EDT
        SwingUtilities.invokeAndWait(() -> {
            try {
                emulatorPanel = new EmulatorPanel(h, true);
                emulatorPanel.setEmbeddedUse(true);
            } catch (Exception e) {
                throw new RuntimeException("EmulatorPanel creation failed: " + e.getMessage(), e);
            }
        });

        // Trigger connection via init (public API)
        emulatorPanel.init(host, port, termType, tls, keepAliveTimeout);
        connected = true;

        // Replace placeholder on EDT
        SwingUtilities.invokeLater(() -> {
            mainPanel.removeAll();
            mainPanel.add(createToolbar(), BorderLayout.NORTH);
            mainPanel.add(emulatorPanel, BorderLayout.CENTER);
            statusLabel.setText("  ✅ Verbunden");
            mainPanel.revalidate();
            mainPanel.repaint();
        });
    }

    /**
     * Disconnect the terminal session.
     */
    public void disconnect() {
        if (emulatorPanel != null) {
            try {
                emulatorPanel.disconnect();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[3270] Disconnect error", e);
            }
        }
        connected = false;
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("  ⛔ Getrennt");
        });
    }

    /**
     * Reconnect from UI (background thread).
     */
    private void reconnect() {
        statusLabel.setText("  ⏳ Verbinde…");
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
                    statusLabel.setText("  ❌ Fehler");
                    JOptionPane.showMessageDialog(mainPanel,
                            "Verbindung fehlgeschlagen:\n" + cause.getMessage(),
                            "3270-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JButton connectBtn = new JButton("🔌 Verbinden");
        connectBtn.setToolTipText("Verbindung (wieder-)herstellen");
        connectBtn.addActionListener(e -> reconnect());
        toolbar.add(connectBtn);

        JButton disconnectBtn = new JButton("⛔ Trennen");
        disconnectBtn.setToolTipText("Terminalsession trennen");
        disconnectBtn.addActionListener(e -> {
            disconnect();
        });
        toolbar.add(disconnectBtn);

        toolbar.addSeparator(new Dimension(16, 0));

        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statusLabel);

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
        // not applicable for terminal
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
