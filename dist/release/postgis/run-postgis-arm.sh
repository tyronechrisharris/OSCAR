#!/bin/bash

if [ ! -d "$(pwd)/pgdata" ]; then
  echo "Creating pgdata folder..."
  mkdir -p "$(pwd)/pgdata"
fi

# Set up DB password secret
PROJECT_DIR="$(pwd)"
if [ -z "$POSTGRES_PASSWORD_FILE" ]; then
    export POSTGRES_PASSWORD_FILE="${PROJECT_DIR}/.db_password"
fi

if [ ! -f "$POSTGRES_PASSWORD_FILE" ]; then
    echo "Generating new database password..."
    openssl rand -base64 32 > "$POSTGRES_PASSWORD_FILE"
fi

# Load environment variables from .env if it exists (up one directory)
if [ -f ../.env ]; then
  export $(grep -v '^#' ../.env | xargs)
fi

# Default to edge profile if not set
DB_PERFORMANCE_PROFILE="${DB_PERFORMANCE_PROFILE:-edge}"

if [ "$DB_PERFORMANCE_PROFILE" = "hub" ]; then
    DB_OPTS="-c shared_buffers=1GB -c work_mem=32MB -c maintenance_work_mem=256MB -c wal_buffers=16MB -c checkpoint_timeout=10min -c max_wal_size=2GB -c max_connections=1000"
else
    DB_OPTS="-c shared_buffers=128MB -c work_mem=4MB -c maintenance_work_mem=64MB -c wal_buffers=4MB -c checkpoint_timeout=5min -c max_wal_size=1GB -c max_connections=100"
fi

docker build . --file=Dockerfile-arm64 --tag=oscar-postgis-arm
docker run \
  -e POSTGRES_DB=gis \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASS=$(cat "$POSTGRES_PASSWORD_FILE") \
  -e DATADIR=/var/lib/postgresql/data \
  -p 5432:5432 \
  -v "$(pwd)/pgdata:/var/lib/postgresql/data" \
  -v "$POSTGRES_PASSWORD_FILE:/run/secrets/db_password" \
  -d \
  oscar-postgis-arm $DB_OPTS