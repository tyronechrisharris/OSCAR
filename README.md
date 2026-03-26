# OSH OAKRIDGE BUILDNODE

This repository combines all the OSH modules and dependencies to deploy the OSH server and client for ORNL using a fully containerized architecture.

## Requirements
- [Java 21](https://www.oracle.com/java/technologies/downloads/#java21) (For local development/builds)
- [Oakridge Build Node Repository](https://github.com/Botts-Innovative-Research/osh-oakridge-buildnode) 
- Node v22 (For local development/builds)
- [Docker](https://docs.docker.com/get-docker/) & [Docker Compose](https://docs.docker.com/compose/install/) (Required for deployment)

## Installation
Clone the repository and update all submodules recursively

```bash
git clone git@github.com:Botts-Innovative-Research/osh-oakridge-buildnode.git --recursive
```
If you've already cloned without `--recursive`, run:
```bash
cd path/to/osh-oakridge-buildnode
git submodule update --init --recursive
```
## Build 
Navigate to the project directory:

```bash
cd path/to/osh-oakridge-buildnode
```

Run the build script (macOS/Linux):

```bash
./build-all.sh
```

Run the build script (Windows):

```bash
./build-all.bat
```

After the build completes, the distribution artifacts are prepared in the repository root and `dist/release/`.

## Deploy and Start OSCAR Stack
The OSCAR stack (PostGIS, OSH Backend, and Caddy Proxy) is orchestrated using Docker Compose.

1. **Configure Scaling (Optional)**:
   Copy `.env.template` to `.env` and uncomment the profile that matches your hardware:
   ```bash
   cp .env.template .env
   # Edit .env to select your profile
   ```

2. **Launch the Stack**:
   Run the unified launch script from `dist/release/`:
   - **Linux/macOS**: `./dist/release/launch-all.sh`
   - **ARM64 (Apple Silicon/Raspberry Pi)**: `./dist/release/launch-all-arm.sh`
   - **Windows**: `dist\release\launch-all.bat`

   These scripts initialize the database password secret (`.db_password`) and start all services in the background.

3. **Access OSCAR**:
   The system is accessible via the Caddy reverse proxy:
   - **Local LAN/Localhost**: `https://localhost` or `https://[ip-address]` (Uses auto-generated Java certificates)
   - **Tailscale**: `https://[your-tailscale-domain]` (If `TAILSCALE_DOMAIN` is configured in `.env`)

   *Note: The OSH backend on port 8282 is restricted to localhost access only for security.*

### First-Time Setup
On first boot, OSCAR enters an **Uninitialized State** and requires configuration via a Setup Wizard.
1. Navigate to `https://localhost/`.
2. You will be automatically redirected to the **Setup Wizard**.
3. **Create an Admin Password**: Set a strong password for the `admin` account.
4. **Configure TOTP**:
   - Scan the displayed QR code with an authenticator app (Google Authenticator, Authy, etc.).
   - **Important**: Save the secret key shown in the wizard!
   - Use the **Test Code** form to verify your setup before proceeding.
5. Once complete, you will be redirected to the Admin UI login.

### Logging In
After initialization, use the following credentials:
- **Username**: `admin`
- **Password**: The password you set during the Setup Wizard.
- **Two-Factor Authentication**:
  - Provide the 6-digit TOTP code when prompted.

## Progressive Web App (PWA)
The OSCAR Viewer can now be installed as a Progressive Web App (PWA) on compatible devices (mobile, tablet, and desktop).
To install it:
1. Navigate to the OSCAR Viewer in a supported browser.
2. Look for the "Install App" or "Add to Home Screen" option in the browser menu.

## WebID Analysis and Spectroscopic QR Scanning
The OSCAR Viewer features integrated Spectroscopic QR Code scanning for WebID analysis in Adjudication workflows.
- Users can scan spectroscopic QR codes via their device camera.
- Results are displayed in the **WebID Analysis Results Log**.
- All UI elements are localized.

## Security and Architecture
- [Security Architecture](SECURITY_ARCHITECTURE.md)
- [System Architecture](SYSTEM_ARCHITECTURE.md)
- [Federation Provisioning (API Keys)](docs/FEDERATION_PROVISIONING.md)
- [Tailscale Security and Configuration](docs/TAILSCALE_CONFIGURATION.md)

# Release Checklist
- Version in `build.gradle`
- Version in `dist/config/standard/config.json`
- Ensure `.db_password` is not committed.
- Build with `./build-all.sh` or `./build-all.bat`
