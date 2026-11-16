$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$driverDirectory=Join-Path $projectRoot '.work/jdbc'
$driverFile=Join-Path $driverDirectory 'ojdbc6-11.2.0.4.jar'
New-Item -ItemType Directory -Force $driverDirectory | Out-Null
# Oracle-published Maven Central coordinates; no Maven/Gradle installation required.
$source='https://repo.maven.apache.org/maven2/com/oracle/database/jdbc/ojdbc6/11.2.0.4/ojdbc6-11.2.0.4.jar'
if(Test-Path -LiteralPath $driverFile){Write-Output 'Driver already present in ignored .work/jdbc; no download performed.';exit 0}
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
try {
  Invoke-WebRequest -UseBasicParsing -Uri $source -OutFile ($driverFile+'.download') -TimeoutSec 60
  # Confirm this is a readable JAR archive with the expected JDBC entry point before using it.
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $archive=[IO.Compression.ZipFile]::OpenRead($driverFile+'.download')
  try {if(-not $archive.GetEntry('oracle/jdbc/OracleDriver.class')){throw 'Unexpected JDBC artifact.'}}
  finally {$archive.Dispose()}
  Move-Item -LiteralPath ($driverFile+'.download') -Destination $driverFile
  Write-Output 'Oracle ojdbc6 11.2.0.4 downloaded to ignored .work/jdbc.'
} catch {throw 'JDBC driver setup failed. Check network access and the Maven Central artifact; no DB credentials are involved.'}
