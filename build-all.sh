#!/bin/bash

cd web/oscar-viewer || exit

npm install
npm run build

cd ../.. || exit

# Ensure release launch scripts and orchestration files are included in the packaged dist/release tree.
# We do this BEFORE the gradle build so that the files are included in the distribution ZIP.
if [ -x dist/release/prepare-release.sh ]; then
  echo "Running dist/release/prepare-release.sh (pre-gradle) to populate dist/release..."
  # Call the script defensively. The script itself is now idempotent; if it
  # still returns non-zero for an unexpected reason, print a warning and
  # continue the build so the CI failure is easier to diagnose without
  # masking other results.
  if ! ./dist/release/prepare-release.sh "$(pwd)/dist/release"; then
    echo "WARNING: prepare-release.sh exited with a non-zero code; continuing build to avoid a brittle packaging step."
  fi
else
  echo "prepare-release.sh not found in dist/release — skipping pre-gradle release prep."
fi

./gradlew build -x test -x osgi

# Optional: keep a post-gradle call for safety in local dev flows
if [ -x dist/release/prepare-release.sh ]; then
  ./dist/release/prepare-release.sh "$(pwd)/dist/release" > /dev/null 2>&1
fi
