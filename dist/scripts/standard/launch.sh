#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Persistent CA Check & Generation
# If keystore doesn't exist, this will generate it and create .app_secrets.
# If it does exist, it will check for auto-renewal of the leaf certificate.

if [ -n "$KEYSTORE_PASSWORD_FILE" ] && [ -f "$KEYSTORE_PASSWORD_FILE" ]; then
    cp "$KEYSTORE_PASSWORD_FILE" ./.app_secrets
fi

java -cp "lib/*" com.botts.impl.security.LocalCAUtility

PASS_FILE=${KEYSTORE_PASSWORD_FILE:-.app_secrets}

if [ -f "$PASS_FILE" ]; then
    export KEYSTORE_PASSWORD=$(head -n 1 "$PASS_FILE")
    # Use the same auto-generated secret for the truststore if not provided
    if [ -z "$TRUSTSTORE_PASSWORD" ]; then
        export TRUSTSTORE_PASSWORD="$KEYSTORE_PASSWORD"
    fi
else
    echo "CRITICAL ERROR: $PASS_FILE not found. Cannot load keystore password. Halting startup."
    exit 1
fi

# Make sure all the necessary certificates are trusted by the system.
"$SCRIPT_DIR/load_trusted_certs.sh"


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
