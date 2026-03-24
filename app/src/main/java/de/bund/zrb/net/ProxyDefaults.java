package de.bund.zrb.net;

public final class ProxyDefaults {

    public static final String DEFAULT_TEST_URL = "https://plugins.gradle.org/m2/";

    public static final String DEFAULT_PAC_SCRIPT =
            "param(\n" +
            "    [string]$TestUrl = \"https://plugins.gradle.org/m2/\",\n" +
            "    [switch]$DebugEnabled\n" +
            ")\n\n" +
            "function Write-DebugLine([string]$msg) {\n" +
            "    if ($DebugEnabled) { Write-Host $msg }\n" +
            "}\n\n" +
            "$uri = [Uri]$TestUrl\n\n" +
            "# ── Methode 1: .NET WebProxy (voller WPAD/PAC-Support) ──────────\n" +
            "try {\n" +
            "    $proxy = [System.Net.WebRequest]::GetSystemWebProxy()\n" +
            "    $proxy.Credentials = [System.Net.CredentialCache]::DefaultNetworkCredentials\n\n" +
            "    if ($proxy.IsBypassed($uri)) {\n" +
            "        Write-DebugLine (\"[DEBUG] DIRECT for {0}\" -f $TestUrl)\n" +
            "        exit 0\n" +
            "    }\n\n" +
            "    $proxyUri = $proxy.GetProxy($uri)\n\n" +
            "    if (-not $proxyUri -or $proxyUri.AbsoluteUri -eq $uri.AbsoluteUri) {\n" +
            "        Write-DebugLine (\"[DEBUG] DIRECT for {0}\" -f $TestUrl)\n" +
            "        exit 0\n" +
            "    }\n\n" +
            "    Write-DebugLine (\"[DEBUG] Proxy for {0} -> {1}\" -f $TestUrl, $proxyUri.AbsoluteUri)\n" +
            "    Write-Output (\"{0}:{1}\" -f $proxyUri.Host, $proxyUri.Port)\n" +
            "    exit 0\n" +
            "}\n" +
            "catch {\n" +
            "    Write-DebugLine (\"[DEBUG] .NET-Methode blockiert (CLM?): {0}\" -f $_.Exception.Message)\n" +
            "}\n\n" +
            "# ── Methode 2: Registry (funktioniert auch im Constrained Language Mode) ──\n" +
            "try {\n" +
            "    $reg = Get-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings' -ErrorAction Stop\n\n" +
            "    if ($reg.ProxyEnable -eq 1 -and $reg.ProxyServer) {\n" +
            "        $server = $reg.ProxyServer\n" +
            "        # Protokoll-spezifisches Format: \"http=host:port;https=host:port\"\n" +
            "        if ($server -match '=') {\n" +
            "            $scheme = $uri.Scheme\n" +
            "            foreach ($entry in $server -split ';') {\n" +
            "                $kv = $entry -split '=', 2\n" +
            "                if ($kv.Length -eq 2 -and $kv[0] -eq $scheme) {\n" +
            "                    $server = $kv[1]\n" +
            "                    break\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "        Write-DebugLine (\"[DEBUG] Registry-Proxy: {0}\" -f $server)\n" +
            "        Write-Output $server\n" +
            "        exit 0\n" +
            "    }\n\n" +
            "    Write-DebugLine \"[DEBUG] Kein statischer Proxy in der Registry\"\n" +
            "}\n" +
            "catch {\n" +
            "    Write-DebugLine (\"[DEBUG] Registry-Zugriff fehlgeschlagen: {0}\" -f $_.Exception.Message)\n" +
            "}\n\n" +
            "# Kein Proxy gefunden\n" +
            "exit 0\n";

    private ProxyDefaults() {
    }
}

