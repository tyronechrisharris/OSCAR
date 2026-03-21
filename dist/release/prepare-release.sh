#!/usr/bin/env bash
set -euo pipefail
#
# prepare-release.sh
# Make copying of launch scripts and orchestration files idempotent and safe for CI:
# - skip copying when source == destination
# - skip missing files
# - skip copying when files are identical
# - make shell scripts executable
#
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
# project root is two levels up from dist/release
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." >/dev/null 2>&1 && pwd)"
TARGET_DIR="${1:-${PROJECT_ROOT}/dist/release}"

echo "Preparing release distribution (target: ${TARGET_DIR})"

mkdir -p "${TARGET_DIR}"

# Files and directories to include in the release root
RELEASE_ITEMS=(
  "launch-all.sh"
  "launch-all-arm.sh"
  "launch-all.ps1"
  "launch-all.bat"
  "docker-compose.yml"
  "Dockerfile.osh"
  "caddy"
)

for item in "${RELEASE_ITEMS[@]}"; do
  # Check in both script directory and project root (supporting different run contexts)
  if [ -e "${SCRIPT_DIR}/${item}" ]; then
    SRC="${SCRIPT_DIR}/${item}"
  elif [ -e "${PROJECT_ROOT}/${item}" ]; then
    SRC="${PROJECT_ROOT}/${item}"
  else
    echo "Skipping missing source ${item}"
    continue
  fi

  DST="${TARGET_DIR}/${item}"

  # If source and destination refer to the same path/inode, skip the copy.
  if command -v realpath >/dev/null 2>&1; then
    if [ "$(realpath "${SRC}")" = "$(realpath "${DST}" 2>/dev/null || echo '')" ]; then
      echo "Source and destination are identical for ${item}, skipping copy."
      # Still ensure permissions for scripts even if same file
      case "${item}" in
        *.sh) chmod +x "${DST}" || true ;;
        *.bat) chmod +x "${DST}" || true ;;
      esac
      continue
    fi
  fi

  # If the destination exists and is identical, skip the copy.
  if [ -d "${SRC}" ]; then
    if [ -d "${DST}" ]; then
        echo "Directory ${item} already exists at destination."
    else
        echo "Copying directory ${SRC} -> ${DST}"
        cp -rp "${SRC}" "${DST}"
    fi
  elif [ -e "${DST}" ] && cmp -s "${SRC}" "${DST}"; then
    echo "Destination ${DST} already up-to-date."
  else
    echo "Copying ${SRC} -> ${DST}"
    cp -p "${SRC}" "${DST}"
  fi

  # Ensure executable bit for appropriate files
  case "${item}" in
    *.sh) chmod +x "${DST}" || true ;;
    *.bat) chmod +x "${DST}" || true ;;
  esac
done

echo "Prepare-release complete."
