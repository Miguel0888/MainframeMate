package de.bund.zrb.util;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Dialog for pairing with KeePassRPC.
 * <p>
 * When KeePassRPC has no SRP key configured, this dialog guides the user
 * through the pairing process:
 * <ol>
 *   <li>MainframeMate connects to the KeePassRPC WebSocket on the configured port.</li>
 *   <li>KeePass detects the unknown client and shows a pairing dialog with a key.</li>
 *   <li>The user copies the key and enters it here.</li>
 *   <li>The dialog tests the connection with the entered key.</li>
 *   <li>On success, the key is saved to settings automatically.</li>
 * </ol>
 */
public final class KeePassRpcPairingDialog {

    private static final Logger LOG = Logger.getLogger(KeePassRpcPairingDialog.class.getName());

    private KeePassRpcPairingDialog() {}

    /**
     * Show the pairing dialog and return the validated SRP key.
     * If the user cancels or pairing fails, returns {@code null}.
     *
     * @return the validated SRP key, or {@code null} if cancelled
     */
    public static String showAndPair() {
        Settings settings = SettingsHelper.load();
        int port = settings.keepassRpcPort;
        String host = settings.getEffectiveRpcHost();

        // Build dialog content
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Instructions
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1;
        JLabel instructions = new JLabel(
                "<html><body style='width:380px'>"
              + "<b>KeePassRPC-Pairing erforderlich</b><br><br>"
              + "Um MainframeMate mit KeePass zu verbinden, wird ein "
              + "einmaliger Pairing-Schlüssel benötigt.<br><br>"
              + "<b>So geht's:</b><br>"
              + "1. Stellen Sie sicher, dass <b>KeePass</b> geöffnet ist und das "
              + "<b>KeePassRPC-Plugin</b> installiert ist.<br>"
              + "2. Klicken Sie unten auf <b>\"Verbindung herstellen\"</b>.<br>"
              + "3. KeePass zeigt daraufhin einen <b>Pairing-Dialog</b> mit einem "
              + "Schlüssel an.<br>"
              + "4. Kopieren Sie den Schlüssel und fügen Sie ihn unten ein.<br>"
              + "5. Klicken Sie auf <b>\"Schlüssel prüfen\"</b>.<br>"
              + "</body></html>"
        );
        panel.add(instructions, gbc);

        // Port info
        gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel portLabel = new JLabel("KeePassRPC-Adresse: " + host + ":" + port);
        portLabel.setForeground(Color.GRAY);
        panel.add(portLabel, gbc);

        // SRP Key field
        gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel("SRP-Schlüssel:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JTextField keyField = new JTextField(30);
        keyField.setToolTipText("Den Schlüssel aus dem KeePass-Pairing-Dialog hier einfügen");
        panel.add(keyField, gbc);

        // Status label
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1;
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        panel.add(statusLabel, gbc);

        // Create buttons
        JButton connectButton = new JButton("Verbindung herstellen");
        JButton testButton = new JButton("Schlüssel prüfen");
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Abbrechen");

        testButton.setEnabled(false);
        okButton.setEnabled(false);

        // Result holder
        final AtomicReference<String> validatedKey = new AtomicReference<String>(null);

        // Dialog
        JOptionPane optionPane = new JOptionPane(
                panel,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[]{connectButton, testButton, okButton, cancelButton},
                connectButton
        );

        JDialog dialog = optionPane.createDialog(null, "KeePassRPC-Pairing");
        dialog.setResizable(true);

        // "Verbindung herstellen" — attempt WebSocket connection to trigger KeePass pairing dialog
        connectButton.addActionListener(e -> {
            statusLabel.setForeground(Color.BLUE);
            statusLabel.setText("Verbinde mit KeePass auf " + host + ":" + port + "…");
            dialog.repaint();

            // Run connection attempt in background
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return triggerPairingDialog(host, port);
                }

                @Override
                protected void done() {
                    try {
                        String result = get();
                        if (result == null) {
                            statusLabel.setForeground(new Color(0, 128, 0));
                            statusLabel.setText("Verbunden! Bitte Schlüssel aus KeePass kopieren und einfügen.");
                            testButton.setEnabled(true);
                            keyField.requestFocusInWindow();
                        } else {
                            statusLabel.setForeground(Color.RED);
                            statusLabel.setText(result);
                        }
                    } catch (Exception ex) {
                        statusLabel.setForeground(Color.RED);
                        statusLabel.setText("Fehler: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        // Enable test button also when key is entered without connect
        keyField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void check() {
                testButton.setEnabled(!keyField.getText().trim().isEmpty());
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { check(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { check(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { check(); }
        });

        // "Schlüssel prüfen" — test SRP authentication with the entered key
        testButton.addActionListener(e -> {
            String key = keyField.getText().trim();
            if (key.isEmpty()) {
                statusLabel.setForeground(Color.RED);
                statusLabel.setText("Bitte geben Sie den SRP-Schlüssel ein.");
                return;
            }

            statusLabel.setForeground(Color.BLUE);
            statusLabel.setText("Prüfe Schlüssel…");
            testButton.setEnabled(false);
            dialog.repaint();

            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return testSrpKey(host, port, key);
                }

                @Override
                protected void done() {
                    try {
                        String result = get();
                        if (result == null) {
                            statusLabel.setForeground(new Color(0, 128, 0));
                            statusLabel.setText("✓ Schlüssel gültig! Verbindung erfolgreich.");
                            validatedKey.set(key);
                            okButton.setEnabled(true);
                            okButton.requestFocusInWindow();
                        } else {
                            statusLabel.setForeground(Color.RED);
                            statusLabel.setText(result);
                            validatedKey.set(null);
                            okButton.setEnabled(false);
                        }
                    } catch (Exception ex) {
                        statusLabel.setForeground(Color.RED);
                        statusLabel.setText("Fehler: " + ex.getMessage());
                        okButton.setEnabled(false);
                    }
                    testButton.setEnabled(!keyField.getText().trim().isEmpty());
                }
            }.execute();
        });

        okButton.addActionListener(e -> {
            optionPane.setValue(okButton);
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> {
            optionPane.setValue(cancelButton);
            dialog.dispose();
        });

        dialog.setVisible(true);

        Object value = optionPane.getValue();
        if (value == okButton && validatedKey.get() != null) {
            String key = validatedKey.get();
            // Save to settings
            settings = SettingsHelper.load();
            settings.keepassRpcKey = key;
            SettingsHelper.save(settings);
            LOG.info("[KeePassRPC] Pairing successful, SRP key saved.");
            return key;
        }

        return null;
    }

    /**
     * Attempt a WebSocket connection to KeePassRPC to trigger its pairing dialog.
     * Tries the configured host first, then falls back to 127.0.0.1 and localhost.
     *
     * @return {@code null} on success, or an error message
     */
    private static String triggerPairingDialog(String configuredHost, int port) {
        // Build ordered candidate list: configured host first, then fallbacks (LinkedHashSet skips duplicates)
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<String>();
        candidates.add(configuredHost);
        candidates.add("127.0.0.1");
        candidates.add("localhost");

        String lastError = null;
        for (String host : candidates) {
            String result = attemptTrigger(host, port);
            if (result == null) {
                return null; // success
            }
            LOG.fine("[KeePassRPC Pairing] " + host + " failed: " + result);
            lastError = result;
        }

        return lastError;
    }

    private static String attemptTrigger(String host, int port) {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final AtomicReference<String> error = new AtomicReference<String>(null);
        final AtomicReference<okhttp3.WebSocket> wsRef = new AtomicReference<okhttp3.WebSocket>(null);

        okhttp3.Request req = new okhttp3.Request.Builder()
                .url("ws://" + host + ":" + port + "/")
                .build();

        okhttp3.WebSocket ws = client.newWebSocket(req, new okhttp3.WebSocketListener() {
            @Override
            public void onOpen(okhttp3.WebSocket webSocket, okhttp3.Response response) {
                wsRef.set(webSocket);
                latch.countDown();
            }

            @Override
            public void onMessage(okhttp3.WebSocket webSocket, String text) {
                LOG.fine("[KeePassRPC Pairing] Server message: "
                        + (text.length() > 100 ? text.substring(0, 100) + "…" : text));
                // We received a message (likely the server hello) — KeePass should now show pairing dialog
                latch.countDown();
            }

            @Override
            public void onFailure(okhttp3.WebSocket webSocket, Throwable t, okhttp3.Response response) {
                error.set("Verbindung fehlgeschlagen: " + t.getMessage()
                        + "\nIst KeePass mit KeePassRPC auf Port " + port + " gestartet?");
                latch.countDown();
            }
        });

        try {
            if (!latch.await(6, java.util.concurrent.TimeUnit.SECONDS)) {
                ws.close(1000, "timeout");
                return "Timeout — keine Antwort von KeePassRPC auf Port " + port + ".";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Unterbrochen.";
        }

        String err = error.get();
        if (err != null) {
            return err;
        }

        // Connection established — now send SRP identifyToServer.
        // KeePassRPC shows its pairing dialog when an unknown client identifies.
        try {
            // Wait briefly for the server hello to arrive (if latch was
            // triggered by onOpen rather than onMessage)
            Thread.sleep(500);

            java.math.BigInteger a = new java.math.BigInteger(256, new java.security.SecureRandom());
            java.math.BigInteger N = new java.math.BigInteger(
                    "EEAF0AB9ADB38DD69C33F80AFA8FC5E86072618775FF3C0B9EA2314C"
                  + "9C256576D674DF7496EA81D3383B4813D692C6E0E0D5D8E250B98BE4"
                  + "8E495C1D6089DAD15DC7D7B46154D6B6CE8EF4AD69B15D4982559B29"
                  + "7BCF1885C529F566660E57EC68EDBC3C05726CC02FD4CBF4976EAA9A"
                  + "FD5138FE8376435B9FC61D2FC0EB06E3", 16);
            java.math.BigInteger g = java.math.BigInteger.valueOf(2);
            java.math.BigInteger A = g.modPow(a, N);

            com.google.gson.JsonObject initiate = new com.google.gson.JsonObject();
            initiate.addProperty("protocol", "setup");
            com.google.gson.JsonObject srp = new com.google.gson.JsonObject();
            srp.addProperty("stage", "identifyToServer");
            srp.addProperty("I", "MainframeMate");
            srp.addProperty("A", A.toString(16));
            srp.addProperty("securityLevel", 2);
            initiate.add("srpinitiate", srp);

            ws.send(new com.google.gson.Gson().toJson(initiate));
            LOG.fine("[KeePassRPC Pairing] SRP identifyToServer sent");
        } catch (Exception ex) {
            LOG.warning("[KeePassRPC Pairing] Failed to send SRP initiate: " + ex.getMessage());
        }

        // Give KeePass time to show the pairing dialog
        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Leave WebSocket open so KeePass keeps the dialog visible
        return null;
    }

    /**
     * Test whether the given SRP key allows successful authentication.
     *
     * @return {@code null} on success, or an error message
     */
    private static String testSrpKey(String host, int port, String srpKey) {
        KeePassRpcClient client = new KeePassRpcClient(host, port, "MainframeMate", srpKey);
        try {
            client.connect();
            return null; // success
        } catch (KeePassNotAvailableException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("SRP")) {
                return "Schlüssel ungültig. Bitte prüfen Sie den Schlüssel aus dem KeePass-Pairing-Dialog.";
            }
            return "Verbindungsfehler: " + msg;
        } catch (Exception e) {
            return "Fehler: " + e.getMessage();
        } finally {
            client.close();
        }
    }
}

