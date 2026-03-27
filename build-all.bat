@echo off

call cd web/oscar-viewer

call npm install
call npm run build

call cd ..\..

call gradlew clean build -x test -x osgi
