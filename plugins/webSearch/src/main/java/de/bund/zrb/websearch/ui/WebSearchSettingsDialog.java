package de.bund.zrb.websearch.ui;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.browser.BrowserLauncher;
import de.bund.zrb.mcpserver.research.NetworkIngestionPipeline;
import de.bund.zrb.mcpserver.research.ResearchSession;
import de.bund.zrb.mcpserver.research.ResearchSessionManager;
import de.bund.zrb.websearch.plugin.WebSearchBrowserManager;
import de.bund.zrb.websearch.tools.BrowserToolAdapter;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings dialog for the WebSearch plugin.
 * Allows configuring browser type, headless mode, and URL boundaries.
 */
public class WebSearchSettingsDialog extends JDialog {

    private static final String PLUGIN_KEY = "webSearch";

    /** Default blacklist: block URLs whose host is a bare IP address (IPv4 or IPv6). */
    static final String DEFAULT_BLACKLIST =
            "# IPv4-Adressen blockieren (http(s)://123.45.67.89)\n"
          + "https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}([:/]|$)\n"
          + "# IPv6-Adressen blockieren (http(s)://[::1])\n"
          + "https?://\\[[:0-9a-fA-F]+\\]";

    private final MainframeContext context;
    private final WebSearchBrowserManager browserManager;
    private final JComboBox<String> browserCombo;
    private final JCheckBox headlessCheckbox;
    private final JTextField browserPathField;
    private final JSpinner timeoutSpinner;
    private final JSpinner debugPortSpinner;
    private final JTextArea whitelistArea;
    private final JTextArea blacklistArea;

    // ---- WebSocket Logging & Live Stats ----
    private final JCheckBox wsLoggingCheckbox;
    private final JLabel wsRxCountLabel;
    private final JLabel wsTxCountLabel;
    private final JLabel wsLastRxLabel;
    private final JLabel wsCongestionLabel;
    private final JLabel wsPipelineStatusLabel;
    private final JButton wsKillInterceptsButton;
    private Timer wsStatsTimer;
    private static final SimpleDateFormat TS_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    public WebSearchSettingsDialog(MainframeContext context, WebSearchBrowserManager browserManager) {
        super(context.getMainFrame(), "Websearch-Einstellungen", true);
        this.context = context;
        this.browserManager = browserManager;

        Map<String, String> settings = context.loadPluginSettings(PLUGIN_KEY);

        setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ── Browser ─────────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("Browser:"), gbc);

        browserCombo = new JComboBox<>(new String[]{"Firefox", "Chrome", "Edge"});
        String savedBrowser = settings.getOrDefault("browser", "Firefox");
        browserCombo.setSelectedItem(savedBrowser);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(browserCombo, gbc);


        // ── Browser-Pfad ────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Browser-Pfad:"), gbc);

        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        String savedBrowserPath = settings.getOrDefault("browserPath", "");
        String defaultBrowserPath = getDefaultPathForBrowser(savedBrowser);
        String displayPath = (savedBrowserPath != null && !savedBrowserPath.trim().isEmpty())
                ? savedBrowserPath : defaultBrowserPath;
        browserPathField = new JTextField(displayPath, 25);
        browserPathField.setToolTipText("Standard: " + defaultBrowserPath);
        pathPanel.add(browserPathField, BorderLayout.CENTER);

        JButton browseBtn = new JButton("...");
        browseBtn.setPreferredSize(new Dimension(30, 25));
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Browser-Executable auswählen");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                browserPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathPanel.add(browseBtn, BorderLayout.EAST);

        gbc.gridx = 1; gbc.weightx = 1;
        form.add(pathPanel, gbc);

        // ── Headless ────────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Modus:"), gbc);

        headlessCheckbox = new JCheckBox("Headless (ohne sichtbares Fenster)");
        boolean headless = !"false".equals(settings.getOrDefault("headless", "true"));
        headlessCheckbox.setSelected(headless);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(headlessCheckbox, gbc);

        // ── Debug-Port ──────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        form.add(new JLabel("Debug-Port:"), gbc);

