param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$chrome = Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe'
if ($env:CHROME_PATH) { $chrome = $env:CHROME_PATH }
if (-not (Test-Path -LiteralPath $chrome)) { throw 'Set CHROME_PATH to Chrome executable.' }
$profile = Join-Path $projectRoot ('.work/browser-' + [guid]::NewGuid().ToString('N'))
$debugPort = 19000 + (Get-Random -Maximum 1000)
$arguments = @('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check',('--remote-debugging-port='+$debugPort),('--user-data-dir="'+$profile+'"'),'about:blank')
$browser = Start-Process -FilePath $chrome -ArgumentList $arguments -WindowStyle Hidden -PassThru
$socket = New-Object System.Net.WebSockets.ClientWebSocket
$script:sequence = 0
function Invoke-Cdp([string]$Method, $Parameters) {
  $script:sequence++
  $message = @{id=$script:sequence;method=$Method;params=$Parameters} | ConvertTo-Json -Depth 20 -Compress
  $bytes = [Text.Encoding]::UTF8.GetBytes($message)
  $timeout = New-Object Threading.CancellationTokenSource
  $timeout.CancelAfter(15000)
  try {
    $socket.SendAsync([ArraySegment[byte]]::new($bytes),[Net.WebSockets.WebSocketMessageType]::Text,$true,$timeout.Token).GetAwaiter().GetResult() | Out-Null
    do {
      $stream = New-Object IO.MemoryStream
      do {
        $buffer = New-Object byte[] 65536
        $received = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$timeout.Token).GetAwaiter().GetResult()
        $stream.Write($buffer,0,$received.Count)
      } until ($received.EndOfMessage)
      $response = [Text.Encoding]::UTF8.GetString($stream.ToArray()) | ConvertFrom-Json
      $stream.Dispose()
    } until ($response.id -eq $script:sequence)
    if ($response.error) { throw ($response.error | ConvertTo-Json -Compress) }
    return $response.result
  } finally { $timeout.Dispose() }
}
try {
  $pages = $null
  for ($attempt=0; $attempt -lt 40; $attempt++) {
    try { $pages = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list"; break } catch { Start-Sleep -Milliseconds 250 }
  }
  if (-not $pages) { throw 'Chrome debugging endpoint did not start.' }
  $page = $pages | Where-Object type -eq 'page' | Select-Object -First 1
  $socket.ConnectAsync([uri]$page.webSocketDebuggerUrl,[Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null
  Invoke-Cdp 'Page.navigate' @{url="http://127.0.0.1:$Port/__checks"} | Out-Null
  $status = ''
  for ($attempt=0; $attempt -lt 180; $attempt++) {
    Start-Sleep -Milliseconds 250
    $evaluation = Invoke-Cdp 'Runtime.evaluate' @{expression="JSON.stringify({status:document.documentElement.dataset.result,text:document.querySelector('#result')?.textContent})";returnByValue=$true}
    if ($evaluation.result.value) {
      $status = $evaluation.result.value | ConvertFrom-Json
      if ($status.status -in @('pass','fail')) { break }
    }
  }
  $status.text | Set-Content -LiteralPath (Join-Path $projectRoot '.work/browser-check-result.txt') -Encoding UTF8
  Write-Output $status.text
  if ($status.status -ne 'pass') { throw 'Browser checks did not pass.' }
  foreach ($size in @(@{width=1440;height=1100;name='desktop'},@{width=390;height=1000;name='mobile'})) {
    Invoke-Cdp 'Emulation.setDeviceMetricsOverride' @{width=$size.width;height=$size.height;deviceScaleFactor=1;mobile=$false} | Out-Null
    Invoke-Cdp 'Page.navigate' @{url="http://127.0.0.1:$Port/"} | Out-Null
    Start-Sleep -Milliseconds 1000
    $shot = Invoke-Cdp 'Page.captureScreenshot' @{format='png';captureBeyondViewport=$true}
    [IO.File]::WriteAllBytes((Join-Path $projectRoot ('.work/home-'+$size.name+'.png')),[Convert]::FromBase64String($shot.data))
  }
} finally {
  if ($socket.State -eq [Net.WebSockets.WebSocketState]::Open) { try { Invoke-Cdp 'Browser.close' @{} | Out-Null } catch {} }
  $socket.Dispose()
  if (-not $browser.HasExited) { Stop-Process -Id $browser.Id -ErrorAction SilentlyContinue }
}
