@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo JDK 8 or newer is required. Add java and javac to PATH.
  exit /b 1
)
if not exist ".work\preview" mkdir ".work\preview"
javac -encoding UTF-8 -d .work\preview tools\PreviewServer.java
if errorlevel 1 exit /b 1
java -cp .work\preview PreviewServer 8080 .
