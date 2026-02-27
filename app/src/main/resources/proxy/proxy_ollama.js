/**
 * Ollama HTTPS Reverse-Proxy with:
 *   - Interactive configuration prompts at startup
 *   - Optional Basic Auth (password + optional username)
 *   - Optional end-to-end payload encryption (AES-256-GCM + PBKDF2), independent of TLS
 *
 * Node.js >= 16 required. No external dependencies — uses only built-in modules.
 *
 * Usage:
 *   node proxy_ollama.js
 *
 * Environment variables (all optional — prompted interactively if unset):
 *   PORT            – Listen port              (default 11435)
 *   LISTEN_HOST     – Bind address             (default 0.0.0.0)
 *   OLLAMA_HOST     – Upstream Ollama host     (default 127.0.0.1)
 *   OLLAMA_PORT     – Upstream Ollama port     (default 11434)
 *   DEBUG           – "true" for verbose logs  (default false)
 *   PROXY_USERNAME  – Auth username            (default "user")
 *   PROXY_PASSWORD  – Auth password            (empty = auth disabled)
 *   E2E_PASSWORD    – Encryption password      (empty = encryption disabled)
 *   E2E_REQUIRED    – "true" = reject unencrypted requests when E2E is configured
 */

import https from "https";
import http from "http";
import fs from "fs";
import path from "path";
import crypto from "crypto";
import readline from "readline";

// ========================== INTERACTIVE CONFIG ==========================

/**
 * Prompts the user for a value if the env var is not already set.
 * Pressing Enter without typing keeps the default.
 */
function askQuestion(rl, prompt) {
  return new Promise((resolve) => rl.question(prompt, (answer) => resolve(answer)));
}

/**
 * Masked password input — characters are replaced with '*'.
 */
function askPassword(rl, prompt) {
  return new Promise((resolve) => {
    process.stdout.write(prompt);
    const stdin = process.stdin;
    const wasRaw = stdin.isRaw;
    if (typeof stdin.setRawMode === "function") {
      stdin.setRawMode(true);
    }
    stdin.resume();

    let password = "";
    const onData = (ch) => {
      const c = ch.toString("utf8");
      if (c === "\n" || c === "\r" || c === "\u0004") {
        // Enter or EOF
        if (typeof stdin.setRawMode === "function") stdin.setRawMode(wasRaw || false);
        stdin.removeListener("data", onData);
        process.stdout.write("\n");
        resolve(password);
      } else if (c === "\u0003") {
        // Ctrl+C
        process.exit(1);
      } else if (c === "\u007f" || c === "\b") {
        // Backspace
        if (password.length > 0) {
          password = password.slice(0, -1);
          process.stdout.write("\b \b");
        }
      } else {
        password += c;
        process.stdout.write("*");
      }
    };
    stdin.on("data", onData);
  });
}

