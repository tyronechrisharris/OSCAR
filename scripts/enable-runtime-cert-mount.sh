#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(pwd)"
OSH_DIR="${REPO_ROOT}/osh-node-oscar"
DOCKERFILE_OSH="${OSH_DIR}/Dockerfile.osh"
LAUNCH_SH="${OSH_DIR}/launch.sh"
TRUST_DIR="${OSH_DIR}/trusted_certificates"

echo "Repository root: ${REPO_ROOT}"
echo "OSH dir: ${OSH_DIR}"
echo

# 1) Ensure osh-node-oscar exists
if [ ! -d "${OSH_DIR}" ]; then
  echo "ERROR: ${OSH_DIR} not found. Run this script from the repo root and make sure osh-node-oscar exists."
  exit 1
fi

# 2) Remove any Dockerfile COPY lines that mention trusted_certificates
if [ -f "${DOCKERFILE_OSH}" ]; then
  echo "Backing up ${DOCKERFILE_OSH} -> ${DOCKERFILE_OSH}.bak"
  cp "${DOCKERFILE_OSH}" "${DOCKERFILE_OSH}.bak"

  # Remove lines containing 'trusted_certificates' (safe: removes COPY lines that reference it)
  perl -0777 -pe 's/^\s*COPY\s+.*trusted_certificates.*\n//mg' "${DOCKERFILE_OSH}.bak" > "${DOCKERFILE_OSH}"

  echo "Removed lines with 'trusted_certificates' from ${DOCKERFILE_OSH} (see .bak for original)."
else
  echo "WARNING: ${DOCKERFILE_OSH} not present; skipping Dockerfile edit."
fi

# 3) Ensure the trusted_certificates folder exists and has at least a placeholder
if [ ! -d "${TRUST_DIR}" ]; then
  echo "Creating trusted_certificates dir: ${TRUST_DIR}"
  mkdir -p "${TRUST_DIR}"
fi

if [ -z "$(ls -A "${TRUST_DIR}" 2>/dev/null || true)" ]; then
  echo "trusted_certificates is empty; creating .placeholder file"
  touch "${TRUST_DIR}/.placeholder"
fi

# 4) Update osh-node-oscar/launch.sh:
#    - Insert OSH_TRUST_DIR / OSH_TRUST_VOL variables after PROJECT_DIR if present
#    - Insert a -v "$OSH_TRUST_VOL" \ line after the first `docker run` line
if [ -f "${LAUNCH_SH}" ]; then
  echo "Backing up ${LAUNCH_SH} -> ${LAUNCH_SH}.bak"
  cp "${LAUNCH_SH}" "${LAUNCH_SH}.bak"

  # If the script already contains OSH_TRUST_DIR, skip variable insertion
  if ! grep -q "OSH_TRUST_DIR" "${LAUNCH_SH}"; then
    # Insert variables after the PROJECT_DIR declaration if it exists; otherwise insert after shebang
    if grep -q "PROJECT_DIR" "${LAUNCH_SH}"; then
      awk -v TRUST_DIR="${TRUST_DIR}" '
      BEGIN { inserted=0 }
      {
        print $0
        if (!inserted && $0 ~ /PROJECT_DIR/) {
          print ""
          print "# Runtime trusted certs volume (added by enable-runtime-cert-mount.sh)"
          print "OSH_TRUST_DIR=\"'"${TRUST_DIR}"'\""
          print "OSH_TRUST_VOL=\"${OSH_TRUST_DIR}:/app/trusted_certificates:ro\""
          print ""
          inserted=1
        }
      }
      ' "${LAUNCH_SH}.bak" > "${LAUNCH_SH}"
    else
      # No PROJECT_DIR line; add variables after first non-shebang line
      awk -v TRUST_DIR="${TRUST_DIR}" '
      BEGIN { printed=0 }
      NR==1 { print $0; next }
      {
        if (!printed) {
          print ""
          print "# Runtime trusted certs volume (added by enable-runtime-cert-mount.sh)"
          print "OSH_TRUST_DIR=\"'"${TRUST_DIR}"'\""
          print "OSH_TRUST_VOL=\"${OSH_TRUST_DIR}:/app/trusted_certificates:ro\""
          print ""
          printed=1
        }
        print $0
      }
      ' "${LAUNCH_SH}.bak" > "${LAUNCH_SH}"
    fi
    echo "Inserted OSH_TRUST_DIR/OSH_TRUST_VOL variables into ${LAUNCH_SH}."
  else
    echo "Found OSH_TRUST_DIR already in ${LAUNCH_SH}; skipping variable insertion."
    cp "${LAUNCH_SH}.bak" "${LAUNCH_SH}"
  fi

  # Now ensure the docker run invocation gets the -v "$OSH_TRUST_VOL" flag.
  # We'll inject the -v line after the first occurrence of a line containing 'docker run'
  # but only if the -v for the trusted volume does not already exist.
  if grep -q "\$OSH_TRUST_VOL" "${LAUNCH_SH}" || grep -q "/app/trusted_certificates" "${LAUNCH_SH}"; then
    echo "Launch script already contains a mount for trusted_certificates; skipping docker run edit."
  else
    awk '
    BEGIN { done=0 }
    {
      print $0
      if (!done && $0 ~ /docker[[:space:]]+run/) {
        # If the docker run line ends with a backslash, preserve style and add -v in following line
        # Insert the mount line
        print "  -v \"${OSH_TRUST_VOL}\" \\"
        done=1
      }
    }
    ' "${LAUNCH_SH}" > "${LAUNCH_SH}.tmp" && mv "${LAUNCH_SH}.tmp" "${LAUNCH_SH}"
    echo "Injected -v \"\${OSH_TRUST_VOL}\" into docker run in ${LAUNCH_SH}."
  fi

  # Make file executable (if it isn't already)
  chmod +x "${LAUNCH_SH}"
else
  echo "WARNING: ${LAUNCH_SH} not present; skipping launch script update (you will need to add the runtime mount manually)."
fi

echo
echo "DONE. Files modified (backups saved with .bak):"
[ -f "${DOCKERFILE_OSH}.bak" ] && echo " - ${DOCKERFILE_OSH} (backup: ${DOCKERFILE_OSH}.bak)"
[ -f "${LAUNCH_SH}.bak" ] && echo " - ${LAUNCH_SH} (backup: ${LAUNCH_SH}.bak)"
echo " - ensured ${TRUST_DIR} exists and contains at least a placeholder"
echo
echo "Please inspect the modified files, run your build (./dist/release/launch-all-arm.sh or your usual command),"
echo "and if everything looks good, commit the changes:"
echo
echo "  git add ${DOCKERFILE_OSH} ${LAUNCH_SH} osh-node-oscar/trusted_certificates/.placeholder"
echo "  git commit -m \"Runtime-mount trusted_certificates into OSH container; remove COPY from Dockerfile.osh\""
echo "  git push"
echo
