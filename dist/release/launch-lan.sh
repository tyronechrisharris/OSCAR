#!/bin/bash

# Ensure Docker is installed
if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not installed. Please install Docker first."
    # Fail
    sh -c "exit 1"
fi

# Optimize kernel parameters for high-density UDP camera streaming (Linux only)
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    echo "Optimizing kernel parameters for high-density UDP streaming..."
    sudo sysctl -w net.core.rmem_max=26214400
    sudo sysctl -w net.core.rmem_default=26214400
    sudo sysctl -w net.core.wmem_max=26214400
    sudo sysctl -w net.core.wmem_default=26214400
    sudo sysctl -w fs.file-max=1000000
fi

# Pre-flight check: ensure hivemq-config directory and logback.xml exist
# This prevents Docker from creating logback.xml as a directory during volume mount.
if [ ! -d "hivemq-config" ]; then
    echo "Creating hivemq-config directory..."
    mkdir -p hivemq-config
fi

if [ ! -f "hivemq-config/logback.xml" ]; then
    echo "Creating hivemq-config/logback.xml..."
    cat << 'INNER_EOF' > hivemq-config/logback.xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- general logging in console -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{0} [%thread] - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>

    <!-- Suppress verbose auth/session debug logs -->
    <logger name="org.sensorhub.impl.service.BridgedAuthenticator" level="WARN" />
    <logger name="org.sensorhub.impl.service.OshLoginService" level="WARN" />

</configuration>
INNER_EOF
fi

# 1. Native MediaMTX check for macOS
if [[ "$OSTYPE" == "darwin"* ]]; then
    VERSION="v1.17.1"
    TARGET_DIR="$(pwd)/mediamtx_${VERSION}"
    EXE_NAME="mediamtx"
    FULL_PATH="${TARGET_DIR}/${EXE_NAME}"

    if [ -f "$FULL_PATH" ]; then
        echo "MediaMTX Version $VERSION found."
    else
        echo "Downloading MediaMTX $VERSION for macOS..."
        mkdir -p "$TARGET_DIR"

        ARCH=$(uname -m)
        if [ "$ARCH" = "arm64" ]; then
            MTX_ARCH="darwin_arm64"
        else
            MTX_ARCH="darwin_amd64"
        fi

        URL="https://github.com/bluenviron/mediamtx/releases/download/${VERSION}/mediamtx_${VERSION}_${MTX_ARCH}.tar.gz"
        TMP_TAR="/tmp/mediamtx.tar.gz"

        curl -L -o "$TMP_TAR" "$URL"
        tar -xzf "$TMP_TAR" -C "$TARGET_DIR"
        rm "$TMP_TAR"
    fi

    echo "Generating MediaMTX configuration..."
    mkdir -p "${TARGET_DIR}/mtx-secrets"

    cat << 'INNER_EOF_MTX' > "${TARGET_DIR}/mtx-secrets/mediamtx.yml"
# MediaMTX Configuration for OSCAR (Auto-Generated macOS)
api: true
apiAddress: :9997
writeQueueSize: 1024

# Protocols (Optimized for high density)
rtmp: false
hls: false
webrtc: false
srt: false

pathDefaults:
  source: publisher

# Path Settings
paths:
  all_others:
INNER_EOF_MTX

    echo "Launching MediaMTX $VERSION in background..."
    "$FULL_PATH" "${TARGET_DIR}/mtx-secrets/mediamtx.yml" > /dev/null 2>&1 &
fi

# 2. Launch Docker Compose FIRST so the networks are actually created
echo "Launching fully containerized OSCAR Stack via Docker Compose..."
export PROXY_OPTS=""
COMPOSE_PROFILES=lan-only docker compose up -d || exit 1

# 3. Configure the firewall dynamically
if [[ "$OSTYPE" == "linux-gnu"* ]] && command -v ufw >/dev/null 2>&1; then
    echo "Configuring firewall for MediaMTX API access..."

    # Dynamically find the exact network name attached to the backend container
    NETWORK_NAME=$(docker inspect oscar-backend-container -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' | tr -d '\r\n')

    if [ -n "$NETWORK_NAME" ]; then
        # Extract the subnet and aggressively strip any hidden Windows carriage returns
        DOCKER_SUBNET=$(docker network inspect "$NETWORK_NAME" -f '{{(index .IPAM.Config 0).Subnet}}' | tr -d '\r\n')

        if [ -n "$DOCKER_SUBNET" ]; then
            echo "Whitelisting Docker subnet: $DOCKER_SUBNET"
            sudo ufw allow from "$DOCKER_SUBNET" to any port 9997 proto tcp && sudo ufw allow from "$DOCKER_SUBNET" to any port 8554 proto tcp
            sudo ufw reload
        else
            echo "Warning: Could not determine Docker subnet. Skipping UFW configuration."
        fi
    else
        echo "Warning: Could not find backend container network. Skipping UFW configuration."
    fi
fi


echo "OSCAR Stack is launching. Please wait a few moments for the database and backend to initialize."
echo "Access the system at: http://localhost or https://localhost"