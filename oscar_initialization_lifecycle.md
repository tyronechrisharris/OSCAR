# OSCAR System: Initialization and Restart Lifecycle

This document provides a comprehensive step-by-step detail of the initialization and restart lifecycle of the current working OSCAR hybrid system. It breaks down the exact logic, execution order, and specific mechanisms that orchestrate the PostGIS database, OSH Java backend, and certificate generation, which are critical when migrating to a fully containerized Docker Compose stack.

## 1. Startup Timing and Service Delays

### Boot Process and Execution Order
The startup sequence is managed through Docker Compose and standardizes the initialization of all components using `depends_on` conditions to enforce execution order:
1. **Pre-flight Checks & Credentials (`init-secrets`):** An ephemeral container generates secure credentials. This includes the database password (`.db_password`) and keystore password (`.app_secrets`) in the `oscar_secrets` volume. It also generates a dedicated 16-character hex password and default username (`oscar-admin`) for the MediaMTX API, and a complete, hardened `mediamtx.yml` configuration file, all stored in the segmented `mtx_secrets` volume.
2. **PostGIS Initialization:** Launches the database container. It depends on `init-secrets` completing successfully. Database flags are passed as a YAML array to ensure reliable initialization without shell interference.
3. **Backend Launch:** Starts the OSH backend. This container waits for the `osh-postgis` container's health check to report `service_healthy`.
4. **Tailscale Sidecar Initialization:** Starts a dedicated `tailscale` container. It reads the `TS_AUTHKEY` from the environment to register the node, and persists its machine identity to the `./tailscale/state` host bind mount. It also creates a unix socket at `./tailscale/sock/tailscaled.sock`.
5. **Proxy Launch:** Starts the `osh-proxy` (Caddy) container. Because it shares the Tailscale network namespace (`network_mode: "service:tailscale"`), it explicitly waits for the `tailscale` container to be `service_started`. It also enforces a strict 60+ second startup delay by waiting for the `osh-backend` to finish the Setup Wizard/Certificate generation and report as `service_healthy`.

### Wait-State Logic for PostGIS
The orchestration uses a non-shell healthcheck to delay the backend startup until PostGIS has fully loaded its spatial extensions (`gis` and `template_postgis` databases).
1. **`pg_isready` Healthcheck:** The PostGIS container utilizes a native healthcheck targeting the `gis` database using the `CMD` exec form to ensure compatibility with distroless and minimal images.
2. **Additional Buffer (Sleep 30):** Once `pg_isready` succeeds, an explicit 30-second sleep (`sleep 30`) is executed to ensure PostGIS has sufficient time to complete loading all internal initializations and spatial extensions before backend connections are attempted.
3. **Final Verification Loop:** A final safety loop ensures PostGIS hasn't entered a restart loop after the 30-second wait before allowing the backend to launch:
   ```bash
   until docker exec -u "$DB_USER" "$CONTAINER_NAME" pg_isready -d "$DB_NAME" > /dev/null 2>&1; do
     echo "PostGIS still restarting, waiting..."
     sleep 5
   done
   ```
4. **SOCKS Proxy Exemption:** During `JAVA_OPTS` configuration, `-DsocksNonProxyHosts` is explicitly appended to ensure local TCP sockets (like physical RPM connections) are exempted from the global Tailscale SOCKS5 intercept, resolving `SOCKS server general failure` exceptions.

## 2. Certificate Authority & TLS Generation

TLS generation has been decoupled from the Java backend and is now managed securely at runtime via the `init-secrets` ephemeral container using standard OpenSSL commands.

### Timing of Certificate Generation
The certificate generation happens **before any other container starts**. The `init-secrets` container executes its shell script upon `docker compose up`, checks the shared `oscar_secrets` named volume, and generates missing assets.

### Generation Mechanism
1. **Password Generation:** Uses `openssl rand` to generate secure 32-byte Base64 passwords for the database (`.db_password`) and the Java keystore/truststore (`.app_secrets`). It also generates a 16-character random hex password for the MediaMTX API (`.mediamtx_api_pass`) and initializes the default `oscar-admin` username (`.mediamtx_api_user`).
2. **MediaMTX Runtime Configuration:** The `init-secrets` container generates a hardened `mediamtx.yml` at boot, baking the auto-generated credentials and performance optimizations (RTSP-only, source-on-demand) directly into the file.
3. **Volume Segmentation:** To enforce the **Principle of Least Privilege**, the MediaMTX credentials and configuration are stored in a dedicated `mtx_secrets` named volume, isolating them from high-sensitivity system secrets like the database password and Root CA private key. This volume is mounted to the MediaMTX sidecar at `/etc/mediamtx`.
3. **PEM Root CA Generation:** Generates a persistent self-signed RSA-2048 Root CA certificate (`ca.pem` and `ca.key`) valid for 20 years (7300 days).
3. **PEM Server Certificate Generation:** Generates an RSA-2048 leaf certificate (`server.pem` and `server.key`) signed by the Root CA, valid for 10 years (3650 days), with the subject `CN=localhost`.
4. **Java Keystore Packaging:** Because the OpenSensorHub Java backend natively requires JKS and PKCS12 formats for its `SSLContext`, the container uses `keytool` and `openssl pkcs12` to bundle the raw PEM files into `truststore.jks` and `osh-keystore.p12`, secured by the generated `.app_secrets` password.
5. **PostGIS Certificate Generation:** Generates dedicated, unique self-signed certificates (`db-server.crt` and `db-server.key`) for the database container and enforces strict `chown 999:999` and `chmod 600` permissions.