        int savedDebugPort = 0;
        try {
            savedDebugPort = Integer.parseInt(settings.getOrDefault("debugPort", "0"));
        } catch (NumberFormatException ignored) {}
        debugPortSpinner = new JSpinner(new SpinnerNumberModel(savedDebugPort, 0, 65535, 1));
        debugPortSpinner.setToolTipText(
                "Remote-Debugging-Port für die Browser-Verbindung.\n"
              + "0 = automatisch einen freien Port wählen (Standard für Firefox).\n"
              + "9222 = typischer Chrome-Default.\n"
              + "Ein fester Port kann nützlich sein um sich von außen zu verbinden.");
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(debugPortSpinner, gbc);

        // ── Browser-Combo ActionListener ────────────────────────────
        // (registered after all fields are initialized to avoid NPE)
        browserCombo.addActionListener(e -> {
            String selected = (String) browserCombo.getSelectedItem();
            if (selected != null) {
                browserPathField.setText(getDefaultPathForBrowser(selected));
                browserPathField.setToolTipText("Standard: " + getDefaultPathForBrowser(selected));
                // Chrome/Edge default port is 9222, Firefox uses 0 (auto)
                if ("Chrome".equalsIgnoreCase(selected) || "Edge".equalsIgnoreCase(selected)) {
                    debugPortSpinner.setValue(9222);
                } else {
                    debugPortSpinner.setValue(0);
                }
            }
        });

        // ── Navigate-Timeout ────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0; gbc.gridwidth = 1;
        form.add(new JLabel("Navigate-Timeout (s):"), gbc);

