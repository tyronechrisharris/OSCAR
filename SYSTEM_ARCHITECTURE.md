# OSCAR System Architecture

## Overview
OSCAR (Open Source Central Alarm Station) is a monitoring system for radiation portal monitors based on the OpenSensorHub (OSH) framework. The system is deployed as a fully containerized stack using Docker Compose.

## Data Flow Diagram
![OSCAR System Data Flow](docs/system_data_flow.svg)

## Unified Container Orchestration

The OSCAR stack consists of three core services managed via `docker-compose.yml`:

| Service | Container Name | Role | Ports |
| :--- | :--- | :--- | :--- |
| **osh-postgis** | `oscar-postgis-container` | Persistent storage with PostGIS extensions. | 5432 (Internal only) |
| **osh-backend** | `oscar-backend-container` | OSH Java application core and API. | 8282 (Restricted to 127.0.0.1) |
| **osh-proxy** | `oscar-proxy-container` | Caddy reverse proxy for TLS termination. | 80/443 (Public/LAN) |

### Network Isolation and Security
- **Internal Network**: All services communicate over the `osh-internal` bridge network.
- **Port Masking**: The OSH Backend (port 8282) is bound specifically to `127.0.0.1` on the host, ensuring it is only reachable via the Caddy proxy or local debug tools. The PostGIS port (5432) is not exposed externally.
- **Secret Management**: Database passwords are managed as Docker secrets (`.db_password`). Application secrets are persisted in `.app_secrets`.

## Component Network Flow and Ports

### Network Flows:
- **Client to Proxy**: Clients interact with OSCAR via HTTPS (port 443) through the Caddy reverse proxy. The proxy handles TLS termination and forwards traffic to the OSH backend.
- **Proxy to OSH**: Caddy forwards traffic to `osh-backend:8282` over the internal Docker network.
- **OSH to PostGIS**: The OSH backend connects to the `osh-postgis` service over the internal network on port `5432`. This connection is secured via TLS and authenticated with SCRAM-SHA-256.
- **Certificate Management**: OSH manages its own internal PKI for signing leaf certificates used for local LAN encryption. Caddy is configured to use these local certificates for the LAN/Localhost listener and Tailscale certificates for federated access.

## Deployment and Lifecycle Commands

### Main Launch Scripts:
Located in `dist/release/`:
- `launch-all.sh`: Orchestrates `docker compose up -d` (Linux/macOS).
- `launch-all-arm.sh`: Orchestrates `docker compose up -d` for ARM64 platforms (Apple Silicon/Raspberry Pi).
- `launch-all.bat`: Orchestrates `docker compose up -d` for Windows environments.

### Scaling Profiles
Scaling for different hardware scenarios is managed via environment variables in the `.env` file. Three standard profiles are provided in `.env.template`:
- **Edge Node**: Optimized for Raspberry Pi (4GB-8GB RAM).
- **Tactical Hub**: Optimized for powerful laptops (16GB RAM).
- **Enterprise Central Hub**: Optimized for distributed server environments.

## Automated Provisioning Utilities:
Located in the repository root:
- `provision-node.sh`: Securely pushes an API key to a remote node via Tailscale.
- `provision-node.bat`: Securely pushes an API key to a remote node via Tailscale.

See [Federation Provisioning](docs/FEDERATION_PROVISIONING.md) and [Tailscale Configuration](docs/TAILSCALE_CONFIGURATION.md) for detailed setup and usage instructions.

## Database Utilities
Cross-platform scripts are provided in the repository root for maintenance:
- `backup.sh/bat`: Safely creates a database dump via `docker exec`.
- `restore.sh/bat`: Restores the database from a dump via `docker exec`.

These utilities respect the `POSTGRES_PASSWORD_FILE` and unified container naming conventions.
