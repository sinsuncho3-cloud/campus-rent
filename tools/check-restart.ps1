param([int]$Port=8082)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue){throw 'Verification port is already in use; no process was changed.'}
$java=(Get-Command java).Source
$apiBase="http://127.0.0.1:$Port/api"
$script:browserSession=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$script:backendProcess=$null
function Start-VerificationServer {
  $script:backendProcess=Start-Process -FilePath $java -ArgumentList @('-jar','target/campus-rent-0.1.0.jar','--server.address=127.0.0.1',('--server.port='+$Port),'--debug=false','--logging.level.root=WARN','--logging.level.org.springframework.jdbc=WARN','--logging.level.com.zaxxer.hikari=WARN') -WindowStyle Hidden -PassThru -RedirectStandardOutput '.work/restart-server.log' -RedirectStandardError '.work/restart-server-error.log'
  for($attempt=0;$attempt -lt 100;$attempt++){
    try{Invoke-RestMethod ($apiBase+'/session') -TimeoutSec 2 | Out-Null;return}catch{Start-Sleep -Milliseconds 250}
  }
  throw 'Verification server did not become ready.'
}
function Invoke-Api([string]$Path,$Payload){
  $identity=Invoke-RestMethod ($apiBase+'/session') -WebSession $script:browserSession -TimeoutSec 15
  if($null -eq $Payload){return Invoke-RestMethod ($apiBase+$Path) -WebSession $script:browserSession -TimeoutSec 15}
  $body=[Text.Encoding]::UTF8.GetBytes(($Payload|ConvertTo-Json -Depth 10))
  return Invoke-RestMethod ($apiBase+$Path) -Method Post -WebSession $script:browserSession -Headers @{'X-CSRF-Token'=$identity.csrf} -ContentType 'application/json; charset=utf-8' -Body $body -TimeoutSec 20
}
try{
  Start-VerificationServer
  $email='restart-'+[guid]::NewGuid().ToString('N')+'@campusrent.example'
  $password=[guid]::NewGuid().ToString('N')+'Aa1!'
  $account=Invoke-Api '/signup' @{name='Restart verification';email=$email;password=$password}
  $day=(Get-Date).AddDays(45).ToString('yyyy-MM-dd')
  $ids=Invoke-Api '/requests' @{items=@(@{resourceId='E2';condition=@{start=($day+'T10:00');end=($day+'T12:00');people=1;use=''}})}
  $recordId=[string]@($ids)[0]
  Stop-Process -Id $script:backendProcess.Id
  $script:backendProcess.WaitForExit()
  Start-VerificationServer
  $after=Invoke-Api '/session' $null
  if($null -ne $after.user){throw 'Old login session survived the application restart unexpectedly.'}
  $login=Invoke-Api '/login' @{email=$email;password=$password}
  if($login.user.id -ne $account.user.id){throw 'Stored account did not survive restart.'}
  $state=Invoke-Api '/state' $null
  $record=$state.records|Where-Object id -eq $recordId
  if(-not $record){throw 'Stored request did not survive restart.'}
  $cancelLabel=[string][char]0xCDE8+[char]0xC18C
  Invoke-Api ('/requests/'+$recordId+'/transition') @{target=$cancelLabel;version=$record.version;details=@{}} | Out-Null
  Write-Output 'PASS: application restarted; old session expired; account login and committed request persisted; test request cancelled.'
} finally {
  if($script:backendProcess -and -not $script:backendProcess.HasExited){Stop-Process -Id $script:backendProcess.Id -ErrorAction SilentlyContinue}
}
