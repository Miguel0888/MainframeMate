$logFile = 'C:\Projects\MainframeMate\tools\bidi-test3-log.txt'
"" | Out-File $logFile -Encoding UTF8
function Log($msg) { $msg | Out-File $logFile -Append -Encoding UTF8 }

$port = 9333
$base = "http://127.0.0.1:" + $port
$wsBase = "ws://127.0.0.1:" + $port

# First check Chrome is running
try {
    $version = Invoke-RestMethod -Uri "$base/json/version" -TimeoutSec 3
    Log "Chrome running: $($version.Browser)"
    Log "webSocketDebuggerUrl: $($version.webSocketDebuggerUrl)"
} catch {
    Log "Chrome not running on port $port - start it first!"
    exit
}

# List all HTTP endpoints
Log "`n--- HTTP endpoints ---"
foreach ($ep in @("/json", "/json/list", "/json/version", "/json/protocol", "/json/new?about:blank")) {
    try {
        $r = Invoke-WebRequest -Uri ($base + $ep) -UseBasicParsing -TimeoutSec 2
        Log "$ep => HTTP $($r.StatusCode) ($($r.Content.Length) bytes)"
    } catch {
        Log "$ep => FAILED"
    }
}

function Test-WsEndpoint($url, $label) {
    Log ("`n--- " + $label + ": " + $url + " ---")
    try {
        $ws = New-Object System.Net.WebSockets.ClientWebSocket
        $cts = New-Object System.Threading.CancellationTokenSource
        $cts.CancelAfter(5000)
        $ws.ConnectAsync([Uri]$url, $cts.Token).Wait()
        Log "CONNECTED! State=$($ws.State)"
        
        # Send session.status
        $msg = '{"id":1,"method":"session.status","params":{}}'
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
        $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
        $ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
        Log ">>> $msg"
        
        $buf = New-Object byte[] 65536
        $seg2 = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
        $cts2 = New-Object System.Threading.CancellationTokenSource
        $cts2.CancelAfter(3000)
        try {
            $r = $ws.ReceiveAsync($seg2, $cts2.Token).Result
            if ($r.Count -gt 0) {
                Log "<<< $([System.Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))"
            } else {
                Log "<<< (empty)"
            }
        } catch {
            Log "<<< (timeout receiving)"
        }
        
        # Try session.new with webSocketUrl capability
        $msg2 = '{"id":2,"method":"session.new","params":{"capabilities":{"alwaysMatch":{"webSocketUrl":true}}}}'
        $bytes2 = [System.Text.Encoding]::UTF8.GetBytes($msg2)
        $seg3 = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes2)
        $ws.SendAsync($seg3, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
        Log ">>> $msg2"
        
        $buf2 = New-Object byte[] 65536
        $seg4 = New-Object System.ArraySegment[byte] -ArgumentList (,$buf2)
        $cts3 = New-Object System.Threading.CancellationTokenSource
        $cts3.CancelAfter(3000)
        try {
            $r2 = $ws.ReceiveAsync($seg4, $cts3.Token).Result
            if ($r2.Count -gt 0) {
                Log "<<< $([System.Text.Encoding]::UTF8.GetString($buf2, 0, $r2.Count))"
            } else {
                Log "<<< (empty)"
            }
        } catch {
            Log "<<< (timeout receiving)"
        }
        
        $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', [System.Threading.CancellationToken]::None).Wait()
    } catch {
        $inner = $_.Exception
        while ($inner.InnerException) { $inner = $inner.InnerException }
        Log "FAILED: $($inner.Message)"
    }
}

# Get targets for page WS URL
$targets = Invoke-RestMethod -Uri "$base/json/list"
$pageTarget = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
$browserWs = $version.webSocketDebuggerUrl
$pageWs = $pageTarget.webSocketDebuggerUrl

# Test all possible WebSocket endpoints
Test-WsEndpoint ($wsBase + "/json") "WS /json"
Test-WsEndpoint ($wsBase + "/devtools") "WS /devtools"
Test-WsEndpoint ($wsBase + "/devtools/browser") "WS /devtools/browser (no UUID)"
Test-WsEndpoint $browserWs "WS browser-level (with UUID)"
Test-WsEndpoint $pageWs "WS page-level"

# Also try the browser-level with CDP commands to enable BiDi
Log "`n--- BROWSER-LEVEL: Try CDP Target.sendMessageToTarget with BiDi payload ---"
try {
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $ws.ConnectAsync([Uri]$browserWs, [System.Threading.CancellationToken]::None).Wait()
    Log "Connected to browser WS"
    
    # First attach to page target
    $attachMsg = '{"id":100,"method":"Target.attachToTarget","params":{"targetId":"' + $pageTarget.id + '","flatten":true}}'
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($attachMsg)
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
    $ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
    Log ">>> $attachMsg"
    
    # Read attach response (may be multiple messages)
    for ($i = 0; $i -lt 3; $i++) {
        $buf = New-Object byte[] 65536
        $seg2 = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
        $cts = New-Object System.Threading.CancellationTokenSource
        $cts.CancelAfter(2000)
        try {
            $r = $ws.ReceiveAsync($seg2, $cts.Token).Result
            if ($r.Count -gt 0) {
                $resp = [System.Text.Encoding]::UTF8.GetString($buf, 0, $r.Count)
                Log "<<< $resp"
                # Extract sessionId if present
                if ($resp -match '"sessionId"\s*:\s*"([^"]+)"') {
                    $sessionId = $Matches[1]
                    Log "Got sessionId: $sessionId"
                }
            }
        } catch { break }
    }
    
    if ($sessionId) {
        # Try sending BiDi command via the session (flattened)
        $bidiMsg = '{"id":101,"method":"session.status","params":{},"sessionId":"' + $sessionId + '"}'
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($bidiMsg)
        $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
        $ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
        Log ">>> $bidiMsg"
        
        $buf = New-Object byte[] 65536
        $seg2 = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
        $cts = New-Object System.Threading.CancellationTokenSource
        $cts.CancelAfter(3000)
        try {
            $r = $ws.ReceiveAsync($seg2, $cts.Token).Result
            if ($r.Count -gt 0) { Log "<<< $([System.Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))" }
        } catch { Log "<<< (timeout)" }
        
        # Try Runtime.evaluate to check if BiDi mapper exists
        $evalMsg = '{"id":102,"method":"Runtime.evaluate","params":{"expression":"typeof globalThis.__bidi_mapper__"},"sessionId":"' + $sessionId + '"}'
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($evalMsg)
        $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
        $ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
        Log ">>> $evalMsg"
        
        $buf = New-Object byte[] 65536
        $seg2 = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
        $cts = New-Object System.Threading.CancellationTokenSource
        $cts.CancelAfter(3000)
        try {
            $r = $ws.ReceiveAsync($seg2, $cts.Token).Result
            if ($r.Count -gt 0) { Log "<<< $([System.Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))" }
        } catch { Log "<<< (timeout)" }
    }
    
    $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', [System.Threading.CancellationToken]::None).Wait()
} catch {
    $inner = $_.Exception
    while ($inner.InnerException) { $inner = $inner.InnerException }
    Log "FAILED: $($inner.Message)"
}

Log "`n===== ALL TESTS DONE ====="
