@echo off

if not exist "%cd%\pgdata" (
    echo Creating pgdata folder...
    mkdir "%cd%\pgdata"
)

REM Load environment variables from .env if it exists (up one directory)
if exist ..\.env (
    for /f "tokens=*" %%a in ('type ..\.env ^| findstr /v "^#"') do (
        set "%%a"
    )
)

REM Default to edge profile if not set
if "%DB_PERFORMANCE_PROFILE%"=="" (set DB_PERFORMANCE_PROFILE=edge)

if "%DB_PERFORMANCE_PROFILE%"=="hub" (
    set DB_OPTS=-c shared_buffers=1GB -c work_mem=32MB -c maintenance_work_mem=256MB -c wal_buffers=16MB -c checkpoint_timeout=10min -c max_wal_size=2GB -c max_connections=1000
) else (
    set DB_OPTS=-c shared_buffers=128MB -c work_mem=4MB -c maintenance_work_mem=64MB -c wal_buffers=4MB -c checkpoint_timeout=5min -c max_wal_size=1GB -c max_connections=100
)

REM Set up DB password secret
if "%POSTGRES_PASSWORD_FILE%"=="" (set POSTGRES_PASSWORD_FILE=%cd%\.db_password)

if not exist "%POSTGRES_PASSWORD_FILE%" (
    echo Generating new database password...
    powershell -Command "$p = New-Object byte[] 32; (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($p); $pwd = [Convert]::ToBase64String($p); [System.IO.File]::WriteAllText('%POSTGRES_PASSWORD_FILE%', $pwd)"
)

docker build . --tag=oscar-postgis

docker run ^
  --name oscar-postgis ^
  --restart unless-stopped ^
  -e POSTGRES_DB=gis ^
  -e POSTGRES_USER=postgres ^
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db_password ^
  -p 5432:5432 ^
  -v "%cd%\pgdata:/var/lib/postgresql/data" ^
  -v "%POSTGRES_PASSWORD_FILE%:/run/secrets/db_password" ^
  -d ^
  oscar-postgis %DB_OPTS%