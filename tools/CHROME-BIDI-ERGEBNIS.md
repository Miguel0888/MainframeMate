# Chrome BiDi Test – Ergebnis (25.02.2026)

## Getestete Endpunkte (Chrome 145)

| Endpoint | WS Connect | BiDi session.status | session.new |
|----------|-----------|--------------------|----|
| `ws://.../json` | ❌ 404 | - | - |
| `ws://.../devtools` | ❌ 404 | - | - |
| `ws://.../devtools/browser` (ohne UUID) | ❌ 404 | - | - |
| `ws://.../devtools/browser/<UUID>` | ✅ | ❌ not found | ❌ not found |
| `ws://.../devtools/page/<UUID>` | ✅ | ❌ not found | ❌ not found |
| `ws://.../session` | ❌ 404 | - | - |

## Protokoll-Analyse

- Das Chrome CDP-Protokoll (1.585.916 Bytes) enthält **keine** BiDi-Methoden.
- `"bidi"` → nur 1 Treffer: `generateBidInterestGroup` (Ad-Auction API)
- `"capability"` → nur Video-Codec-Capabilities
- `"newSession"` → nur `DeviceBoundSession` (Auth/Cookie)
- `"webSocketUrl"` als Capability → **existiert nicht** im CDP-Protokoll

## Schlussfolgerung

Chrome bietet **keinen nativen BiDi-Endpoint** über `--remote-debugging-port`.

BiDi wird bei Chrome vollständig durch den **chromium-bidi Mapper** realisiert:
- ChromeDriver lädt `chromium-bidi` (npm-Paket) als JavaScript
- Injiziert es via CDP `Runtime.evaluate` in eine spezielle Target-Page
- Der Mapper übersetzt BiDi-Befehle → CDP-Befehle

**Ohne ChromeDriver oder manuelles Laden des chromium-bidi Mappers gibt es kein BiDi bei Chrome.**

## Optionen

1. **Firefox beibehalten** – BiDi funktioniert dort nativ ohne Driver
2. **Chrome nur mit CDP** – kein BiDi, aber Navigation/Network/DOM funktioniert über CDP
3. **chromium-bidi Mapper selbst laden** – theoretisch möglich via `Runtime.evaluate`, 
   aber extrem aufwändig (das npm-Paket ist >100KB bundled JS)
