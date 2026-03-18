#!/bin/bash
set -e

PROFILE=${DB_PERFORMANCE_PROFILE:-edge}

echo "Starting PostGIS with Database Performance Profile: $PROFILE"

if [ "$PROFILE" = "hub" ]; then
    set -- "$@" \
        -c max_connections=100 \
        -c shared_buffers=4GB \
        -c effective_cache_size=8GB \
        -c maintenance_work_mem=512MB \
        -c checkpoint_completion_target=0.9 \
        -c wal_buffers=16MB \
        -c default_statistics_target=100 \
        -c random_page_cost=1.1 \
        -c effective_io_concurrency=200 \
        -c work_mem=16MB \
        -c min_wal_size=1GB \
        -c max_wal_size=4GB \
        -c checkpoint_timeout=15min
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