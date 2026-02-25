$logFile = "C:\Projects\MainframeMate\tools\bidi-test2-log.txt"
"" | Out-File $logFile -Encoding UTF8
function Log($msg) { $msg | Out-File $logFile -Append -Encoding UTF8 }

$port = 9334

# Test /session on port with --enable-bidi
Log "--- /session on port $port ---"
try {
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter(5000)
    $ws.ConnectAsync([Uri]"ws://127.0.0.1:${port}/session", $cts.Token).Wait()
    Log "CONNECTED to /session! State=$($ws.State)"
    
    $msg = '{"id":1,"method":"session.status","params":{}}'
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
    $ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
    Log ">>> $msg"
    
    $buf = New-Object byte[] 65536
    $seg2 = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
    $cts2 = New-Object System.Threading.CancellationTokenSource
    $cts2.CancelAfter(3000)
    $r = $ws.ReceiveAsync($seg2, $cts2.Token).Result
    $resp = [System.Text.Encoding]::UTF8.GetString($buf, 0, $r.Count)
    Log "<<< $resp"
    $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', [System.Threading.CancellationToken]::None).Wait()
} catch {
    $inner = $_.Exception
    while ($inner.InnerException) { $inner = $inner.InnerException }
    Log "/session FAILED: $($inner.Message)"
}

# Test CDP page-level with BiDi command
Log "`n--- CDP page-level with BiDi ---"
$targets = Invoke-RestMethod -Uri "http://127.0.0.1:${port}/json/list"
$pageTarget = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
Log "Page: $($pageTarget.webSocketDebuggerUrl)"
try {
    $ws2 = New-Object System.Net.WebSockets.ClientWebSocket
    $ws2.ConnectAsync([Uri]$pageTarget.webSocketDebuggerUrl, [System.Threading.CancellationToken]::None).Wait()
    Log "Connected State=$($ws2.State)"
    
    $msg = '{"id":1,"method":"session.status","params":{}}'
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
    $ws2.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
    Log ">>> $msg"
    
    $buf = New-Object byte[] 65536
    $seg2 = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
    $cts3 = New-Object System.Threading.CancellationTokenSource
    $cts3.CancelAfter(3000)
    $r = $ws2.ReceiveAsync($seg2, $cts3.Token).Result
    $resp = [System.Text.Encoding]::UTF8.GetString($buf, 0, $r.Count)
    Log "<<< $resp"
    $ws2.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', [System.Threading.CancellationToken]::None).Wait()
} catch {
    $inner = $_.Exception
    while ($inner.InnerException) { $inner = $inner.InnerException }
    Log "page-level FAILED: $($inner.Message)"
}

Log "`n--- DONE ---"
