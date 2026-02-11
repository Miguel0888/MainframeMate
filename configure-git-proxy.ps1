[CmdletBinding()]
param(
    [string]$RemoteName = "origin",
    [switch]$DebugEnabled,
    [switch]$Unset
)

function Write-Info([string]$Message) { Write-Host ("[INFO] {0}" -f $Message) }
function Write-ErrorLine([string]$Message) { Write-Host ("[ERROR] {0}" -f $Message) }

$proxyResolver = Join-Path $PSScriptRoot "get-proxy-from-pac.ps1"
if (-not (Test-Path $proxyResolver)) {
    Write-ErrorLine ("get-proxy-from-pac.ps1 nicht gefunden: {0}" -f $proxyResolver)
    exit 1
}

if ($Unset) {
    git config --global --unset-all http.proxy 2>$null
    git config --global --unset-all https.proxy 2>$null
    Write-Info "Git-Proxy wurde entfernt (http.proxy / https.proxy)."
    exit 0
}

# Determine a URL to resolve the correct proxy for (prefer git remote).
$remoteUrl = $null
try {
    $remoteUrl = (git remote get-url $RemoteName 2>$null)
} catch {
    $remoteUrl = $null
}

$testUrl = $remoteUrl
if (-not $testUrl) {
    $testUrl = "https://github.com/"
    Write-Info ("Kein Remote '{0}' gefunden. Verwende TestUrl: {1}" -f $RemoteName, $testUrl)
}

# If remote is SSH (git@host:org/repo.git), derive an https URL for proxy resolution.
if ($testUrl -match '^[^/]+@[^:]+:') {
    $host = ($testUrl.Split('@')[1].Split(':')[0]).Trim()
    $testUrl = ("https://{0}/" -f $host)
    Write-Info ("SSH-Remote erkannt. Verwende für Proxy-Auflösung: {0}" -f $testUrl)
}

# Ensure it's a valid URI
try { [void][Uri]$testUrl } catch {
    $testUrl = "https://github.com/"
    Write-Info ("Remote-URL war nicht als URI nutzbar. Fallback: {0}" -f $testUrl)
}

# Resolve proxy host:port via Windows (PAC/WPAD).
$proxyHostPort = & $proxyResolver -TestUrl $testUrl -DebugEnabled:$DebugEnabled

if (-not $proxyHostPort) {
    git config --global --unset-all http.proxy 2>$null
    git config --global --unset-all https.proxy 2>$null
    Write-Info ("DIRECT für {0} -> Git-Proxy entfernt." -f $testUrl)
    exit 0
}

if ($proxyHostPort -notmatch '^[^:]+:\d+$') {
    Write-ErrorLine ("Unerwartetes Proxy-Format: '{0}'" -f $proxyHostPort)
    exit 2
}

$proxyUrl = ("http://{0}" -f $proxyHostPort)

Write-Info ("Setze Git-Proxy auf {0}" -f $proxyUrl)
git config --global http.proxy  $proxyUrl
git config --global https.proxy $proxyUrl

Write-Info "Fertig. Aktuelle Werte:"
Write-Host ("  http.proxy  = {0}" -f (git config --global --get http.proxy))
Write-Host ("  https.proxy = {0}" -f (git config --global --get https.proxy))
