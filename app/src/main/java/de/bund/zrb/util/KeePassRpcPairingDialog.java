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
                            statusLabel.setText("<html>Verbunden! KeePass sollte jetzt einen Pairing-Dialog "
                                    + "mit dem Schlüssel anzeigen.<br>Bitte den Schlüssel kopieren und hier einfügen.</html>");
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

        final java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(1);
        final AtomicReference<String> error = new AtomicReference<String>(null);

        okhttp3.Request req = new okhttp3.Request.Builder()
                .url("ws://" + host + ":" + port + "/")
                .build();

        okhttp3.WebSocket ws = client.newWebSocket(req, new okhttp3.WebSocketListener() {
            @Override
            public void onOpen(okhttp3.WebSocket webSocket, okhttp3.Response response) {
                // Connection succeeded — send SRP identifyToServer to trigger
                // KeePass pairing dialog, then signal the main thread.
                try {
                    String clientId = "MainframeMate-" + Long.toHexString(System.currentTimeMillis());

                    // 512-bit prime from KeePassRPC's SRP.cs
                    java.math.BigInteger N = new java.math.BigInteger(
                            "d4c7f8a2b32c11b8fba9581ec4ba4f1b04215642ef7355e37c0fc0443ef756ea"
                          + "2c6b8eeb755a1c723027663caa265ef785b8ff6a9b35227a52d86633dbdfca43", 16);
                    java.math.BigInteger g = java.math.BigInteger.valueOf(2);
                    java.math.BigInteger a = new java.math.BigInteger(256, new java.security.SecureRandom());
                    java.math.BigInteger A = g.modPow(a, N);

                    // Protocol version: {1,7,2} → (1<<16)|(7<<8)|2 = 67330
                    int protocolVersion = (1 << 16) | (7 << 8) | 2;

                    com.google.gson.JsonObject msg = new com.google.gson.JsonObject();
                    msg.addProperty("protocol", "setup");
                    msg.addProperty("version", protocolVersion);
                    msg.addProperty("clientTypeId", "MainframeMate");
                    msg.addProperty("clientDisplayName", "MainframeMate");
                    msg.addProperty("clientDisplayDescription", "Mainframe Data Integration Tool");

                    com.google.gson.JsonArray features = new com.google.gson.JsonArray();
                    features.add("KPRPC_FEATURE_VERSION_1_6");
                    features.add("KPRPC_FEATURE_WARN_USER_WHEN_FEATURE_MISSING");
                    msg.add("features", features);

                    com.google.gson.JsonObject srp = new com.google.gson.JsonObject();
                    srp.addProperty("stage", "identifyToServer");
                    srp.addProperty("I", clientId);
                    srp.addProperty("A", A.toString(16).toUpperCase());
                    srp.addProperty("securityLevel", 2);
                    msg.add("srp", srp);

                    boolean sent = webSocket.send(new com.google.gson.Gson().toJson(msg));
                    LOG.fine("[KeePassRPC Pairing] SRP identifyToServer sent (clientId=" + clientId + ", ok=" + sent + ")");

                    if (!sent) {
                        error.set("SRP-Nachricht konnte nicht gesendet werden.");
                    }
                } catch (Exception ex) {
                    LOG.warning("[KeePassRPC Pairing] Failed to send SRP initiate: " + ex.getMessage());
                    error.set("Fehler beim Senden der Client-Identifikation: " + ex.getMessage());
                }
                latch.countDown();
            }

            @Override
            public void onMessage(okhttp3.WebSocket webSocket, String text) {
                LOG.fine("[KeePassRPC Pairing] Server message: "
                        + (text.length() > 100 ? text.substring(0, 100) + "…" : text));
                // Server responded — pairing dialog should be showing
                latch.countDown();
            }

            @Override
            public void onFailure(okhttp3.WebSocket webSocket, Throwable t, okhttp3.Response response) {
                String detail = t.getMessage() != null ? t.getMessage() : t.toString();
                error.set("Verbindung fehlgeschlagen: " + detail
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
            ws.close(1000, "error");
            return err;
        }

        // Give KeePass a moment to process SRP and show pairing dialog,
        // then CLOSE the WebSocket so the port is free for testSrpKey().
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ws.close(1000, "pairing trigger done");

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

