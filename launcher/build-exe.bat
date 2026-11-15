@echo off
cd /d "%~dp0"
call npm install
call npm run dist
echo Готово: папка dist\ (Nizay client.exe)
pause