async function promptConfig() {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });

  console.log("\n╔══════════════════════════════════════════════════════════╗");
  console.log("║         Ollama HTTPS Proxy — Configuration              ║");
  console.log("║  Press Enter to keep the [default] value.               ║");
  console.log("╚══════════════════════════════════════════════════════════╝\n");

  const config = {};

  // --- Network settings ---
  const portEnv = process.env.PORT;
  if (portEnv) {
    config.port = Number(portEnv);
    console.log(`  PORT            = ${config.port} (from env)`);
  } else {
    const v = await askQuestion(rl, "  Listen port [11435]: ");
    config.port = v.trim() ? Number(v.trim()) : 11435;
  }

  const listenHostEnv = (process.env.LISTEN_HOST || "").trim();
  if (listenHostEnv) {
    config.listenHost = listenHostEnv;
    console.log(`  LISTEN_HOST     = ${config.listenHost} (from env)`);
  } else {
    const v = await askQuestion(rl, "  Listen host [0.0.0.0]: ");
    config.listenHost = v.trim() || undefined;
  }

  const ollamaHostEnv = process.env.OLLAMA_HOST;
  if (ollamaHostEnv) {
    config.upstreamHost = ollamaHostEnv;
    console.log(`  OLLAMA_HOST     = ${config.upstreamHost} (from env)`);
  } else {
    const v = await askQuestion(rl, "  Ollama upstream host [127.0.0.1]: ");
    config.upstreamHost = v.trim() || "127.0.0.1";
  }

  const ollamaPortEnv = process.env.OLLAMA_PORT;
  if (ollamaPortEnv) {
    config.upstreamPort = Number(ollamaPortEnv);
    console.log(`  OLLAMA_PORT     = ${config.upstreamPort} (from env)`);
  } else {
    const v = await askQuestion(rl, "  Ollama upstream port [11434]: ");
    config.upstreamPort = v.trim() ? Number(v.trim()) : 11434;
  }

  const debugEnv = process.env.DEBUG;
  if (debugEnv !== undefined) {
    config.debug = debugEnv.toLowerCase() === "true";
    console.log(`  DEBUG           = ${config.debug} (from env)`);
  } else {
    const v = await askQuestion(rl, "  Debug mode [false]: ");
    config.debug = v.trim().toLowerCase() === "true";
  }

  // --- Auth settings ---
  console.log("");
  const usernameEnv = process.env.PROXY_USERNAME;
  if (usernameEnv) {
    config.authUsername = usernameEnv;
    console.log(`  PROXY_USERNAME  = ${config.authUsername} (from env)`);
  } else {
    const v = await askQuestion(rl, "  Auth username (optional) [user]: ");
    config.authUsername = v.trim() || "user";
  }

  // Close readline before raw-mode password input
  rl.close();

  const passwordEnv = process.env.PROXY_PASSWORD;
  if (passwordEnv) {
    config.authPassword = passwordEnv;
    console.log(`  PROXY_PASSWORD  = ****** (from env)`);
  } else {
    const v = await askPassword(null, "  Auth password (empty = no auth): ");
    config.authPassword = v.trim() || "";
  }

  if (config.authPassword) {
    console.log("  ✔ Authentication ENABLED");
  } else {
    console.log("  ✖ Authentication DISABLED (no password set)");
  }

  // --- E2E encryption settings ---
  console.log("");
  const e2ePasswordEnv = process.env.E2E_PASSWORD;
  if (e2ePasswordEnv) {
    config.e2ePassword = e2ePasswordEnv;
    console.log(`  E2E_PASSWORD    = ****** (from env)`);
  } else {
    const v = await askPassword(null, "  E2E encryption password (empty = no encryption): ");
    config.e2ePassword = v.trim() || "";
  }

  const e2eRequiredEnv = process.env.E2E_REQUIRED;
  if (e2eRequiredEnv !== undefined) {
    config.e2eRequired = e2eRequiredEnv.toLowerCase() === "true";
    console.log(`  E2E_REQUIRED    = ${config.e2eRequired} (from env)`);
  } else if (config.e2ePassword) {
    // Only open a new readline if we need to ask
    const rl2 = readline.createInterface({ input: process.stdin, output: process.stdout });
    const v = await askQuestion(rl2, "  Require E2E for all requests? [false]: ");
    config.e2eRequired = v.trim().toLowerCase() === "true";
    rl2.close();
  } else {
    config.e2eRequired = false;
  }

  if (config.e2ePassword) {
    console.log(`  ✔ E2E Encryption ENABLED (required=${config.e2eRequired})`);
  } else {
    console.log("  ✖ E2E Encryption DISABLED (no password set)");
  }

  console.log("");
  return config;
}

// ========================== AUTHENTICATION ==========================

/**
 * Creates an auth validator.
 * Stores password as scrypt hash so the cleartext is not kept in memory.
 */
