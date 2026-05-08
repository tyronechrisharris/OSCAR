# OSCAR

This repository combines all the OSH modules and dependencies to deploy the OSH server and client for ORNL.

## Quick Start Guide
The easiest way to begin using OSCAR is to use the pre-built release assets.

1. **Ensure Docker is installed and running** on your system.
2. Navigate to the **Latest Release** on GitHub.
3. Download and extract **`oscar.zip`**. This bundle contains all the necessary deployment scripts and configuration files, including:
   - `docker-compose.yml`
   - `.env.example`
   - `launch-lan.sh` / `launch-lan.bat`
   - `launch-mesh.sh` / `launch-mesh.bat`
   - `stop-all.sh` / `stop-all.bat`
4. Rename `.env.example` to `.env`.
   - **If using Mesh:** Open `.env` and configure `TS_AUTHKEY` with your Tailscale auth key, and `TAILSCALE_DOMAIN` with your static Tailnet URL.
5. Launch the system by running the launch script that matches your desired deployment type (e.g., `./launch-mesh.sh` for Mesh or `./launch-lan.sh` for LAN-only).
7. Access the system:
   - **LAN:** Open `https://localhost` or `http://localhost` (or the machine IP) in your browser.
   - **Mesh:** Open `https://[your-tailscale-domain]` in your browser.
8. **Stopping the system:** To safely power down the stack without losing any configurations, database records, or credentials, follow the [Safe Stop and Restart SOP](docs/SAFE_RESTART_SOP.md).

## Requirements
- [Java 21](https://www.oracle.com/java/technologies/downloads/#java21)
- [OSCAR Repository](https://github.com/tyronechrisharris/OSCAR)
- Node v22
- [Docker](https://docs.docker.com/get-docker/) (Required to run the containerized OSCAR stack)

## Installation
Clone the repository:

```bash
git clone git@github.com:tyronechrisharris/OSCAR.git
```
## Build 
Navigate to the project directory:

```bash
cd path/to/OSCAR
```

Run the build script (macOS/Linux):

```bash
./build-all.sh
```

Run the build script (Windows):

```bash
./build-all.bat
```

After the build completes, it can be located in `build/distributions/` 

### First-Time Setup
On first boot, OSCAR enters an **Uninitialized State** and requires configuration via a Setup Wizard.
1. Navigate to `https://localhost/sensorhub/admin`.
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
  - If your browser or client supports it, enter your password as usual and provide the 6-digit TOTP code when prompted.
  - If you are prompted for a single login by the browser and can't provide a TOTP code separately, enter your password followed by a colon and the code (e.g., `mypassword:123456`).

**Language Selection**
The user can select different languages for the Admin UI by using the language drop-down menu located in the top right corner of the Admin UI toolbar. Selecting a new language will instantly switch the UI localization.

**Two-Factor Authentication (2FA)**
2FA is mandatory for the administrator account and can be configured for other users to add an extra layer of security. To set this up for additional users:
1. Log in to the Admin UI as `admin`.
2. Navigate to the **Security** section.
3. Edit a user profile and set up Two-Factor Authentication. A popup window will appear with a QR code generated locally on the server.
4. Scan the QR code with an authenticator app (like Google Authenticator or Authy) to complete the setup.

**Importing/Exporting Lane Configurations via CSV**
Configurations for Lane Systems can be bulk managed via spreadsheet (CSV).
1. Log in to the Admin UI.
2. Navigate to **Services -> OSCAR Service**.
3. Within the configuration form for the OSCAR service, locate the property for spreadsheet configuration (`spreadsheetConfigPath`).
4. To export, click the download button to retrieve the current configurations as a CSV file.
5. To import, upload your modified CSV file through the provided upload mechanism in the service configuration to apply new or updated lane setups.

For documentation on configuring a Lane System on the OSH Admin panel, please refer to the OSCAR Documentation provided in the Google Drive documentation folder.


## Progressive Web App (PWA)
The OSCAR Viewer can now be installed as a Progressive Web App (PWA) on compatible devices (mobile, tablet, and desktop) for offline-capable or app-like experiences.
To install it:
1. Navigate to the OSCAR Viewer in a supported browser (e.g., Chrome, Safari).
2. Look for the "Install App" or "Add to Home Screen" option in the browser menu.

## WebID Analysis and Spectroscopic QR Scanning
The OSCAR Viewer now features integrated Spectroscopic QR Code scanning for WebID analysis in the Adjudication workflows.
- During an adjudication, users can open the **QR Scanner** to scan spectroscopic QR codes via their device camera.
- Scanned items can be configured with a Detector Response Function (DRF) or used to synthesize background data.
- The system parses the scanned QR code to perform WebID Analysis, displaying results in the **WebID Analysis Results Log** within the adjudication panel.
- All WebID UI elements are localized and adapt to the user's selected language.

## Deploy the Client
After configuring the Lanes on the OSH Admin Panel, you can navigate to the Clients endpoint:
- Remote: **https://[ip-address]** or **http://[ip-address]**
- Local: **https://localhost** or **http://localhost**

For documentation on configuring a server on the OSCAR Client refer to the OSCAR Documentation provided in the Google Drive documentation folder. 

## Security and Federation
- [Security Architecture](SECURITY_ARCHITECTURE.md)
- [System Architecture](SYSTEM_ARCHITECTURE.md)
- [Federation Provisioning (API Keys)](docs/FEDERATION_PROVISIONING.md)
- [Tailscale Security and Configuration](docs/TAILSCALE_CONFIGURATION.md)

# Release Checklist
- Version in `build.gradle`
- Version in `dist/config/standard/config.json`
- Make sure no `pgdata` in `dist/release/postgis`
- Build with `./build-all.sh` or `./build-all.bat`

## Preferred Deployment Environment
For production use, **Ubuntu Server 24.04.4 LTS** is the preferred and recommended operating system. Docker on Linux provides the best security, performance, and automated orchestration for the OSCAR stack. Due to host-level network limitations on Windows and macOS, the deployment scripts (`launch-all.bat` and `launch-all.sh`) automatically download and manage a native `mediamtx` host process to bypass Docker Desktop networking constraints, allowing automated MediaMTX path provisioning to function identically to Linux.

See the [Ubuntu Server Setup Guide](docs/ubuntu_setup.md) for detailed instructions.
