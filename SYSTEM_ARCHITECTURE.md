# OSCAR System Architecture (Based on OSH v3.3.1)

## Overview
OSCAR (Open Source Central Alarm Station) is a monitoring system for radiation portal monitors based on the OpenSensorHub (OSH) framework.

## Data Flow Diagram
![OSCAR System Data Flow](docs/system_data_flow.svg)

## Component Network Flow and Ports

### Components:
- **OSH Backend**: Java-based core application.
- **PostGIS Database**: PostgreSQL with PostGIS extensions for persistent storage.
- **Client Web UI**: React/Frontend viewer.
- **Tailscale Sidecar**: A dedicated container running Tailscale to manage the local mesh network, handling proxy egress/ingress.
- **MediaMTX Sidecar**: A dedicated RTSP media proxy sidecar running on the host network (`network_mode: "host"`) that intercepts raw IP camera streams and forwards them reliably to the OSH FFmpeg sensors. To ensure stability and security for 100 high-density streams, specific image versions are pinned (e.g., `1.17.1`), and configuration is explicitly loaded via the container `command` argument from the `mtx_secrets` volume at runtime. The configuration is optimized with an increased `writeQueueSize` (1024) and explicitly disabled unused protocols to minimize overhead.

## Hybrid Volume Architecture
To balance security and usability, the containerized OSCAR stack utilizes a hybrid volume strategy:
1. **Named Volumes (High Security)**: Highly sensitive files, such as the dynamically generated database password (`.db_password`), keystore passwords (`.app_secrets`), and Caddy internal state data are locked inside Docker Named Volumes (e.g., `oscar_secrets`, `caddy_data`). This ensures these secrets are abstracted from the host file system and handled entirely by the Docker daemon. To enforce the **Principle of Least Privilege**, a dedicated `mtx_secrets` volume is used to segment MediaMTX API credentials (`.mediamtx_api_user`, `.mediamtx_api_pass`) from high-sensitivity system secrets.
2. **Bind Mounts (Persistent Configuration & Data)**: Non-sensitive persistent data (like the PostGIS database records in `./pgdata`, the backend's configuration in `./osh-node-oscar/config`, and Tailscale state in `./tailscale`) are bound to the host filesystem. This ensures administrators have direct access to back up databases and manually tweak configuration files locally.

## Deployment Scenarios & Minimum System Requirements

OSCAR is designed to scale from edge devices to enterprise environments. You can configure your deployment by selecting a scenario in the `.env` file.

1. **Scenario A: "Edge Node" (1 Lane, All-in-One)**
   *   **Hardware**: Raspberry Pi (4GB-8GB RAM)
   *   **Setup**: Runs the complete stack (PostGIS, OSH, Proxy, Tailscale) locally.
2. **Scenario B: "Tactical Hub" (10 Lanes / 20 Cameras, All-in-One)** - **Default**
   *   **Hardware**: Powerful Laptop or Desktop (16GB RAM)
   *   **Setup**: Runs the complete stack locally with increased JVM and database memory limits.
3. **Scenario C: "Enterprise Central Hub" (50 Lanes / 100 Cameras, Distributed LAN)**
   *   **Hardware**: Machine 1 (App Server, 16GB RAM), Machine 2 (DB Server, 16GB RAM)
   *   **Setup**: Separates the database from the application backend over a high-speed LAN connection. See Deployment instructions below. **Native Ubuntu Deployments**: For high-density UDP streaming, Linux launch scripts automatically apply `sysctl` kernel tuning (UDP buffers, file limits) and `ufw` firewall rules to permit containerized backend communication with the host MediaMTX proxy (port 9997). Additionally, FFmpeg sensors utilize authenticated RTSP connection strings to communicate with the host-bound MediaMTX proxy.

### Default Port Configuration:
- **Caddy Reverse Proxy (within Tailscale namespace)**: Operates entirely inside the sidecar's networking context. It does not map ports to the host file directly, but dynamically secures ports `80` (HTTP) and `443` (HTTPS) over the mesh.
- **OSH Backend API (HTTP)**: `8282` (Bound to `127.0.0.1` locally, accessible externally via proxy)
- **OSH Backend Admin UI**: `8282` (Bound to `127.0.0.1` locally, accessible externally via proxy)
- **PostGIS Database**: `5432` (Internal Docker Network only)
- **MQTT Server (HiveMQ)**: WebSockets on `/mqtt` (via proxy)
- **MediaMTX API (HTTP)**: `9997` (Bound to host, internal REST control via `MEDIAMTX_IP` routed through the `host.docker.internal` gateway. Requires HTTP Basic Auth)
- **MediaMTX RTSP Server**: `8554` (Bound to host, intercepts camera streams)

### Network Flows:
- **Client to OSH**: Clients interact with OSH through its REST API and Web UI via the reverse proxy on ports `80` or `443` (or port `8282` locally). The client is now progressive web app (PWA) compatible and can be installed locally via a modern web browser.
- **Client Features**: The progressive web application contains specialized functionality such as offline caching, client-side WebID analysis, and camera integration for Spectroscopic QR Code scanning during Adjudication workflows.
- **OSH to PostGIS**: The OSH backend connects to the PostGIS database over the network (local or LAN) on port `5432`. This connection is secured via TLS and authenticated with SCRAM-SHA-256.
- **MQTT Connectivity**: The system maintains stable MQTT connections over WebSockets. Both the OSH backend and the reverse proxy are configured with an increased 10-minute idle timeout to prevent frequent disconnections in high-latency environments (e.g., Tailscale mesh). Additionally, the frontend MQTT client implements a proactive 15-second keepalive interval to ensure the connection remains active during periods of telemetry inactivity.
- **OSH Backend Hardening**: The OSH container utilizes a dedicated startup script (`start-osh.sh`) that disables shell globbing (`set -f`) before launching the JVM. This prevents the shell from unintentionally expanding wildcards in `$JAVA_OPTS` (such as `10.*` in proxy exclusion lists), ensuring network configuration integrity.
- **Certificate Management**: OSCAR operates entirely on a dynamic PEM-first cryptographic root. On first boot, the `init-secrets` container utilizes OpenSSL to generate a 20-year Root CA (`ca.pem`) and a 10-year server certificate (`server.pem`). To maintain ecosystem compatibility, these PEMs are natively bundled into PKCS12 and JKS formats and stored securely in the Docker `oscar_secrets` volume, eliminating the need to bake private keys into deployment images or rely on local Java generators.

## Deployment and Lifecycle Commands

### Main Launch Commands:
The stack is fully containerized using Docker Compose. Official Docker images (`oscar-backend`, `oscar-postgis`) are published to Docker Hub for every release.

There are two ways to launch the stack:
- **Online (Docker Hub)**: Download the `docker-compose.yml` and `.env.example` from the latest release and run `docker compose up -d`. This will automatically pull the pre-built images.
- **Offline (Source)**: Extract the full `.zip` release artifact containing the Dockerfiles and run `docker compose up -d` from the root directory to build the images locally. Launch scripts in `dist/release/` (e.g., `launch-all.sh`, `launch-all.bat`) are provided for backward compatibility.

### Distributed Enterprise Deployment (Scenario C)
To deploy the Enterprise Central Hub profile, you must split the components across two distinct machines on the same local network (LAN). **Important Initialization Logic**: The system relies on a unified, randomly generated database password. Because the application and database will be on separate machines, you must manually sync this secret.

**Machine 2 (Database Server)**
1. Copy the repository release files.
2. In `.env`, uncomment the **Scenario C: Database Server Profile**.
3. Generate a secure database password and save it locally:
   - Linux: `mkdir -p secrets && openssl rand -base64 32 > secrets/.db_password`
4. Use the standalone Database script to launch the container:
   - `cd dist/release/postgis`
   - `./run-postgis.sh` (or `run-postgis.bat` for Windows).
5. Ensure the machine's firewall allows incoming connections on port `5432` from Machine 1.

**Machine 1 (Application Server)**
1. Copy the repository release files.
2. In `.env`, uncomment the **Scenario C: Application Server Profile**.
3. Set the `DB_HOST` in `.env` to the IP address of Machine 2.
4. Securely copy the `secrets/.db_password` file generated on Machine 2 and place it in the same `secrets/` path on Machine 1. *This guarantees the `init-secrets` pre-flight container will use the matching credentials instead of generating a conflicting random password.*
5. Launch the application stack: `docker compose up -d`. This will start the OSH backend, Tailscale sidecar, and Caddy proxy without launching a local database.

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
