#!/bin/bash

# Directory where the script is located
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

# Wait for required PEM files for Offline mode
echo "Waiting for internal leaf certificates (osh-leaf.crt/key)..."
MAX_RETRIES=10
RETRY_COUNT=0
while [[ ! -f "../osh-node-oscar/osh-leaf.crt" || ! -f "../osh-node-oscar/osh-leaf.key" ]]; do
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo "Warning: Internal leaf certificates not found after $MAX_RETRIES retries."
        echo "The proxy may fail to start in Offline mode. Please ensure the OSH backend has been launched."
        break
    fi
    echo "Still waiting for certificates... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 5
    RETRY_COUNT=$((RETRY_COUNT+1))
done

# Build and start the proxy
echo "Building and starting OSH Proxy (Caddy)..."
docker-compose up -d --build

echo "OSH Proxy is now running on ports 443 and 80."
