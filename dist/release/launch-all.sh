#!/bin/bash

# Ensure Docker is installed
if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not installed. Please install Docker first."
    # Fail
    sh -c "exit 1"
fi

# Fetch the Tailscale DNS name dynamically (requires jq)
echo "Fetching Tailscale MagicDNS domain..."
if command -v tailscale >/dev/null 2>&1 && command -v jq >/dev/null 2>&1; then
    TS_DOMAIN=$(tailscale status --json 2>/dev/null | jq -r '.Self.DNSName' | sed 's/\.$//')
    if [ -n "$TS_DOMAIN" ] && [ "$TS_DOMAIN" != "null" ]; then
        export TAILSCALE_DOMAIN=$TS_DOMAIN
        echo "Tailscale domain dynamically set to: $TAILSCALE_DOMAIN"
    else
        echo "Warning: Could not fetch Tailscale domain. Falling back to default behavior."
    fi
else
    echo "Warning: tailscale or jq not found. Proceeding without dynamic domain injection."
fi

echo "Launching fully containerized OSCAR Stack via Docker Compose..."
docker compose up -d || sh -c 'exit 1'

echo "OSCAR Stack is launching. Please wait a few moments for the database and backend to initialize."
echo "Access the system at: http://localhost or https://localhost"
