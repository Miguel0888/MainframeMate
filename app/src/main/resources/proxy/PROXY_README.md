# Ollama HTTPS Proxy — Auth & E2E Encryption

Ein sicherer HTTPS-Reverse-Proxy für Ollama mit optionaler Authentifizierung und Ende-zu-Ende-Verschlüsselung.

## Features

1. **Interaktive Konfiguration** — Beim Start werden nicht gesetzte Umgebungsvariablen abgefragt. Enter überspringt (Standardwert wird verwendet).
2. **Basic Auth** — Optionaler Passwortschutz. Clients ohne gültige Credentials werden mit `401 Unauthorized` abgewiesen.
3. **E2E-Verschlüsselung** — AES-256-GCM mit PBKDF2-Schlüsselableitung, unabhängig von TLS. Das Passwort wird **nie** über das Netzwerk übertragen.

## Schnellstart

```bash
# Proxy starten (interaktive Eingabe)
node proxy_ollama.js

# Oder mit Umgebungsvariablen
PORT=11435 PROXY_PASSWORD=geheim E2E_PASSWORD=supergeheim node proxy_ollama.js
```

## Konfiguration

| Variable | Default | Beschreibung |
|---|---|---|
| `PORT` | 11435 | HTTPS-Listen-Port |
| `LISTEN_HOST` | 0.0.0.0 | Bind-Adresse |
| `OLLAMA_HOST` | 127.0.0.1 | Ollama-Upstream-Host |
| `OLLAMA_PORT` | 11434 | Ollama-Upstream-Port |
| `DEBUG` | false | Verbose Logging |
| `PROXY_USERNAME` | user | Auth-Benutzername |
| `PROXY_PASSWORD` | *(leer)* | Auth-Passwort (leer = Auth deaktiviert) |
| `E2E_PASSWORD` | *(leer)* | Verschlüsselungs-Passwort (leer = E2E deaktiviert) |
| `E2E_REQUIRED` | false | Erzwingt E2E für alle Requests |

## Authentifizierung

Wenn `PROXY_PASSWORD` gesetzt ist, erfordert der Proxy **HTTP Basic Auth**:

```
Authorization: Basic base64(username:password)
```

### Beispiel (curl ohne E2E):
```bash
curl -k -u user:geheim https://example.com:11435/api/generate \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3.2","prompt":"Hello","stream":false}'
```

## E2E-Verschlüsselung

### Sicherheitseigenschaften

- **AES-256-GCM** — Authentifizierte Verschlüsselung (AEAD)
- **PBKDF2** mit 600.000 Iterationen SHA-256 (OWASP 2023 Empfehlung)
- **Zufälliger Salt** (32 Bytes) pro Nachricht → eindeutiger Schlüssel pro Nachricht
- **Zufälliger IV** (12 Bytes) pro Nachricht → eindeutige Nonce pro Nachricht
- **Keine statistischen Muster** — Ciphertext ist von Zufallsdaten ununterscheidbar
- **Keine Autokorrelation** — Jede Nachricht hat unabhängigen Salt/IV/Key
- **Tamper-Schutz** — GCM AuthTag verhindert Manipulation

### Wire-Format

```
[Version 1B][Salt 32B][IV 12B][AuthTag 16B][Ciphertext ...]
```

Overhead pro Nachricht: 61 Bytes (1 + 32 + 12 + 16).

### Protokoll

1. Client verschlüsselt den JSON-Body
2. Client sendet mit Header:
   - `X-E2E-Encrypted: true`
   - `Content-Type: application/octet-stream`
   - `X-Original-Content-Type: application/json`
3. Proxy entschlüsselt → leitet an Ollama weiter
4. Proxy verschlüsselt die Antwort
5. Proxy sendet mit Header:
   - `X-E2E-Encrypted: true`
   - `X-Original-Content-Type: application/json`
6. Client entschlüsselt die Antwort

### Browser-Client

```javascript
import { fetchWithE2E } from './proxy_ollama_e2e_browser.js';

const response = await fetchWithE2E(
  'https://example.com:11435/api/generate',
  { model: 'llama3.2', prompt: 'Hello!', stream: false },
  'mein-e2e-passwort',
  { username: 'user', password: 'mein-auth-passwort' }
);
console.log(response);
```

### Node.js-Client

```bash
node proxy_ollama_client_example.js
```

## Dateien

| Datei | Beschreibung |
|---|---|
| `proxy_ollama.js` | Haupt-Proxy (Server) |
| `proxy_ollama_client_example.js` | Node.js-Client-Beispiel |
| `proxy_ollama_e2e_browser.js` | Browser-E2E-Modul (Web Crypto API) |
| `proxy_ollama_min_sample.js` | Ursprünglicher Minimal-Proxy (ohne Auth/E2E) |

## Architektur

```
Browser/Chatbot                    Proxy                         Ollama
     |                               |                             |
     |-- [TLS + E2E encrypted] ----->|                             |
     |   POST /api/generate          |                             |
     |   X-E2E-Encrypted: true       |-- [E2E decrypt] ---------->|
     |   Authorization: Basic ...    |                             |
     |                               |-- [HTTP plaintext] ------->|
     |                               |   POST /api/generate       |
     |                               |                             |
     |                               |<-- [HTTP response] --------|
     |                               |                             |
     |<-- [TLS + E2E encrypted] -----|-- [E2E encrypt] ---------->|
     |   X-E2E-Encrypted: true       |                             |
     |                               |                             |
```

## Hinweise

- **TLS-Zertifikate** müssen im Ordner `./certs/` liegen:
  - `example.com-key.pem`
  - `example.com-fullchain.pem`
- **Streaming** ist bei E2E deaktiviert (GCM erfordert vollständiges Buffering). Ohne E2E wird weiterhin gestreamt.
- Der Proxy unterstützt **Mischbetrieb**: Clients mit und ohne E2E können gleichzeitig zugreifen (sofern `E2E_REQUIRED` nicht `true` ist).
