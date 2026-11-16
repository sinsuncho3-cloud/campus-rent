@echo off
setlocal
cd /d "%~dp0"
if not exist ".work\jdbc-check" mkdir ".work\jdbc-check"
javac -encoding UTF-8 -source 8 -target 8 -d .work\jdbc-check tools\OracleConnectionCheck.java
if errorlevel 1 exit /b 1
java -cp ".work\jdbc-check;.work\jdbc\ojdbc6-11.2.0.4.jar" OracleConnectionCheck %*
exit /b %errorlevel%
