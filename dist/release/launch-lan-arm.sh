#!/bin/bash

# Ensure Docker is installed
if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not installed. Please install Docker first."
    # Fail
    sh -c "exit 1"
fi

echo "Launching fully containerized OSCAR Stack via Docker Compose for ARM64..."
export POSTGIS_DOCKERFILE="Dockerfile-arm64"
docker compose --profile lan-only up -d || sh -c 'exit 1'

echo "OSCAR Stack is launching. Please wait a few moments for the database and backend to initialize."
echo "Access the system at: http://localhost or https://localhost"
