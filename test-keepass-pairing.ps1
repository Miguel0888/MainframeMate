# KeePassRPC Pairing via .NET WebSocket
# Connects, sends SRP identifyToServer, waits for response

Add-Type -AssemblyName System.Net.WebSockets

$uri = [Uri]"ws://127.0.0.1:12546/"
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$cts = New-Object System.Threading.CancellationTokenSource

Write-Host "Connecting to $uri ..."
try {
    $ws.ConnectAsync($uri, $cts.Token).GetAwaiter().GetResult()
    Write-Host "Connected! State: $($ws.State)"
} catch {
    Write-Host "Connect failed: $_"
    exit 1
}

# Generate SRP A value (simple: just a large random hex for triggering the dialog)
$rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
$aBytes = New-Object byte[] 64
$rng.GetBytes($aBytes)
$aHex = ($aBytes | ForEach-Object { $_.ToString("X2") }) -join ""

# Protocol version: {1,7,2} = (1 -shl 16) -bor (7 -shl 8) -bor 2 = 67330
$version = (1 -shl 16) -bor (7 -shl 8) -bor 2

$msg = @{
    protocol = "setup"
    version = $version
    clientTypeId = "MainframeMate"
    clientDisplayName = "MainframeMate"
    clientDisplayDescription = "Mainframe Data Integration Tool"
    features = @("KPRPC_FEATURE_VERSION_1_6", "KPRPC_FEATURE_WARN_USER_WHEN_FEATURE_MISSING")
    srp = @{
        stage = "identifyToServer"
        I = "MainframeMate"
        A = $aHex
        securityLevel = 2
    }
} | ConvertTo-Json -Depth 5 -Compress

Write-Host "`nSending SRP identifyToServer ($($msg.Length) chars)..."
$msgBytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
$segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$msgBytes)

try {
    $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token).GetAwaiter().GetResult()
    Write-Host "Sent!"
} catch {
    Write-Host "Send failed: $_"
    Write-Host "WebSocket State: $($ws.State)"
    $ws.Dispose()
    exit 1
}

# Wait for response
Write-Host "`nWaiting for response (up to 30s)..."
$buf = New-Object byte[] 65536
$recvSegment = New-Object System.ArraySegment[byte] -ArgumentList @(,$buf)
$cts2 = New-Object System.Threading.CancellationTokenSource
$cts2.CancelAfter(30000)

try {
    $result = $ws.ReceiveAsync($recvSegment, $cts2.Token).GetAwaiter().GetResult()
    $count = $result.Count
    $msgType = $result.MessageType
    $response = [System.Text.Encoding]::UTF8.GetString($buf, 0, $count)
    Write-Host "Response type: $msgType, $count bytes:"
    Write-Host $response
} catch {
    Write-Host "Receive: $_"
    if ($_.Exception.InnerException) {
        Write-Host "Inner: $($_.Exception.InnerException.Message)"
    }
    Write-Host "State: $($ws.State)"
}

Write-Host "`nWebSocket State: $($ws.State)"
Write-Host "Keeping connection open for 20s so KeePass can show dialog..."
Start-Sleep -Seconds 20

$ws.Dispose()
Write-Host "Done."

