@echo off
setlocal enabledelayedexpansion

where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker is not installed or not in PATH.
    cmd /c exit /b 1
)

echo Fetching Tailscale MagicDNS domain...
where tailscale >nul 2>&1
if %errorlevel% neq 0 (
    echo Warning: tailscale not found. Proceeding without dynamic domain injection.
) else (
    :: Run tailscale status, parse JSON with PowerShell, and strip the trailing dot
    for /f "delims=" %%I in ('powershell -Command "(tailscale status --json | ConvertFrom-Json).Self.DNSName.TrimEnd('.')" 2^>nul') do set TAILSCALE_DOMAIN=%%I

    if "!TAILSCALE_DOMAIN!"=="" (
        echo Warning: Could not fetch Tailscale domain. Falling back to default behavior.
    ) else (
        echo Tailscale domain dynamically set to: !TAILSCALE_DOMAIN!
    )
)

echo Launching fully containerized OSCAR Stack via Docker Compose...
docker compose up -d
if %errorlevel% neq 0 (
    echo ERROR: Docker Compose failed to start.
    cmd /c exit /b 1
)

echo OSCAR Stack is launching. Please wait a few moments for the database and backend to initialize.
echo Access the system at: http://localhost or https://localhost
endlocal
