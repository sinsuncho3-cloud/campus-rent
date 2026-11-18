$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$destination=Join-Path $projectRoot '.work'
New-Item -ItemType Directory -Force $destination | Out-Null
if(Test-Path (Join-Path $destination 'apache-maven-3.9.9/bin/mvn.cmd')){Write-Output 'Local Maven already prepared.';exit 0}
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
$archive=Join-Path $destination 'apache-maven-3.9.9-bin.zip'
Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile $archive -TimeoutSec 60
Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
Write-Output 'Maven 3.9.9 prepared under ignored .work directory.'
