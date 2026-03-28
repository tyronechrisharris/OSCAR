#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Identify project root based on presence of docker-compose.yml
if [ -f "$SCRIPT_DIR/docker-compose.yml" ]; then
    PROJECT_ROOT="$SCRIPT_DIR"
elif [ -f "$SCRIPT_DIR/../../docker-compose.yml" ]; then
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
else
    echo "ERROR: Could not find docker-compose.yml relative to $SCRIPT_DIR"
    exit 1
fi

cd "$PROJECT_ROOT" || exit 1

# Default environment variables for Docker Compose
export DEPLOYMENT_PROFILE="${DEPLOYMENT_PROFILE:-federated}"
export DOMAIN="${DOMAIN:-localhost}"

# Initialize .env from template if missing
if [ ! -f .env ] && [ -f .env.template ]; then
    echo "Initializing .env from .env.template..."
    cp .env.template .env
fi

# Ensure .db_password secret is initialized (check for non-zero size)
if [ ! -s .db_password ]; then
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
[ ! -f osh-node-oscar/osh-keystore.p12 ] && touch osh-node-oscar/osh-keystore.p12
[ ! -f osh-node-oscar/.app_secrets ] && touch osh-node-oscar/.app_secrets
[ ! -f osh-node-oscar/truststore.jks ] && touch osh-node-oscar/truststore.jks
[ ! -f osh-node-oscar/osh-leaf.crt ] && touch osh-node-oscar/osh-leaf.crt
[ ! -f osh-node-oscar/osh-leaf.key ] && touch osh-node-oscar/osh-leaf.key

# Secure existing secret files if they have content
[ -s osh-node-oscar/.app_secrets ] && chmod 600 osh-node-oscar/.app_secrets

echo "Starting OSCAR Stack via Docker Compose..."
docker compose up -d

echo "OSCAR Stack is starting. Use 'docker compose logs -f' to monitor."