### File Extraction and Proxy Access
*   **Format Migration:** The migration to a PEM-first architecture ensures that standard tools like the Caddy Reverse Proxy (`osh-proxy`) can natively consume the `server.pem` and `server.key` directly from the secure `oscar_secrets` volume without needing to extract keys from proprietary Java `.p12` vaults.
*   **Exposure to Reverse Proxy:** The `docker-compose.yml` mounts the shared `oscar_secrets` volume directly to `/etc/caddy/certs` in the `osh-proxy` container, fulfilling the Caddyfile TLS requirement dynamically.

## 3. Database Provisioning & Authentication

### Generating and Passing `.db_password`
1. The `.db_password` is generated natively within the Docker environment by the `init-secrets` container during the pre-flight phase, completely abstracting it from the host machine:
   ```bash
   if [ ! -f /secrets/.db_password ]; then
       openssl rand -base64 32 > /secrets/.db_password;
   fi
   ```
2. It is passed into the PostGIS Docker container via the highly secure Hybrid Volume Architecture:
   *   The file is stored in the `oscar_secrets` Docker Named Volume.
   *   The volume is mounted Read-Only (`ro`) to the `osh-postgis` container.
   *   The environment variable `POSTGRES_PASSWORD_FILE=/secrets/.db_password` informs the database exactly where to read the secret.

### Enforcing `scram-sha-256` and TLS
The enforcement of `scram-sha-256` authentication is handled explicitly in the PostGIS Dockerfile (`dist/release/postgis/Dockerfile`). The `POSTGRES_INITDB_ARGS` environment variable is set to configure the database initialization command:

```dockerfile
ENV POSTGRES_INITDB_ARGS="--auth-local=trust --auth-host=scram-sha-256"
```
The TLS encryption is enforced dynamically at runtime in `docker-compose.yml` using the `command:` override. This allows the container to dynamically consume the securely generated PostGIS certificates out of the named volume rather than baking a static private key into the public Docker Hub image:
```yaml
    command: >
      -c ssl=on
      -c ssl_cert_file=/secrets/db-server.crt
      -c ssl_key_file=/secrets/db-server.key
```
The `--auth-host=scram-sha-256` flag ensures all TCP connections (which the Java backend will use) require SCRAM-SHA-256 password hashing.

## 4. Setup Wizard & State Persistence (TOTP/Auth)

### Uninitialized vs. Initialized State
The system determines its initialization state dynamically on boot via `SecurityManagerImpl.isUninitialized()` (`include/osh-core/sensorhub-core/src/main/java/org/sensorhub/impl/security/SecurityManagerImpl.java`).

The system is considered **Uninitialized** (and thus redirects to the Setup Wizard) if any of the following conditions are met:
1.  The `IUserRegistry` is missing.
2.  The `admin` user does not exist in the registry.
3.  The `admin` user's password is a default value (e.g., null, empty, `"admin"`, `"oscar"`, `"test"`, `"__INITIAL_ADMIN_PASSWORD__"`, or matches a specific default hash signature `8x2vK/T2P9I2f2vK/T2P9A==`).
4.  The `admin` user has not configured TOTP (Two-Factor Authentication) secrets (`twoFactorSecret` is null).

If all conditions are cleared (admin exists, custom password set, TOTP enabled), the system boots in an **Initialized** state.

### State Persistence of Admin Credentials and TOTP
The security state, including users, roles, password hashes, and TOTP secrets, is managed by the `BasicSecurityRealm` module (`include/osh-core/sensorhub-core/src/main/java/org/sensorhub/impl/security/BasicSecurityRealm.java`) and `BasicSecurityRealmConfig.java`.

When the Setup Wizard is completed, the changes to the `admin` user (password hash, `twoFactorSecret`) are committed to the configuration. The persistence mechanism works as follows:
1.  **Serialization:** The `BasicSecurityRealmConfig` uses Gson to serialize user configurations (including `password` and `twoFactorSecret` fields from `BasicSecurityRealmConfig.UserConfig`) into JSON. Note that `BasicSecurityRealm.java` specifically handles *permissions* in `user_permissions.json` and `role_permissions.json`, while the overarching configuration state (the `users` array containing passwords/secrets) is inherently tied to the module's core JSON configuration (e.g., `config/modules/security.json`).
2.  **Filesystem Storage:** The state is saved to the backend's filesystem.
3.  **Survival Across Restarts:** Because these configuration files are written to the host filesystem (which should be mounted as a persistent volume in Docker), the updated `UserConfig` (with the hashed password and TOTP secret) is reloaded into memory during the `doInit()` phase of `BasicSecurityRealm` on the next boot, ensuring the system remains in an "Initialized" state.
