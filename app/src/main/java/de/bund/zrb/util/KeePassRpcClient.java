package de.bund.zrb.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for the <a href="https://github.com/kee-org/keepassrpc">KeePassRPC</a>
 * plugin that ships with KeePass&nbsp;2.x.
 * <p>
 * Implements the KeePassRPC protocol exactly as documented at
 * <a href="https://forum.kee.pm/t/keepassrpc-technical-detail/2364">Kee Forum</a>
 * and the reference Go client <a href="https://github.com/logic/gkp">gkp</a>.
 * <p>
 * Protocol overview:
 * <ol>
 *   <li>WebSocket to 127.0.0.1:12546 — client sends the first message</li>
 *   <li>SRP handshake via {@code protocol:"setup", srp:{…}} messages</li>
 *   <li>After auth, AES-encrypted JSON-RPC via {@code protocol:"jsonrpc"}</li>
 * </ol>
 */
final class KeePassRpcClient {

    private static final Logger LOG = Logger.getLogger(KeePassRpcClient.class.getName());
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_S = 15;

    // ── KeePassRPC SRP parameters ────────────────────────────────────────
    // 512-bit prime from KeePassRPC's SRP.cs — NOT the RFC 5054 prime!
    private static final BigInteger N = new BigInteger(
            "d4c7f8a2b32c11b8fba9581ec4ba4f1b04215642ef7355e37c0fc0443ef756ea"
          + "2c6b8eeb755a1c723027663caa265ef785b8ff6a9b35227a52d86633dbdfca43", 16);
    private static final BigInteger g = BigInteger.valueOf(2);
    // k = SHA1(N | pad(g)) — hardcoded in KeePassRPC server
    private static final BigInteger k = new BigInteger("b7867f1299da8cc24ab93e08986ebc4d6a478ad0", 16);

    // Protocol version: {1,7,2} packed as (1<<16)|(7<<8)|2 = 67330
    private static final int PROTOCOL_VERSION = (1 << 16) | (7 << 8) | 2;

    // Features required by the server for version-mismatched clients
    private static final String[] FEATURES = {
            "KPRPC_FEATURE_VERSION_1_6",
            "KPRPC_FEATURE_WARN_USER_WHEN_FEATURE_MISSING"
    };

    // ── Instance state ──────────────────────────────────────────────────
    private final String host;
    private final int port;
    private final String clientId;
    private final String srpKey;          // shared pairing key (the "password" for SRP)

    private WebSocketClient ws;
    private final AtomicInteger rpcId = new AtomicInteger(1);

    // After SRP auth: hex key for AES encryption (64 lowercase hex chars)
    private String sessionKeyHex;

    // Synchronous response mailbox
    private final AtomicReference<String> mailbox = new AtomicReference<String>();
    private volatile CountDownLatch latch;

