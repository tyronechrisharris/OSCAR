@echo off
setlocal enabledelayedexpansion

REM Get the directory where the batch file is located
set "SCRIPT_DIR=%~dp0"
REM Remove trailing backslash
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

REM Navigate to the project root (where docker-compose.yml is)
cd "%SCRIPT_DIR%\..\.."
set "PROJECT_ROOT=%cd%"

REM Default environment variables for Docker Compose
if "%DEPLOYMENT_PROFILE%"=="" set "DEPLOYMENT_PROFILE=federated"
if "%DOMAIN%"=="" set "DOMAIN=localhost"

REM Ensure .db_password secret is initialized
if not exist .db_password (
    echo Initializing database password...
    powershell -Command "$p = New-Object byte[] 32; (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($p); $pwd = [Convert]::ToBase64String($p); [System.IO.File]::WriteAllText('.db_password', $pwd)"
)

REM Create required directories
if not exist "osh-node-oscar\config" mkdir "osh-node-oscar\config"
if not exist "osh-node-oscar\db" mkdir "osh-node-oscar\db"
if not exist "osh-node-oscar\files" mkdir "osh-node-oscar\files"
if not exist "osh-node-oscar\trusted_certificates" mkdir "osh-node-oscar\trusted_certificates"
if not exist "osh-node-oscar\rules" mkdir "osh-node-oscar\rules"

REM Touch required secret/cert files to prevent Docker from creating them as directories
if not exist "osh-node-oscar\osh-keystore.p12" type nul > "osh-node-oscar\osh-keystore.p12"
if not exist "osh-node-oscar\.app_secrets" type nul > "osh-node-oscar\.app_secrets"
if not exist "osh-node-oscar\truststore.jks" type nul > "osh-node-oscar\truststore.jks"
if not exist "osh-node-oscar\osh-leaf.crt" type nul > "osh-node-oscar\osh-leaf.crt"
if not exist "osh-node-oscar\osh-leaf.key" type nul > "osh-node-oscar\osh-leaf.key"

echo Starting OSCAR Stack via Docker Compose...
docker compose up -d

echo OSCAR Stack is starting. Use 'docker compose logs -f' to monitor.
pause
endlocal
