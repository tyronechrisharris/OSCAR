# OSCAR System Architecture

## Overview
OSCAR (Open Source Central Alarm Station) is a monitoring system for radiation portal monitors based on the OpenSensorHub (OSH) framework.

## Data Flow Diagram
![OSCAR System Data Flow](docs/system_data_flow.svg)

## Component Network Flow and Ports

### Components:
- **OSH Proxy**: Caddy-based reverse proxy for external TLS termination (Tailscale/Federated/Offline).
- **OSH Backend**: Java-based core application (runs on the host OS).
- **PostGIS Database**: PostgreSQL with PostGIS extensions for persistent storage.
- **Client Web UI**: React/Frontend viewer.

### Default Port Configuration:
- **External API/UI (HTTPS/HTTP)**: `443`, `80` (via Caddy)
- **OSH Backend API (Internal TLS)**: `8282` (Proxy connects to host.docker.internal:8282)
- **PostGIS Database**: `5432`
- **MQTT Server (HiveMQ)**: WebSockets on `/mqtt` (via proxy on port `8282`)

### Network Flows:
- **Client to OSH Proxy**: Clients interact with the Caddy proxy on port `443` (HTTPS) or `80` (HTTP). Caddy terminates the external TLS (e.g., Let's Encrypt via Tailscale) and provides the "green padlock" in browsers.
- **Proxy to OSH Backend**: Caddy decrypts the external request and re-encrypts it using the internal Ephemeral CA before forwarding it to the OSH backend on the host OS at `https://host.docker.internal:8282`. This ensures two layers of encryption and protects data even when traversing from the Docker network to the host OS. Caddy is configured with `tls_insecure_skip_verify` for this internal hop.
- **OSH to PostGIS**: The OSH backend connects to the PostGIS database over the network (local or LAN) on port `5432`. This connection is secured via TLS and authenticated with SCRAM-SHA-256.

## Deployment and Lifecycle Commands

### Main Launch Scripts:
Located in `dist/release/`:
- `launch-all.sh`: Starts the PostGIS container and the OSH backend (Linux/macOS).
- `launch-all-arm.sh`: Starts the PostGIS container and the OSH backend (ARM64, e.g., Mac M1/M2/M3).
- `launch-all.bat`: Starts the PostGIS container and the OSH backend (Windows).

### Automated Provisioning Utilities:
Located in the repository root:
- `provision-node.sh`: Securely pushes an API key to a remote node via Tailscale (Unix/Linux/macOS).
- `provision-node.bat`: Securely pushes an API key to a remote node via Tailscale (Windows).

See [Federation Provisioning](docs/FEDERATION_PROVISIONING.md) and [Tailscale Configuration](docs/TAILSCALE_CONFIGURATION.md) for detailed setup and usage instructions.

### Standalone Database Scripts:
Located in `dist/release/postgis/`:
- `run-postgis.sh`: Starts the PostGIS container independently (Linux/macOS).
- `run-postgis-arm.sh`: Starts the PostGIS container independently (ARM64).
- `run-postgis.bat`: Starts the PostGIS container independently (Windows).

### Standalone Proxy Scripts:
Located in `dist/release/proxy/`:
- `docker-compose up -d`: Starts the Caddy-based TLS termination proxy.

## Database Utilities
Cross-platform scripts are provided in the repository root for maintenance:
- `backup.sh/bat`: Safely creates a database dump.
- `restore.sh/bat`: Restores the database from a dump.

These utilities respect the `DB_HOST` and `POSTGRES_PASSWORD_FILE` environment variables.
