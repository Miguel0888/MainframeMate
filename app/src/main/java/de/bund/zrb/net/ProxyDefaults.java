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
            "$proxy = [System.Net.WebRequest]::GetSystemWebProxy()\n" +
            "$proxy.Credentials = [System.Net.CredentialCache]::DefaultNetworkCredentials\n\n" +
            "if ($proxy.IsBypassed($uri)) {\n" +
            "    Write-DebugLine (\"[DEBUG] DIRECT for {0}\" -f $TestUrl)\n" +
            "    exit 0\n" +
            "}\n\n" +
            "$proxyUri = $proxy.GetProxy($uri)\n\n" +
            "if (-not $proxyUri -or $proxyUri.AbsoluteUri -eq $uri.AbsoluteUri) {\n" +
            "    Write-DebugLine (\"[DEBUG] DIRECT for {0}\" -f $TestUrl)\n" +
            "    exit 0\n" +
            "}\n\n" +
            "Write-DebugLine (\"[DEBUG] Proxy for {0} -> {1}\" -f $TestUrl, $proxyUri.AbsoluteUri)\n" +
            "Write-Output (\"{0}:{1}\" -f $proxyUri.Host, $proxyUri.Port)\n" +
            "exit 0\n";

    private ProxyDefaults() {
    }
}

