# Chrome BiDi Connection Test Script
# Tests various ways to establish a BiDi session with Chrome

$port = 9333
$wsUrl = "ws://127.0.0.1:${port}/devtools/browser/0bc1bb7f-427a-4d4f-9609-93fd3967d20c"

Write-Host "=== Chrome BiDi Connection Test ==="
Write-Host "Connecting to: $wsUrl"

$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = New-Object System.Threading.CancellationToken
$ws.ConnectAsync([Uri]$wsUrl, $ct).Wait()
Write-Host "Connected: $($ws.State)"

function Send-WS($message) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($message)
    $segment = New-Object System.ArraySegment[byte] -ArgumentList (,$bytes)
    $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
    Write-Host "`n>>> SENT: $message"
}

function Receive-WS() {
    $buf = New-Object byte[] 131072
    $seg = New-Object System.ArraySegment[byte] -ArgumentList (,$buf)
    $result = $ws.ReceiveAsync($seg, $ct).Result
    $response = [System.Text.Encoding]::UTF8.GetString($buf, 0, $result.Count)
    Write-Host "<<< RECV: $response"
    return $response
}

# Test 1: CDP Target.getTargets
Write-Host "`n--- Test 1: CDP Target.getTargets ---"
Send-WS '{"id":1,"method":"Target.getTargets","params":{}}'
Receive-WS

# Test 2: Try BiDi session.status
Write-Host "`n--- Test 2: BiDi session.status ---"
Send-WS '{"id":2,"method":"session.status","params":{}}'
Receive-WS

# Test 3: Try BiDi browsingContext.getTree
Write-Host "`n--- Test 3: BiDi browsingContext.getTree ---"
Send-WS '{"id":3,"method":"browsingContext.getTree","params":{}}'
Receive-WS

# Test 4: Try CDP Target.getBrowserContexts
Write-Host "`n--- Test 4: CDP Target.getBrowserContexts ---"
Send-WS '{"id":4,"method":"Target.getBrowserContexts","params":{}}'
Receive-WS

# Test 5: Try to create a BiDi session via CDP - createTarget with special URL
Write-Host "`n--- Test 5: CDP Target.createTarget (BiDi mapper?) ---"
Send-WS '{"id":5,"method":"Target.createTarget","params":{"url":"about:blank"}}'
Receive-WS

# Test 6: Try CDP Page.navigate via the browser-level connection
Write-Host "`n--- Test 6: CDP Page.navigate (browser-level, should fail) ---"
Send-WS '{"id":6,"method":"Page.navigate","params":{"url":"https://www.w3.org/TR/webdriver-bidi"}}'
Receive-WS

$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, '', $ct).Wait()
Write-Host "`n=== Done ==="
