# win-proxy — Windows Proxy Resolver

Detects proxy settings on Windows by reading the **Windows Registry** and
evaluating **PAC/WPAD auto-configuration scripts** via **GraalJS**.

**No PowerShell required.** Works on hardened systems where PowerShell
Constrained Language Mode (CLM) blocks .NET method calls like
`[System.Net.WebRequest]::GetSystemWebProxy()`.

## The Problem

In enterprise environments, proxy configuration is typically managed via
Group Policy (WPAD/PAC auto-config or static proxy settings). Java applications
need to detect these settings at runtime, but the standard approaches fail:

| Approach | Problem |
|---|---|
| `java.net.useSystemProxies=true` | Only works as JVM startup arg; `DefaultProxySelector` reads it once at class init |
| PowerShell `.NET` calls | Blocked by Constrained Language Mode (CLM) on hardened systems |
| Manual `ProxySelector` | Doesn't understand PAC/WPAD scripts |

**win-proxy** solves this by:
1. Reading `AutoConfigURL`, `ProxyEnable`, `ProxyServer`, and `ProxyOverride`
   from `HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings`
   via `reg.exe` (never blocked by policy)
2. Checking the **WPAD auto-detect flag** in `DefaultConnectionSettings` (binary
   blob, byte 8, bit `0x08`) — if enabled, downloads `http://wpad/wpad.dat`
3. Evaluating PAC scripts via GraalJS (pure Java, no native deps)
4. Providing a simple, typed result (`ProxyResult`)

## Usage

```java
import de.bund.zrb.winproxy.WindowsProxyResolver;
import de.bund.zrb.winproxy.ProxyResult;

// Full auto-detection: AutoConfigURL → static proxy → DIRECT
ProxyResult result = WindowsProxyResolver.resolve("https://example.com");

if (result.isDirect()) {
    connection = url.openConnection();
} else {
    connection = url.openConnection(result.toJavaProxy());
    // or: result.getHost() + ":" + result.getPort()
}

// Evaluate a specific PAC URL
ProxyResult pac = WindowsProxyResolver.evaluatePac(
    "http://wpad.corp.local/wpad.dat", "https://example.com");

// Test a PAC script string (without downloading)
ProxyResult test = WindowsProxyResolver.evaluatePacScript(
    "function FindProxyForURL(url,host) { return 'PROXY proxy:8080'; }",
    "https://example.com");

// Raw registry access
String autoConfig = WindowsProxyResolver.readRegistryValue(
    WindowsProxyResolver.INTERNET_SETTINGS_KEY, "AutoConfigURL");

// Bypass list check
boolean bypassed = WindowsProxyResolver.isBypassed(
    "intranet.corp.local", "*.corp.local;10.*;<local>");
```

## API

### `WindowsProxyResolver` (Facade)

| Method | Description |
|---|---|
| `resolve(url)` | Full detection chain: PAC → WPAD → static → DIRECT |
| `resolveStatic(url)` | Static proxy only (skips AutoConfigURL and WPAD) |
| `evaluatePac(pacUrl, targetUrl)` | Download + evaluate a PAC file |
| `evaluatePacScript(script, targetUrl)` | Evaluate a PAC script string |
| `readRegistryValue(key, name)` | Read any Windows Registry value via `reg.exe` |
| `readConnectionFlags()` | Read raw connection flags byte from `DefaultConnectionSettings` |
| `isWpadAutoDetectEnabled()` | Check if "Automatically detect settings" is on |
| `isBypassed(host, overrideList)` | Check proxy bypass rules (wildcards, `<local>`) |

### `ProxyResult` (Value Object)

| Method | Description |
|---|---|
| `isDirect()` | `true` if no proxy needed |
| `getHost()` | Proxy hostname (or `null` for DIRECT) |
| `getPort()` | Proxy port (or `0` for DIRECT) |
| `getReason()` | Diagnostic string for logging |
| `toJavaProxy()` | Converts to `java.net.Proxy` |
| `toString()` | Human-readable: `"PROXY host:port (reason)"` or `"DIRECT (reason)"` |

## Dependencies

- **Java 8+**
- **GraalJS 21.2.0** (`org.graalvm.js:js`) — for PAC/WPAD script evaluation
- **`reg.exe`** — ships with every Windows installation

## How It Works

```
┌─────────────────────────────────────────┐
│         WindowsProxyResolver            │
│                                         │
│  1. Read AutoConfigURL from Registry    │
│     ├── Download PAC file               │
│     └── Evaluate FindProxyForURL()      │
│         via GraalJS                     │
│                                         │
│  2. WPAD Auto-Detect                    │
│     ├── Read DefaultConnectionSettings  │
│     │   binary (byte 8, bit 0x08)       │
│     ├── If enabled: download            │
│     │   http://wpad/wpad.dat            │
│     └── Evaluate FindProxyForURL()      │
│                                         │
│  3. Read ProxyEnable + ProxyServer      │
│     ├── Check ProxyOverride bypass list │
│     └── Parse protocol-specific format  │
│         (http=host:port;https=host:port)│
│                                         │
│  4. Return DIRECT if nothing configured │
└─────────────────────────────────────────┘
```

## PAC Helper Functions

The following standard PAC functions are provided as JavaScript shims:

`isPlainHostName`, `dnsDomainIs`, `localHostOrDomainIs`, `isResolvable`,
`dnsResolve`, `myIpAddress`, `dnsDomainLevels`, `shExpMatch`, `isInNet`,
`weekdayRange`, `dateRange`, `timeRange`, `alert`
