# Security Evaluation Report: Open Source Central Alarm Station (OSCAR)

## 1. Executive Summary

This report evaluates the security posture of the OSCAR platform, a critical infrastructure monitoring application designed to detect and alert on unauthorized access and radiation presence (specifically Gamma (G), Neutron (N), and Gamma-Neutron (G-N) alarms).

The application utilizes a Java-based backend built on the OpenSensorHub (OSH) framework, integrating various modules for hardware interfaces, video processing, and persistence (PostGIS).

Overall, the application suffers from critical foundational security issues, most notably the prevalence of hardcoded credentials (`admin:oscar` / `admin:admin`), lack of application-level encryption for database communication, and over-permissive default configurations. The risk of unauthorized suppression, delay, modification, or spoofing of Gamma (G) and Neutron (N) alarms is **Critical**, especially in deployments sharing networks with other systems or exposing interfaces to broader networks.

## 2. Topology-Specific Threat Models

### 2.1. Federated Site-to-Site (Tailscale)
*   **Context:** Multiple sites federated using a Tailscale mesh network.
*   **Threat 1: Lateral Movement & Alarm Spoofing via Compromised Tailnet Node.** If a single device on the Tailnet is compromised, an attacker can directly communicate with the PostGIS database (port 5432) or the application API (port 8282) of other federated nodes due to flat routing within the Tailnet. An attacker could inject spoofed `occupancy` records or suppress legitimate G/N alarms by directly altering the database state.
*   **Threat 2: Man-in-the-Middle (MitM) inside the Tailnet.** While Tailscale encrypts traffic (WireGuard), it does not authenticate application-layer sessions. The lack of Layer 7 TLS allows a compromised internal node intercepting traffic to modify API payloads (e.g., flipping a `gammaAlarm` boolean from `true` to `false`) before it reaches the central logging server.

### 2.2. Fully Offline / Air-Gapped Single Node
*   **Context:** Isolated hardware at a physical site.
*   **Threat 1: Physical Access and Local Privilege Escalation.** Physical access allows an attacker to manipulate the host environment. Given that database credentials are well-known (`postgres:postgres`) and application configuration files contain hardcoded secrets, an attacker booting from a USB or gaining initial unprivileged execution can immediately compromise the PostGIS database, deleting historical G/N alarm logs and blinding the system.
*   **Threat 2: Local Denial of Service via Log/Database Exhaustion.** An insider with physical access could flood the local network interface with malformed UDP/TCP sensor data. Without strict rate-limiting and resource quotas, the local disk could fill up, causing the PostGIS database to crash and preventing real G/N alarms from being recorded.

### 2.3. Shared Physical Security Network
*   **Context:** Node deployed on a local network shared with CCTV/Access Control.
*   **Threat 1: Alarm Suppression via Broadcast Storms / ARP Spoofing.** An attacker compromising a vulnerable CCTV camera on the same subnet can launch an ARP spoofing attack or a broadcast storm. This can intercept or delay UDP/TCP packets containing critical G/N alarm data from the physical radiation portals before they reach the OSCAR node.
*   **Threat 2: Unauthenticated Database/API Access.** Because the application defaults to trusting local subnets implicitly (no internal TLS, default credentials), any compromised device on the physical security network can connect to the OSCAR node's API (port 8282) or Database (port 5432) to modify configurations or disable alarm polling.

### 2.4. Single Site Network with Internet Access
*   **Context:** LAN deployment with outbound/inbound Internet access.
*   **Threat 1: Supply Chain and Dependency Exploitation.** The application relies on numerous older Java libraries and external binary drivers (e.g., FFmpeg, various JARs). Outbound internet access allows a vulnerable dependency (like Log4j if reintroduced via Axis driver) to execute an attacker's payload and establish a Command and Control (C2) beacon outward, granting remote access to the alarm systems.
*   **Threat 2: Remote Exploitation of the Admin Interface.** If port 8282 is exposed (even accidentally via NAT misconfiguration), an external attacker can trivially gain administrative access using the default credentials (`admin:oscar` / `admin:admin`), allowing total control over the G/N alarm logic and system configuration.

