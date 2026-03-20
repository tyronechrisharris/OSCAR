@echo off
setlocal enabledelayedexpansion

REM ==== CONFIG ====
set PROJECT_DIR=%cd%
set "POSTGRES_PASSWORD_FILE=%PROJECT_DIR%\.db_password"

REM Generate new database password if missing
if not exist "%POSTGRES_PASSWORD_FILE%" (
    echo Generating new database password...
    powershell -Command "$p = New-Object byte[] 32; (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($p); $pwd = [Convert]::ToBase64String($p); [System.IO.File]::WriteAllText(\"%POSTGRES_PASSWORD_FILE:\=\\%\", $pwd)"
)

REM Check for Docker
where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker is not installed or not in PATH.
    exit /b 1
)

REM Check for Docker Compose
where docker-compose >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: docker-compose is not installed or not in PATH.
    exit /b 1
)

REM Ensure necessary directories exist for runtime mounts
if not exist "osh-node-oscar\trusted_certificates" mkdir "osh-node-oscar\trusted_certificates"
if not exist "osh-node-oscar\rules" mkdir "osh-node-oscar\rules"

echo Starting OSCAR stack via Docker Compose...

REM Set defaults to silence Docker Compose warnings
if "%DEPLOYMENT_PROFILE%"=="" (set DEPLOYMENT_PROFILE=federated)
if "%DOMAIN%"=="" (set DOMAIN=localhost)

docker-compose up -d

echo OSCAR stack is starting...
echo Access the OSH Backend via Caddy on ports 80/443.

endlocal
