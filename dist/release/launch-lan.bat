@echo off
setlocal enabledelayedexpansion

where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker is not installed or not in PATH.
    cmd /c exit /b 1
)

echo Launching fully containerized OSCAR Stack via Docker Compose...
docker compose --profile lan-only up -d
if %errorlevel% neq 0 (
    echo ERROR: Docker Compose failed to start.
    cmd /c exit /b 1
)

echo OSCAR Stack is launching. Please wait a few moments for the database and backend to initialize.
echo Access the system at: http://localhost or https://localhost
endlocal