### 2.5. Network with Wireless Access Points (WAPs)
*   **Context:** Operators or devices connect via Wi-Fi.
*   **Threat 1: Session Hijacking and API Interception.** Wireless networks are inherently broadcast media. Because the application defaults to HTTP on port 8282 without enforcing HTTPS/WSS, an attacker in physical proximity to the WAP can sniff traffic to capture session tokens or inject malicious API responses that suppress G/N alarms on the operator's dashboard.
*   **Threat 2: Wireless Deauthentication and Sensor Disconnection.** An attacker can perform Wi-Fi deauthentication attacks against wireless sensors or the operator's tablet, causing a localized denial of service. The system must fail-secure and raise an immediate alert if a sensor stops reporting, rather than failing silently.

## 3. Vulnerability Findings

### 3.1. Hardcoded and Default Database Credentials
*   **Severity:** Critical
*   **Description:** The PostGIS database is instantiated via Docker and scripts with the hardcoded credentials `postgres:postgres`. This is referenced in multiple configuration files (`config.json`) and startup scripts (`launch-all.sh`, `run-postgis.sh`).
*   **File/Line:** `dist/config/standard/config.json:178`, `dist/release/launch-all.sh:54`, `test/test-run-postgis.bat:6`
*   **Exploit Scenario:** An attacker with network access to port 5432 can connect directly to the database and DROP the alarms tables or MODIFY historical records of radiation detection.
*   **Remediation:** Remove hardcoded credentials. Use Docker Secrets or environment variables injected securely at runtime to define the `POSTGRES_PASSWORD`. Ensure the application connects using a least-privilege database user, not the `postgres` superuser.

### 3.2. Hardcoded Application Keystore Passwords
*   **Severity:** High
*   **Description:** The `launch.sh` and `launch.bat` scripts set environment variables and Java system properties using hardcoded passwords (`atakatak` for KeyStore, `changeit` for TrustStore).
*   **File/Line:** `dist/scripts/standard/launch.sh:9`, `dist/scripts/standard/launch.sh:31`
*   **Exploit Scenario:** An attacker who reads the startup scripts can extract the Keystore password, allowing them to decrypt stored keys or forge certificates to spoof a legitimate sensor node or Admin UI session.
*   **Remediation:** Store keystore passwords in a secure secrets manager or prompt for them securely upon initialization. Do not hardcode them in version-controlled scripts.

### 3.3. Hardcoded Initial Admin Password
*   **Severity:** High
*   **Description:** The system ships with a hardcoded initial admin password (`oscar` found in `.s`, or falls back to `admin`), and the backend logic explicitly checks for `admin` if variables are unset.
*   **File/Line:** `dist/scripts/standard/.s`, `dist/scripts/standard/launch.sh:18`
*   **Exploit Scenario:** If an operator fails to change the default password immediately upon deployment (a common occurrence), an attacker can trivially log into the Admin UI and disable the entire alarm system.
*   **Remediation:** Force the administrator to create a strong, unique password on the very first boot before any functionality is available. Do not ship with a default password.

### 3.4. Lack of Transport Layer Security (TLS) for Internal Communication
*   **Severity:** High
*   **Description:** The communication between the Java backend and the PostGIS database, as well as the default HTTP interface on port 8282, occurs in plaintext by default.
*   **File/Line:** `dist/config/standard/config.json:177` (`"url": "localhost:5432"`)
*   **Exploit Scenario:** An attacker performing a Man-in-the-Middle attack on the local network (e.g., Shared Physical Security Network topology) can capture plaintext G/N alarm data, database credentials, or active session cookies.
*   **Remediation:** Enforce `sslmode=require` or `sslmode=verify-full` in the PostGIS connection string. Ensure the web interface forces HTTPS and uses Secure, HttpOnly cookies.

### 3.5. Outdated Base Images and Potential Dependency Vulnerabilities
*   **Severity:** Medium
*   **Description:** The Dockerfiles reference specific versions of PostGIS (e.g., `postgis/postgis:16-3.4`). Furthermore, the codebase contains commented-out references to potentially vulnerable drivers (e.g., `sensorhub-driver-axis` specifically noted for log4j).
*   **File/Line:** `dist/release/postgis/Dockerfile`, `build.gradle:29`
*   **Exploit Scenario:** Known CVEs in older base images or bundled JARs could allow remote code execution or denial of service.
*   **Remediation:** Implement automated dependency scanning (e.g., Dependabot, Snyk). Use minimal base images (like Alpine or distroless) for Docker containers. Regularly update PostGIS and Java dependencies.
