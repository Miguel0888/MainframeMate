package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DebugSettingsPanel extends AbstractSettingsPanel {

    private static final String[] LOG_LEVELS = {"OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"};
    private static final String[] LOG_CATEGORIES = {
            de.bund.zrb.util.AppLogger.MAIL,
            de.bund.zrb.util.AppLogger.STAR,
            de.bund.zrb.util.AppLogger.FTP,
            de.bund.zrb.util.AppLogger.NDV,
            de.bund.zrb.util.AppLogger.TOOL,
            de.bund.zrb.util.AppLogger.INDEX,
            de.bund.zrb.util.AppLogger.SEARCH,
            de.bund.zrb.util.AppLogger.RAG,
            de.bund.zrb.util.AppLogger.UI,
            de.bund.zrb.util.AppLogger.PLUGIN,
            de.bund.zrb.util.AppLogger.BROWSER
    };

    private final JComboBox<String> globalLogLevelCombo;
    private final java.util.Map<String, JComboBox<String>> categoryLevelCombos = new java.util.LinkedHashMap<>();

    // ---- WebSocket Logging & Live Stats ----
    private final JCheckBox wsLoggingCheckbox;
    private final JLabel wsRxCountLabel;
    private final JLabel wsTxCountLabel;
    private final JLabel wsLastRxLabel;
    private final JLabel wsLastTxLabel;
    private final JLabel wsCongestionLabel;
    private Timer wsStatsTimer;

    private static final SimpleDateFormat TS_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    public DebugSettingsPanel() {
        super("debug", "Debug");
        FormBuilder fb = new FormBuilder();

        globalLogLevelCombo = new JComboBox<>(LOG_LEVELS);
        globalLogLevelCombo.setSelectedItem(settings.logLevel != null ? settings.logLevel : "INFO");
        globalLogLevelCombo.setToolTipText("INFO = normal, FINE = Debug, FINEST = alles");
        fb.addRow("Globales Log-Level:", globalLogLevelCombo);

        fb.addSection("Kategorie-Log-Level");
        fb.addInfo("Überschreibt das globale Level für einzelne Kategorien.");

        String[] catLevelsWithDefault = {"(global)", "OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"};
        for (String cat : LOG_CATEGORIES) {
            JComboBox<String> combo = new JComboBox<>(catLevelsWithDefault);
            String current = settings.logCategoryLevels != null ? settings.logCategoryLevels.get(cat) : null;
            combo.setSelectedItem(current != null && !current.isEmpty() ? current : "(global)");
            categoryLevelCombos.put(cat, combo);
            fb.addRow(cat + ":", combo);
        }

        // ---- WebSocket Logging Section ----
        fb.addSection("WebSocket (BiDi)");
        fb.addInfo("Aktiviert detailliertes Logging aller ein- und ausgehenden WebSocket-Frames (wd4j.log.websocket).");

        wsLoggingCheckbox = new JCheckBox("WebSocket-Logging aktivieren");
        wsLoggingCheckbox.setSelected(Boolean.getBoolean("wd4j.log.websocket"));
        wsLoggingCheckbox.addActionListener(e -> {
            boolean enabled = wsLoggingCheckbox.isSelected();
            System.setProperty("wd4j.log.websocket", String.valueOf(enabled));
        });
        fb.addWide(wsLoggingCheckbox);

        fb.addGap(4);
        fb.addInfo("Live-Statistiken der WebSocket-Verbindung (aktualisiert sich automatisch, solange dieser Tab sichtbar ist):");

        wsRxCountLabel = createStatsLabel();
        wsTxCountLabel = createStatsLabel();
        wsLastRxLabel = createStatsLabel();
        wsLastTxLabel = createStatsLabel();
        wsCongestionLabel = createStatsLabel();

        fb.addRow("Empfangene Frames:", wsRxCountLabel);
        fb.addRow("Gesendete Frames:", wsTxCountLabel);
        fb.addRow("Letzte Nachricht (RX):", wsLastRxLabel);
        fb.addRow("Letzte Nachricht (TX):", wsLastTxLabel);
        fb.addRow("Congestion-Status:", wsCongestionLabel);

        installPanel(fb);

        // ---- Lifecycle: Start/Stop stats timer based on visibility ----
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

    private static JLabel createStatsLabel() {
        JLabel label = new JLabel("–");
        label.setFont(label.getFont().deriveFont(Font.PLAIN));
        return label;
    }

    // ---- Stats Timer ----

    private void startStatsTimer() {
        if (wsStatsTimer != null && wsStatsTimer.isRunning()) {
            return;
        }
        wsStatsTimer = new Timer(1000, e -> updateStats());
        wsStatsTimer.setInitialDelay(0); // sofort erste Aktualisierung
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
        String txTs = System.getProperty("wd4j.stats.tx.lastTimestamp");

        wsRxCountLabel.setText(rxCount);
        wsTxCountLabel.setText(txCount);
        wsLastRxLabel.setText(formatTimestamp(rxTs));
        wsLastTxLabel.setText(formatTimestamp(txTs));

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
    }

    private static String formatTimestamp(String epochMs) {
        if (epochMs == null || epochMs.isEmpty()) {
            return "–";
        }
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
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.logLevel = (String) globalLogLevelCombo.getSelectedItem();
        s.logCategoryLevels.clear();
        for (java.util.Map.Entry<String, JComboBox<String>> entry : categoryLevelCombos.entrySet()) {
            String selected = (String) entry.getValue().getSelectedItem();
            if (selected != null && !"(global)".equals(selected)) {
                s.logCategoryLevels.put(entry.getKey(), selected);
            }
        }
        // WebSocket-Logging: Checkbox wirkt sofort über System.setProperty,
        // hier stellen wir sicher, dass der aktuelle Zustand auch beim Apply konsistent ist.
        System.setProperty("wd4j.log.websocket", String.valueOf(wsLoggingCheckbox.isSelected()));
    }

    @Override
    protected void afterApply(Settings s) {
        de.bund.zrb.util.AppLogger.applySettings();
    }
}

