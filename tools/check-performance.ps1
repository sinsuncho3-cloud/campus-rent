param([ValidateRange(1,10)][int]$Samples=3)
$ErrorActionPreference='Stop'
# Local demonstration account only. Do not change this to point at a production host.
$base='http://127.0.0.1:8080'
$session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$identity=Invoke-RestMethod ($base+'/api/session') -WebSession $session
$body=@{email='admin@campusrent.example';password='CampusAdmin123!'} | ConvertTo-Json -Compress
$logged=Invoke-RestMethod ($base+'/api/login') -Method Post -ContentType 'application/json' -Headers @{'X-CSRF-Token'=$identity.csrf} -Body $body -WebSession $session
try {
  $results=@()
  foreach($size in @(10,25,100)){
    for($i=0;$i -lt $Samples;$i++){
      $watch=[Diagnostics.Stopwatch]::StartNew()
      $response=Invoke-WebRequest ($base+'/api/state?page=0&view=all&size='+$size) -UseBasicParsing -WebSession $session -TimeoutSec 25
      $watch.Stop();$data=$response.Content | ConvertFrom-Json
      if(@($data.records).Count -gt $size){throw 'Page size exceeded.'}
      $results+=[pscustomobject]@{size=$size;milliseconds=[math]::Round($watch.Elapsed.TotalMilliseconds,1);returned=@($data.records).Count;total=$data.pageInfo.total;bytes=[Text.Encoding]::UTF8.GetByteCount($response.Content)}
    }
  }
  $results | Group-Object size | ForEach-Object {$times=@($_.Group.milliseconds | Sort-Object);[pscustomobject]@{size=$_.Name;samples=$times.Count;p50_ms=$times[[int][math]::Ceiling($times.Count*0.5)-1];max_ms=$times[-1];returned=$_.Group[0].returned;total=$_.Group[0].total;max_bytes=($_.Group.bytes | Measure-Object -Maximum).Maximum}}
  $root=Split-Path -Parent $PSScriptRoot
  $results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root '.work/page-performance.json') -Encoding UTF8
}finally{Invoke-RestMethod ($base+'/api/logout') -Method Post -ContentType 'application/json' -Body '{}' -Headers @{'X-CSRF-Token'=$logged.csrf} -WebSession $session | Out-Null}
