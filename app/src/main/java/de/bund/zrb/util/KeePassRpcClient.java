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
            "KPRPC_FEATURE_WARN_USER_WHEN_FEATURE_MISSING",
            "KPRPC_ENTRIES_WITH_NO_URL"
    };

    // ── Instance state ──────────────────────────────────────────────────
    private final String host;
    private final int port;
    private final String clientId;
    private final String srpKey;          // shared pairing key (the "password" for SRP)
    private final String origin;          // Origin header for WebSocket handshake

    private WebSocketClient ws;
    private final AtomicInteger rpcId = new AtomicInteger(1);

    // After SRP auth: hex key for AES encryption (64 lowercase hex chars)
    private String sessionKeyHex;

    // Synchronous response mailbox
    private final AtomicReference<String> mailbox = new AtomicReference<String>();
    private volatile CountDownLatch latch;

    KeePassRpcClient(String host, int port, String clientId, String srpKey, String origin) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.srpKey = srpKey;
        this.origin = (origin != null && !origin.isEmpty()) ? origin : "chrome-extension://mainframemate";
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
        ws.addHeader("Origin", origin);
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
        performKcrAuth();                  // authenticate with stored session key
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
        if (first.has("password")) return first.get("password").getAsString();
        return formFieldValue(first, "FFTpassword");
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
        return formFieldValue(first, "FFTusername");
    }

    /**
     * Query the filename of the currently active KeePass database.
     * Tries {@code GetDatabaseFileName} first, falls back to {@code GetDatabaseName}.
     *
     * @return the database filename, or empty string if not available
     */
    String getDatabaseFileName() {
        try {
            String response = rpcCall("GetDatabaseFileName", new JsonArray());
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("result") && !resp.get("result").isJsonNull()) {
                String name = resp.get("result").getAsString();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception e) {
            LOG.fine("[KeePassRPC] GetDatabaseFileName failed: " + e.getMessage());
        }
        try {
            String response = rpcCall("GetDatabaseName", new JsonArray());
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("result") && !resp.get("result").isJsonNull()) {
                String name = resp.get("result").getAsString();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception e) {
            LOG.fine("[KeePassRPC] GetDatabaseName failed: " + e.getMessage());
        }
        return "";
    }

    /**
     * Create a new login entry in KeePass via the {@code AddLogin} V1 RPC call.
     *
     * @param title    entry title
     * @param userName username
     * @param password password
     * @param url      URL (may be empty)
     */
    void addLogin(String title, String userName, String password, String url) {
        // Build form field list (V1 DTO)
        JsonArray formFields = new JsonArray();

        JsonObject userField = new JsonObject();
        userField.addProperty("name", "KeePass username");
        userField.addProperty("displayName", "KeePass username");
        userField.addProperty("value", userName != null ? userName : "");
        userField.addProperty("type", "FFTusername");
        userField.addProperty("id", "");
        userField.addProperty("page", -1);
        formFields.add(userField);

        JsonObject passField = new JsonObject();
        passField.addProperty("name", "KeePass password");
        passField.addProperty("displayName", "KeePass password");
        passField.addProperty("value", password != null ? password : "");
        passField.addProperty("type", "FFTpassword");
        passField.addProperty("id", "");
        passField.addProperty("page", -1);
        formFields.add(passField);

        // Build login object
        JsonObject login = new JsonObject();
        login.addProperty("title", title);
        login.add("formFieldList", formFields);

        JsonArray urls = new JsonArray();
        if (url != null && !url.isEmpty()) {
            urls.add(url);
        } else {
            urls.add(title); // use title as URL placeholder so the entry is discoverable
        }
        login.add("uRLs", urls);

        // AddLogin(login, parentUUID, dbFileName)
        JsonArray params = new JsonArray();
        params.add(login);
        params.add("");  // parentUUID — empty = root group
        params.add("");  // dbFileName — empty = default database

        String response = rpcCall("AddLogin", params);
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("error") && !resp.get("error").isJsonNull()) {
                throw new KeePassNotAvailableException(
                        "KeePassRPC AddLogin fehlgeschlagen: " + resp.get("error"));
            }
        } catch (KeePassNotAvailableException e) {
            throw e;
        } catch (Exception e) {
            LOG.warning("[KeePassRPC] AddLogin response parse: " + e.getMessage());
        }
        LOG.info("[KeePassRPC] Entry created: \"" + title + "\" user=\"" + userName + "\"");
    }

    /**
     * Update an existing login entry in KeePass via the {@code UpdateLogin} V1 RPC call.
     *
     * @param title    entry title (used to find existing entry)
     * @param userName new username
     * @param password new password
     */
    void updateLogin(String title, String userName, String password) {
        // First find the existing entry to get its uniqueID
        JsonArray entries = findLoginsByTitle(title);
        if (entries == null || entries.size() == 0) {
            // Entry doesn't exist yet — create it
            addLogin(title, userName, password, "");
            return;
        }

        JsonObject existing = entries.get(0).getAsJsonObject();
        String uniqueID = existing.has("uniqueID") ? existing.get("uniqueID").getAsString() : "";

        // Build updated form field list
        JsonArray formFields = new JsonArray();

        JsonObject userField = new JsonObject();
        userField.addProperty("name", "KeePass username");
        userField.addProperty("displayName", "KeePass username");
        userField.addProperty("value", userName != null ? userName : "");
        userField.addProperty("type", "FFTusername");
        userField.addProperty("id", "");
        userField.addProperty("page", -1);
        formFields.add(userField);

        JsonObject passField = new JsonObject();
        passField.addProperty("name", "KeePass password");
        passField.addProperty("displayName", "KeePass password");
        passField.addProperty("value", password != null ? password : "");
        passField.addProperty("type", "FFTpassword");
        passField.addProperty("id", "");
        passField.addProperty("page", -1);
        formFields.add(passField);

        JsonObject login = new JsonObject();
        login.addProperty("title", title);
        login.add("formFieldList", formFields);
        login.addProperty("uniqueID", uniqueID);

        // Preserve existing URLs
        if (existing.has("uRLs")) {
            login.add("uRLs", existing.get("uRLs"));
        } else {
            JsonArray urls = new JsonArray();
            urls.add(title);
            login.add("uRLs", urls);
        }

        // UpdateLogin(login, oldLoginUUID, urlMergeMode, dbFileName)
        // Server requires non-empty dbFileName (throws ArgumentException otherwise).
        // Extract from existing entry, or query active database name via RPC.
        String dbFile = jsonStr(existing, "dbFileName", "db");
        if (dbFile.isEmpty()) dbFile = getDatabaseFileName();
        if (dbFile.isEmpty()) dbFile = "*";  // last resort fallback

        JsonArray params = new JsonArray();
        params.add(login);
        params.add(uniqueID);
        params.add(2);       // urlMergeMode: 2 = replace
        params.add(dbFile);  // dbFileName — must be non-empty

        String response = rpcCall("UpdateLogin", params);
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("error") && !resp.get("error").isJsonNull()) {
                throw new KeePassNotAvailableException(
                        "KeePassRPC UpdateLogin fehlgeschlagen: " + resp.get("error"));
            }
        } catch (KeePassNotAvailableException e) {
            throw e;
        } catch (Exception e) {
            LOG.warning("[KeePassRPC] UpdateLogin response parse: " + e.getMessage());
        }
        LOG.info("[KeePassRPC] Entry updated: \"" + title + "\" user=\"" + userName + "\"");
    }

    /**
     * Remove an entry from KeePass by its uniqueID.
     *
     * @param uniqueID the unique identifier of the entry
     */
    void removeEntry(String uniqueID) {
        // RemoveEntry(uuid, dbFileName)
        JsonArray params = new JsonArray();
        params.add(uniqueID);
        params.add("");  // dbFileName — empty = default

        String response = rpcCall("RemoveEntry", params);
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("error") && !resp.get("error").isJsonNull()) {
                throw new KeePassNotAvailableException(
                        "KeePassRPC RemoveEntry fehlgeschlagen: " + resp.get("error"));
            }
        } catch (KeePassNotAvailableException e) {
            throw e;
        } catch (Exception e) {
            LOG.warning("[KeePassRPC] RemoveEntry response parse: " + e.getMessage());
        }
        LOG.info("[KeePassRPC] Entry removed: " + uniqueID);
    }

    /**
     * Return all entries as a raw JsonArray (for CRUD operations in the UI).
     */
    JsonArray getAllEntriesRaw() {
        String response = rpcCall("GetAllEntries", new JsonArray());
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("result") && resp.get("result").isJsonArray()) {
                return resp.getAsJsonArray("result");
            }
        } catch (Exception e) {
            LOG.warning("[KeePassRPC] Failed to parse entries: " + e.getMessage());
        }
        return new JsonArray();
    }

    /**
     * List all entries visible to KeePassRPC.
     */
    String listEntries() {
        String response = rpcCall("GetAllEntries", new JsonArray());
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
    //  Key Challenge-Response Authentication (for established pairings)
    //  Server stores a session key after initial SRP pairing.
    //  Subsequent connections use KCR to authenticate with that key.
    // ═════════════════════════════════════════════════════════════════════

    private void performKcrAuth() {
        // ── Step 1: send username to server ──
        JsonObject msg = buildSetupMessage();
        JsonObject key = new JsonObject();
        key.addProperty("username", clientId);
        key.addProperty("securityLevel", 2);
        msg.add("key", key);
        sendAndWait(msg, "KCR identify");

        // ── Step 2: parse server challenge ──
        String challengeMsg = mailbox.get();
        JsonObject challenge = parseOrThrow(challengeMsg);

        if (challenge.has("error") && !challenge.get("error").isJsonNull()) {
            throw new KeePassNotAvailableException(
                    "KCR-Fehler vom Server: " + challenge.get("error") + "\n"
                  + "Möglicherweise muss das Pairing erneut durchgeführt werden.");
        }

        JsonObject srvKey = challenge.getAsJsonObject("key");
        if (srvKey == null || !srvKey.has("sc")) {
            throw new KeePassNotAvailableException(
                    "Unerwartete KCR-Antwort (kein 'key.sc'): " + truncate(challengeMsg));
        }

        String sc = srvKey.get("sc").getAsString();

        // ── Step 3: compute response and send client challenge ──
        // Generate random client challenge
        byte[] ccBytes = new byte[32];
        new SecureRandom().nextBytes(ccBytes);
        BigInteger ccBi = new BigInteger(1, ccBytes);
        String cc = ccBi.toString().toLowerCase();

        // cr = hex(SHA256("1" + sessionKey + sc + cc)).toLowerCase()
        String cr = bytesToHex(sha256str("1" + srpKey + sc + cc));

        JsonObject resp = buildSetupMessage();
        JsonObject keyResp = new JsonObject();
        keyResp.addProperty("cc", cc);
        keyResp.addProperty("cr", cr);
        keyResp.addProperty("securityLevel", 2);
        resp.add("key", keyResp);
        sendAndWait(resp, "KCR proof");

        // ── Step 4: verify server response ──
        String verifyMsg = mailbox.get();
        JsonObject verify = parseOrThrow(verifyMsg);

        if (verify.has("error") && !verify.get("error").isJsonNull()) {
            throw new KeePassNotAvailableException(
                    "KCR-Authentifizierung fehlgeschlagen.\n"
                  + "Bitte Pairing erneut durchführen.\n"
                  + "Server: " + truncate(verifyMsg));
        }

        JsonObject verifyKey = verify.getAsJsonObject("key");
        if (verifyKey != null && verifyKey.has("sr")) {
            String srReceived = verifyKey.get("sr").getAsString();
            String expectedSr = bytesToHex(sha256str("0" + srpKey + sc + cc));
            if (!srReceived.equalsIgnoreCase(expectedSr)) {
                throw new KeePassNotAvailableException("KCR: Server-Verifizierung fehlgeschlagen.");
            }
        }

        // The stored session key IS the AES key for encrypted JSON-RPC
        sessionKeyHex = srpKey;

        LOG.info("[KeePassRPC] KCR authentication successful");
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
        String response = rpcCall("GetAllEntries", new JsonArray());
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

                String user = jsonStr(e, "usernameValue", "username");
                if (user.isEmpty()) user = formFieldValue(e, "FFTusername");
                sb.append("UserName: ").append(user != null ? user : "").append('\n');

                String pass = jsonStr(e, "password");
                if (pass.isEmpty()) pass = formFieldValue(e, "FFTpassword");
                sb.append("Password: ").append(pass != null ? pass : "").append('\n');

                sb.append("URL: ").append(jsonStr(e, "url", "uRLs")).append('\n');
                sb.append("UniqueID: ").append(jsonStr(e, "uniqueID")).append('\n');
                sb.append('\n');
            }
        }
        sb.append("OK: Operation completed successfully.");
        return sb.toString();
    }

    /**
     * Extract a value from the KeePassRPC v1 DTO {@code formFieldList} array.
     * Each entry has {@code type} (e.g. "FFTpassword", "FFTusername") and {@code value}.
     */
    private static String formFieldValue(JsonObject entry, String fieldType) {
        if (!entry.has("formFieldList")) return null;
        JsonElement ffl = entry.get("formFieldList");
        if (!ffl.isJsonArray()) return null;
        for (JsonElement fe : ffl.getAsJsonArray()) {
            if (!fe.isJsonObject()) continue;
            JsonObject field = fe.getAsJsonObject();
            String type = field.has("type") ? field.get("type").getAsString() : "";
            if (fieldType.equals(type) && field.has("value")) {
                return field.get("value").getAsString();
            }
        }
        return null;
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