function createAuthValidator(username, password) {
  if (!password) return null;

  const salt = crypto.randomBytes(32);
  const hash = crypto.scryptSync(password, salt, 64);

  return {
    /**
     * Validates a Basic Auth header value.
     * @returns {boolean}
     */
    validate(authHeader) {
      if (!authHeader || !authHeader.startsWith("Basic ")) return false;
      try {
        const decoded = Buffer.from(authHeader.slice(6), "base64").toString("utf8");
        const colonIdx = decoded.indexOf(":");
        if (colonIdx < 0) return false;

        const u = decoded.slice(0, colonIdx);
        const p = decoded.slice(colonIdx + 1);

        // Timing-safe username comparison
        const uBuf = Buffer.from(u);
        const expectedUBuf = Buffer.from(username);
        const usernameMatch =
          uBuf.length === expectedUBuf.length && crypto.timingSafeEqual(uBuf, expectedUBuf);

        // Derive hash from submitted password and compare timing-safe
        const submittedHash = crypto.scryptSync(p, salt, 64);
        const passwordMatch = crypto.timingSafeEqual(submittedHash, hash);

        return usernameMatch && passwordMatch;
      } catch {
        return false;
      }
    }
  };
}

// ========================== E2E ENCRYPTION ==========================

/**
 * AES-256-GCM encryption with PBKDF2 key derivation.
 *
 * Wire format (binary):
 *   [version 1B] [salt 32B] [iv 12B] [authTag 16B] [ciphertext ...]
 *
 * - version: 0x01 (for future extensibility)
 * - salt: random per-message, used with PBKDF2 to derive the AES key
 * - iv: random 12-byte nonce (standard for GCM)
 * - authTag: 16-byte GCM authentication tag
 * - ciphertext: AES-256-GCM encrypted payload
 *
 * Properties:
 * - Each message uses a unique random salt AND iv → unique key + nonce per message
 * - Ciphertext is indistinguishable from random (GCM property)
 * - No statistical patterns or autocorrelation (each byte is effectively random)
 * - Authenticated encryption prevents tampering
 * - PBKDF2 with 600k iterations makes brute-force expensive
 * - Compatible with Web Crypto API (browser) and Node.js crypto
 */

const E2E_VERSION = 0x01;
const PBKDF2_ITERATIONS = 600000; // OWASP recommendation for SHA-256
const PBKDF2_DIGEST = "sha256";
const KEY_LENGTH = 32; // AES-256

/**
 * PBKDF2 key derivation — compatible with both Node.js and Web Crypto API.
 * Using PBKDF2 with 600k iterations of SHA-256 (OWASP 2023 recommendation).
 * This ensures browser clients can use the same algorithm via SubtleCrypto.
 */
function deriveKey(password, salt) {
  return crypto.pbkdf2Sync(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH, PBKDF2_DIGEST);
}

/**
 * Encrypt a Buffer with AES-256-GCM + PBKDF2 key derivation.
 * @param {Buffer} plaintext
 * @param {string} password
 * @returns {Buffer} encrypted payload
 */
function e2eEncrypt(plaintext, password) {
  const salt = crypto.randomBytes(32);
  const iv = crypto.randomBytes(12);
  const key = deriveKey(password, salt);

  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  const encrypted = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const authTag = cipher.getAuthTag(); // 16 bytes

  // Pack: [version][salt][iv][authTag][ciphertext]
  return Buffer.concat([
    Buffer.from([E2E_VERSION]),
    salt,
    iv,
    authTag,
    encrypted
  ]);
}

/**
 * Decrypt a Buffer encrypted with e2eEncrypt.
 * @param {Buffer} payload
 * @param {string} password
 * @returns {Buffer} plaintext
 * @throws {Error} on decryption failure (wrong password, tampered data, etc.)
 */
