#!/usr/bin/env bash
set -euo pipefail

# Determine script location
SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Ensure docker present
if ! command -v docker >/dev/null 2>&1; then
    echo "Error: 'docker' not found in PATH. Please install Docker and the Docker Compose plugin."
    exit 1
fi

# Ensure docker compose plugin available
if ! docker compose version >/dev/null 2>&1; then
    echo "Error: 'docker compose' plugin is not available. Ensure you have Docker Compose v2 (the 'docker compose' plugin)."
    exit 1
fi

# 1. Look for docker-compose.yml in the same directory (standalone release)
if [ -f "$SCRIPT_DIR/docker-compose.yml" ]; then
    RELEASE_ROOT="$SCRIPT_DIR"
    COMPOSE_FILE="$RELEASE_ROOT/docker-compose.yml"
# 2. Look for it two levels up (standard dev repo structure)
elif [ -f "$SCRIPT_DIR/../../docker-compose.yml" ]; then
    RELEASE_ROOT="$(cd "$SCRIPT_DIR/../../" && pwd)"
    COMPOSE_FILE="$RELEASE_ROOT/docker-compose.yml"
else
    echo "Error: Could not find docker-compose.yml in $SCRIPT_DIR or repo root."
    exit 1
fi

cd "$RELEASE_ROOT"

export POSTGRES_PASSWORD_FILE="$RELEASE_ROOT/.db_password"

if [ ! -f "$POSTGRES_PASSWORD_FILE" ]; then
    echo "Generating new database password..."
    openssl rand -base64 32 > "$POSTGRES_PASSWORD_FILE"
fi

# Ensure necessary directories exist for runtime mounts
mkdir -p osh-node-oscar/trusted_certificates
mkdir -p osh-node-oscar/rules

echo
echo "OSCAR deterministic startup: PostGIS -> OSH -> Caddy"
echo

echo "Starting PostGIS..."
docker compose -f "$COMPOSE_FILE" up -d postgis

echo -n "Waiting for PostGIS (pg_isready)..."
until docker exec postgis pg_isready -h localhost -d gis -U postgres >/dev/null 2>&1; do
  printf "."
  sleep 2
done
echo " OK"

echo -n "Waiting for 'gis' database to exist..."
until docker exec postgis psql -h localhost -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='gis'" 2>/dev/null | grep -q 1; do
  printf "."
  sleep 2
done
echo " OK"

echo "Starting OSH backend..."
docker compose -f "$COMPOSE_FILE" up -d --build osh

echo "Waiting for OSH to become stable..."
OSH_WAIT_SECS=240
END=$((SECONDS + OSH_WAIT_SECS))
while [ $SECONDS -lt $END ]; do
  STATE="$(docker inspect --format '{{.State.Status}}' osh 2>/dev/null || echo 'missing')"
  if [ "$STATE" = "exited" ] || [ "$STATE" = "dead" ]; then
    echo "OSH container exited unexpectedly — last logs:"
    docker logs --tail 300 osh || true
    echo "Aborting startup due to OSH failure."
    exit 2
  fi

  if [ "$STATE" = "running" ]; then
    # If OSH logs show datastore errors, fail early and show logs
    if docker logs osh --tail 200 2>&1 | grep -E "Error starting datastores|Fatal error during sensorhub execution" >/dev/null 2>&1; then
      echo "OSH reported datastore startup error. Showing logs:"
      docker logs --tail 300 osh || true
      exit 2
    fi

    # OSH is running and no immediate errors visible — assume startup succeeded
    sleep 5
    echo "OSH is running."
    break
  fi
  printf "."
  sleep 2
done

if [ $SECONDS -ge $END ]; then
  echo
  echo "Timed out waiting for OSH to become stable. Showing last 300 lines of logs:"
  docker logs --tail 300 osh || true
  exit 2
fi

echo "Starting Caddy (last)..."
# Set defaults to silence Docker Compose warnings
export DEPLOYMENT_PROFILE="${DEPLOYMENT_PROFILE:-federated}"
export DOMAIN="${DOMAIN:-localhost}"

docker compose -f "$COMPOSE_FILE" up -d caddy

echo
echo "OSCAR stack is starting. Access the OSH Backend via Caddy on ports 80/443."
