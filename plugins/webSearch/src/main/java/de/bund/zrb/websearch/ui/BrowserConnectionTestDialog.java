package de.bund.zrb.websearch.ui;

import de.bund.zrb.api.WDWebSocket;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.type.script.WDEvaluateResult;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A modeless terminal-like overlay window that tests the browser connection.
 * Launches the configured browser (Firefox or Chrome), connects via WebDriver BiDi,
 * navigates to a test URL, and displays all WebSocket traffic.
 */
public class BrowserConnectionTestDialog extends JDialog {

    private final JTextArea terminalArea;
    private final JButton closeBtn;
    private final JButton stopBtn;
    private volatile boolean stopped = false;
    private volatile BrowserSession testSession;

    public BrowserConnectionTestDialog(Window owner) {
        super(owner, "Browser-Verbindungstest", ModalityType.MODELESS);
        setLayout(new BorderLayout());

        // Terminal area
        terminalArea = new JTextArea();
        terminalArea.setEditable(false);
        terminalArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        terminalArea.setBackground(Color.BLACK);
        terminalArea.setForeground(Color.GREEN);
        terminalArea.setCaretColor(Color.GREEN);
        terminalArea.setLineWrap(true);
        terminalArea.setWrapStyleWord(true);
        // Auto-scroll to bottom
        DefaultCaret caret = (DefaultCaret) terminalArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(terminalArea);
        scrollPane.setPreferredSize(new Dimension(900, 600));
        add(scrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.DARK_GRAY);
        stopBtn = new JButton("⏹ Stop");
        stopBtn.addActionListener(e -> stopTest());
        closeBtn = new JButton("Schließen");
        closeBtn.addActionListener(e -> {
            stopTest();
            dispose();
        });
        buttonPanel.add(stopBtn);
        buttonPanel.add(closeBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Starts the browser connection test in a background thread.
     */
    public void startTest(String browserName, String browserPath, boolean headless, int debugPort) {
        log("╔══════════════════════════════════════════════════════════════╗");
        log("║  Browser-Verbindungstest                                    ║");
        log("╚══════════════════════════════════════════════════════════════╝");
        log("");
        log("Browser:    " + browserName);
        log("Pfad:       " + browserPath);
        log("Headless:   " + headless);
        log("Debug-Port: " + (debugPort == 0 ? "auto" : debugPort));
        log("Test-URL:   https://zrb.bund.de/");
        log("");
        log("─── Starte Browser ───────────────────────────────────────────");

        Thread testThread = new Thread(() -> {
            try {
                runTest(browserName, browserPath, headless, debugPort);
            } catch (Exception e) {
                logError("Test fehlgeschlagen", e);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    stopBtn.setEnabled(false);
                    stopBtn.setText("⏹ Beendet");
                });
            }
        }, "browser-connection-test");
        testThread.setDaemon(true);
        testThread.start();
    }

