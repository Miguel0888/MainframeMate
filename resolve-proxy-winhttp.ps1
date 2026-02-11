param(
    [string]$TestUrl = "https://plugins.gradle.org/m2/",
    [switch]$DebugEnabled
)

function Write-DebugLine([string]$msg) {
    if ($DebugEnabled) { Write-Host $msg }
}

function Test-TcpPort {
    param([string]$Host, [int]$Port, [int]$TimeoutMs = 1500)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($Host, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if (-not $ok) { $client.Close(); return $false }
        $client.EndConnect($iar) | Out-Null
        $client.Close()
        return $true
    } catch { return $false }
}

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
  public static extern IntPtr WinHttpOpen(string ua, uint accessType, string proxy, string bypass, uint flags);

  [DllImport("winhttp.dll", SetLastError=true)]
  [return: MarshalAs(UnmanagedType.Bool)]
  public static extern bool WinHttpCloseHandle(IntPtr h);

  [DllImport("winhttp.dll", SetLastError=true, CharSet = CharSet.Unicode)]
  [return: MarshalAs(UnmanagedType.Bool)]
  public static extern bool WinHttpGetIEProxyConfigForCurrentUser(out WINHTTP_CURRENT_USER_IE_PROXY_CONFIG cfg);

  [DllImport("winhttp.dll", SetLastError=true, CharSet = CharSet.Unicode)]
  [return: MarshalAs(UnmanagedType.Bool)]
  public static extern bool WinHttpGetProxyForUrl(
      IntPtr session,
      string url,
      ref WINHTTP_AUTOPROXY_OPTIONS options,
      out WINHTTP_PROXY_INFO info);
}
"@
}

$session = [WinHttp.NativeMethods]::WinHttpOpen("ProxyResolver/1.0", [WinHttp.NativeMethods]::WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, $null, $null, 0)
if ($session -eq [IntPtr]::Zero) { throw "WinHttpOpen failed." }

try {
    $cfg = New-Object WinHttp.NativeMethods+WINHTTP_CURRENT_USER_IE_PROXY_CONFIG
    if (-not [WinHttp.NativeMethods]::WinHttpGetIEProxyConfigForCurrentUser([ref]$cfg)) {
        throw "WinHttpGetIEProxyConfigForCurrentUser failed."
    }

    $opts = New-Object WinHttp.NativeMethods+WINHTTP_AUTOPROXY_OPTIONS
    $opts.fAutoLogonIfChallenged = $true

    if ($cfg.fAutoDetect) {
        $opts.dwFlags = $opts.dwFlags -bor [WinHttp.NativeMethods]::WINHTTP_AUTOPROXY_AUTO_DETECT
        $opts.dwAutoDetectFlags = [WinHttp.NativeMethods]::WINHTTP_AUTO_DETECT_TYPE_DHCP -bor [WinHttp.NativeMethods]::WINHTTP_AUTO_DETECT_TYPE_DNS_A
        Write-DebugLine "[DEBUG] Enable autodetect."
    }

    if ($cfg.lpszAutoConfigUrl -ne [IntPtr]::Zero) {
        $opts.dwFlags = $opts.dwFlags -bor [WinHttp.NativeMethods]::WINHTTP_AUTOPROXY_CONFIG_URL
        $opts.lpszAutoConfigUrl = $cfg.lpszAutoConfigUrl
        Write-DebugLine "[DEBUG] Use PAC url from IE settings."
    }

    $info = New-Object WinHttp.NativeMethods+WINHTTP_PROXY_INFO
    if (-not [WinHttp.NativeMethods]::WinHttpGetProxyForUrl($session, $TestUrl, [ref]$opts, [ref]$info)) {
        throw "WinHttpGetProxyForUrl failed for $TestUrl"
    }

    if ($info.dwAccessType -eq [WinHttp.NativeMethods]::WINHTTP_ACCESS_TYPE_NO_PROXY) {
        Write-DebugLine "[DEBUG] DIRECT."
        Write-Output ""
        exit 0
    }

    $proxyString = [Runtime.InteropServices.Marshal]::PtrToStringUni($info.lpszProxy)
    Write-DebugLine "[DEBUG] Raw proxy string: $proxyString"

    $candidates = @()
    foreach ($part in ($proxyString -split ';')) {
        $p = $part.Trim()
        if ($p -match '^(?i)PROXY\s+([^\s;]+)') { $candidates += $Matches[1] }
        elseif ($p -match '^(?i)HTTPS?\s+([^\s;]+)') { $candidates += $Matches[1] }
        elseif ($p -match '^[^:]+:\d+$') { $candidates += $p }
    }

    $candidates = $candidates | Select-Object -Unique
    Write-DebugLine "[DEBUG] Candidates: $($candidates -join ', ')"

    foreach ($c in $candidates) {
        $h, $po = $c.Split(":")
        $port = [int]$po
        Write-DebugLine "[DEBUG] Test {0}:{1}" -f $h, $port
        if (Test-TcpPort -Host $h -Port $port) {
            Write-Output "$h`:$port"
            exit 0
        }
    }

    Write-Output $candidates[0]
    exit 0
}
finally {
    [WinHttp.NativeMethods]::WinHttpCloseHandle($session) | Out-Null
}
