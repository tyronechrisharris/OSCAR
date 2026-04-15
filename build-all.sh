#!/bin/bash
set -euo pipefail

cd web/oscar-viewer
npm install
npm run build

cd ../..

./gradlew build relDistZip -x test -x osgi

# Clean up local test secrets before Docker packaging
rm -f .app_secrets .db_password .initial_admin_password *.jks *.p12
rm -rf security-utils/config/

# Ensure Unix line endings for bash scripts in a macOS/Linux-compatible way
find . -name "*.sh" -exec perl -pi -e 's/\r$//' {} +
