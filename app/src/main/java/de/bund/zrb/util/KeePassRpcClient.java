package de.bund.zrb.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for the <a href="https://github.com/kee-org/keepassrpc">KeePassRPC</a>
 * plugin that ships with KeePass 2.x.
 * <p>
 * Connects via WebSocket to the KeePassRPC JSON-RPC server, authenticates
 * using SRP-6a, and provides methods to read/write password entries.
 * <p>
 * <b>Prerequisites:</b>
 * <ul>
 *   <li>KeePass must be running with the database open.</li>
 *   <li>The KeePassRPC plugin must be installed and active.</li>
 *   <li>On first use the user must authorise the client in KeePass
 *       (a pairing dialog with a key is shown).</li>
 * </ul>
 * <p>
 * Uses OkHttp for WebSocket and Gson for JSON — both already on the classpath.
 */
final class KeePassRpcClient {

    private static final Logger LOG = Logger.getLogger(KeePassRpcClient.class.getName());
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_S = 15;

    // ── SRP-6a parameters (RFC 5054, 1024-bit group, SHA-256) ───────────
    private static final BigInteger N = new BigInteger(
            "EEAF0AB9ADB38DD69C33F80AFA8FC5E86072618775FF3C0B9EA2314C"
          + "9C256576D674DF7496EA81D3383B4813D692C6E0E0D5D8E250B98BE4"
          + "8E495C1D6089DAD15DC7D7B46154D6B6CE8EF4AD69B15D4982559B29"
          + "7BCF1885C529F566660E57EC68EDBC3C05726CC02FD4CBF4976EAA9A"
          + "FD5138FE8376435B9FC61D2FC0EB06E3", 16);
    private static final BigInteger g = BigInteger.valueOf(2);
    private static final BigInteger k;

    static {
        // k = H(N | pad(g))
        byte[] padG = padTo(toBytes(g), toBytes(N).length);
        k = new BigInteger(1, sha256(concat(toBytes(N), padG)));
    }

    // ── Instance state ──────────────────────────────────────────────────
    private final String host;
    private final int port;
    private final String clientId;
    private final String srpKey;          // shared pairing key (the "password" for SRP)
    private final OkHttpClient http;

    private WebSocket ws;
    private final AtomicInteger rpcId = new AtomicInteger(1);

    // Synchronous response mailbox
    private final AtomicReference<String> mailbox = new AtomicReference<>();
    private CountDownLatch latch;

