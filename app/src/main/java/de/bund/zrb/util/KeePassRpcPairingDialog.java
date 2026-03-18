package de.bund.zrb.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Dialog for pairing with KeePassRPC.
 * <p>
 * The complete SRP handshake runs on a <b>single</b> WebSocket connection:
 * <ol>
 *   <li>"Verbindung herstellen" → opens WebSocket, sends {@code identifyToServer},
 *       receives server's {@code {s, B}} and stores SRP state.
 *       KeePass shows its auth dialog with the generated key.</li>
 *   <li>User copies the key from KeePass and pastes it here.</li>
 *   <li>"Schlüssel prüfen" → computes SRP proof M using stored state + entered key,
 *       sends {@code proofToServer} on the <b>same</b> WebSocket, verifies M2.</li>
 *   <li>On success, the key is saved to settings.</li>
 * </ol>
 */
public final class KeePassRpcPairingDialog {

    private static final Logger LOG = Logger.getLogger(KeePassRpcPairingDialog.class.getName());
    private static final Gson GSON = new Gson();

    // ── SRP constants (must match KeePassRpcClient) ──────────────────────
    private static final BigInteger N = new BigInteger(
            "d4c7f8a2b32c11b8fba9581ec4ba4f1b04215642ef7355e37c0fc0443ef756ea"
          + "2c6b8eeb755a1c723027663caa265ef785b8ff6a9b35227a52d86633dbdfca43", 16);
    private static final BigInteger g = BigInteger.valueOf(2);
    private static final BigInteger k = new BigInteger("b7867f1299da8cc24ab93e08986ebc4d6a478ad0", 16);
    private static final int PROTOCOL_VERSION = (1 << 16) | (7 << 8) | 2;
    private static final String[] FEATURES = {
            "KPRPC_FEATURE_VERSION_1_6",
            "KPRPC_FEATURE_WARN_USER_WHEN_FEATURE_MISSING"
    };

    private KeePassRpcPairingDialog() {}

    /** Holds SRP state across the two phases of the pairing dialog. */
    private static class SrpState {
        WebSocketClient ws;
        BigInteger a;       // client private ephemeral
        String aHex;        // A as uppercase hex (= toHex(g^a mod N))
        String salt;        // s from server
        String bHex;        // B from server (normalised uppercase hex)
        // mailbox + latch for receiving messages on the trigger WS
        final AtomicReference<String> mailbox = new AtomicReference<String>(null);
        volatile CountDownLatch latch;
    }

    /**
     * Show the pairing dialog and return the validated SRP key.
     * If the user cancels or pairing fails, returns {@code null}.
     */
    public static String showAndPair() {
        Settings settings = SettingsHelper.load();
        int port = settings.keepassRpcPort;
        String host = settings.getEffectiveRpcHost();
        String origin = settings.getEffectiveRpcOrigin();

        final AtomicReference<SrpState> stateRef = new AtomicReference<SrpState>(null);

        // ── Build dialog content ─────────────────────────────────────────
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

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
              + "</body></html>");
        panel.add(instructions, gbc);

        gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel portLabel = new JLabel("KeePassRPC-Adresse: " + host + ":" + port);
        portLabel.setForeground(Color.GRAY);
        panel.add(portLabel, gbc);

        gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel("SRP-Schlüssel:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JTextField keyField = new JTextField(30);
        keyField.setToolTipText("Den Schlüssel aus dem KeePass-Pairing-Dialog hier einfügen");
        panel.add(keyField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1;
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        panel.add(statusLabel, gbc);

        JButton connectButton = new JButton("Verbindung herstellen");
        JButton testButton = new JButton("Schlüssel prüfen");
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Abbrechen");
        testButton.setEnabled(false);
        okButton.setEnabled(false);

        final AtomicReference<String> validatedKey = new AtomicReference<String>(null);

        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null,
                new Object[]{connectButton, testButton, okButton, cancelButton}, connectButton);
        JDialog dialog = optionPane.createDialog(null, "KeePassRPC-Pairing");
        dialog.setResizable(true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                closeSrpState(stateRef);
                optionPane.setValue(cancelButton);
                dialog.dispose();
            }
        });

        // ── "Verbindung herstellen" ──────────────────────────────────────
        connectButton.addActionListener(e -> {
            statusLabel.setForeground(Color.BLUE);
            statusLabel.setText("Verbinde mit KeePass auf " + host + ":" + port + "…");
            connectButton.setEnabled(false);
            dialog.repaint();

            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    return openAndIdentify(host, port, origin, stateRef);
                }
                @Override protected void done() {
                    try {
                        String result = get();
                        if (result == null) {
                            statusLabel.setForeground(new Color(0, 128, 0));
                            statusLabel.setText("<html>Verbunden! KeePass sollte jetzt einen Pairing-Dialog "
                                    + "mit dem Schlüssel anzeigen.<br>"
                                    + "Bitte den Schlüssel kopieren und hier einfügen.</html>");
                            testButton.setEnabled(true);
                            keyField.requestFocusInWindow();
                        } else {
                            statusLabel.setForeground(Color.RED);
                            statusLabel.setText(result);
                            connectButton.setEnabled(true);
                        }
                    } catch (Exception ex) {
                        statusLabel.setForeground(Color.RED);
                        statusLabel.setText("Fehler: " + ex.getMessage());
                        connectButton.setEnabled(true);
                    }
                }
            }.execute();
        });

        keyField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void check() { testButton.setEnabled(!keyField.getText().trim().isEmpty()); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { check(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { check(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { check(); }
        });

        // ── "Schlüssel prüfen" ───────────────────────────────────────────
        testButton.addActionListener(e -> {
            String key = keyField.getText().trim();
            if (key.isEmpty()) {
                statusLabel.setForeground(Color.RED);
                statusLabel.setText("Bitte geben Sie den SRP-Schlüssel ein.");
                return;
            }
            SrpState state = stateRef.get();
            if (state == null || state.ws == null) {
                statusLabel.setForeground(Color.RED);
                statusLabel.setText("Bitte zuerst \"Verbindung herstellen\" klicken.");
                return;
            }

            statusLabel.setForeground(Color.BLUE);
            statusLabel.setText("Prüfe Schlüssel…");
            testButton.setEnabled(false);
            dialog.repaint();

            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    return verifySrpKey(state, key);
                }
                @Override protected void done() {
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
                            // Connection is now dead — user must reconnect
                            closeSrpState(stateRef);
                            connectButton.setEnabled(true);
                        }
                    } catch (Exception ex) {
                        statusLabel.setForeground(Color.RED);
                        statusLabel.setText("Fehler: " + ex.getMessage());
                        okButton.setEnabled(false);
                        closeSrpState(stateRef);
                        connectButton.setEnabled(true);
                    }
                    testButton.setEnabled(!keyField.getText().trim().isEmpty() && stateRef.get() != null);
                }
            }.execute();
        });

        okButton.addActionListener(e -> {
            closeSrpState(stateRef);
            optionPane.setValue(okButton);
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> {
            closeSrpState(stateRef);
            optionPane.setValue(cancelButton);
            dialog.dispose();
        });

        dialog.setVisible(true);
        closeSrpState(stateRef);

        Object value = optionPane.getValue();
        if (value == okButton && validatedKey.get() != null) {
            String key = validatedKey.get();
            settings = SettingsHelper.load();
            settings.keepassRpcKey = key;
            SettingsHelper.save(settings);
            LOG.info("[KeePassRPC] Pairing successful, SRP key saved.");
            return key;
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Phase 1: Open WebSocket, send identifyToServer, receive {s, B}
    // ═════════════════════════════════════════════════════════════════════

    private static String openAndIdentify(String configuredHost, int port, String origin,
                                          AtomicReference<SrpState> stateOut) {
        LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        candidates.add(configuredHost);
        candidates.add("127.0.0.1");
        candidates.add("localhost");

        String lastError = null;
        for (String host : candidates) {
            String result = attemptIdentify(host, port, origin, stateOut);
            if (result == null) return null;
            LOG.fine("[KeePassRPC Pairing] " + host + " failed: " + result);
            lastError = result;
        }
        return lastError;
    }

    private static String attemptIdentify(String host, int port, String origin,
                                          AtomicReference<SrpState> stateOut) {
        SrpState state = new SrpState();
        state.latch = new CountDownLatch(1);
        final AtomicReference<String> connectError = new AtomicReference<String>(null);

        URI uri;
        try {
            uri = new URI("ws://" + host + ":" + port + "/");
        } catch (Exception e) {
            return "Ungültige Adresse: " + host + ":" + port;
        }

        // Generate client ephemeral key pair
        SecureRandom random = new SecureRandom();
        state.a = new BigInteger(256, random);
        BigInteger A = g.modPow(state.a, N);
        state.aHex = toHex(A);
        String clientId = "MainframeMate-" + Long.toHexString(System.currentTimeMillis());

        state.ws = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                LOG.fine("[KeePassRPC Pairing] WebSocket open (status=" + handshake.getHttpStatus() + ")");
                try {
                    JsonObject msg = buildSetupMessage();
                    JsonObject srp = new JsonObject();
                    srp.addProperty("stage", "identifyToServer");
                    srp.addProperty("I", clientId);
                    srp.addProperty("A", state.aHex);
                    srp.addProperty("securityLevel", 2);
                    msg.add("srp", srp);
                    send(GSON.toJson(msg));
                    LOG.fine("[KeePassRPC Pairing] SRP identifyToServer sent");
                } catch (Exception ex) {
                    connectError.set("Fehler beim Senden: " + ex.getMessage());
                    state.latch.countDown();
                }
            }

            @Override
            public void onMessage(String text) {
                LOG.fine("[KeePassRPC Pairing] ← " + (text.length() > 120 ? text.substring(0, 120) + "…" : text));
                state.mailbox.set(text);
                CountDownLatch l = state.latch;
                if (l != null) l.countDown();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                LOG.fine("[KeePassRPC Pairing] WebSocket closed: " + code + " " + reason
                        + (remote ? " (by server)" : " (by client)"));
            }

            @Override
            public void onError(Exception ex) {
                String detail = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                connectError.set("Verbindung fehlgeschlagen: " + detail);
                state.latch.countDown();
            }
        };

        state.ws.addHeader("Origin", origin);
        state.ws.setConnectionLostTimeout(0);
        try {
            if (!state.ws.connectBlocking(5, TimeUnit.SECONDS)) {
                return "Timeout beim Verbinden mit " + host + ":" + port;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Unterbrochen.";
        }

        // Wait for the server's identifyToClient response (s, B)
        try {
            if (!state.latch.await(10, TimeUnit.SECONDS)) {
                try { state.ws.close(); } catch (Exception ignored) {}
                return "Timeout — keine Antwort von KeePassRPC.";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Unterbrochen.";
        }

        String err = connectError.get();
        if (err != null) {
            try { state.ws.close(); } catch (Exception ignored) {}
            return err;
        }

        // Parse the server's identifyToClient response
        String responseText = state.mailbox.get();
        if (responseText == null) {
            try { state.ws.close(); } catch (Exception ignored) {}
            return "Keine Antwort vom Server.";
        }

        try {
            JsonObject resp = JsonParser.parseString(responseText).getAsJsonObject();
            if (resp.has("error") && !resp.get("error").isJsonNull()) {
                try { state.ws.close(); } catch (Exception ignored) {}
                return "Server-Fehler: " + resp.get("error");
            }
            JsonObject srvSrp = resp.getAsJsonObject("srp");
            if (srvSrp == null || !srvSrp.has("s") || !srvSrp.has("B")) {
                try { state.ws.close(); } catch (Exception ignored) {}
                return "Unerwartete Server-Antwort (kein srp/s/B).";
            }
            state.salt = srvSrp.get("s").getAsString();
            String bRaw = srvSrp.get("B").getAsString();
            if (bRaw.length() % 2 != 0) bRaw = "0" + bRaw;
            BigInteger B = new BigInteger(bRaw, 16);
            if (B.mod(N).equals(BigInteger.ZERO)) {
                try { state.ws.close(); } catch (Exception ignored) {}
                return "Ungültiger Server-Wert B.";
            }
            state.bHex = toHex(B);
        } catch (Exception e) {
            try { state.ws.close(); } catch (Exception ignored) {}
            return "Fehler beim Parsen der Server-Antwort: " + e.getMessage();
        }

        // Success — keep WS open, store state for phase 2
        stateOut.set(state);
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Phase 2: Compute SRP proof, send proofToServer on SAME connection
    // ═════════════════════════════════════════════════════════════════════

    private static String verifySrpKey(SrpState state, String srpKey) {
        try {
            BigInteger B = new BigInteger(state.bHex, 16);

            // u = SHA256(A_hex + B_hex)
            BigInteger u = new BigInteger(1, sha256str(state.aHex + state.bHex));
            if (u.equals(BigInteger.ZERO)) return "SRP: Ungültiger u-Wert.";

            // x = SHA256(salt + password)
            BigInteger x = new BigInteger(1, sha256str(state.salt + srpKey));

            // S = (B - k * g^x)^(a + u*x) mod N
            BigInteger gx = g.modPow(x, N);
            BigInteger kgx = k.multiply(gx).mod(N);
            BigInteger diff = B.subtract(kgx).mod(N);
            if (diff.signum() < 0) diff = diff.add(N);
            BigInteger exp = state.a.add(u.multiply(x));
            BigInteger S = diff.modPow(exp, N);
            String sHex = toHex(S);

            // M = SHA256(A_hex + B_hex + S_hex)
            byte[] mBytes = sha256str(state.aHex + state.bHex + sHex);
            String mHex = bytesToHex(mBytes);

            // Send proofToServer on the SAME WebSocket
            state.latch = new CountDownLatch(1);
            JsonObject proof = buildSetupMessage();
            JsonObject srpProof = new JsonObject();
            srpProof.addProperty("stage", "proofToServer");
            srpProof.addProperty("M", mHex);
            srpProof.addProperty("securityLevel", 2);
            proof.add("srp", srpProof);
            state.ws.send(GSON.toJson(proof));

            // Wait for proofToClient
            if (!state.latch.await(10, TimeUnit.SECONDS)) {
                return "Timeout bei Schlüsselprüfung.";
            }

            String verifyText = state.mailbox.get();
            if (verifyText == null) return "Keine Antwort auf SRP-Proof.";

            JsonObject verify = JsonParser.parseString(verifyText).getAsJsonObject();
            if (verify.has("error") && !verify.get("error").isJsonNull()) {
                return "Schlüssel ungültig. Bitte prüfen Sie den Schlüssel aus dem KeePass-Pairing-Dialog.";
            }

            // Verify M2
            JsonObject verifySrp = verify.getAsJsonObject("srp");
            if (verifySrp != null && verifySrp.has("M2")) {
                String m2Received = verifySrp.get("M2").getAsString();
                byte[] expectedM2 = sha256str(state.aHex + mHex.toLowerCase() + sHex);
                String expectedM2Hex = bytesToHex(expectedM2);
                if (!m2Received.equalsIgnoreCase(expectedM2Hex)) {
                    return "Server-Verifizierung M2 fehlgeschlagen.";
                }
            }

            LOG.info("[KeePassRPC Pairing] SRP proof verified — key is valid.");
            return null; // success

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Unterbrochen.";
        } catch (Exception e) {
            return "Fehler bei SRP-Berechnung: " + e.getMessage();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════════

    private static void closeSrpState(AtomicReference<SrpState> ref) {
        SrpState state = ref.getAndSet(null);
        if (state != null && state.ws != null) {
            try { state.ws.close(); } catch (Exception ignored) {}
        }
    }

    private static JsonObject buildSetupMessage() {
        JsonObject msg = new JsonObject();
        msg.addProperty("protocol", "setup");
        msg.addProperty("version", PROTOCOL_VERSION);
        msg.addProperty("clientTypeId", "MainframeMate");
        msg.addProperty("clientDisplayName", "MainframeMate");
        msg.addProperty("clientDisplayDescription", "Mainframe Data Integration Tool");
        JsonArray feat = new JsonArray();
        for (String f : FEATURES) feat.add(f);
        msg.add("features", feat);
        return msg;
    }

    private static String toHex(BigInteger bi) {
        return bi.toString(16).toUpperCase();
    }

    private static byte[] sha256str(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }
}

