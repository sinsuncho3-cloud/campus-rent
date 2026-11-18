@echo off
setlocal
cd /d "%~dp0"
call run-server.cmd
exit /b %errorlevel%
