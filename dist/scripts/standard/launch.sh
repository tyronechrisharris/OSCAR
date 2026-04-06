#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

PASS_FILE=${KEYSTORE_PASSWORD_FILE:-.app_secrets}

if [ -f "$PASS_FILE" ]; then
    export KEYSTORE_PASSWORD=$(head -n 1 "$PASS_FILE")
    if [ -z "$TRUSTSTORE_PASSWORD" ]; then
        export TRUSTSTORE_PASSWORD="$KEYSTORE_PASSWORD"
    fi
else
    echo "CRITICAL ERROR: $PASS_FILE not found. Cannot load keystore password. Halting startup."
    exit 1
fi

if [ -f "./.initial_admin_password" ]; then
    export INITIAL_ADMIN_PASSWORD_FILE="./.initial_admin_password"
fi

# Database configuration
export DB_HOST="${DB_HOST:-localhost}"
if [ -z "$POSTGRES_PASSWORD_FILE" ]; then
    # Check for password file in parent directory (standard for release) or current
    if [ -f "../.db_password" ]; then
        export POSTGRES_PASSWORD_FILE="$(cd .. && pwd)/.db_password"
    elif [ -f "./.db_password" ]; then
        export POSTGRES_PASSWORD_FILE="$(pwd)/.db_password"
    fi
fi

# After copying the default configuration file, also look to see if they
# specified what they want the initial admin user's password to be, either
# as a secret file or by providing it as an environment variable.
if [ ! -z "$INITIAL_ADMIN_PASSWORD_FILE" ] || [ ! -z "$INITIAL_ADMIN_PASSWORD" ]; then
  "$SCRIPT_DIR/set-initial-admin-password.sh"
fi



# Start the node
java -Xms6g -Xmx6g -Xss256k -XX:ReservedCodeCacheSize=512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError \
	-Dlogback.configurationFile=./logback.xml \
	-cp "lib/*" \
	-Djava.system.class.loader="org.sensorhub.utils.NativeClassLoader" \
	com.botts.impl.security.SensorHubWrapper ./config.json ./db
