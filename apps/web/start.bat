@echo off
REM cheesino web - Windows tek tik baslat
cd /d "%~dp0"
start "" http://localhost:8088
node proxy.js
