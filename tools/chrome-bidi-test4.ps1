$logFile = 'C:\Projects\MainframeMate\tools\bidi-test4-log.txt'
"" | Out-File $logFile -Encoding UTF8
function Log($msg) { $msg | Out-File $logFile -Append -Encoding UTF8 }

$port = 9333

# Step 1: Search Chrome protocol for anything BiDi-related
Log "=== Searching Chrome protocol for BiDi/webSocket/capability ==="
cmd /c "curl.exe -s http://127.0.0.1:9333/json/protocol > C:\Projects\MainframeMate\tools\chrome-proto.json"
$proto = Get-Content 'C:\Projects\MainframeMate\tools\chrome-proto.json' -Raw -Encoding UTF8

# Search for relevant keywords
foreach ($kw in @("bidi", "BiDi", "webSocket", "capability", "session", "createSession", "newSession")) {
    $matches = [regex]::Matches($proto, ".{0,80}$kw.{0,80}", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Log ("`n--- Keyword: $kw (" + $matches.Count + " hits) ---")
        foreach ($m in ($matches | Select-Object -First 5)) {
            Log ("  ..." + $m.Value.Trim() + "...")
        }
    } else {
        Log "--- Keyword: $kw => NO MATCHES ---"
    }
}

# Step 2: List all Target.* methods
Log "`n=== All Target.* methods in protocol ==="
$targetMatches = [regex]::Matches($proto, '"name"\s*:\s*"(Target\.[^"]+)"')
foreach ($m in $targetMatches) {
    Log ("  " + $m.Groups[1].Value)
}

# Step 3: List all methods containing "create" or "new" or "session"
Log "`n=== Methods with create/new/session ==="
$methodMatches = [regex]::Matches($proto, '"name"\s*:\s*"([^"]*(?:create|new|session|Session)[^"]*)"', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
foreach ($m in $methodMatches) {
    Log ("  " + $m.Groups[1].Value)
}

# Step 4: Connect to browser WS and try various CDP approaches to enable BiDi
$version = Invoke-RestMethod -Uri "http://127.0.0.1:9333/json/version"
$browserWs = $version.webSocketDebuggerUrl
Log ("`n=== Connecting to " + $browserWs + " ===")

$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ws.ConnectAsync([Uri]$browserWs, [System.Threading.CancellationToken]::None).Wait()
Log "Connected"

function WsSend($msg) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
    $ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).Wait()
    Log (">>> " + $msg)
}

function WsRecv {
    $buf = New-Object byte[] 262144
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter(3000)
    try {
        $r = $ws.ReceiveAsync($seg, $cts.Token).Result
        if ($r.Count -gt 0) {
            $txt = [System.Text.Encoding]::UTF8.GetString($buf, 0, $r.Count)
            Log ("<<< " + $txt)
            return $txt
        }
        Log "<<< (empty)"
        return $null
    } catch {
        Log "<<< (timeout)"
        return $null
    }
}

# Try Target.setAutoAttach with BiDi filter
Log "`n--- T1: Target.setAutoAttach with filter ---"
WsSend '{"id":1,"method":"Target.setAutoAttach","params":{"autoAttach":true,"waitForDebuggerOnStart":false,"flatten":true,"filter":[{"type":"page"}]}}'
WsRecv

# Try Browser.getVersion
Log "`n--- T2: Browser.getVersion ---"
WsSend '{"id":2,"method":"Browser.getVersion","params":{}}'
WsRecv

# Try Target.setDiscoverTargets with BiDi
Log "`n--- T3: Target.setDiscoverTargets ---"
WsSend '{"id":3,"method":"Target.setDiscoverTargets","params":{"discover":true}}'
WsRecv
WsRecv

# Try creating a new target and attaching with special params
Log "`n--- T4: Target.createTarget ---"
WsSend '{"id":4,"method":"Target.createTarget","params":{"url":"about:blank","enableBeginFrameControl":false}}'
$resp = WsRecv

# Extract targetId
$newTargetId = $null
if ($resp -match '"targetId"\s*:\s*"([^"]+)"') {
    $newTargetId = $Matches[1]
    Log ("New target: " + $newTargetId)
}

# Try to attach with flatten + maybe BiDi gets enabled
if ($newTargetId) {
    Log "`n--- T5: Attach to new target ---"
    WsSend ('{"id":5,"method":"Target.attachToTarget","params":{"targetId":"' + $newTargetId + '","flatten":true}}')
    $resp = WsRecv
    WsRecv
    
    # Extract sessionId
    $sid = $null
    if ($resp -match '"sessionId"\s*:\s*"([^"]+)"') {
        $sid = $Matches[1]
    }
    
    if ($sid) {
        # Try Runtime.evaluate to inject chromium-bidi mapper
        Log "`n--- T6: Check for BiDi mapper via Runtime.evaluate ---"
        WsSend ('{"id":6,"method":"Runtime.evaluate","params":{"expression":"JSON.stringify({hasBidi: typeof globalThis.__bidi_mapper__ !== ''undefined'', hasSession: typeof globalThis.__bidiSession__ !== ''undefined'', keys: Object.keys(globalThis).filter(k => k.toLowerCase().includes(''bidi'')).join('','')})"},"sessionId":"' + $sid + '"}')
        WsRecv
        
        # Try Page.createIsolatedWorld - maybe BiDi mapper lives there
        Log "`n--- T7: Page.enable ---"
        WsSend ('{"id":7,"method":"Page.enable","params":{},"sessionId":"' + $sid + '"}')
        WsRecv
        
        Log "`n--- T8: Network.enable ---"
        WsSend ('{"id":8,"method":"Network.enable","params":{},"sessionId":"' + $sid + '"}')
        WsRecv
        
        # Navigate and see network events
        Log "`n--- T9: Page.navigate to example.com ---"
        WsSend ('{"id":9,"method":"Page.navigate","params":{"url":"https://example.com"},"sessionId":"' + $sid + '"}')
        WsRecv
        
        # Drain a few events
        for ($i = 0; $i -lt 5; $i++) { WsRecv }
    }
}

$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', [System.Threading.CancellationToken]::None).Wait()
Log "`n===== DONE ====="
