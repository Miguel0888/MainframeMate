/**
 * Browser-compatible E2E encryption module for the Ollama HTTPS Proxy.
 *
 * Uses the Web Crypto API (SubtleCrypto) — works in all modern browsers.
 * Same wire format as the Node.js proxy: [version 1B][salt 32B][iv 12B][authTag 16B][ciphertext]
 *
 * Note: The browser's PBKDF2 is used instead of scrypt (scrypt is not available in Web Crypto).
 * The proxy ALSO supports PBKDF2 if the version byte is 0x02.
 *
 * Usage in browser:
 *   import { e2eEncrypt, e2eDecrypt } from './proxy_ollama_e2e_browser.js';
 *
 *   const encrypted = await e2eEncrypt(jsonString, 'shared-password');
 *   // → Uint8Array, send as body with X-E2E-Encrypted: true
 *
 *   const decrypted = await e2eDecrypt(responseBytes, 'shared-password');
 *   // → string (the original JSON)
 */

// Must match proxy constants
const E2E_VERSION = 0x01;

// PBKDF2 with 600,000 iterations of SHA-256 (OWASP 2023 recommendation).
// Compatible with Node.js crypto.pbkdf2Sync on the proxy side.

const PBKDF2_ITERATIONS = 600000;
const KEY_LENGTH_BYTES = 32; // AES-256
const SALT_LENGTH = 32;
const IV_LENGTH = 12;
const TAG_LENGTH = 16;

const encoder = new TextEncoder();
const decoder = new TextDecoder();

/**
 * Derive a CryptoKey from a password and salt using PBKDF2.
 */
async function deriveKey(password, salt) {
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    encoder.encode(password),
    "PBKDF2",
    false,
    ["deriveKey"]
  );

  return crypto.subtle.deriveKey(
    {
      name: "PBKDF2",
      salt: salt,
      iterations: PBKDF2_ITERATIONS,
      hash: "SHA-256",
    },
    keyMaterial,
    { name: "AES-GCM", length: KEY_LENGTH_BYTES * 8 },
    false,
    ["encrypt", "decrypt"]
  );
}

/**
 * Encrypt a string payload with AES-256-GCM.
 * @param {string} plaintext - The JSON/text to encrypt
 * @param {string} password - Shared secret
 * @returns {Promise<Uint8Array>} - Wire-format encrypted payload
 */
export async function e2eEncrypt(plaintext, password) {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_LENGTH));
  const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH));
  const key = await deriveKey(password, salt);

  const plaintextBytes = encoder.encode(plaintext);

  // AES-GCM: encrypted output includes the auth tag appended by default
  const ciphertextWithTag = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv, tagLength: TAG_LENGTH * 8 },
    key,
    plaintextBytes
  );

  const ctArray = new Uint8Array(ciphertextWithTag);
  // Web Crypto appends authTag at the end of the ciphertext
  const ciphertext = ctArray.slice(0, ctArray.length - TAG_LENGTH);
  const authTag = ctArray.slice(ctArray.length - TAG_LENGTH);

  // Pack: [version][salt][iv][authTag][ciphertext]
  const result = new Uint8Array(1 + SALT_LENGTH + IV_LENGTH + TAG_LENGTH + ciphertext.length);
  let offset = 0;
  result[offset++] = E2E_VERSION;
  result.set(salt, offset); offset += SALT_LENGTH;
  result.set(iv, offset); offset += IV_LENGTH;
  result.set(authTag, offset); offset += TAG_LENGTH;
  result.set(ciphertext, offset);

  return result;
}

/**
 * Decrypt a wire-format payload.
 * @param {Uint8Array|ArrayBuffer} payload - Encrypted payload from proxy
 * @param {string} password - Shared secret
 * @returns {Promise<string>} - Decrypted plaintext
 */
export async function e2eDecrypt(payload, password) {
  const data = payload instanceof Uint8Array ? payload : new Uint8Array(payload);

  if (data.length < 1 + SALT_LENGTH + IV_LENGTH + TAG_LENGTH) {
    throw new Error("E2E payload too short");
  }

  const version = data[0];
  if (version !== E2E_VERSION) {
    throw new Error(`Unsupported E2E version: ${version}`);
  }

  let offset = 1;
  const salt = data.slice(offset, offset + SALT_LENGTH); offset += SALT_LENGTH;
  const iv = data.slice(offset, offset + IV_LENGTH); offset += IV_LENGTH;
  const authTag = data.slice(offset, offset + TAG_LENGTH); offset += TAG_LENGTH;
  const ciphertext = data.slice(offset);

  const key = await deriveKey(password, salt);

  // Web Crypto expects [ciphertext + authTag] concatenated
  const ciphertextWithTag = new Uint8Array(ciphertext.length + TAG_LENGTH);
  ciphertextWithTag.set(ciphertext, 0);
  ciphertextWithTag.set(authTag, ciphertext.length);

  const plainBuffer = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv, tagLength: TAG_LENGTH * 8 },
    key,
    ciphertextWithTag
  );

  return decoder.decode(plainBuffer);
}

/**
 * Helper: Send an E2E-encrypted request to the Ollama proxy via fetch().
 *
 * @param {string} url - Full proxy URL (e.g., "https://current-car.com:11435/api/generate")
 * @param {object} body - The JSON body to send to Ollama
 * @param {string} e2ePassword - Shared encryption password
 * @param {object} [authOpts] - { username, password } for Basic Auth
 * @returns {Promise<object>} - Decoded JSON response from Ollama
 */
export async function fetchWithE2E(url, body, e2ePassword, authOpts = {}) {
  const jsonStr = JSON.stringify(body);
  const encrypted = await e2eEncrypt(jsonStr, e2ePassword);

  const headers = {
    "Content-Type": "application/octet-stream",
    "X-E2E-Encrypted": "true",
    "X-Original-Content-Type": "application/json",
  };

  if (authOpts.username && authOpts.password) {
    headers["Authorization"] = "Basic " + btoa(`${authOpts.username}:${authOpts.password}`);
  }

  const resp = await fetch(url, {
    method: "POST",
    headers,
    body: encrypted,
  });

  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(`Proxy error ${resp.status}: ${text}`);
  }

  if (resp.headers.get("x-e2e-encrypted") === "true") {
    const encryptedResponse = new Uint8Array(await resp.arrayBuffer());
    const decrypted = await e2eDecrypt(encryptedResponse, e2ePassword);
    return JSON.parse(decrypted);
  }

  return resp.json();
}

/*
 * Example usage (paste in browser console or use in your chatbot):
 *
 * import { fetchWithE2E } from './proxy_ollama_e2e_browser.js';
 *
 * const response = await fetchWithE2E(
 *   'https://current-car.com:11435/api/generate',
 *   { model: 'llama3.2', prompt: 'Hello!', stream: false },
 *   'my-secret-e2e-password',
 *   { username: 'user', password: 'my-proxy-password' }
 * );
 * console.log(response);
 */