        int savedTimeout = 30;
        try {
            savedTimeout = Integer.parseInt(settings.getOrDefault("navigateTimeoutSeconds", "30"));
        } catch (NumberFormatException ignored) {}
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(savedTimeout, 5, 300, 5));
        timeoutSpinner.setToolTipText("Maximale Wartezeit in Sekunden für eine Seitennavigation (Standard: 30)");
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(timeoutSpinner, gbc);

        // Apply timeout to system properties immediately
        System.setProperty("websearch.navigate.timeout.seconds", String.valueOf(savedTimeout));

        // ── Info-Label ──────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel(
                "<html><i>Die Browser-Tools (browser_navigate, browser_click_css, ...) werden "
                + "automatisch in der Tool-Registry registriert und stehen im Chat zur Verfügung.</i></html>");
        infoLabel.setForeground(Color.GRAY);
        form.add(infoLabel, gbc);

        // ── URL Whitelist ───────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel whitelistLabel = new JLabel("<html>URL-Whitelist<br><small>(Regex, pro Zeile)</small>:</html>");
        whitelistLabel.setToolTipText("Nur URLs, die einem Pattern matchen, werden erlaubt. Leer = alle erlaubt.");
        form.add(whitelistLabel, gbc);

        whitelistArea = new JTextArea(settings.getOrDefault("urlWhitelist", ""), 4, 30);
        whitelistArea.setToolTipText(
                "Regex-Patterns (ein Pattern pro Zeile). Beispiele:\n"
              + "  yahoo\\.com       → erlaubt alle Yahoo-URLs\n"
              + "  https://news\\.yahoo\\.com/.*  → nur Yahoo News\n"
              + "Zeilen mit # sind Kommentare. Leer = alle URLs erlaubt.");
        whitelistArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane whitelistScroll = new JScrollPane(whitelistArea);
        gbc.gridx = 1; gbc.weightx = 1; gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(whitelistScroll, gbc);

        // ── URL Blacklist ───────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel blacklistLabel = new JLabel("<html>URL-Blacklist<br><small>(Regex, pro Zeile)</small>:</html>");
        blacklistLabel.setToolTipText("URLs, die einem Blacklist-Pattern matchen, werden blockiert.");
        form.add(blacklistLabel, gbc);

        String savedBlacklist = settings.containsKey("urlBlacklist")
                ? settings.get("urlBlacklist")
                : DEFAULT_BLACKLIST;
        blacklistArea = new JTextArea(savedBlacklist, 4, 30);
        blacklistArea.setToolTipText(
                "Regex-Patterns (ein Pattern pro Zeile). Beispiele:\n"
              + "  ads\\.example\\.com  → blockiert Werbe-Domain\n"
              + "  \\.(exe|zip|msi)$   → blockiert Downloads\n"
              + "Blacklist hat Vorrang vor Whitelist.");
        blacklistArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane blacklistScroll = new JScrollPane(blacklistArea);
        gbc.gridx = 1; gbc.weightx = 1; gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(blacklistScroll, gbc);

        // ── Debug: WebSocket-Logging & Live-Stats ───────────────────
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2; gbc.weightx = 1;
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        JSeparator sep = new JSeparator();
        form.add(sep, gbc);

        gbc.gridx = 0; gbc.gridy = 9; gbc.gridwidth = 2;
        JLabel debugSectionLabel = new JLabel("Debug / WebSocket");
        debugSectionLabel.setFont(debugSectionLabel.getFont().deriveFont(Font.BOLD, 12f));
        form.add(debugSectionLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 10; gbc.gridwidth = 1; gbc.weightx = 0;
        form.add(new JLabel("WS-Logging:"), gbc);

        wsLoggingCheckbox = new JCheckBox("WebSocket-Frame-Logging aktivieren");
        wsLoggingCheckbox.setToolTipText("Aktiviert detailliertes Logging aller ein-/ausgehenden WebSocket-Frames (wd4j.log.websocket).");
        wsLoggingCheckbox.setSelected(Boolean.getBoolean("wd4j.log.websocket"));
        wsLoggingCheckbox.addActionListener(e -> {
            boolean enabled = wsLoggingCheckbox.isSelected();
            System.setProperty("wd4j.log.websocket", String.valueOf(enabled));
        });
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(wsLoggingCheckbox, gbc);

        // Live-Stats labels
        wsRxCountLabel = createStatsLabel();
        wsTxCountLabel = createStatsLabel();
        wsLastRxLabel = createStatsLabel();
        wsCongestionLabel = createStatsLabel();

        gbc.gridx = 0; gbc.gridy = 11; gbc.weightx = 0;
        form.add(new JLabel("Empfangene Frames:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(wsRxCountLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 12; gbc.weightx = 0;
        form.add(new JLabel("Gesendete Frames:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(wsTxCountLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 13; gbc.weightx = 0;
        form.add(new JLabel("Letzte Nachricht:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(wsLastRxLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 14; gbc.weightx = 0;
        form.add(new JLabel("Congestion-Status:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(wsCongestionLabel, gbc);

        // ── Pipeline Status ─────────────────────────────────────────
        wsPipelineStatusLabel = createStatsLabel();
        gbc.gridx = 0; gbc.gridy = 15; gbc.weightx = 0;
        form.add(new JLabel("Pipeline:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(wsPipelineStatusLabel, gbc);

        // ── Kill Intercepts Button ──────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 16; gbc.weightx = 0;
        form.add(new JLabel("Notfall:"), gbc);

        wsKillInterceptsButton = new JButton("🚨 Pipeline stoppen & zurücksetzen");
        wsKillInterceptsButton.setToolTipText(
                "Stoppt die NetworkIngestionPipeline und entfernt den DataCollector.\n"
              + "Das gibt den Browser-Speicher frei und kann bei Problemen helfen.\n"
              + "Die Pipeline wird beim nächsten research_navigate automatisch neu gestartet.");
        wsKillInterceptsButton.setForeground(new Color(180, 0, 0));
        wsKillInterceptsButton.addActionListener(e -> killAllIntercepts());
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(wsKillInterceptsButton, gbc);

        add(form, BorderLayout.CENTER);

        // ── Buttons ─────────────────────────────────────────────────
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton testButton = new JButton("🔌 Verbindung testen");
        testButton.setToolTipText("Startet den Browser und testet die WebDriver-BiDi-Verbindung");
        testButton.addActionListener(e -> {
            String browser = (String) browserCombo.getSelectedItem();
            String path = browserPathField.getText().trim();
            boolean hl = headlessCheckbox.isSelected();
            int port = ((Number) debugPortSpinner.getValue()).intValue();
            BrowserConnectionTestDialog testDialog = new BrowserConnectionTestDialog(this);
            testDialog.setVisible(true);
            testDialog.startTest(browser, path, hl, port);
        });
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Abbrechen");

        okButton.addActionListener(e -> {
            saveSettings();
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(testButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(550, 650));
        setLocationRelativeTo(context.getMainFrame());

        // ---- Lifecycle: Start/Stop stats timer based on dialog visibility ----
        addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) {
                        startStatsTimer();
                    } else {
                        stopStatsTimer();
                    }
                }
            }
        });
    }

    // ---- WebSocket Stats Helper Methods ----

    private static JLabel createStatsLabel() {
        JLabel label = new JLabel("–");
        label.setFont(label.getFont().deriveFont(Font.PLAIN));
        return label;
    }

    private void startStatsTimer() {
        if (wsStatsTimer != null && wsStatsTimer.isRunning()) {
            return;
        }
        wsStatsTimer = new Timer(1000, e -> updateStats());
        wsStatsTimer.setInitialDelay(0);
        wsStatsTimer.start();
    }

    private void stopStatsTimer() {
        if (wsStatsTimer != null) {
            wsStatsTimer.stop();
            wsStatsTimer = null;
        }
    }

    private void updateStats() {
        String rxCount = System.getProperty("wd4j.stats.rx.count", "0");
        String txCount = System.getProperty("wd4j.stats.tx.count", "0");
        String rxTs = System.getProperty("wd4j.stats.rx.lastTimestamp");

        wsRxCountLabel.setText(rxCount);
        wsTxCountLabel.setText(txCount);
        wsLastRxLabel.setText(formatTimestamp(rxTs));

        // Congestion detection: warn if last RX is more than 10s ago while there's traffic
        long rxCountVal = parseLong(rxCount);
        long rxTsVal = parseLong(rxTs);
        if (rxCountVal > 0 && rxTsVal > 0) {
            long silenceMs = System.currentTimeMillis() - rxTsVal;
            if (silenceMs > 10_000) {
                wsCongestionLabel.setText("⚠ Möglicherweise verstopft! Keine Nachricht seit " + (silenceMs / 1000) + " s");
                wsCongestionLabel.setForeground(new Color(180, 0, 0));
            } else {
                wsCongestionLabel.setText("OK (" + (silenceMs < 1000 ? "<1s" : (silenceMs / 1000) + " s") + " seit letzter Nachricht)");
                wsCongestionLabel.setForeground(new Color(0, 120, 0));
            }
        } else {
            wsCongestionLabel.setText("Keine Verbindung / Keine Daten");
            wsCongestionLabel.setForeground(Color.GRAY);
        }

        // Pipeline / Intercept status
        updatePipelineStatus();
    }

    /**
     * Updates the pipeline status label with information about the active
     * NetworkIngestionPipeline and its intercept.
     */
    private void updatePipelineStatus() {
        if (browserManager == null) {
            wsPipelineStatusLabel.setText("Kein BrowserManager");
            wsPipelineStatusLabel.setForeground(Color.GRAY);
            wsKillInterceptsButton.setEnabled(false);
            return;
        }

        BrowserSession session = browserManager.getExistingSession();
        if (session == null || !session.isConnected()) {
            wsPipelineStatusLabel.setText("Keine aktive Browser-Session");
            wsPipelineStatusLabel.setForeground(Color.GRAY);
            wsKillInterceptsButton.setEnabled(false);
            return;
        }

        ResearchSessionManager rsm = ResearchSessionManager.getInstance();
        ResearchSession rs = rsm != null ? rsm.get(session) : null;
        NetworkIngestionPipeline pipeline = rs != null ? rs.getNetworkPipeline() : null;

        if (pipeline == null) {
            wsPipelineStatusLabel.setText("Keine Pipeline aktiv");
            wsPipelineStatusLabel.setForeground(Color.GRAY);
            wsKillInterceptsButton.setEnabled(false);
        } else if (pipeline.isActive()) {
            String status = "✅ Aktiv – Captured=" + pipeline.getCapturedCount()
                    + " Skipped=" + pipeline.getSkippedCount()
                    + " Failed=" + pipeline.getFailedCount();
            wsPipelineStatusLabel.setText(status);
            wsPipelineStatusLabel.setForeground(new Color(0, 120, 0));
            wsKillInterceptsButton.setEnabled(true);
        } else {
            wsPipelineStatusLabel.setText("Pipeline gestoppt (inaktiv)");
            wsPipelineStatusLabel.setForeground(new Color(180, 120, 0));
            wsKillInterceptsButton.setEnabled(false);
        }
    }

    /**
     * Emergency action: stops the NetworkIngestionPipeline and removes the DataCollector.
     * This frees browser memory and can help when the pipeline is stuck.
     * The pipeline will be automatically restarted on the next research_navigate.
     */
    private void killAllIntercepts() {
        StringBuilder log = new StringBuilder();
        log.append("🚨 Pipeline-Reset gestartet...\n\n");

        try {
            // 1. Stop the pipeline (removes intercept, collector, event listeners)
            BrowserSession session = browserManager != null ? browserManager.getExistingSession() : null;
            if (session == null || !session.isConnected()) {
                JOptionPane.showMessageDialog(this,
                        "Keine aktive Browser-Session vorhanden.",
                        "Pipeline-Reset", JOptionPane.WARNING_MESSAGE);
                return;
            }

            ResearchSessionManager rsm = ResearchSessionManager.getInstance();
            ResearchSession rs = rsm != null ? rsm.get(session) : null;
            NetworkIngestionPipeline pipeline = rs != null ? rs.getNetworkPipeline() : null;

            if (pipeline != null && pipeline.isActive()) {
                log.append("1. Pipeline stoppen... ");
                try {
                    pipeline.stop();
                    log.append("✅ OK\n");
                } catch (Exception e) {
                    log.append("⚠ Fehler: ").append(e.getMessage()).append("\n");
                }
                // Detach pipeline from session so next navigate creates a fresh one
                rs.setNetworkPipeline(null);
            } else {
                log.append("1. Keine aktive Pipeline gefunden.\n");
            }

            // 2. As a safety measure, log the state.
            log.append("\n✅ Pipeline und DataCollector wurden zurückgesetzt.\n");
            log.append("Die Pipeline wird beim nächsten research_navigate automatisch neu gestartet.\n");
            log.append("\nFalls der Browser immer noch eingefroren ist, kann ein Seiten-Reload helfen.");

            // Force an immediate stats update
            updatePipelineStatus();

        } catch (Exception e) {
            log.append("\n❌ Fehler: ").append(e.getMessage());
        }

        JOptionPane.showMessageDialog(this,
                log.toString(),
                "Pipeline-Reset", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String formatTimestamp(String epochMs) {
        if (epochMs == null || epochMs.isEmpty()) return "–";
        try {
            long ts = Long.parseLong(epochMs);
            if (ts <= 0) return "–";
            return TS_FORMAT.format(new Date(ts));
        } catch (NumberFormatException e) {
            return "–";
        }
    }

    private static long parseLong(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private void saveSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("browser", (String) browserCombo.getSelectedItem());
        settings.put("headless", String.valueOf(headlessCheckbox.isSelected()));
        settings.put("browserPath", browserPathField.getText().trim());
        String timeoutVal = String.valueOf(timeoutSpinner.getValue());
        settings.put("navigateTimeoutSeconds", timeoutVal);
        settings.put("debugPort", String.valueOf(debugPortSpinner.getValue()));
        settings.put("urlWhitelist", whitelistArea.getText());
        settings.put("urlBlacklist", blacklistArea.getText());
        context.savePluginSettings(PLUGIN_KEY, settings);

        // Apply timeout to system properties so tools pick it up immediately
        System.setProperty("websearch.navigate.timeout.seconds", timeoutVal);

        // Reload URL boundary checker with new settings
        BrowserToolAdapter.reloadBoundaries(settings);
    }

    /**
     * Returns the default executable path for the given browser name.
     */
    private static String getDefaultPathForBrowser(String browser) {
        if (browser == null) return BrowserLauncher.DEFAULT_FIREFOX_PATH;
        switch (browser.toLowerCase()) {
            case "chrome":
                return BrowserLauncher.DEFAULT_CHROME_PATH;
            case "edge":
                return BrowserLauncher.resolveEdgePath();
            default:
                return BrowserLauncher.DEFAULT_FIREFOX_PATH;
        }
    }
}