function e2eDecrypt(payload, password) {
  if (payload.length < 1 + 32 + 12 + 16) {
    throw new Error("E2E payload too short");
  }

  const version = payload[0];
  if (version !== E2E_VERSION) {
    throw new Error(`Unsupported E2E version: ${version}`);
  }

  let offset = 1;
  const salt = payload.subarray(offset, offset + 32);
  offset += 32;
  const iv = payload.subarray(offset, offset + 12);
  offset += 12;
  const authTag = payload.subarray(offset, offset + 16);
  offset += 16;
  const ciphertext = payload.subarray(offset);

  const key = deriveKey(password, salt);

  const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(authTag);

  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

// ========================== CORS ==========================

function buildCorsHeaders(req) {
  const origin = req.headers.origin;
  const allowOrigin = origin || "*";
  return {
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Headers": "authorization,content-type,x-api-key,x-e2e-encrypted",
    "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
    "Access-Control-Expose-Headers": "x-e2e-encrypted,x-original-content-type,x-ollama-upstream",
    Vary: origin ? "Origin" : undefined
  };
}

function writeHeadWithCors(res, statusCode, headers, cors) {
  const out = Object.assign({}, headers || {});
  for (const k of Object.keys(cors)) {
    if (cors[k] !== undefined) out[k] = cors[k];
  }
  res.writeHead(statusCode, out);
}

// ========================== HEADER SANITIZATION ==========================

function sanitizeUpstreamHeaders(req, config, bodyLength) {
  const h = {};
  if (req.headers["content-type"]) h["content-type"] = req.headers["content-type"];
  if (bodyLength !== undefined) {
    h["content-length"] = String(bodyLength);
  } else if (req.headers["content-length"]) {
    h["content-length"] = req.headers["content-length"];
  }
  if (req.headers["accept"]) h["accept"] = req.headers["accept"];
  if (req.headers["user-agent"]) h["user-agent"] = req.headers["user-agent"];
  h["host"] = `${config.upstreamHost}:${config.upstreamPort}`;
  return h;
}

// ========================== HELPERS ==========================

function collectBody(stream) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    stream.on("data", (chunk) => chunks.push(chunk));
    stream.on("end", () => resolve(Buffer.concat(chunks)));
    stream.on("error", reject);
  });
}

// ========================== MAIN SERVER ==========================

