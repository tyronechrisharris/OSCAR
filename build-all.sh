#!/bin/bash

cd web/oscar-viewer || exit

npm install
npm run build

cd ../.. || exit

./gradlew build -x test -x osgi

# Ensure release launch scripts are included in the packaged dist/release tree.
if [ -x dist/release/prepare-release.sh ]; then
  echo "Running dist/release/prepare-release.sh to include launch scripts in the release..."
  ./dist/release/prepare-release.sh
else
  echo "prepare-release.sh not found in dist/release — skipping release script copy."
fi