$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$compiler=(Get-Command javac -ErrorAction Stop).Source
$env:JAVA_HOME=Split-Path -Parent (Split-Path -Parent $compiler)
& (Join-Path $projectRoot '.work/apache-maven-3.9.9/bin/mvn.cmd') '-Dmaven.repo.local=.work/m2' -q package -DskipTests
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -jar target/campus-rent-0.1.0.jar --server.address=127.0.0.1 --debug=false --logging.level.root=INFO --logging.level.org.springframework.jdbc=INFO --logging.level.com.zaxxer.hikari=INFO
exit $LASTEXITCODE
