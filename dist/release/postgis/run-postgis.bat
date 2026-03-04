@echo off

if not exist "%cd%\pgdata" (
    echo Creating pgdata folder...
    mkdir "%cd%\pgdata"
)

# Set up DB password secret
if "%POSTGRES_PASSWORD_FILE%"=="" (set POSTGRES_PASSWORD_FILE=%cd%\.db_password)

if not exist "%POSTGRES_PASSWORD_FILE%" (
    echo Generating new database password...
    powershell -Command "[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))" > "%POSTGRES_PASSWORD_FILE%"
)

docker build . --tag=oscar-postgis

docker run ^
  --name oscar-postgis ^
  --restart unless-stopped ^
  -e PG_MAX_CONNECTIONS=500 ^
  -e POSTGRES_DB=gis ^
  -e POSTGRES_USER=postgres ^
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db_password ^
  -p 5432:5432 ^
  -v "%cd%\pgdata:/var/lib/postgresql/data" ^
  -v "%POSTGRES_PASSWORD_FILE%:/run/secrets/db_password" ^
  oscar-postgis