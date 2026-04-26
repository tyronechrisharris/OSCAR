@echo off
setlocal enabledelayedexpansion

echo Stopping MediaMTX...
taskkill /F /IM mediamtx.exe >nul 2>&1

echo Stopping fully containerized OSCAR Stack via Docker Compose...
docker compose down

echo OSCAR Stack has been stopped.
endlocal
