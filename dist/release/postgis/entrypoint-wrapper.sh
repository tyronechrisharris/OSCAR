#!/bin/bash
set -e

PROFILE=${DB_PERFORMANCE_PROFILE:-edge}

echo "Starting PostGIS with Database Performance Profile: $PROFILE"

if [ "$PROFILE" = "hub" ]; then
    set -- "$@" \
        -c shared_buffers=4GB \
        -c work_mem=64MB \
        -c maintenance_work_mem=512MB \
        -c wal_buffers=16MB \
        -c checkpoint_timeout=15min \
        -c max_wal_size=4GB \
        -c max_connections=1024
else
    set -- "$@" \
        -c shared_buffers=256MB \
        -c work_mem=4MB \
        -c maintenance_work_mem=64MB \
        -c wal_buffers=4MB \
        -c checkpoint_timeout=5min \
        -c max_wal_size=1GB \
        -c max_connections=100
fi

# Hand over to the original entrypoint script
exec docker-entrypoint.sh "$@"