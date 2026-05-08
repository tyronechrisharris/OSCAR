#!/bin/bash
# Quiet wrapper for PostGIS entrypoint to suppress routine initdb/bootstrap chatter
# Only errors and crucial failures will bypass this wrapper if we pipe stdout to /dev/null

if [ "${LOG_LEVEL:-error}" = "error" ]; then
    # Execute the original entrypoint but redirect stdout to /dev/null, leaving stderr alone
    exec /usr/local/bin/docker-entrypoint.sh "$@" > /dev/null
else
    # Execute normally if not in error-only mode
    exec /usr/local/bin/docker-entrypoint.sh "$@"
fi