    KeePassRpcClient(String host, int port, String clientId, String srpKey) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.srpKey = srpKey;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .build();
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Open WebSocket, authenticate via SRP-6a, leave connection ready for RPC.
     */
    void connect() {
        latch = new CountDownLatch(1);
        Request req = new Request.Builder()
                .url("ws://" + host + ":" + port + "/")
                .build();

        ws = http.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                LOG.fine("[KeePassRPC] WebSocket open");
                latch.countDown();
            }
            @Override public void onMessage(WebSocket webSocket, String text) {
                LOG.fine("[KeePassRPC] ← " + truncate(text));
                mailbox.set(text);
                CountDownLatch l = latch;
                if (l != null) l.countDown();
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                LOG.log(Level.WARNING, "[KeePassRPC] WebSocket failure", t);
                mailbox.set("ERROR:" + t.getMessage());
                CountDownLatch l = latch;
                if (l != null) l.countDown();
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                LOG.fine("[KeePassRPC] WebSocket closed: " + code + " " + reason);
            }
        });

        awaitLatch("WebSocket connect");   // wait for onOpen

        // Read server hello
        String hello = readMessage("server hello");
        LOG.fine("[KeePassRPC] Server hello: " + truncate(hello));

        // Perform SRP authentication
        performSrpAuth(hello);
    }

    /** Close the WebSocket connection. */
    void close() {
        if (ws != null) {
            ws.close(1000, "done");
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
        // KeePassRPC returns "usernameValue" or "username"
        if (first.has("usernameValue")) return first.get("usernameValue").getAsString();
        if (first.has("username"))      return first.get("username").getAsString();
        return null;
    }

    /**
     * List all entries visible to KeePassRPC.
     * Returns formatted text compatible with the PowerShell output format.
     */
    String listEntries() {
        JsonObject params = new JsonObject();
        // GetAllDatabases returns databases with their root groups and entries
        String response = rpcCall("QueryHomepage", params);
        // Try GetAllLogins as a simpler alternative
        if (response == null || response.contains("error")) {
            response = rpcCall("GetAllLogins", new JsonObject());
        }

        // Parse and format
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            if (resp.has("result")) {
                JsonElement result = resp.get("result");
                return formatEntries(result);
            }
        } catch (Exception e) {
            LOG.warning("[KeePassRPC] Failed to parse entries: " + e.getMessage());
        }
        return response; // raw fallback
    }

    // ── SRP-6a Authentication ───────────────────────────────────────────

    private void performSrpAuth(String helloMsg) {
        SecureRandom random = new SecureRandom();

        // Client: generate a, A = g^a mod N
        BigInteger a = new BigInteger(256, random);
        BigInteger A = g.modPow(a, N);

        // Send SRP initiate
        JsonObject initiate = new JsonObject();
        initiate.addProperty("protocol", "setup");
        JsonObject srp = new JsonObject();
        srp.addProperty("stage", "identifyToServer");
        srp.addProperty("I", clientId);
        srp.addProperty("A", A.toString(16));
        srp.addProperty("securityLevel", 2);
        initiate.add("srpinitiate", srp);
        sendAndWait(initiate, "SRP initiate");

        String challengeMsg = mailbox.get();
        JsonObject challenge = JsonParser.parseString(challengeMsg).getAsJsonObject();

        // Extract salt and B from server challenge
        JsonObject chal;
        if (challenge.has("key")) {
            chal = challenge.getAsJsonObject("key");
        } else if (challenge.has("srpchallengeserver")) {
            chal = challenge.getAsJsonObject("srpchallengeserver");
        } else {
            throw new KeePassNotAvailableException(
                    "Unerwartete SRP-Antwort vom Server: " + truncate(challengeMsg));
        }

        String saltHex = chal.get("s").getAsString();
        String bHex = chal.get("B").getAsString();
        byte[] salt = hexToBytes(saltHex);
        BigInteger B = new BigInteger(bHex, 16);

        // Verify B != 0 mod N
        if (B.mod(N).equals(BigInteger.ZERO)) {
            throw new KeePassNotAvailableException("SRP: Ungültiger Server-Wert B.");
        }

        // u = H(A | B)
        BigInteger u = new BigInteger(1, sha256(concat(toBytes(A), toBytes(B))));
        if (u.equals(BigInteger.ZERO)) {
            throw new KeePassNotAvailableException("SRP: Ungültiger u-Wert.");
        }

        // x = H(salt | H(I : password))
        byte[] innerHash = sha256((clientId + ":" + srpKey).getBytes(StandardCharsets.UTF_8));
        BigInteger x = new BigInteger(1, sha256(concat(salt, innerHash)));

        // S = (B - k * g^x)^(a + u*x) mod N
        BigInteger gx = g.modPow(x, N);
        BigInteger diff = B.subtract(k.multiply(gx).mod(N)).mod(N);
        if (diff.signum() < 0) diff = diff.add(N);
        BigInteger exp = a.add(u.multiply(x));
        BigInteger S = diff.modPow(exp, N);

        // K = H(S)
        byte[] K = sha256(toBytes(S));

        // M1 = H(H(N) XOR H(g) | H(I) | salt | A | B | K)
        byte[] hN = sha256(toBytes(N));
        byte[] hg = sha256(toBytes(g));
        byte[] xorNg = xor(hN, hg);
        byte[] hI = sha256(clientId.getBytes(StandardCharsets.UTF_8));
        byte[] M1 = sha256(concat(xorNg, hI, salt, toBytes(A), toBytes(B), K));

        // Send M1 proof
        JsonObject proof = new JsonObject();
        proof.addProperty("protocol", "setup");
        JsonObject srpProof = new JsonObject();
        srpProof.addProperty("stage", "proveToServer");
        srpProof.addProperty("M", bytesToHex(M1));
        srpProof.addProperty("securityLevel", 2);
        proof.add("srpproof", srpProof);
        sendAndWait(proof, "SRP proof");

        String verifyMsg = mailbox.get();
        JsonObject verify = JsonParser.parseString(verifyMsg).getAsJsonObject();

        // Check for auth error
        if (verify.has("error")) {
            throw new KeePassNotAvailableException(
                    "SRP-Authentifizierung fehlgeschlagen. "
                  + "Bitte prüfen Sie den KeePassRPC-Schlüssel in den Einstellungen.\n"
                  + "Server: " + truncate(verifyMsg));
        }

        // Verify M2
        JsonObject vfy;
        if (verify.has("key")) {
            vfy = verify.getAsJsonObject("key");
        } else if (verify.has("srpverify")) {
            vfy = verify.getAsJsonObject("srpverify");
        } else {
            // Auth may have succeeded without explicit M2 in some versions
            LOG.fine("[KeePassRPC] No explicit M2 verify, assuming auth OK");
            return;
        }

        if (vfy.has("M2")) {
            byte[] M2 = hexToBytes(vfy.get("M2").getAsString());
            byte[] expectedM2 = sha256(concat(toBytes(A), M1, K));
            if (!MessageDigest.isEqual(M2, expectedM2)) {
                throw new KeePassNotAvailableException("SRP: Server-Verifizierung M2 fehlgeschlagen.");
            }
        }

        LOG.info("[KeePassRPC] SRP authentication successful");
    }

    // ── JSON-RPC ────────────────────────────────────────────────────────

    private JsonArray findLoginsByTitle(String title) {
        // KeePassRPC FindLogins expects URL-based search; for title search
        // we use GetAllLogins and filter client-side, or try a search
        JsonObject params = new JsonObject();
        params.addProperty("url", title);  // some versions allow title-based search via url field
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

        // Fallback: get all and filter by title
        return getAllAndFilterByTitle(title);
    }

    private JsonArray getAllAndFilterByTitle(String title) {
        String response = rpcCall("GetAllLogins", new JsonObject());
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

    private String rpcCall(String method, JsonObject params) {
        int id = rpcId.getAndIncrement();
        JsonObject req = new JsonObject();
        req.addProperty("protocol", "jsonrpc");
        req.addProperty("jsonrpc", "2.0");
        req.addProperty("method", method);
        req.add("params", params);
        req.addProperty("id", id);

        sendAndWait(req, method);
        return mailbox.get();
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
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                JsonElement el = o.get(k);
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
        ws.send(json);
        awaitLatch(label);
    }

    private String readMessage(String label) {
        latch = new CountDownLatch(1);
        awaitLatch(label);
        return mailbox.get();
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

    // ── Crypto helpers (SRP-6a, SHA-256) ────────────────────────────────

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static byte[] toBytes(BigInteger bi) {
        byte[] b = bi.toByteArray();
        if (b[0] == 0 && b.length > 1) {
            byte[] tmp = new byte[b.length - 1];
            System.arraycopy(b, 1, tmp, 0, tmp.length);
            return tmp;
        }
        return b;
    }

    private static byte[] padTo(byte[] src, int len) {
        if (src.length >= len) return src;
        byte[] padded = new byte[len];
        System.arraycopy(src, 0, padded, len - src.length, src.length);
        return padded;
    }

    private static byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] a : arrays) len += a.length;
        byte[] result = new byte[len];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] r = new byte[Math.min(a.length, b.length)];
        for (int i = 0; i < r.length; i++) r[i] = (byte) (a[i] ^ b[i]);
        return r;
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

    private static String truncate(String s) {
        return (s != null && s.length() > 200) ? s.substring(0, 200) + "…" : s;
    }
}

