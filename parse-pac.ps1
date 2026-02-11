param(
    [string]$TestUrl = "https://plugins.gradle.org/m2/",
    [switch]$DebugEnabled
)

function Write-DebugLine([string]$msg) {
    if ($DebugEnabled) { Write-Host $msg }
}

function Test-TcpPort {
    param(
        [string]$Host,
        [int]$Port,
        [int]$TimeoutMs = 1500
    )

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($Host, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if (-not $ok) {
            $client.Close()
            return $false
        }
        $client.EndConnect($iar) | Out-Null
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Normalize-ProxyToken([string]$token) {
    $t = ($token ?? "").Trim()
    if (-not $t) { return $null }

    # Remove scheme mappings like "https=" or "http="
    if ($t -match '^(?i)(http|https)=') {
        $t = ($t -replace '^(?i)(http|https)=', '').Trim()
    }

    # Remove common prefixes
    $t = ($t -replace '^(?i)PROXY\s+', '').Trim()
    $t = ($t -replace '^(?i)HTTPS?\s+', '').Trim()
    $t = ($t -replace '^(?i)SOCKS?\s+', '').Trim()

    # Remove protocol prefix if present
    $t = ($t -replace '^(?i)https?://', '').Trim()

    if ($t -match '^[^:]+:\d+$') { return $t }
    return $null
}

function Get-ProxyCandidates([string]$proxyString, [string]$url) {
    $uri = [Uri]$url
    $scheme = $uri.Scheme.ToLowerInvariant()

    $rawParts = @()
    foreach ($p in ($proxyString -split ';')) {
        $pp = $p.Trim()
        if ($pp) { $rawParts += $pp }
    }

    # Prefer explicit scheme mapping (e.g. "https=proxy:8080") when present
    $schemeSpecific = @()
    foreach ($p in $rawParts) {
        if ($p -match "^(?i)$scheme=") {
            $n = Normalize-ProxyToken $p
            if ($n) { $schemeSpecific += $n }
        }
    }

    if ($schemeSpecific.Count -gt 0) { return $schemeSpecific }

    # Otherwise accept general list
    $general = @()
    foreach ($p in $rawParts) {
        $n = Normalize-ProxyToken $p
        if ($n) { $general += $n }
    }
    return $general
}

# Load WinHTTP helpers only once
if (-not ("WinHttp.NativeMethods" -as [type])) {
    Add-Type -Namespace WinHttp -Name NativeMethods -MemberDefinition @"
using System;
using System.Runtime.InteropServices;

public static class NativeMethods {
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct WINHTTP_CURRENT_USER_IE_PROXY_CONFIG {
    [MarshalAs(UnmanagedType.Bool)]
    public bool fAutoDetect;
    public IntPtr lpszAutoConfigUrl;
    public IntPtr lpszProxy;
    public IntPtr lpszProxyBypass;
  }

  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct WINHTTP_AUTOPROXY_OPTIONS {
    public uint dwFlags;
    public uint dwAutoDetectFlags;
    public IntPtr lpszAutoConfigUrl;
    public IntPtr lpvReserved;
    public uint dwReserved;
    [MarshalAs(UnmanagedType.Bool)]
    public bool fAutoLogonIfChallenged;
  }

  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct WINHTTP_PROXY_INFO {
    public uint dwAccessType;
    public IntPtr lpszProxy;
    public IntPtr lpszProxyBypass;
  }

  public const uint WINHTTP_ACCESS_TYPE_DEFAULT_PROXY = 0;
  public const uint WINHTTP_ACCESS_TYPE_NO_PROXY = 1;
  public const uint WINHTTP_ACCESS_TYPE_NAMED_PROXY = 3;

  public const uint WINHTTP_AUTOPROXY_AUTO_DETECT = 0x00000001;
  public const uint WINHTTP_AUTOPROXY_CONFIG_URL  = 0x00000002;

  public const uint WINHTTP_AUTO_DETECT_TYPE_DHCP  = 0x00000001;
  public const uint WINHTTP_AUTO_DETECT_TYPE_DNS_A = 0x00000002;

  [DllImport("winhttp.dll", SetLastError=true, CharSet = CharSet.Unicode)]
  public static extern IntPtr WinHttpOpen(
      string pwszUserAgent,
      uint dwAccessType,
      string pwszProxyName,
      string pwszProxyBypass,
      uint dwFlags);

  [DllImport("winhttp.dll", SetLastError=true)]
  [return: MarshalAs(UnmanagedType.Bool)]
  public static extern bool WinHttpCloseHandle(IntPtr hInternet);

  [DllImport("winhttp.dll", SetLastError=true, CharSet = CharSet.Unicode)]
  [return: MarshalAs(UnmanagedType.Bool)]
  public static extern bool WinHttpGetIEProxyConfigForCurrentUser(out WINHTTP_CURRENT_USER_IE_PROXY_CONFIG pProxyConfig);

  [DllImport("winhttp.dll", SetLastError=true, CharSet = CharSet.Unicode)]
  [return: MarshalAs(UnmanagedType.Bool)]
  public static extern bool WinHttpGetProxyForUrl(
      IntPtr hSession,
      string lpcwszUrl,
      ref WINHTTP_AUTOPROXY_OPTIONS pAutoProxyOptions,
      out WINHTTP_PROXY_INFO pProxyInfo);

  [DllImport("kernel32.dll", SetLastError=true)]
  public static extern IntPtr GlobalFree(IntPtr hMem);
}
"@
}

$session = [WinHttp.NativeMethods]::WinHttpOpen("GradleProxyResolver/1.0", [WinHttp.NativeMethods]::WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, $null, $null, 0)
if ($session -eq [IntPtr]::Zero) {
    Write-Error "WinHttpOpen failed."
    exit 2
}

try {
    $ieCfg = New-Object WinHttp.NativeMethods+WINHTTP_CURRENT_USER_IE_PROXY_CONFIG
    $okCfg = [WinHttp.NativeMethods]::WinHttpGetIEProxyConfigForCurrentUser([ref]$ieCfg)
    if (-not $okCfg) {
        Write-Error "WinHttpGetIEProxyConfigForCurrentUser failed."
        exit 3
    }

    $opts = New-Object WinHttp.NativeMethods+WINHTTP_AUTOPROXY_OPTIONS
    $opts.fAutoLogonIfChallenged = $true
    $opts.dwFlags = 0
    $opts.dwAutoDetectFlags = 0
    $opts.lpszAutoConfigUrl = [IntPtr]::Zero

    if ($ieCfg.fAutoDetect) {
        $opts.dwFlags = $opts.dwFlags -bor [WinHttp.NativeMethods]::WINHTTP_AUTOPROXY_AUTO_DETECT
        $opts.dwAutoDetectFlags = [WinHttp.NativeMethods]::WINHTTP_AUTO_DETECT_TYPE_DHCP -bor [WinHttp.NativeMethods]::WINHTTP_AUTO_DETECT_TYPE_DNS_A
        Write-DebugLine "[DEBUG] IE proxy autodetect enabled."
    }

    if ($ieCfg.lpszAutoConfigUrl -ne [IntPtr]::Zero) {
        $opts.dwFlags = $opts.dwFlags -bor [WinHttp.NativeMethods]::WINHTTP_AUTOPROXY_CONFIG_URL
        $opts.lpszAutoConfigUrl = $ieCfg.lpszAutoConfigUrl
        $pacUrl = [Runtime.InteropServices.Marshal]::PtrToStringUni($ieCfg.lpszAutoConfigUrl)
        Write-DebugLine "[DEBUG] IE PAC url: $pacUrl"
    }

    $proxyInfo = New-Object WinHttp.NativeMethods+WINHTTP_PROXY_INFO
    $okProxy = [WinHttp.NativeMethods]::WinHttpGetProxyForUrl($session, $TestUrl, [ref]$opts, [ref]$proxyInfo)
    if (-not $okProxy) {
        Write-Error "WinHttpGetProxyForUrl failed for $TestUrl"
        exit 4
    }

    if ($proxyInfo.dwAccessType -eq [WinHttp.NativeMethods]::WINHTTP_ACCESS_TYPE_NO_PROXY) {
        Write-DebugLine "[DEBUG] DIRECT for $TestUrl"
        Write-Output ""
        exit 0
    }

    $proxyString = [Runtime.InteropServices.Marshal]::PtrToStringUni($proxyInfo.lpszProxy)
    Write-DebugLine "[DEBUG] Raw proxy string: $proxyString"

    $candidates = Get-ProxyCandidates -proxyString $proxyString -url $TestUrl
    if ($candidates.Count -eq 0) {
        Write-DebugLine "[DEBUG] No proxy candidates found -> DIRECT"
        Write-Output ""
        exit 0
    }

    Write-DebugLine "[DEBUG] Candidates: $($candidates -join ', ')"

    foreach ($candidate in $candidates) {
        $parts = $candidate.Split(":")
        $host = $parts[0].Trim()
        $port = [int]$parts[1].Trim()

        Write-DebugLine "[DEBUG] Test $host:$port"
        if (Test-TcpPort -Host $host -Port $port) {
            Write-DebugLine "[DEBUG] Select $host:$port"
            Write-Output "$host`:$port"
            exit 0
        }
    }

    # If nothing is reachable, output the first candidate to make behavior deterministic.
    Write-DebugLine "[DEBUG] No reachable proxy found. Output first candidate anyway."
    Write-Output $candidates[0]
    exit 0
}
finally {
    [WinHttp.NativeMethods]::WinHttpCloseHandle($session) | Out-Null
}
