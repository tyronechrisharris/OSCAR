#!/bin/bash
# Portable scheduled Cypress run script for Linux/macOS
# Usage: ./run-cypress-scheduled.sh [baseUrl] [username] [password]

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$PROJECT_DIR/cypress/scheduled-logs"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%m-%ss")
LOG_FILE="$LOG_DIR/run-$TIMESTAMP.log"

mkdir -p "$LOG_DIR"

# Keep only the last 48 log files (~24 hours at 30-min intervals)
ls -t "$LOG_DIR"/run-*.log 2>/dev/null | tail -n +49 | xargs rm -f -- 2>/dev/null

cd "$PROJECT_DIR" || exit 1

# Setup environment from arguments or use defaults
export CYPRESS_BASE_URL=${1:-"http://localhost:8282"}
export CYPRESS_AUTH_USERNAME=${2:-""}
export CYPRESS_AUTH_PASSWORD=${3:-""}

echo "=== Cypress scheduled run at $TIMESTAMP ===" | tee -a "$LOG_FILE"
echo "Targeting $CYPRESS_BASE_URL" | tee -a "$LOG_FILE"

npm run test:scheduled 2>&1 | tee -a "$LOG_FILE"

EXIT_CODE=${PIPESTATUS[0]}
echo "=== Exit code: $EXIT_CODE ===" | tee -a "$LOG_FILE"
exit $EXIT_CODE
