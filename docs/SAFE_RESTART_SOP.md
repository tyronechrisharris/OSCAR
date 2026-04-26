**SOP: Safe Stop and Restart of OSCAR Without Losing Credentials or Data**

**Purpose**
Safely stop and restart OSCAR without losing credentials, certificates, database contents, Tailscale identity, or other persistent data. This applies to both **mesh** and **LAN-only** deployments.

**Scope**
Use this procedure for routine maintenance, reboots, and configuration/image updates.
Do **not** use this procedure for factory reset operations.

**Persistent data protected by this SOP**
OSCAR persists secrets and runtime state in named volumes and bind mounts, including:

* admin/security configuration and 2FA state
* database data
* TLS certificates
* Tailscale state for mesh deployments

**Procedure**

**1. Go to the OSCAR deployment directory**
Run all commands from the directory containing `docker-compose.yml` and `.env`.

**2. Choose the correct restart type**

**A. Soft stop/start for routine pause or reboot**
Use when you just need to stop and resume the system.

**Linux (Docker Only):**
```bash
docker compose stop
docker compose start
```

**macOS / Windows (Hybrid Native + Docker):**
If you deployed on macOS or Windows, MediaMTX runs as a native host process. Therefore, you must use the provided scripts.
To pause: Run `./stop-all.sh` (or `stop-all.bat`)
To resume: Run your respective `./launch-mesh` or `./launch-lan` script.

This preserves the existing containers and does not remove volumes or networks.

**B. Clean restart for updates or config changes**
Use when `docker-compose.yml`, `.env`, images, or profile settings have changed.

**Linux (Docker Only):**

*Mesh deployment*
```bash
docker compose down
COMPOSE_PROFILES=mesh docker compose up -d
```

*LAN-only deployment*
```bash
docker compose down
COMPOSE_PROFILES=lan-only docker compose up -d
```

**macOS / Windows (Hybrid Native + Docker):**
Run `./stop-all.sh` (or `stop-all.bat`). Then re-run your respective `./launch-mesh` or `./launch-lan` script.

This removes and recreates containers safely while keeping volumes and persisted data intact.

**3. Verify startup**

```bash
docker compose logs -f
```

Watch until backend, database, proxy, and if applicable Tailscale are healthy.

**Expected result**
After restart, OSCAR should come back up with:

* existing admin credentials intact
* existing TOTP/2FA intact
* existing database contents intact
* existing TLS materials intact
* existing Tailscale node identity intact in mesh mode

**Do not do this unless you want a full reset**

```bash
docker compose down -v
```

This is the documented destructive reset path and will delete stored data, saved passwords, and certificates.

**Notes**

* Legacy scripts such as `launch-all.sh` are kept for backward compatibility and initial setup convenience.
* For mesh deployments, Tailscale state is stored under `./tailscale/state`, which is why normal restarts should not require re-authentication.