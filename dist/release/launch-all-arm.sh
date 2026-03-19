#!/bin/bash

HOST="${DB_HOST:-localhost}"
PORT="5432"
DB_NAME="gis"
DB_USER="postgres"
RETRY_MAX=20
RETRY_INTERVAL=5
PROJECT_DIR="$(pwd)"   # Store the original directory
CONTAINER_NAME="oscar-postgis-container"

# Set up DB password secret
if [ -z "$POSTGRES_PASSWORD_FILE" ]; then
    export POSTGRES_PASSWORD_FILE="${PROJECT_DIR}/.db_password"
fi

if [ ! -f "$POSTGRES_PASSWORD_FILE" ]; then
    echo "Generating new database password..."
    openssl rand -base64 32 > "$POSTGRES_PASSWORD_FILE"
fi

#docker rm -f "$CONTAINER_NAME" 2>/dev/null || true

# Create pgdata directory if needed
if [ ! -d "${PROJECT_DIR}/pgdata" ]; then
  echo "Creating pgdata folder..."
  mkdir -p "${PROJECT_DIR}/pgdata"
fi

# Check Docker
if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not installed. Please install Docker first."
    exit 1
fi

# Check Docker Compose
if ! docker compose version >/dev/null 2>&1; then
    echo "Error: Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

echo "Starting OSCAR Deployment via Docker Compose..."

# Export ARM64 Dockerfile for PostGIS
export POSTGIS_DOCKERFILE="Dockerfile-arm64"

# Use Docker Compose to launch everything
docker compose up -d

# Wait for PostGIS to be ready
echo "Waiting for PostGIS ARM64 (PostgreSQL) to be ready..."
RETRY_COUNT=0
until docker exec -u "$DB_USER" "$CONTAINER_NAME" pg_isready -d "$DB_NAME" > /dev/null 2>&1; do
  echo "PostGIS not ready yet, retrying..."
  RETRY_COUNT=$((RETRY_COUNT+1))
  if [ $RETRY_COUNT -ge $RETRY_MAX ]; then
    echo "Error: PostGIS did not become ready in time."
    exit 1
  fi
  sleep "${RETRY_INTERVAL}"
done

echo "OSCAR Stack is initializing. Access the application via https://localhost (Offline Mode) or your Tailscale domain (Federated Mode)."