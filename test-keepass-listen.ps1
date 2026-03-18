# KeePassRPC: connect and just LISTEN (don't send anything)
# Maybe this version has the server sending first

$uri = [Uri]"ws://127.0.0.1:12546/"
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$cts = New-Object System.Threading.CancellationTokenSource

Write-Host "Connecting to $uri ..."
try {
    $ws.ConnectAsync($uri, $cts.Token).GetAwaiter().GetResult()
    Write-Host "Connected! State: $($ws.State)"
} catch {
    Write-Host "Connect failed: $($_.Exception.Message)"
    exit 1
}

# Just listen - don't send anything
Write-Host "Listening for server messages (15s timeout)..."
$buf = New-Object byte[] 65536
$recvSegment = New-Object System.ArraySegment[byte] -ArgumentList @(,$buf)
$cts2 = New-Object System.Threading.CancellationTokenSource
$cts2.CancelAfter(15000)

try {
    $result = $ws.ReceiveAsync($recvSegment, $cts2.Token).GetAwaiter().GetResult()
    $count = $result.Count
    $response = [System.Text.Encoding]::UTF8.GetString($buf, 0, $count)
    Write-Host "Server sent ($($result.MessageType), $count bytes):"
    Write-Host $response
} catch [System.OperationCanceledException] {
    Write-Host "15s timeout - server sent nothing. State: $($ws.State)"
} catch {
    Write-Host "Receive error: $($_.Exception.Message)"
    if ($_.Exception.InnerException) {
        Write-Host "Inner: $($_.Exception.InnerException.Message)"
        if ($_.Exception.InnerException.InnerException) {
            Write-Host "Inner2: $($_.Exception.InnerException.InnerException.Message)"
        }
    }
    Write-Host "State: $($ws.State)"
}

$ws.Dispose()
Write-Host "Done."

