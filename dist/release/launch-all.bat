@echo off
setlocal enabledelayedexpansion

REM ==== CONFIG ====
if "%DB_HOST%"=="" (set HOST=localhost) else (set HOST=%DB_HOST%)
set PORT=5432
set DB_NAME=gis
set USER=postgres
set RETRY_MAX=20
set RETRY_INTERVAL=5
set PROJECT_DIR=%cd%
set CONTAINER_NAME=oscar-postgis-container
set IMAGE_NAME=oscar-postgis

echo PROJECT_DIR is: %PROJECT_DIR%

REM Set up DB password secret
if "%POSTGRES_PASSWORD_FILE%"=="" (set "POSTGRES_PASSWORD_FILE=%PROJECT_DIR%\.db_password")

if not exist "%POSTGRES_PASSWORD_FILE%" (
    echo Generating new database password...
    powershell -Command "$p = New-Object byte[] 32; (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($p); $pwd = [Convert]::ToBase64String($p); [System.IO.File]::WriteAllText(\"%POSTGRES_PASSWORD_FILE:\=\\%\", $pwd)"
)

set /p DB_PASSWORD=<"%POSTGRES_PASSWORD_FILE%"

where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker is not installed or not in PATH.
    exit /b 1
)

docker compose version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker Compose is not installed or not in PATH.
    exit /b 1
)

if not exist "%PROJECT_DIR%\pgdata" (
    echo Creating pgdata directory...
    mkdir "%PROJECT_DIR%\pgdata"
)

echo Starting OSCAR Deployment via Docker Compose...
docker compose up -d

echo Waiting for PostGIS database to become ready...

set RETRY_COUNT=0

:wait_loop
docker exec -u %USER% %CONTAINER_NAME% pg_isready -d %DB_NAME% >nul 2>&1
if %errorlevel% equ 0 (
    echo Received OK from PostGIS.
    goto after_wait
)

echo PostGIS not ready yet, retrying...
set /a RETRY_COUNT+=1

if %RETRY_COUNT% geq %RETRY_MAX% (
    echo ERROR: PostGIS did not become ready in time.
    exit /b 1
)

timeout /t %RETRY_INTERVAL% >nul
goto wait_loop

:after_wait

echo OSCAR Stack is initializing. Access the application via https://localhost (Offline Mode) or your Tailscale domain (Federated Mode).

endlocal
