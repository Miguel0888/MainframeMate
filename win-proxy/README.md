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
| Only reading `HKCU\...\Internet Settings` | Misses Group Policy settings (`Software\Policies\...`) on hardened machines |

**win-proxy** solves this by:
1. Searching **all four registry hives** in correct priority order:
   - `HKCU\Software\Policies\...\Internet Settings` (User GPO)
   - `HKLM\Software\Policies\...\Internet Settings` (Machine GPO)
   - `HKCU\Software\Microsoft\...\Internet Settings` (User settings)
   - `HKLM\Software\Microsoft\...\Internet Settings` (Machine settings)
2. Reading `AutoConfigURL`, `ProxyEnable`, `ProxyServer`, and `ProxyOverride`
   via `reg.exe` (never blocked by policy)
3. Parsing the **`DefaultConnectionSettings` binary blob** for an embedded PAC URL
   (flag `0x04`) — even when `AutoConfigURL` is not a separate registry value
4. Checking the **WPAD auto-detect flag** (`0x08`) and trying `http://wpad/wpad.dat`
   as a last-resort fallback
5. Evaluating PAC scripts via GraalJS (pure Java, no native deps)
6. Providing a simple, typed result (`ProxyResult`)

## Usage

```java
import de.bund.zrb.winproxy.WindowsProxyResolver;
import de.bund.zrb.winproxy.ProxyResult;

// Full auto-detection: GPO PAC → user PAC → blob PAC → WPAD → static → DIRECT
ProxyResult result = WindowsProxyResolver.resolve("https://example.com");

if (result.isDirect()) {
    connection = url.openConnection();
} else {
    connection = url.openConnection(result.toJavaProxy());
    // or: result.getHost() + ":" + result.getPort()
}

// Search all registry hives (GPO first) for a specific value
String autoConfig = WindowsProxyResolver.readRegistryValueFromAllHives("AutoConfigURL");

// Evaluate a specific PAC URL
ProxyResult pac = WindowsProxyResolver.evaluatePac(
    "http://wpad.corp.local/wpad.dat", "https://example.com");

// Test a PAC script string (without downloading)
ProxyResult test = WindowsProxyResolver.evaluatePacScript(
    "function FindProxyForURL(url,host) { return 'PROXY proxy:8080'; }",
    "https://example.com");

// Raw registry access (single key)
String autoConfigUser = WindowsProxyResolver.readRegistryValue(
    WindowsProxyResolver.INTERNET_SETTINGS_KEY, "AutoConfigURL");

// Bypass list check
boolean bypassed = WindowsProxyResolver.isBypassed(
    "intranet.corp.local", "*.corp.local;10.*;<local>");
```

## API

### `WindowsProxyResolver` (Facade)

| Method | Description |
|---|---|
| `resolve(url)` | Full detection chain: GPO PAC → user PAC → blob PAC → WPAD → static → DIRECT |
| `resolveStatic(url)` | Static proxy only (all hives, skips PAC/WPAD) |
| `evaluatePac(pacUrl, targetUrl)` | Download + evaluate a PAC file |
| `evaluatePacScript(script, targetUrl)` | Evaluate a PAC script string |
| `readRegistryValueFromAllHives(name)` | Search all 4 hives (GPO first) for a registry value |
| `readRegistryValue(key, name)` | Read a specific Windows Registry value via `reg.exe` |
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
┌─────────────────────────────────────────────────────┐
│              WindowsProxyResolver                    │
│                                                     │
│  1. AutoConfigURL (PAC URL) from Registry           │
│     ├── Search 4 hives in priority order:           │
│     │   ① HKCU\...\Policies\...\Internet Settings  │
│     │   ② HKLM\...\Policies\...\Internet Settings  │
│     │   ③ HKCU\...\Microsoft\...\Internet Settings  │
│     │   ④ HKLM\...\Microsoft\...\Internet Settings  │
│     ├── Download PAC file (direct, no proxy)        │
│     └── Evaluate FindProxyForURL() via GraalJS      │
│                                                     │
│  2. DefaultConnectionSettings Blob                  │
│     ├── Parse binary structure for embedded PAC URL │
│     │   (flag 0x04 = auto-config script)            │
│     └── Evaluate if URL differs from step 1         │
│                                                     │
│  3. WPAD Auto-Detect (last resort)                  │
│     ├── Read flag 0x08 from blob byte 8             │
│     ├── If enabled: try http://wpad/wpad.dat        │
│     │   (DNS devolution appends domain suffixes)    │
│     └── Evaluate FindProxyForURL()                  │
│                                                     │
│  4. Static Proxy (all 4 hives)                      │
│     ├── ProxyEnable=1 + ProxyServer                 │
│     ├── Check ProxyOverride bypass list             │
│     └── Parse protocol-specific format              │
│         (http=host:port;https=host:port)            │
│                                                     │
│  5. Return DIRECT if nothing configured             │
└─────────────────────────────────────────────────────┘
```

## Why GPO Keys Matter

On hardened enterprise machines (e.g. Windows 11 with Group Policy), the PAC URL
is deployed via GPO and stored in `HKCU\Software\Policies\...` or
`HKLM\Software\Policies\...`. The normal user-level key
(`HKCU\Software\Microsoft\...\Internet Settings`) will be **empty**.

If you only check the user-level key (which most Java proxy libraries do), you get
a false `DIRECT` result — and all outgoing connections fail silently or timeout.

## PAC Helper Functions

The following standard PAC functions are provided as JavaScript shims:

`isPlainHostName`, `dnsDomainIs`, `localHostOrDomainIs`, `isResolvable`,
`dnsResolve`, `myIpAddress`, `dnsDomainLevels`, `shExpMatch`, `isInNet`,
`weekdayRange`, `dateRange`, `timeRange`, `alert`
