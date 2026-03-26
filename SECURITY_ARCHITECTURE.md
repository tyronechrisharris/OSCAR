# OSCAR Security Hardening Architecture

**Critical Domain Context:**
This project is an Open Source Central Alarm Station (OSCAR) monitoring radiation portal monitors. The application runs cross-platform on Windows, macOS, and Linux. The primary critical threat is the unauthorized suppression, modification, or spoofing of alarms. Note this specific nomenclature:
* **G Alarm:** Gamma Alarm.
* **N Alarm:** Neutron Alarm.
* **G-N:** Gamma Neutron Alarm.

**OpenSensorHub (OSH) Ecosystem Constraint:**
OSCAR is built on the OpenSensorHub framework. **Under no circumstances may any code modifications break compatibility with the larger OSH ecosystem.** * Standard OGC SWE, SOS, and SPS API endpoints must remain fully compliant.
* Sensor drivers (e.g., video processing, hardware interfaces mapped in `config.csv`) must not be prevented from initializing or communicating.
* Machine-to-machine API routes cannot rely on human-interactive authentication (like 302 redirects to a TOTP login).

**Global Build Constraint:**
Whenever generating or modifying Dockerfiles for this project, you MUST ensure the font package is explicitly set to `fonts-freefont-ttf`. This is strictly required to prevent downstream rendering failures in the application's graphical reporting components.

## Containerized Infrastructure Security

OSCAR uses a multi-layered container security model orchestrated by Docker Compose.

### Network Isolation
- **Service Segregation**: Services (PostGIS, OSH Backend, Caddy) reside on a private bridge network (`osh-internal`).
- **Internal-Only Services**: The PostGIS database is not exposed to the host network.
- **Port Binding Enforcement**: The OSH Backend (port 8282) is bound strictly to `127.0.0.1` on the host, ensuring all external traffic must pass through the Caddy Reverse Proxy.

### Reverse Proxy and TLS Termination
Caddy provides a secure gateway to the OSCAR stack:
- **TLS Termination**: Caddy handles all external HTTPS traffic (port 443).
- **Dynamic TLS Switching**:
  - **Local LAN**: Uses auto-generated Java certificates (`osh-leaf.crt/key`) signed by the OSCAR Root CA.
  - **Tailscale**: Integrated with Tailscale's automatic certificate management (`get_certificate tailscale`).
- **Header Forwarding**: Standard headers (`X-Forwarded-For`, `X-Forwarded-Proto`, etc.) are forwarded to the OSH backend for accurate audit logging.

### Database Security Implementation
- **SCRAM-SHA-256 Authentication**: PostgreSQL enforces `scram-sha-256` for all users.
- **Secret Management**: Database passwords are managed as Docker Secrets (`.db_password`). The `POSTGRES_PASSWORD_FILE` environment variable is used to point to the secret path.
- **TLS Enforcement**: All internal connections between the OSH backend and PostGIS are secured via TLS with `sslmode=require`.

## Application-Level Security Hardening

### Persistent Local CA and TLS Certificates
On first boot, the system generates a persistent Root CA and a leaf TLS certificate.
- **Root CA Private Key**: Securely stored within the PKCS12 keystore (`osh-keystore.p12`) under the alias `root-ca`.
- **Automated Renewal**: The system automatically renews the Leaf certificate if it is within 30 days of expiration.
- **Key Storage Security**: The keystore password is stored in a hidden `.app_secrets` file, protected by POSIX permissions or ACLs. A "fail-secure" policy halts the application if this file is missing.

### Setup Wizard and Credential Management
The system does not ship with default administrative credentials.
- **Uninitialized State**: New deployments enter an uninitialized state, redirecting all human traffic to a Setup Wizard.
- **Bifurcated Authentication**:
  - **Human UI Routes**: Require session-based login with username, password, and TOTP 2FA.
  - **Machine API Routes**: Secured via PBKDF2-hashed API Keys passed as Bearer tokens.
- **Secure Provisioning**: Automated utilities (`provision-node.sh/bat`) are provided for secure key distribution via Tailscale.

See [Federation Provisioning](docs/FEDERATION_PROVISIONING.md) and [Tailscale Configuration](docs/TAILSCALE_CONFIGURATION.md) for detailed instructions.
