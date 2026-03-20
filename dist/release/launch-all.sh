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

echo "Starting OSCAR stack via Docker Compose..."

docker-compose up -d

echo "OSCAR stack is starting..."
echo "Access the OSH Backend via Caddy on ports 80/443."
