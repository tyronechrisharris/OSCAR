# OSCAR FLAT

This repository combines all the OSH modules and dependencies to deploy the OSH server and client for ORNL.

## Requirements
- [Java 21](https://www.oracle.com/java/technologies/downloads/#java21)
- [OSCAR Flat Repository](https://github.com/Botts-Innovative-Research/oscar-flat)
- Node v22
- [Docker](https://docs.docker.com/get-docker/) (Required to run the containerized OSCAR stack)

## Installation
Clone the repository:

```bash
git clone git@github.com:tyronechrisharris/oscar-flat.git
```
## Build 
Navigate to the project directory:

```bash
cd path/to/oscar-flat
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

## Deploy and Start OSH Node

There are two primary methods to deploy OSCAR depending on your internet connectivity and preference.

### Method 1: Online Deployment (Docker Hub)
For connected environments, you can run the stack directly using our pre-built Docker images hosted on Docker Hub. This is the fastest method.
1. Download `docker-compose.yml` and `.env.example` from the [Latest Release](https://github.com/tyronechrisharris/oscar-flat/releases).
2. Place both files in a new directory (e.g., `oscar-deployment/`).
3. Proceed to **Environment Setup** below.

### Method 2: Offline / Source Deployment
For air-gapped environments or local builds, you can use the complete distribution archive containing the Dockerfiles and launch scripts.
1. Unzip the distribution archive (`oscar-<version>.zip`) downloaded from the Releases page or built locally:

    Option 1: Command Line
    ```bash
    # Note: Replace <version> with the current version, e.g. 3.0.0
    unzip oscar-<version>.zip
    cd oscar-<version>
    ```
   ```bash
    # Note: Replace <version> with the current version, e.g. 3.0.0
    tar -xf oscar-<version>.zip
    cd oscar-<version>
    ```
   Option 2: Use File Explorer
    1. Right-click `oscar-<version>.zip` (where `<version>` is the current release version, e.g. `3.0.0`).
    2. Select **Extract All..**
    3. Choose your destination, (or leave the default) and extract.
    4. Navigate into the extracted `oscar-<version>` folder.
2. Proceed to **Environment Setup** below.

### Environment Setup
Before launching, copy `.env.example` to `.env`. The `.env` file contains critical scaling profiles. The default is **Scenario B (Tactical Hub)**. If your system requires a different scale (like the Edge Node or Enterprise Central Hub), uncomment the appropriate profile block. For Enterprise deployments spanning multiple machines, see `SYSTEM_ARCHITECTURE.md` for specific initialization logic.

### Tailscale Sidecar Setup
OSCAR uses a dedicated Tailscale sidecar architecture to safely expose the proxy to your Tailnet without requiring host-machine Tailscale daemons or complicated socket mounts.
- Provide a reusable or ephemeral Tailscale auth key in `.env` as `TS_AUTHKEY`.
- Set your static `TAILSCALE_DOMAIN` (e.g., `oscar-server.tailxxxxx.ts.net`) in the `.env` file to enable automatic Let's Encrypt certificates.

### Launch the Stack
The entire OSCAR stack (PostGIS, OSH Backend, Tailscale Sidecar, MediaMTX, and Caddy Reverse Proxy) is fully containerized. Ensure Docker is installed and run:

```bash
docker compose up -d
```
*Note for Offline Deployments: The legacy launch scripts (`launch-all.sh`, `launch-all.bat`, etc.) are still available inside the `dist/release/` directory of the zip archive for convenience.*

### Shutdown and Restart Procedures

To properly shut down and restart your OSCAR stack, you have a few different options depending on whether you just want to pause the system, rebuild it, or completely wipe it.

These exact terminal commands should be run from inside your `oscar` directory, regardless of whether you are using Windows PowerShell, Mac Terminal, or Linux Bash.

**Option 1: The "Soft" Stop and Start (Recommended for pausing)**
This is the fastest way to bring the system down and back up. It stops the containers exactly where they are without deleting them or removing them from Docker's internal network.

* **To shut down:**
    ```bash
    docker compose stop
    ```
* **To start back up:**
    ```bash
    docker compose start
    ```

**Option 2: The "Clean" Restart (Recommended for applying updates)**
If you make changes to your `docker-compose.yml` file, your `.env` file, or if you download a new version of your backend image, you must use this method. It safely deletes the containers and networks, but **keeps your data and certificates perfectly safe** in their volumes.

* **To shut down:**
    ```bash
    docker compose down
    ```
* **To start back up:**
    ```bash
    docker compose up -d
    ```

**Option 3: The "Nuclear" Reset (Data Wipe)**
Only use this if you want to completely factory reset the system. **This permanently deletes your database history, your saved passwords, and your generated SSL certificates.**

* **To wipe everything:**
    ```bash
    docker compose down -v
    ```
* **To boot fresh:**
    ```bash
    docker compose up -d
    ```

---

**A Pro-Tip for Monitoring the Restart:**
Whenever you run `docker compose up -d`, the `-d` flag runs everything in the background so you can keep using your terminal. If you want to watch the system boot up to make sure everything connects smoothly, run this command right after:

```bash
docker compose logs -f
```
*(Press `Ctrl + C` when you are done watching to exit the log view).*

### Access the OSCAR System
- Local/Remote Access (via Caddy Proxy): **https://localhost** or **http://localhost**
- Tailscale Access: **https://[your-tailscale-domain]**

**Important Note on Localhost TLS Warnings:**
When accessing `https://localhost` or a local IP (including raw Tailscale IPs without using your MagicDNS domain), you may see a "Not Secure" or "Your connection is not private" warning in your browser. This is expected behavior because the system uses auto-generated self-signed certificates for local encryption.
- **To resolve this warning locally:** You can optionally export and install the generated Root CA certificate (`ca.pem`) into your system or browser's Trust Store. This file is generated automatically upon first boot inside the secure `oscar_secrets` Docker volume.
- **To resolve this warning over Tailscale:** Always access the system using your fully qualified `TAILSCALE_DOMAIN` (configured in `.env`). The sidecar architecture allows the Caddy proxy to automatically fetch and apply a trusted Let's Encrypt certificate for that domain.

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
For production use, **Ubuntu Server 24.04.4 LTS** is the preferred and recommended operating system. Due to host-level network limitations on Windows and macOS, certain advanced features like automated MediaMTX path provisioning may require manual configuration or native host-executables. Docker on Linux provides the best security, performance, and automated orchestration for the OSCAR stack.

See the [Ubuntu Server Setup Guide](docs/ubuntu_setup.md) for detailed instructions.
