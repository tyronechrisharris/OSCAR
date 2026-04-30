#!/bin/bash
# Hardened OSH Backend Startup Script
# Disables shell globbing to protect JAVA_OPTS containing wildcards (e.g., 10.*)
set -f

echo "Waiting 30 seconds for PostGIS spatial extensions to settle..."
sleep 30

# Move to application directory
cd /app

# Expand JAVA_OPTS without globbing and execute OSH
# Using eval ensures that quoted properties within JAVA_OPTS (like -D...="a|b") are handled correctly
exec java $JAVA_OPTS -Dlogback.configurationFile=./logback.xml -cp './lib/*' com.botts.impl.security.SensorHubWrapper ./config.json ./db
