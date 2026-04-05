@echo off
setlocal enabledelayedexpansion


REM Persistent CA Check & Generation
REM If keystore doesn't exist, this will generate it and create .app_secrets.

if not "%KEYSTORE_PASSWORD_FILE%"=="" (
    if exist "%KEYSTORE_PASSWORD_FILE%" (
        copy /y "%KEYSTORE_PASSWORD_FILE%" ".\.app_secrets" >NUL
    )
)

REM If it does exist, it will check for auto-renewal of the leaf certificate.
java -cp "lib/*" com.botts.impl.security.LocalCAUtility

set PASS_FILE=%KEYSTORE_PASSWORD_FILE%
if "%PASS_FILE%"=="" set PASS_FILE=.app_secrets

if exist "%PASS_FILE%" (
    set /p KEYSTORE_PASSWORD=<%PASS_FILE%
    REM Use the same auto-generated secret for the truststore if not provided
    if "%TRUSTSTORE_PASSWORD%"=="" (
        set "TRUSTSTORE_PASSWORD=!KEYSTORE_PASSWORD!"
    )
) else (
    echo CRITICAL ERROR: %PASS_FILE% not found. Cannot load keystore password. Halting startup.
    exit /b 1
)

REM Make sure all the necessary certificates are trusted by the system.
CALL %~dp0load_trusted_certs.bat

set KEYSTORE=.\osh-keystore.p12
set KEYSTORE_TYPE=PKCS12
set TRUSTSTORE=.\truststore.jks
set TRUSTSTORE_TYPE=JKS

if exist ".\.initial_admin_password" (
    set INITIAL_ADMIN_PASSWORD_FILE=.\.initial_admin_password
)

REM Database configuration
if "%DB_HOST%"=="" (set DB_HOST=localhost)
if "%POSTGRES_PASSWORD_FILE%"=="" (
    if exist "..\.db_password" (
        for %%i in ("..\.db_password") do set POSTGRES_PASSWORD_FILE=%%~fi
    ) else (
        if exist ".\.db_password" (
            for %%i in (".\.db_password") do set POSTGRES_PASSWORD_FILE=%%~fi
        )
    )
)

REM Check if INITIAL_ADMIN_PASSWORD_FILE or INITIAL_ADMIN_PASSWORD are provided
REM If so, call the next batch script to handle setting the initial admin password
if not "%INITIAL_ADMIN_PASSWORD_FILE%"=="" (
    CALL "%SCRIPT_DIR%set-initial-admin-password.bat"
) else (
    if not "%INITIAL_ADMIN_PASSWORD%"=="" (
        CALL "%SCRIPT_DIR%set-initial-admin-password.bat"
    )
)

REM Start the node
java -Xms6g -Xmx6g -Xss256k -XX:ReservedCodeCacheSize=512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError ^
    -Dlogback.configurationFile=./logback.xml ^
    -cp "lib/*" ^
    -Djava.system.class.loader="org.sensorhub.utils.NativeClassLoader" ^
    com.botts.impl.security.SensorHubWrapper config.json db


endlocal
