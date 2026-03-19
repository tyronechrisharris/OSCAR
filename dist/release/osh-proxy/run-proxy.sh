#!/bin/bash

# Directory where the script is located
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

# Check for required PEM files for Offline mode
if [[ ! -f "../osh-node-oscar/osh-leaf.crt" || ! -f "../osh-node-oscar/osh-leaf.key" ]]; then
    echo "Warning: Internal leaf certificates (osh-leaf.crt/key) not found in the osh-node-oscar directory."
    echo "The proxy may fail to start in Offline mode. Please ensure the OSH backend has been launched at least once to generate these certificates."
fi

# Build and start the proxy
echo "Building and starting OSH Proxy (Caddy)..."
docker-compose up -d --build

echo "OSH Proxy is now running on ports 443 and 80."
