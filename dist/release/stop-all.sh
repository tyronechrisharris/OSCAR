#!/bin/bash

# Stop native MediaMTX instance if running on macOS
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "Stopping native MediaMTX instance..."
    pkill -f mediamtx || true
fi

echo "Stopping fully containerized OSCAR Stack via Docker Compose..."
docker compose down

echo "OSCAR Stack has been stopped."