param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if(-not $SkipBuild){
  $compiler=(Get-Command javac -ErrorAction Stop).Source
  $env:JAVA_HOME=Split-Path -Parent (Split-Path -Parent $compiler)
  & (Join-Path $projectRoot '.work/apache-maven-3.9.9/bin/mvn.cmd') '-Dmaven.repo.local=.work/m2' -q package -DskipTests
  if($LASTEXITCODE -ne 0){throw 'Build failed. Stop the local server before replacing its JAR.'}
}
$jarPath=Join-Path $projectRoot 'target/campus-rent-0.1.0.jar'
if(-not (Test-Path -LiteralPath $jarPath)){throw 'Build the JAR first.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead($jarPath)
try{
  if(@($archive.Entries | Where-Object {$_.FullName -match 'application-(local|runtime)|(^|/)\.env($|\.)'}).Count){throw 'Local configuration found in JAR.'}
}finally{$archive.Dispose()}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$releaseRoot=Join-Path $projectRoot '.work/releases'
$stage=Join-Path $releaseRoot ('campusrent-'+$stamp+'-'+[guid]::NewGuid().ToString('N').Substring(0,6))
New-Item -ItemType Directory -Path $stage -Force | Out-Null
Copy-Item -LiteralPath $jarPath -Destination $stage
New-Item -ItemType Directory -Path (Join-Path $stage 'ops') | Out-Null
# Explicit allowlist: never copy the working tree or real local properties.
foreach($name in @('application.example.properties','campusrent.service.example','nginx.conf.example','backup-check.sql')){Copy-Item -LiteralPath (Join-Path $projectRoot ('ops/'+$name)) -Destination (Join-Path $stage 'ops')}
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/deployment.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/operations.md') -Destination $stage
Get-FileHash -LiteralPath (Join-Path $stage 'campus-rent-0.1.0.jar') -Algorithm SHA256 | ForEach-Object {$_.Hash+'  campus-rent-0.1.0.jar'} | Set-Content -LiteralPath (Join-Path $stage 'SHA256SUMS.txt') -Encoding ASCII
$zipPath=$stage+'.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$zipPath)
Write-Output ('Release package: '+$zipPath)
Write-Output 'No local DB configuration or installation SQL is included. Package creation does not deploy.'