    KeePassRpcClient(String host, int port, String clientId, String srpKey) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.srpKey = srpKey;
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Open WebSocket, authenticate via SRP, leave connection ready for RPC.
     * The KeePassRPC server does NOT send a hello — the client must send the
     * first message (SRP identifyToServer) immediately after connecting.
     */
    void connect() {
        latch = new CountDownLatch(1);
        URI uri;
        try {
            uri = new URI("ws://" + host + ":" + port + "/");
        } catch (Exception e) {
            throw new KeePassNotAvailableException("Ungültige Adresse: " + host + ":" + port, e);
        }

        ws = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                LOG.fine("[KeePassRPC] WebSocket open (status=" + handshake.getHttpStatus() + ")");
                latch.countDown();
            }
            @Override
            public void onMessage(String text) {
                LOG.fine("[KeePassRPC] ← " + truncate(text));
                mailbox.set(text);
                CountDownLatch l = latch;
                if (l != null) l.countDown();
            }
            @Override
            public void onClose(int code, String reason, boolean remote) {
                LOG.fine("[KeePassRPC] WebSocket closed: " + code + " " + reason
                        + (remote ? " (by server)" : " (by client)"));
            }
            @Override
            public void onError(Exception ex) {
                LOG.log(Level.WARNING, "[KeePassRPC] WebSocket error", ex);
                String detail = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                mailbox.set("ERROR:" + detail);
                CountDownLatch l = latch;
                if (l != null) l.countDown();
            }
        };
        // KeePassRPC validates the Origin header — connections without a permitted
        // origin are silently rejected.  The default whitelist includes the browser-
        // extension prefixes (chrome-extension://, moz-extension://, …).
        // We use "chrome-extension://mainframemate" so the handshake passes without
        // requiring the user to reconfigure KeePassRPC's PermittedOrigins.
        ws.addHeader("Origin", "chrome-extension://mainframemate");
        ws.setConnectionLostTimeout(0);

        try {
            if (!ws.connectBlocking(TIMEOUT_S, TimeUnit.SECONDS)) {
                throw new KeePassNotAvailableException(
                        "KeePassRPC-Timeout beim Verbinden mit " + host + ":" + port + ".\n"
                      + "Ist KeePass mit KeePassRPC-Plugin gestartet?");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeePassNotAvailableException("Verbindung unterbrochen.", e);
        }

        awaitLatch("WebSocket connect");   // wait for onOpen
        performSrpAuth();                  // client sends first message
    }

    /** Close the WebSocket connection. */
    void close() {
        if (ws != null) {
            try { ws.close(); } catch (Exception ignored) {}
            ws = null;
        }
    }

    /**
     * Get password for an entry by title.
     */
    String getPassword(String entryTitle) {
        JsonArray entries = findLoginsByTitle(entryTitle);
        if (entries == null || entries.size() == 0) return null;
        JsonObject first = entries.get(0).getAsJsonObject();
        return first.has("password") ? first.get("password").getAsString() : null;
    }

    /**
     * Get username for an entry by title.
     */
    String getUserName(String entryTitle) {
        JsonArray entries = findLoginsByTitle(entryTitle);
        if (entries == null || entries.size() == 0) return null;
        JsonObject first = entries.get(0).getAsJsonObject();
        if (first.has("usernameValue")) return first.get("usernameValue").getAsString();
        if (first.has("username"))      return first.get("username").getAsString();
        return null;
    }

    /**
     * List all entries visible to KeePassRPC.
     */
    String listEntries() {
        String response = rpcCall("GetAllLogins", new JsonArray());
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("result")) {
                return formatEntries(resp.get("result"));
            }
        } catch (Exception e) {
            LOG.warning("[KeePassRPC] Failed to parse entries: " + e.getMessage());
        }
        return response;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SRP Authentication — matches gkp (Go) and KeePassRPC server (C#)
    // ═════════════════════════════════════════════════════════════════════

    private void performSrpAuth() {
        SecureRandom random = new SecureRandom();

        // Client ephemeral: a (private), A = g^a mod N (public)
        BigInteger a = new BigInteger(256, random);
        BigInteger A = g.modPow(a, N);

        // ── Step 1: identifyToServer ──
        JsonObject msg = buildSetupMessage();
        JsonObject srp = new JsonObject();
        srp.addProperty("stage", "identifyToServer");
        srp.addProperty("I", clientId);
        srp.addProperty("A", toHex(A));
        srp.addProperty("securityLevel", 2);
        msg.add("srp", srp);
        sendAndWait(msg, "SRP identifyToServer");

        // ── Step 2: parse identifyToClient response ──
        String challengeMsg = mailbox.get();
        JsonObject challenge = parseOrThrow(challengeMsg);

        if (challenge.has("error") && !challenge.get("error").isJsonNull()) {
            throw new KeePassNotAvailableException(
                    "SRP-Fehler vom Server: " + challenge.get("error"));
        }

        JsonObject srvSrp = challenge.getAsJsonObject("srp");
        if (srvSrp == null) {
            throw new KeePassNotAvailableException(
                    "Unerwartete SRP-Antwort (kein 'srp'-Objekt): " + truncate(challengeMsg));
        }

        String saltStr = srvSrp.get("s").getAsString();
        String bHex = srvSrp.get("B").getAsString();

        // Pad B to even length (as gkp does)
        if (bHex.length() % 2 != 0) bHex = "0" + bHex;
        BigInteger B = new BigInteger(bHex, 16);

        if (B.mod(N).equals(BigInteger.ZERO)) {
            throw new KeePassNotAvailableException("SRP: Ungültiger Server-Wert B.");
        }

        // ── Compute SRP values ──
        // All hex formatting uses UPPERCASE to match gkp's %X
        String aHex = toHex(A);
        String bHexNorm = toHex(B);

        // u = SHA256(A_hex + B_hex)  — hex string concatenation, NOT byte concat
        BigInteger u = new BigInteger(1, sha256str(aHex + bHexNorm));
        if (u.equals(BigInteger.ZERO)) {
            throw new KeePassNotAvailableException("SRP: Ungültiger u-Wert.");
        }

        // x = SHA256(salt_string + password_string) — plain string concatenation
        // KeePassRPC: _x = new BigInteger(Utils.Hash(_s + password))
        BigInteger x = new BigInteger(1, sha256str(saltStr + srpKey));

        // S = (B - k * g^x)^(a + u*x) mod N
        BigInteger gx = g.modPow(x, N);
        BigInteger kgx = k.multiply(gx).mod(N);
        BigInteger diff = B.subtract(kgx).mod(N);
        if (diff.signum() < 0) diff = diff.add(N);
        BigInteger exp = a.add(u.multiply(x));
        BigInteger S = diff.modPow(exp, N);

        String sHex = toHex(S);

        // M = SHA256(A_hex + B_hex + S_hex) — hex string concatenation
        byte[] mBytes = sha256str(aHex + bHexNorm + sHex);
        String mHex = bytesToHex(mBytes);

        // ── Step 3: proofToServer ──
        JsonObject proof = buildSetupMessage();
        JsonObject srpProof = new JsonObject();
        srpProof.addProperty("stage", "proofToServer");
        srpProof.addProperty("M", mHex);
        srpProof.addProperty("securityLevel", 2);
        proof.add("srp", srpProof);
        sendAndWait(proof, "SRP proofToServer");

        // ── Step 4: verify proofToClient ──
        String verifyMsg = mailbox.get();
        JsonObject verify = parseOrThrow(verifyMsg);

        if (verify.has("error") && !verify.get("error").isJsonNull()) {
            throw new KeePassNotAvailableException(
                    "SRP-Authentifizierung fehlgeschlagen. "
                  + "Bitte prüfen Sie den KeePassRPC-Schlüssel.\n"
                  + "Server: " + truncate(verifyMsg));
        }

        JsonObject verifySrp = verify.getAsJsonObject("srp");
        if (verifySrp != null && verifySrp.has("M2")) {
            String m2Received = verifySrp.get("M2").getAsString();
            // M2 = SHA256(A_hex + M_lowercase + S_hex)
            byte[] expectedM2 = sha256str(aHex + mHex.toLowerCase() + sHex);
            String expectedM2Hex = bytesToHex(expectedM2);
            if (!m2Received.equalsIgnoreCase(expectedM2Hex)) {
                throw new KeePassNotAvailableException("SRP: Server-Verifizierung M2 fehlgeschlagen.");
            }
        }

        // Session key: K = lowercase_hex(SHA256(S_hex))
        // Must be 64 chars (zero-padded) — stored as hex key for AES
        byte[] keyHash = sha256str(sHex);
        sessionKeyHex = bytesToHex(keyHash);

        LOG.info("[KeePassRPC] SRP authentication successful");
    }

    /**
     * Build a top-level setup message with all required fields.
     */
    private JsonObject buildSetupMessage() {
        JsonObject msg = new JsonObject();
        msg.addProperty("protocol", "setup");
        msg.addProperty("version", PROTOCOL_VERSION);
        msg.addProperty("clientTypeId", "MainframeMate");
        msg.addProperty("clientDisplayName", "MainframeMate");
        msg.addProperty("clientDisplayDescription", "Mainframe Data Integration");
        JsonArray feat = new JsonArray();
        for (String f : FEATURES) feat.add(f);
        msg.add("features", feat);
        return msg;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  JSON-RPC (AES-encrypted)
    // ═════════════════════════════════════════════════════════════════════

    private JsonArray findLoginsByTitle(String title) {
        // FindLogins expects positional params: [urls[], actionURL, httpRealm,
        //   loginSearchType, requireFullURLMatches, uniqueID, dbFileName,
        //   freeTextSearch, username]
        JsonArray params = new JsonArray();
        JsonArray urls = new JsonArray();
        urls.add(title);
        params.add(urls);                   // unsanitizedURLs
        params.add("");                      // actionURL
        params.add("");                      // httpRealm
        params.add("LSTall");               // loginSearchType
        params.add(false);                   // requireFullURLMatches
        params.add("");                      // uniqueID
        params.add("");                      // dbFileName
        params.add(title);                   // freeTextSearch
        params.add("");                      // username

        String response = rpcCall("FindLogins", params);
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("result")) {
                JsonElement result = resp.get("result");
                if (result.isJsonArray() && result.getAsJsonArray().size() > 0) {
                    return result.getAsJsonArray();
                }
            }
        } catch (Exception e) {
            LOG.fine("[KeePassRPC] FindLogins parse error: " + e.getMessage());
        }
        return getAllAndFilterByTitle(title);
    }

    private JsonArray getAllAndFilterByTitle(String title) {
        String response = rpcCall("GetAllLogins", new JsonArray());
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (!resp.has("result")) return null;
            JsonArray all = resp.getAsJsonArray("result");
            JsonArray filtered = new JsonArray();
            for (JsonElement el : all) {
                JsonObject entry = el.getAsJsonObject();
                String t = entry.has("title") ? entry.get("title").getAsString() : "";
                if (title.equalsIgnoreCase(t)) {
                    filtered.add(entry);
                }
            }
            return filtered.size() > 0 ? filtered : null;
        } catch (Exception e) {
            LOG.fine("[KeePassRPC] GetAllLogins parse error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Issue an encrypted JSON-RPC call and return the decrypted response.
     */
    private String rpcCall(String method, JsonElement params) {
        if (sessionKeyHex == null) {
            throw new KeePassNotAvailableException("Nicht authentifiziert — connect() zuerst aufrufen.");
        }

        int id = rpcId.getAndIncrement();
        JsonObject rpc = new JsonObject();
        rpc.addProperty("jsonrpc", "2.0");
        rpc.addProperty("method", method);
        rpc.add("params", params);
        rpc.addProperty("id", id);

        String plaintext = GSON.toJson(rpc);
        LOG.fine("[KeePassRPC] → RPC: " + truncate(plaintext));

        try {
            byte[] keyBytes = hexToBytes(sessionKeyHex);
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            // Encrypt with AES-CBC + PKCS5Padding
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // HMAC = SHA1(SHA1(key) + ciphertext + iv)
            byte[] hmacBytes = computeHmac(keyBytes, ciphertext, iv);

            // Build encrypted wrapper
            JsonObject container = new JsonObject();
            container.addProperty("message", base64Encode(ciphertext));
            container.addProperty("iv", base64Encode(iv));
            container.addProperty("hmac", base64Encode(hmacBytes));

            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("protocol", "jsonrpc");
            wrapper.addProperty("version", PROTOCOL_VERSION);
            wrapper.add("jsonrpc", container);

            sendAndWait(wrapper, method);
        } catch (KeePassNotAvailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeePassNotAvailableException("Verschlüsselungsfehler: " + e.getMessage(), e);
        }

        // Decrypt response
        String encryptedResponse = mailbox.get();
        return decryptResponse(encryptedResponse);
    }

    private String decryptResponse(String rawJson) {
        try {
            JsonObject resp = JsonParser.parseString(rawJson).getAsJsonObject();

            String protocol = resp.has("protocol") ? resp.get("protocol").getAsString() : "";
            if ("error".equals(protocol)) {
                throw new KeePassNotAvailableException("Server-Fehler: " + resp);
            }

            JsonObject jsonrpc = resp.getAsJsonObject("jsonrpc");
            if (jsonrpc == null) {
                // Might be an unencrypted error
                return rawJson;
            }

            byte[] ciphertext = base64Decode(jsonrpc.get("message").getAsString());
            byte[] iv = base64Decode(jsonrpc.get("iv").getAsString());
            byte[] hmac = base64Decode(jsonrpc.get("hmac").getAsString());
            byte[] keyBytes = hexToBytes(sessionKeyHex);

            // Verify HMAC
            byte[] expectedHmac = computeHmac(keyBytes, ciphertext, iv);
            if (!MessageDigest.isEqual(hmac, expectedHmac)) {
                throw new KeePassNotAvailableException("HMAC-Prüfung fehlgeschlagen.");
            }

            // Decrypt
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            String result = new String(plaintext, StandardCharsets.UTF_8);
            LOG.fine("[KeePassRPC] ← RPC: " + truncate(result));
            return result;
        } catch (KeePassNotAvailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeePassNotAvailableException("Entschlüsselungsfehler: " + e.getMessage(), e);
        }
    }

    /**
     * HMAC as used by KeePassRPC: SHA1(SHA1(key) + ciphertext + iv)
     */
    private static byte[] computeHmac(byte[] key, byte[] ciphertext, byte[] iv) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] keyHash = sha1.digest(key);

            sha1.reset();
            sha1.update(keyHash);
            sha1.update(ciphertext);
            sha1.update(iv);
            return sha1.digest();
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    private String formatEntries(JsonElement result) {
        StringBuilder sb = new StringBuilder();
        if (result.isJsonArray()) {
            for (JsonElement el : result.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject e = el.getAsJsonObject();
                sb.append("Title: ").append(jsonStr(e, "title")).append('\n');
                sb.append("UserName: ").append(jsonStr(e, "usernameValue", "username")).append('\n');
                sb.append("Password: ").append(jsonStr(e, "password")).append('\n');
                sb.append("URL: ").append(jsonStr(e, "url", "uRLs")).append('\n');
                sb.append('\n');
            }
        }
        sb.append("OK: Operation completed successfully.");
        return sb.toString();
    }

    private static String jsonStr(JsonObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && !o.get(key).isJsonNull()) {
                JsonElement el = o.get(key);
                if (el.isJsonPrimitive()) return el.getAsString();
                if (el.isJsonArray() && el.getAsJsonArray().size() > 0) {
                    return el.getAsJsonArray().get(0).getAsString();
                }
            }
        }
        return "";
    }

    // ── WebSocket helpers ───────────────────────────────────────────────

    private void sendAndWait(JsonObject msg, String label) {
        latch = new CountDownLatch(1);
        String json = GSON.toJson(msg);
        LOG.fine("[KeePassRPC] → " + truncate(json));
        try {
            ws.send(json);
        } catch (Exception e) {
            throw new KeePassNotAvailableException(
                    "Nachricht konnte nicht gesendet werden (WebSocket geschlossen) bei: " + label, e);
        }
        awaitLatch(label);
    }

    private void awaitLatch(String label) {
        try {
            if (!latch.await(TIMEOUT_S, TimeUnit.SECONDS)) {
                throw new KeePassNotAvailableException(
                        "KeePassRPC-Timeout bei: " + label + " (" + TIMEOUT_S + "s).\n"
                      + "Ist KeePass gestartet und die Datenbank geöffnet?");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeePassNotAvailableException("KeePassRPC unterbrochen bei: " + label, e);
        }
        String msg = mailbox.get();
        if (msg != null && msg.startsWith("ERROR:")) {
            throw new KeePassNotAvailableException(
                    "KeePassRPC-Verbindungsfehler: " + msg.substring(6) + "\n"
                  + "Ist KeePass mit KeePassRPC-Plugin auf Port " + port + " erreichbar?");
        }
    }

    private static JsonObject parseOrThrow(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new KeePassNotAvailableException("Ungültige JSON-Antwort: " + truncate(json));
        }
    }

    // ── Crypto helpers ──────────────────────────────────────────────────

    /** SHA-256 of UTF-8 string */
    private static byte[] sha256str(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Format BigInteger as UPPERCASE hex string without leading zeros,
     * matching Go's {@code fmt.Sprintf("%X", bigInt)}.
     */
    private static String toHex(BigInteger bi) {
        return bi.toString(16).toUpperCase();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private static String base64Encode(byte[] data) {
        // Java 8 compatible
        return javax.xml.bind.DatatypeConverter.printBase64Binary(data);
    }

    private static byte[] base64Decode(String data) {
        return javax.xml.bind.DatatypeConverter.parseBase64Binary(data);
    }

    private static String truncate(String s) {
        return (s != null && s.length() > 200) ? s.substring(0, 200) + "…" : s;
    }
}

