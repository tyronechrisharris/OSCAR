# OSCAR Security Evaluation Report

## 1. Executive Summary
The Open Source Central Alarm Station (OSCAR) is a critical application for monitoring radiation portal monitors. Given its role in detecting Gamma (G) and Neutron (N) alarms, ensuring the integrity and availability of alarm telemetry is paramount. This evaluation assesses the architecture, code, and configurations of the `oscar-flat` repository across five distinct deployment scenarios. A critical vulnerability was identified in the data persistence layer involving SQL injection, which could allow unauthorized manipulation, suppression, or spoofing of G/N alarms across all topologies.

## 2. Topology-Specific Threat Models

**Primary Threat Focus:** Unauthorized suppression, delay, modification, or spoofing of Gamma (G) and Neutron (N) Alarms.

### 2.1. Federated Site-to-Site (Tailscale)
*   **Context:** Multiple sites federated using a Tailscale mesh network.
*   **Threat Vector 1: Compromised Tailnet Node Spoofing Alarms.** An attacker compromising a single node in the mesh could leverage the bifurcated API Key authentication to spoof or suppress G/N alarms across the federation if cross-site state synchronization is not strictly validated and micro-segmented via Tailscale ACLs.
*   **Threat Vector 2: Lateral Movement via API Key Compromise.** While TLS over WireGuard secures transit, if a long-lived API key used for federation is extracted from a compromised node, it could be used to persistently inject false G/N alarm telemetry into the central hub.

### 2.2. Fully Offline / Air-Gapped Single Node
*   **Context:** Deployed on isolated hardware at a physical site with zero external network connectivity.
*   **Threat Vector 1: Local Database Manipulation via Physical Access.** An attacker with physical access or local privilege escalation could access the PostGIS database or the Docker volumes (e.g., `pgdata`) directly to delete or alter historical G/N alarm records.
*   **Threat Vector 2: NTP Manipulation/Time Drift.** If isolated from centralized NTP, local time synchronization attacks could delay or misorder critical G/N alarm timestamps, undermining incident response timelines.

### 2.3. Shared Physical Security Network
*   **Context:** A single node deployed on a local LAN shared with other systems (e.g., CCTV).
*   **Threat Vector 1: Lateral Broadcast Attacks (ARP Spoofing/DoS).** An attacker compromising a low-security device (like a CCTV camera) on the same subnet could launch ARP spoofing or broadcast storms, disrupting the raw TCP socket communication (`TCPCommProvider`) from the local radiation sensors, effectively suppressing G/N alarms.
*   **Threat Vector 2: Insecure LAN Ingress.** The `lan-only` Caddy profile allows ingress from any RFC 1918 address. If the shared network lacks micro-segmentation, a compromised adjacent device could attempt to brute-force the Setup Wizard or API endpoints to manipulate alarm thresholds.

### 2.4. Single Site Network with Internet Access
*   **Context:** Standard LAN deployment with outbound/inbound Internet access.
*   **Threat Vector 1: External C2 Beaconing and Data Exfiltration.** If the host is compromised, the outbound internet access could be used for Command and Control (C2) beaconing, allowing external actors to remotely suppress G/N alarms or exfiltrate sensitive site layouts and threshold configurations.
*   **Threat Vector 2: Supply Chain Attack via External Dependencies.** Vulnerabilities in external Docker images (e.g., `eclipse-temurin`, `caddy`) pulled over the internet could introduce vectors for remote code execution, granting attackers access to the OSH backend to modify alarm parsing logic.

### 2.5. Network with Wireless Access Points (WAPs)
*   **Context:** Operators or devices connect via Wi-Fi.
*   **Threat Vector 1: Wireless Packet Sniffing/Session Hijacking.** An attacker in physical proximity could sniff Wi-Fi traffic. Even with HTTPS, if a session token (e.g., for the `BridgedAuthenticator`) is compromised via XSS or an insecure Wi-Fi setup, the attacker could hijack an operator's session to suppress active G/N alarms in the UI.
*   **Threat Vector 2: Rogue WAP / Evil Twin.** Operators might connect to a rogue access point. If the client doesn't strictly validate the auto-generated `ca.pem`, an attacker could perform a Man-in-the-Middle (MitM) attack to intercept and modify G/N alarm data before it reaches the dashboard.

## 3. Vulnerability Findings

### 3.1. Critical: SQL Injection in Persistence Layer via StringSubstitutor
*   **Severity:** Critical
*   **Description:** The persistence layer classes (`PostgisBatchObsStoreImpl`, `PostgisObsStoreImpl`, and `PostgisBaseFeatureStoreImpl`) use `org.apache.commons.text.StringSubstitutor` to construct SQL queries by replacing placeholders (e.g., `${1}`) with stringified data. This approach bypasses the protections of standard JDBC `PreparedStatement` parameters. The `add()` and `addOrUpdate()` methods serialize observation data and insert it directly into the query string.
*   **File/Line References:**
    *   `PostgisObsStoreImpl.java` (lines ~169-215): `fillAddStatement` method.
    *   `PostgisBatchObsStoreImpl.java` (lines ~37-43): `add` method using `fillAddStatement`.
    *   `PostgisBaseFeatureStoreImpl.java` (lines ~163-199): `fillAddOrUpdateStatement` method.
*   **Exploit Scenario:** An attacker who can send crafted telemetry data (e.g., a specially crafted alarm string or description) could inject malicious SQL commands. During batch ingestion (`PostgisBatchObsStoreImpl`), this could lead to mass deletion, suppression, or modification of historical and active G/N alarms.
*   **Remediation:** Remove `StringSubstitutor` for SQL query construction. Refactor `PostgisBatchObsStoreImpl`, `PostgisObsStoreImpl`, and `PostgisBaseFeatureStoreImpl` to strictly use `PreparedStatement` with parameterized queries (`?`) and the `addBatch()` method for batch operations.

### 3.2. High: Command Injection / Shell Metacharacter Risks in Docker Scripts
*   **Severity:** Medium
*   **Description:** While the `start-osh.sh` script employs `set -f` to disable shell globbing to protect `JAVA_OPTS` containing wildcards, the use of `exec java $JAVA_OPTS` and other shell executions should be carefully audited to ensure no user-controlled input can influence environment variables that dictate shell commands. (Noted as a defense-in-depth measure; the current implementation is adequately hardened).
*   **Exploit Scenario:** If an attacker can modify the `.env` file or `docker-compose.yml` (e.g., via LFI), they could inject shell metacharacters into `JAVA_OPTS`.
*   **Remediation:** Continue using strict script boundaries and consider moving entirely to direct binary execution without intermediate shell interpreters where possible.
