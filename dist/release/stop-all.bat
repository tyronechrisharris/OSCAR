@echo off
set CONTAINER_NAME=oscar-postgis-container
set PROXY_CONTAINER_NAME=osh-proxy
set SENSORHUB_NAME=com.botts.impl.security.SensorHubWrapper

echo Stopping containers: %CONTAINER_NAME%, %PROXY_CONTAINER_NAME%...

docker stop %CONTAINER_NAME%
docker stop %PROXY_CONTAINER_NAME%

echo.
echo Stopping SensorHubWrapper Java Process...

FOR /F "tokens=1" %%A IN ('wmic process where "CommandLine like '%%%SENSORHUB_NAME%%%' and name='java.exe'" get ProcessId ^| findstr /R "[0-9]"') DO (
    echo Stopping SensorHubWrapper with PID %%A...
    taskkill /PID %%A /F
    echo SensorHubWrapper stopped.
    goto :DoneJava
)

echo SensorHubWrapper process not found.

:DoneJava
echo.
echo Done.
