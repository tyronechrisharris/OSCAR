#!/bin/bash
docker compose --profile mesh down -v
docker images --format "{{.Repository}}:{{.Tag}}" | grep -E 'harristc825/oscar-backend|harristc825/oscar-postgis' | xargs -r docker rmi -f
sudo find . -mindepth 1 -maxdepth 1 ! -name 'docker-compose*.yml' ! -name '.env*' ! -name '*.sh' ! -name '*.bat' -exec rm -rf {} +
