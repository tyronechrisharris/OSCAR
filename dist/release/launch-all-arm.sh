#!/bin/bash

PROJECT_DIR="$(pwd)"
export POSTGRES_PASSWORD_FILE="${PROJECT_DIR}/.db_password"

if [ ! -f "$POSTGRES_PASSWORD_FILE" ]; then
    echo "Generating new database password..."
    openssl rand -base64 32 > "$POSTGRES_PASSWORD_FILE"
fi

# Check Docker
if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not installed. Please install Docker first."
    exit 1
fi

# Check Docker Compose
if ! command -v docker-compose >/dev/null 2>&1; then
    echo "Error: docker-compose is not installed. Please install docker-compose first."
    exit 1
fi

# Ensure necessary directories exist for runtime mounts
mkdir -p osh-node-oscar/trusted_certificates
mkdir -p osh-node-oscar/rules

echo "Starting OSCAR stack via Docker Compose (ARM64)..."

# Set defaults to silence Docker Compose warnings
export DEPLOYMENT_PROFILE="${DEPLOYMENT_PROFILE:-federated}"
export DOMAIN="${DOMAIN:-localhost}"

# Ensure ARM64 Dockerfile is used for PostGIS
export POSTGIS_DOCKERFILE=Dockerfile-arm64
DOCKER_DEFAULT_PLATFORM=linux/arm64 docker-compose up -d

echo "OSCAR stack is starting..."
echo "Access the OSH Backend via Caddy on ports 80/443."
