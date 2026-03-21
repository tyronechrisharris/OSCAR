@echo off
REM Windows batch wrapper that calls the PowerShell launcher
REM Place this file in dist\release\ alongside launch-all.ps1

set SCRIPT_DIR=%~dp0
set PS_SCRIPT=%SCRIPT_DIR%launch-all.ps1

REM Run PowerShell with execution policy bypass
powershell -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" %*
IF %ERRORLEVEL% NEQ 0 (
  echo Launch script failed with exit code %ERRORLEVEL%.
  exit /b %ERRORLEVEL%
)
exit /b 0
