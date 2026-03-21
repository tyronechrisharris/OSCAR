#!/usr/bin/env bash
set -euo pipefail
#
# prepare-release.sh
# Make copying of launch scripts idempotent and safe for CI:
# - skip copying when source == destination
# - skip missing files
# - skip copying when files are identical
# - make shell scripts executable
#
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
# project root is two levels up from dist/release
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." >/dev/null 2>&1 && pwd)"
TARGET_DIR="${1:-${PROJECT_ROOT}/dist/release}"

echo "Preparing release scripts for packaging (target: ${TARGET_DIR})"

# The directory that historically held the canonical launch scripts. If you
# change where you keep them, update SOURCE_DIR accordingly.
SOURCE_DIR="${PROJECT_ROOT}/dist/release"

# Files we want to ensure are present in the release. Add or remove names as
# appropriate for your project.
LAUNCH_FILES=(
  "launch-all.sh"
  "launch-all-arm.sh"
  "launch-all.ps1"
  "launch-all.bat"
)

mkdir -p "${TARGET_DIR}"

# If the source and target directories are the same, nothing to copy — just
# ensure permissions and exit successfully.
if command -v realpath >/dev/null 2>&1; then
  if [ "$(realpath "${SOURCE_DIR}")" = "$(realpath "${TARGET_DIR}")" ]; then
    echo "Source and target are the same (${SOURCE_DIR}). Ensuring permissions and exiting."
    for f in "${LAUNCH_FILES[@]}"; do
      if [ -f "${TARGET_DIR}/${f}" ]; then
        case "${f}" in
          *.sh) chmod +x "${TARGET_DIR}/${f}" || true ;;
          *.bat) chmod +x "${TARGET_DIR}/${f}" || true ;;
        esac
      fi
    done
    exit 0
  fi
fi

for f in "${LAUNCH_FILES[@]}"; do
  SRC="${SOURCE_DIR}/${f}"
  DST="${TARGET_DIR}/${f}"

  if [ ! -e "${SRC}" ]; then
    echo "Skipping missing source ${SRC}"
    continue
  fi

  # If source and destination refer to the same path/inode, skip the copy.
  if command -v realpath >/dev/null 2>&1; then
    if [ "$(realpath "${SRC}")" = "$(realpath "${DST}" 2>/dev/null || echo '')" ]; then
      echo "Source and destination are identical for ${f}, skipping copy."
      continue
    fi
  fi

  # If the destination exists and is byte-for-byte identical, skip the copy.
  if [ -e "${DST}" ] && cmp -s "${SRC}" "${DST}"; then
    echo "Destination ${DST} already up-to-date."
  else
    echo "Copying ${SRC} -> ${DST}"
    cp -p "${SRC}" "${DST}"
  fi

  # Ensure executable bit for shell scripts and for windows batch mark them as
  # executable too (keeps parity for some packaging systems).
  case "${DST}" in
    *.sh) chmod +x "${DST}" || true ;;
    *.bat) chmod +x "${DST}" || true ;;
  esac
done

echo "Prepare-release complete."
