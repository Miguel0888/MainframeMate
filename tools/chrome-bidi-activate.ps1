# Chrome BiDi Activation Test - Output to file
$logFile = "C:\Projects\MainframeMate\tools\bidi-test-log.txt"
"" | Out-File $logFile -Encoding UTF8

function Log($msg) {
    $msg | Out-File $logFile -Append -Encoding UTF8
}

$port = 9333
$version = Invoke-RestMethod -Uri "http://127.0.0.1:${port}/json/version"
$wsUrl = $version.webSocketDebuggerUrl
Log "Browser WS URL: $wsUrl"

$targets = Invoke-RestMethod -Uri "http://127.0.0.1:${port}/json/list"
$pageTarget = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
Log "Page target: $($pageTarget.id) - $($pageTarget.url)"
$pageWsUrl = $pageTarget.webSocketDebuggerUrl
Log "Page WS URL: $pageWsUrl"

$script:ws = $null

function Connect-WS($url) {
    if ($script:ws -and $script:ws.State -eq 'Open') {
        $script:ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', [System.Threading.CancellationToken]::None).Wait()
    }
    $script:ws = New-Object System.Net.WebSockets.ClientWebSocket
    $script:ws.ConnectAsync([Uri]$url, [System.Threading.CancellationToken]::None).Wait()
    Log "Connected to $url  State=$($script:ws.State)"
}

function Send-Msg($msg) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
    $script:ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
    Log ">>> $msg"
}

function Recv-Msg {
    $buf = New-Object byte[] 131072
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter(3000)
    try {
        $result = $script:ws.ReceiveAsync($seg, $cts.Token).Result
        if ($result.Count -gt 0) {
            $txt = [System.Text.Encoding]::UTF8.GetString($buf, 0, $result.Count)
            Log "<<< $txt"
            return $txt
        } else {
            Log "<<< (empty)"
            return $null
        }
    } catch {
        Log "<<< (timeout)"
        return $null
    }
}

function Close-WS {
    if ($script:ws -and $script:ws.State -eq 'Open') {
        $script:ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', [System.Threading.CancellationToken]::None).Wait()
    }
}

# === TEST A: Page-level ===
Log "`n===== TEST A: PAGE-level CDP ====="
Connect-WS $pageWsUrl

Log "`n--- A1: session.status ---"
Send-Msg '{"id":1,"method":"session.status","params":{}}'
Recv-Msg

Log "`n--- A2: browsingContext.getTree ---"
Send-Msg '{"id":2,"method":"browsingContext.getTree","params":{}}'
Recv-Msg

Log "`n--- A3: Page.navigate ---"
Send-Msg '{"id":3,"method":"Page.navigate","params":{"url":"https://example.com"}}'
Recv-Msg

Close-WS

# === TEST B: Browser-level + attach ===
Log "`n===== TEST B: BROWSER-level + attach ====="
Connect-WS $wsUrl

Log "`n--- B1: Target.attachToTarget ---"
$attachCmd = '{"id":10,"method":"Target.attachToTarget","params":{"targetId":"' + $pageTarget.id + '","flatten":true}}'
Send-Msg $attachCmd
Recv-Msg
Recv-Msg

Log "`n--- B2: session.status after attach ---"
Send-Msg '{"id":11,"method":"session.status","params":{}}'
Recv-Msg

Log "`n--- B3: browsingContext.getTree after attach ---"
Send-Msg '{"id":12,"method":"browsingContext.getTree","params":{}}'
Recv-Msg

Close-WS

# === TEST C: /session endpoint ===
Log "`n===== TEST C: /session endpoint ====="
try {
    $ws3 = New-Object System.Net.WebSockets.ClientWebSocket
    $cts3 = New-Object System.Threading.CancellationTokenSource
    $cts3.CancelAfter(5000)
    $ws3.ConnectAsync([Uri]"ws://127.0.0.1:${port}/session", $cts3.Token).Wait()
    Log "/session CONNECTED! State=$($ws3.State)"
    
    $bytes = [System.Text.Encoding]::UTF8.GetBytes('{"id":1,"method":"session.status","params":{}}')
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
    $ws3.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
    Log ">>> session.status"
    
    $buf = New-Object byte[] 65536
    $seg2 = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
    $cts4 = New-Object System.Threading.CancellationTokenSource
    $cts4.CancelAfter(3000)
    $r = $ws3.ReceiveAsync($seg2, $cts4.Token).Result
    Log "<<< $([System.Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))"
    $ws3.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', [System.Threading.CancellationToken]::None).Wait()
} catch {
    $inner = $_.Exception
    while ($inner.InnerException) { $inner = $inner.InnerException }
    Log "/session FAILED: $($inner.Message)"
}

Log "`n===== DONE ====="
