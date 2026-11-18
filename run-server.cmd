@echo off
setlocal
cd /d "%~dp0"
if not exist ".work\apache-maven-3.9.9\bin\mvn.cmd" (
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-maven.ps1
  if errorlevel 1 exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-server.ps1
exit /b %errorlevel%
