#!/bin/bash

# Stop all running containers
docker stop $(docker ps -q)

# Deep clean docker system
docker system prune --all --volumes -f

# Remove directories and log file
sudo rm -rf hivemq-config/ osh-node-oscar/ pgdata/ dist/ logs.txt tailscale/ postgis/ oscar.zip

# Increment the NODE_NAME number in .env
sed -i -E 's/(NODE_NAME=.*)([0-9]+)$/echo "\1$((\2+1))"/e' .env

echo "Cleanup complete and NODE_NAME incremented."
