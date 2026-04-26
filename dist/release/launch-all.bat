@echo off
setlocal enabledelayedexpansion

where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker is not installed or not in PATH.
    cmd /c exit /b 1
)

set "VERSION=v1.17.1"
set "EXE_NAME=mediamtx.exe"
set "TARGET_DIR=%~dp0mediamtx_%VERSION%\"
set "FULL_PATH=%TARGET_DIR%%EXE_NAME%"

if exist "%FULL_PATH%" (
    echo MediaMTX Version %VERSION% found.
) else (
    echo Downloading MediaMTX %VERSION%...
    if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"

    :: Use the specific version tag in the download URL
    powershell -Command ^
    "$url = 'https://github.com/bluenviron/mediamtx/releases/download/%VERSION%/mediamtx_%VERSION%_windows_amd64.zip';" ^
    "$zip = '%TEMP%\mediamtx.zip';" ^
    "Invoke-WebRequest -Uri $url -OutFile $zip;" ^
    "Expand-Archive -Path $zip -DestinationPath '%TARGET_DIR%' -Force;" ^
    "Remove-Item $zip;"
)

echo Generating MediaMTX configuration...
if not exist "%TARGET_DIR%mtx-secrets" mkdir "%TARGET_DIR%mtx-secrets"

:: We generate a simplified configuration mimicking the docker-compose init-secrets process
echo # MediaMTX Configuration for OSCAR (Auto-Generated Windows) > "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo api: true >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo apiAddress: :9997 >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo writeQueueSize: 1024 >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo. >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo # Protocols (Optimized for high density) >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo rtmp: false >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo hls: false >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo webrtc: false >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo srt: false >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo. >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo pathDefaults: >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo   source: publisher >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo. >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo # Path Settings >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo paths: >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"
echo   all_others: >> "%TARGET_DIR%mtx-secrets\mediamtx.yml"

echo Launching MediaMTX %VERSION% in background...
start "" "%FULL_PATH%" "%TARGET_DIR%mtx-secrets\mediamtx.yml"

echo Launching fully containerized OSCAR Stack via Docker Compose...
docker compose up -d
if %errorlevel% neq 0 (
    echo ERROR: Docker Compose failed to start.
    cmd /c exit /b 1
)

echo OSCAR Stack is launching. Please wait a few moments for the database and backend to initialize.
echo Access the system at: http://localhost or https://localhost
endlocal