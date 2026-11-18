param([string]$BaseUrl='http://127.0.0.1:8080',[ValidateRange(1,30)][int]$Samples=5)
$ErrorActionPreference='Stop'
$results=@()
foreach($path in @('/api/health/live','/api/health/ready','/api/catalog')){
  for($i=0;$i -lt $Samples;$i++){
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $response=Invoke-WebRequest -Uri ($BaseUrl.TrimEnd('/')+$path) -UseBasicParsing -TimeoutSec 20
    $watch.Stop()
    $results+= [pscustomobject]@{path=$path;status=$response.StatusCode;milliseconds=[math]::Round($watch.Elapsed.TotalMilliseconds,1);bytes=[Text.Encoding]::UTF8.GetByteCount($response.Content)}
  }
}
foreach($group in ($results | Group-Object path)){
  $values=@($group.Group.milliseconds | Sort-Object)
  [pscustomobject]@{path=$group.Name;samples=$values.Count;p50_ms=$values[[int][math]::Ceiling($values.Count*0.5)-1];p95_ms=$values[[int][math]::Ceiling($values.Count*0.95)-1];max_bytes=($group.Group.bytes | Measure-Object -Maximum).Maximum}
}
$root=Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Path (Join-Path $root '.work') -Force | Out-Null
$results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root '.work/health-results.json') -Encoding UTF8
# Read-only latency samples; not a load or outage test. No authentication information is logged.
