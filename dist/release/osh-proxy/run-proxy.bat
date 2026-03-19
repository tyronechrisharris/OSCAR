@echo off
setlocal

:: Get script directory
set "DIR=%~dp0"
cd /d "%DIR%"

:: Check for required PEM files
if not exist "..\osh-node-oscar\osh-leaf.crt" (
    echo Warning: Internal leaf certificates (osh-leaf.crt) not found in the osh-node-oscar directory.
)
if not exist "..\osh-node-oscar\osh-leaf.key" (
    echo Warning: Internal leaf certificates (osh-leaf.key) not found in the osh-node-oscar directory.
)

:: Build and start the proxy
echo Building and starting OSH Proxy (Caddy)...
docker-compose up -d --build

echo OSH Proxy is now running on ports 443 and 80.
pause
