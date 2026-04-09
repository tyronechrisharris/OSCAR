#!/bin/bash

# Ensure Docker is installed
if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not installed. Please install Docker first."
    # Fail
    sh -c "exit 1"
fi

# Optimize kernel parameters for high-density UDP camera streaming (Linux only)
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    echo "Optimizing kernel parameters for high-density UDP streaming..."
    sudo sysctl -w net.core.rmem_max=26214400
    sudo sysctl -w net.core.rmem_default=26214400
    sudo sysctl -w net.core.wmem_max=26214400
    sudo sysctl -w net.core.wmem_default=26214400
    sudo sysctl -w fs.file-max=1000000

    echo "Configuring firewall for MediaMTX API access..."
    # Dynamically identify the Docker bridge subnet (defaulting to 172.18.0.0/16 if not found)
    DOCKER_SUBNET=$(docker network inspect osh-internal -f '{{range .IPAM.Config}}{{.Subnet}}{{end}}' 2>/dev/null || echo "172.18.0.0/16")
    if command -v ufw >/dev/null 2>&1; then
        sudo ufw allow from "$DOCKER_SUBNET" to any port 9997 proto tcp
    fi
fi

echo "Launching fully containerized OSCAR Stack via Docker Compose..."
docker compose up -d || sh -c 'exit 1'

echo "OSCAR Stack is launching. Please wait a few moments for the database and backend to initialize."
echo "Access the system at: http://localhost or https://localhost"
