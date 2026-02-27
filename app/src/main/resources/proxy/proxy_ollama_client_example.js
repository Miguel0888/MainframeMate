/**
 * Example client for the Ollama HTTPS Proxy with E2E encryption.
 *
 * Demonstrates:
 *   1. Basic Auth against the proxy
 *   2. E2E payload encryption (AES-256-GCM + PBKDF2) — same scheme as the proxy
 *   3. Sending an encrypted request and decrypting the response
 *
 * Usage:
 *   node proxy_ollama_client_example.js
 *
 * Requirements:
 *   - The shared E2E password must match the one configured on the proxy.
 *   - The auth username/password must match the proxy configuration.
 *   - Node.js >= 16
 */

import https from "https";
import crypto from "crypto";
import readline from "readline";

// ========================== CONFIG ==========================

const PROXY_HOST = process.env.PROXY_HOST || "current-car.com";
const PROXY_PORT = Number(process.env.PROXY_PORT || 11435);

// Set to true to skip TLS certificate verification (self-signed certs)
const SKIP_TLS_VERIFY = process.env.SKIP_TLS_VERIFY === "true";

// ========================== E2E ENCRYPTION ==========================
// Must match the proxy's encryption parameters exactly.

const E2E_VERSION = 0x01;
const PBKDF2_ITERATIONS = 600000; // Must match proxy
const PBKDF2_DIGEST = "sha256";
const KEY_LENGTH = 32; // AES-256

function deriveKey(password, salt) {
  return crypto.pbkdf2Sync(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH, PBKDF2_DIGEST);
}

function e2eEncrypt(plaintext, password) {
  const salt = crypto.randomBytes(32);
  const iv = crypto.randomBytes(12);
  const key = deriveKey(password, salt);

  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  const encrypted = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const authTag = cipher.getAuthTag();

  return Buffer.concat([
    Buffer.from([E2E_VERSION]),
    salt,  // 32 bytes
    iv,    // 12 bytes
    authTag, // 16 bytes
    encrypted,
  ]);
}

function e2eDecrypt(payload, password) {
  if (payload.length < 1 + 32 + 12 + 16) {
    throw new Error("E2E payload too short");
  }

  const version = payload[0];
  if (version !== E2E_VERSION) {
    throw new Error(`Unsupported E2E version: ${version}`);
  }

  let offset = 1;
  const salt = payload.subarray(offset, offset + 32); offset += 32;
  const iv = payload.subarray(offset, offset + 12); offset += 12;
  const authTag = payload.subarray(offset, offset + 16); offset += 16;
  const ciphertext = payload.subarray(offset);

  const key = deriveKey(password, salt);
  const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(authTag);

  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

// ========================== HELPERS ==========================

function askQuestion(rl, prompt) {
  return new Promise((resolve) => rl.question(prompt, resolve));
}

function askPassword(prompt) {
  return new Promise((resolve) => {
    process.stdout.write(prompt);
    const stdin = process.stdin;
    const wasRaw = stdin.isRaw;
    if (typeof stdin.setRawMode === "function") stdin.setRawMode(true);
    stdin.resume();

    let pw = "";
    const onData = (ch) => {
      const c = ch.toString("utf8");
      if (c === "\n" || c === "\r" || c === "\u0004") {
        if (typeof stdin.setRawMode === "function") stdin.setRawMode(wasRaw || false);
        stdin.removeListener("data", onData);
        process.stdout.write("\n");
        resolve(pw);
      } else if (c === "\u0003") {
        process.exit(1);
      } else if (c === "\u007f" || c === "\b") {
        if (pw.length > 0) { pw = pw.slice(0, -1); process.stdout.write("\b \b"); }
      } else {
        pw += c;
        process.stdout.write("*");
      }
    };
    stdin.on("data", onData);
  });
}

function httpsRequest(options, body) {
  return new Promise((resolve, reject) => {
    const req = https.request(options, (res) => {
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
    });
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

// ========================== MAIN ==========================

async function main() {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });

  console.log("\n=== Ollama Proxy E2E Client Example ===\n");

  // Gather credentials
  const username = (await askQuestion(rl, `Auth username [user]: `)).trim() || "user";
  rl.close();
  const password = await askPassword("Auth password: ");
  const e2ePassword = await askPassword("E2E encryption password (empty = no encryption): ");

  const useE2E = !!e2ePassword.trim();

  // Build the Ollama request
  const ollamaPayload = JSON.stringify({
    model: "llama3.2",
    prompt: "Hello, how are you?",
    stream: false,
  });

  console.log(`\nSending request to https://${PROXY_HOST}:${PROXY_PORT}/api/generate`);
  console.log(`  E2E encryption: ${useE2E ? "ON" : "OFF"}`);
  console.log(`  Payload size: ${ollamaPayload.length} bytes\n`);

  let requestBody;
  const headers = {
    Authorization: "Basic " + Buffer.from(`${username}:${password}`).toString("base64"),
  };

  if (useE2E) {
    // Encrypt the JSON payload
    const plainBuffer = Buffer.from(ollamaPayload, "utf8");
    requestBody = e2eEncrypt(plainBuffer, e2ePassword.trim());

    headers["Content-Type"] = "application/octet-stream";
    headers["X-E2E-Encrypted"] = "true";
    headers["X-Original-Content-Type"] = "application/json";
    headers["Content-Length"] = requestBody.length;

    console.log(`  Encrypted payload size: ${requestBody.length} bytes`);
  } else {
    requestBody = Buffer.from(ollamaPayload, "utf8");
    headers["Content-Type"] = "application/json";
    headers["Content-Length"] = requestBody.length;
  }

  const options = {
    hostname: PROXY_HOST,
    port: PROXY_PORT,
    path: "/api/generate",
    method: "POST",
    headers,
    rejectUnauthorized: !SKIP_TLS_VERIFY,
    timeout: 120000,
  };

  try {
    const resp = await httpsRequest(options, requestBody);
    console.log(`\nResponse status: ${resp.status}`);

    if (resp.status === 401) {
      console.error("Authentication failed! Check username and password.");
      return;
    }

    let responseText;
    if (useE2E && resp.headers["x-e2e-encrypted"] === "true") {
      // Decrypt the response
      const decrypted = e2eDecrypt(resp.body, e2ePassword.trim());
      responseText = decrypted.toString("utf8");
      console.log(`  Encrypted response size: ${resp.body.length} bytes`);
      console.log(`  Decrypted response size: ${decrypted.length} bytes`);
    } else {
      responseText = resp.body.toString("utf8");
    }

    console.log("\n--- Response ---");
    try {
      const json = JSON.parse(responseText);
      console.log(JSON.stringify(json, null, 2));
    } catch {
      console.log(responseText);
    }
  } catch (err) {
    console.error("Request failed:", err.message);
  }
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});
