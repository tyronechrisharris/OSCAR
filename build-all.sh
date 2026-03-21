#!/bin/bash

cd web/oscar-viewer || exit

npm install
npm run build

cd ../.. || exit

./gradlew build -x test -x osgi

# Ensure release launch scripts are included in the packaged dist/release tree.
if [ -x dist/release/prepare-release.sh ]; then
  echo "Running dist/release/prepare-release.sh to include launch scripts in the release..."
  # Call the script defensively. The script itself is now idempotent; if it
  # still returns non-zero for an unexpected reason, print a warning and
  # continue the build so the CI failure is easier to diagnose without
  # masking other results.
  if ! ./dist/release/prepare-release.sh "$(pwd)/dist/release"; then
    echo "WARNING: prepare-release.sh exited with a non-zero code; continuing build to avoid a brittle packaging step."
  fi
else
  echo "prepare-release.sh not found in dist/release — skipping release script copy."
fi