async function main() {
  const config = await promptConfig();

  // --- TLS ---
  const certFolder = path.resolve("./certs");
  let tlsOptions;
  try {
    tlsOptions = {
      key: fs.readFileSync(path.join(certFolder, "current-car.com-key.pem")),
      cert: fs.readFileSync(path.join(certFolder, "current-car.com-fullchain.pem"))
    };
  } catch (err) {
    console.error(`\n⚠  Could not load TLS certificates from ${certFolder}:`);
    console.error(`   ${err.message}`);
    console.error(`   The server requires TLS certificates to start.\n`);
    process.exit(1);
  }

  // --- Auth ---
  const authValidator = createAuthValidator(config.authUsername, config.authPassword);

  // --- E2E ---
  const e2eEnabled = !!config.e2ePassword;

  // --- Server ---
  const server = https.createServer(tlsOptions, async (req, res) => {
    const cors = buildCorsHeaders(req);

    // ---- Preflight ----
    if (req.method === "OPTIONS") {
      writeHeadWithCors(res, 204, {}, cors);
      return res.end();
    }

    // ---- Authentication ----
    if (authValidator) {
      if (!authValidator.validate(req.headers.authorization)) {
        writeHeadWithCors(res, 401, {
          "Content-Type": "application/json",
          "WWW-Authenticate": 'Basic realm="Ollama Proxy"'
        }, cors);
        res.end(JSON.stringify({ error: "Unauthorized" }));
        if (config.debug) {
          console.log(`[AUTH] ${req.method} ${req.url} REJECTED (invalid credentials)`);
        }
        return;
      }
    }

    // ---- Determine if E2E encrypted ----
    const isE2eRequest = req.headers["x-e2e-encrypted"] === "true";

    if (config.e2eRequired && e2eEnabled && !isE2eRequest) {
      writeHeadWithCors(res, 403, { "Content-Type": "application/json" }, cors);
      res.end(JSON.stringify({ error: "E2E encryption required but request was not encrypted" }));
      return;
    }

    const url = req.url || "/";

    // ---- E2E mode: buffer + decrypt/encrypt ----
    if (isE2eRequest && e2eEnabled) {
      try {
        // Collect full request body
        const encryptedBody = await collectBody(req);

        // Decrypt request body
        let plainBody;
        try {
          plainBody = e2eDecrypt(encryptedBody, config.e2ePassword);
        } catch (decErr) {
          writeHeadWithCors(res, 400, { "Content-Type": "application/json" }, cors);
          res.end(JSON.stringify({ error: "E2E decryption failed", details: decErr.message }));
          return;
        }

        // Restore original content-type if provided
        const originalContentType =
          req.headers["x-original-content-type"] || "application/json";

        // Forward to upstream
        const upHeaders = sanitizeUpstreamHeaders(req, config, plainBody.length);
        upHeaders["content-type"] = originalContentType;

        const upstreamRes = await new Promise((resolve, reject) => {
          const upReq = http.request(
            {
              host: config.upstreamHost,
              port: config.upstreamPort,
              method: req.method,
              path: url,
              headers: upHeaders,
              timeout: 120000
            },
            resolve
          );
          upReq.on("timeout", () => upReq.destroy(new Error("Upstream timeout")));
          upReq.on("error", reject);
          upReq.end(plainBody);
        });

        // Collect upstream response
        const upstreamBody = await collectBody(upstreamRes);

        // Encrypt response
        const encryptedResponse = e2eEncrypt(upstreamBody, config.e2ePassword);

        const responseHeaders = {
          "Content-Type": "application/octet-stream",
          "X-E2E-Encrypted": "true",
          "X-Original-Content-Type": upstreamRes.headers["content-type"] || "application/json",
          "X-Ollama-Upstream": `http://${config.upstreamHost}:${config.upstreamPort}`
        };
        writeHeadWithCors(res, upstreamRes.statusCode || 502, responseHeaders, cors);
        res.end(encryptedResponse);

        if (config.debug) {
          console.log(
            `[E2E] ${req.method} ${url} ` +
              `plain_in=${plainBody.length}B enc_out=${encryptedResponse.length}B -> ${upstreamRes.statusCode}`
          );
        }
      } catch (err) {
        if (config.debug) {
          console.log(`[E2E-ERR] ${req.method} ${url} -> ${err.message}`);
        }
        if (res.headersSent) return res.destroy(err);
        writeHeadWithCors(res, 502, { "Content-Type": "application/json" }, cors);
        res.end(JSON.stringify({ error: "Bad gateway (E2E)", details: err.message }));
      }
      return;
    }

    // ---- Non-E2E mode: standard streaming proxy ----
    const upstreamReq = http.request(
      {
        host: config.upstreamHost,
        port: config.upstreamPort,
        method: req.method,
        path: url,
        headers: sanitizeUpstreamHeaders(req, config),
        timeout: 120000
      },
      (upstreamRes) => {
        const headers = Object.assign({}, upstreamRes.headers);
        headers["x-ollama-upstream"] = `http://${config.upstreamHost}:${config.upstreamPort}`;
        writeHeadWithCors(res, upstreamRes.statusCode || 502, headers, cors);
        upstreamRes.pipe(res);

        if (config.debug) {
          console.log(`[OK] ${req.method} ${url} origin=${req.headers.origin || "-"} -> ${upstreamRes.statusCode}`);
        }
      }
    );

    upstreamReq.on("timeout", () => {
      upstreamReq.destroy(new Error("Upstream timeout"));
    });

    upstreamReq.on("error", (err) => {
      if (config.debug) {
        console.log(`[ERR] ${req.method} ${url} origin=${req.headers.origin || "-"} -> ${err.message}`);
      }
      if (res.headersSent) return res.destroy(err);
      writeHeadWithCors(res, 502, { "Content-Type": "application/json" }, cors);
      res.end(JSON.stringify({ error: "Bad gateway", details: err.message }));
    });

    req.pipe(upstreamReq);
  });

  server.listen(config.port, config.listenHost, () => {
    const bind = config.listenHost ? `${config.listenHost}:${config.port}` : `0.0.0.0:${config.port}`;
    console.log("╔══════════════════════════════════════════════════════════╗");
    console.log(`║  Ollama HTTPS Proxy listening on https://${bind.padEnd(19)}║`);
    console.log(`║  Upstream: http://${config.upstreamHost}:${config.upstreamPort}`.padEnd(59) + "║");
    console.log(`║  Auth: ${authValidator ? "ENABLED" : "DISABLED"}`.padEnd(59) + "║");
    console.log(`║  E2E:  ${e2eEnabled ? `ENABLED (required=${config.e2eRequired})` : "DISABLED"}`.padEnd(59) + "║");
    console.log("╚══════════════════════════════════════════════════════════╝");
  });
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
