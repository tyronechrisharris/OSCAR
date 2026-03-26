#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Navigate to the project root (where docker-compose.yml is)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$PROJECT_ROOT" || exit 1

# Default environment variables for Docker Compose
export DEPLOYMENT_PROFILE="${DEPLOYMENT_PROFILE:-federated}"
export DOMAIN="${DOMAIN:-localhost}"
export POSTGIS_DOCKERFILE="Dockerfile-arm64"

# Ensure .db_password secret is initialized
if [ ! -f .db_password ]; then
    echo "Initializing database password..."
    openssl rand -base64 32 > .db_password
    chmod 600 .db_password
fi

# Create required directories
mkdir -p osh-node-oscar/config
mkdir -p osh-node-oscar/db
mkdir -p osh-node-oscar/files
mkdir -p osh-node-oscar/trusted_certificates
mkdir -p osh-node-oscar/rules

# Touch required secret/cert files to prevent Docker from creating them as directories
touch osh-node-oscar/osh-keystore.p12
touch osh-node-oscar/.app_secrets
touch osh-node-oscar/truststore.jks
touch osh-node-oscar/osh-leaf.crt
touch osh-node-oscar/osh-leaf.key

# Secure existing secret files
[ -f osh-node-oscar/.app_secrets ] && chmod 600 osh-node-oscar/.app_secrets

echo "Starting OSCAR Stack (ARM64) via Docker Compose..."
docker compose up -d

echo "OSCAR Stack is starting. Use 'docker compose logs -f' to monitor."