    private void runTest(String browserName, String browserPath, boolean headless, int debugPort) {
        testSession = new BrowserSession();

        try {
            // Step 1: Launch browser
            log("[1/6] Browser wird gestartet...");
            log("  Typ: " + (browserPath.toLowerCase().contains("chrome") ? "Chrome (BiDi via CDP-Mapper)" : "Firefox (nativer BiDi-Support)"));
            List<String> extraArgs = new ArrayList<String>();

            long launchStart = System.currentTimeMillis();
            testSession.launchAndConnect(browserPath, extraArgs, headless, 30000, debugPort);
            long launchTime = System.currentTimeMillis() - launchStart;
            if (stopped) return;

            String contextId = testSession.getContextId();
            if (contextId != null) {
                log("  ✅ Browser gestartet und verbunden (" + launchTime + "ms)");
                log("  Context-ID: " + contextId);
            } else {
                log("  ⚠️ Browser gestartet aber kein Context verfügbar");
                log("  Der Test wird trotzdem fortgesetzt...");
            }

            // Register WebSocket frame listeners for traffic display
            WDWebSocket ws = testSession.getWebSocket();
            if (ws != null) {
                ws.onFrameSent(frame -> {
                    String text = frame.text();
                    String truncated = text.length() > 200 ? text.substring(0, 200) + "…" : text;
                    log("  >>> " + truncated);
                });
                ws.onFrameReceived(frame -> {
                    String text = frame.text();
                    String truncated = text.length() > 200 ? text.substring(0, 200) + "…" : text;
                    log("  <<< " + truncated);
                });
            }
            log("");

            // Step 2: Check connection
            log("[2/6] Verbindung prüfen...");
            boolean connected = testSession.isConnected();
            log("  WebDriver BiDi verbunden: " + connected);
            if (!connected) {
                log("  ❌ Keine Verbindung – Test abgebrochen");
                return;
            }
            log("  ✅ Verbindung steht");
            log("");

            // Step 3: Navigate to test URL
            if (stopped) return;
            log("[3/6] Navigation zu https://zrb.bund.de/ ...");
            long navStart = System.currentTimeMillis();
            try {
                testSession.navigate("https://zrb.bund.de/");
                long navTime = System.currentTimeMillis() - navStart;
                log("  ✅ Navigation erfolgreich (" + navTime + "ms)");
            } catch (Exception e) {
                long navTime = System.currentTimeMillis() - navStart;
                log("  ⚠️ Navigation-Fehler nach " + navTime + "ms: " + e.getMessage());
                log("  (Das kann normal sein wenn die Seite nicht erreichbar ist)");
            }
            log("");

            // Step 4: Take screenshot
            if (stopped) return;
            log("[4/6] Screenshot erstellen...");
            try {
                String screenshotBase64 = testSession.captureScreenshot();
                if (screenshotBase64 != null && !screenshotBase64.isEmpty()) {
                    log("  ✅ Screenshot erfolgreich (" + screenshotBase64.length() + " Zeichen Base64)");
                } else {
                    log("  ⚠️ Screenshot leer");
                }
            } catch (Exception e) {
                log("  ⚠️ Screenshot fehlgeschlagen: " + e.getMessage());
            }
            log("");

            // Step 5: Evaluate simple JS
            if (stopped) return;
            log("[5/6] JavaScript-Evaluation testen...");
            try {
                WDEvaluateResult result = testSession.evaluate("document.title", true);
                String title = "(unbekannt)";
                if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                    title = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
                }
                log("  document.title = \"" + title + "\"");
                log("  ✅ JS-Evaluation funktioniert");
            } catch (Exception e) {
                log("  ⚠️ JS-Eval fehlgeschlagen: " + e.getMessage());
            }
            log("");

            // Step 6: Console logs
            log("[6/6] Browser-Konsole prüfen...");
            List<String> consoleLogs = testSession.getConsoleLogs();
            if (consoleLogs.isEmpty()) {
                log("  (keine Konsolen-Einträge)");
            } else {
                for (String logLine : consoleLogs) {
                    log("  [BROWSER] " + logLine);
                }
            }
            log("");

            // Summary
            log("═══════════════════════════════════════════════════════════════");
            log("  ✅ Verbindungstest abgeschlossen!");
            log("  Browser: " + browserName);
            log("  Context: " + testSession.getContextId());
            log("  Status:  " + (testSession.isConnected() ? "VERBUNDEN" : "GETRENNT"));
            log("═══════════════════════════════════════════════════════════════");

        } catch (Exception e) {
            logError("Fehler im Verbindungstest", e);
        } finally {
            // Cleanup: close browser
            log("");
            log("─── Browser wird beendet ─────────────────────────────────────");
            try {
                if (testSession != null) {
                    testSession.killBrowserProcess();
                    log("  Browser-Prozess beendet.");
                }
            } catch (Exception e) {
                log("  Fehler beim Beenden: " + e.getMessage());
            }
            testSession = null;
        }
    }

    private void stopTest() {
        stopped = true;
        log("\n⏹ Test wird gestoppt...");
        BrowserSession session = testSession;
        if (session != null) {
            try {
                session.killBrowserProcess();
            } catch (Exception ignored) {}
        }
    }

    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        String line = timestamp + "  " + message + "\n";
        SwingUtilities.invokeLater(() -> terminalArea.append(line));
    }

    private void logError(String message, Exception e) {
        log("❌ " + message + ": " + e.getMessage());
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        // Only first 10 lines of stacktrace
        String[] lines = sw.toString().split("\n");
        for (int i = 0; i < Math.min(lines.length, 10); i++) {
            log("  " + lines[i].trim());
        }
    }
}
