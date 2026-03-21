#!/usr/bin/env bash
set -euo pipefail

# prepare-release.sh
# Copy the launch scripts (platform launchers) into the dist/release
# root so packaging includes them at top level (this mirrors historical behavior).

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST_DIR="$REPO_ROOT/dist/release"

echo "Preparing release scripts for packaging (target: $DIST_DIR)"

mkdir -p "$DIST_DIR"

# List of source files to copy into the dist/release root.
# We're already in the repo, so we use existing files.
SRC_FILES=(
  "$DIST_DIR/launch-all.sh"
  "$DIST_DIR/launch-all-arm.sh"
  "$DIST_DIR/launch-all.ps1"
  "$DIST_DIR/launch-all.bat"
)

for f in "${SRC_FILES[@]}"; do
  if [ -f "$f" ]; then
    cp -p "$f" "$DIST_DIR/$(basename "$f")"
    echo "  ensured: $(basename "$f")"
  else
    echo "  skipped (not found): $f"
  fi
done

# Ensure unix launchers are executable in the packaged tree
if [ -f "$DIST_DIR/launch-all.sh" ]; then
  chmod +x "$DIST_DIR/launch-all.sh" || true
fi
if [ -f "$DIST_DIR/launch-all-arm.sh" ]; then
  chmod +x "$DIST_DIR/launch-all-arm.sh" || true
fi

echo "Done. Release scripts are available under: $DIST_DIR"
