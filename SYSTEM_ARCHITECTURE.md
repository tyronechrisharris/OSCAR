# OSCAR System Architecture

## Overview
OSCAR (Open Source Central Alarm Station) is a monitoring system for radiation portal monitors based on the OpenSensorHub (OSH) framework.

## Data Flow Diagram
![OSCAR System Data Flow](docs/system_data_flow.svg)

## Component Network Flow and Ports

### Components:
- **OSH Backend**: Java-based core application.
- **PostGIS Database**: PostgreSQL with PostGIS extensions for persistent storage.
- **Client Web UI**: React/Frontend viewer.

### Default Port Configuration:
- **OSH Backend API (HTTP)**: `8282`
- **OSH Backend Admin UI**: `8282`
- **PostGIS Database**: `5432`
- **MQTT Server (HiveMQ)**: WebSockets on `/mqtt` (via proxy on port `8282`)

### Network Flows:
- **Client to OSH**: Clients interact with OSH through its REST API and Web UI on port `8282`.
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

## Database Utilities
Cross-platform scripts are provided in the repository root for maintenance:
- `backup.sh/bat`: Safely creates a database dump.
- `restore.sh/bat`: Restores the database from a dump.

These utilities respect the `DB_HOST` and `POSTGRES_PASSWORD_FILE` environment variables.

## Inbound Network Flow — Caddy TLS Termination (New)

### Overview
Caddy has been introduced as the TLS termination and reverse proxy in front of the OpenSensorHub (OSH) Java backend. Caddy is deployed as a container (`caddy`) on the same Docker network as `osh` and `postgis`. The OSH container no longer exposes its internal HTTP port (8282) to external networks — all traffic must go through Caddy on ports 80/443.

### Topology / Flow
1. **Federated mode (public / multi-site)**
   - Public Internet -> DNS resolves to host -> Caddy listens on 80/443 and obtains/renews certificates via Let’s Encrypt (ACME).
   - Caddy terminates TLS and reverse-proxies requests to `osh:8282` over the internal Docker network.
   - Caddy sets `X-Forwarded-For`, `X-Forwarded-Proto`, and `Host` to preserve client origin information for OSH logging and alarm origin attribution.

2. **Offline mode (air-gapped / private)**
   - Public or local clients -> Caddy listens on 443 and uses a leaf certificate provided by the Ephemeral CA (Issue #2).
   - Caddy TLS configuration uses the locally-mounted certificate and key (expected under `/certs`) and reverse-proxies to `osh:8282`.
   - HTTP (port 80) is redirected to HTTPS (port 443). As above, `X-Forwarded-*` headers are forwarded.

### Caddyfile routing logic
* `Caddyfile.fed`
  * Site block for `${DOMAIN}` (set at runtime). Uses Caddy’s default ACME provider (Let’s Encrypt) to obtain certs.
  * Reverse proxy to `osh:8282`:
    * `header_up X-Forwarded-For {remote_host}`
    * `header_up X-Forwarded-Proto {scheme}`
    * `header_up Host {host}`

* `Caddyfile.offline`
  * `:80` redirects to `https://{host}{uri}`.
  * `:443` uses `tls /certs/fullchain.pem /certs/privkey.pem` (or the equivalent filenames produced by the Ephemeral CA).
  * Reverse proxy to `osh:8282` with the same `X-Forwarded-*` headers as above.

### Security / Network controls
* OSH does not publish port `8282` to the host. It is only available via Docker network `oshnet`. This enforces that all external requests are routed and TLS-terminated by Caddy.
* The Caddy container is the only external ingress (ports 80/443). Ensure host firewall / security groups reflect this.
