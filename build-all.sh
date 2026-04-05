#!/bin/bash

cd web/oscar-viewer || exit

npm install
npm run build

cd ../.. || exit

./gradlew build -x test -x osgi

# Clean up local test secrets before Docker packaging
rm -f .app_secrets .db_password .initial_admin_password *.jks *.p12

# Ensure Linux line endings for bash scripts
find . -name "*.sh" -exec sed -i 's/\r$//' {} +