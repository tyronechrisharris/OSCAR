# Tailscale Security and Configuration for OSCAR Federation

This document explains the requirements and security considerations for using Tailscale to secure the OSCAR proxy and provision API keys between OSCAR nodes.

## 1. Tailscale Sidecar Configuration

OSCAR utilizes a dedicated Docker container (a "sidecar") running the Tailscale daemon to natively bridge the stack onto your Tailnet. This abstracts the complexity of host-level networking and provides reliable operation across macOS, Windows (WSL2), and Linux.

### A. Authentication
The `oscar-tailscale` sidecar requires an authentication key to join your Tailnet.
1. Generate an **Auth Key** in the Tailscale Admin Console. It is recommended to use a reusable, non-expiring key tagged with a specific scope (e.g., `tag:oscar-node`).
2. Provide this key in your `.env` file via the `TS_AUTHKEY` variable.

### B. MagicDNS and HTTPS Certificates
To allow Caddy's `get_certificate tailscale` directive to automatically fetch trusted Let's Encrypt certificates, your Tailnet must have permission to generate them. If this feature is turned off in the admin console, your Tailscale sidecar will be denied when it asks for the certificate, and Caddy will throw an SSL error.

Here is exactly what you need to do in the Tailscale Admin Console (this is a one-time toggle for your entire network):
1. Log in to the [Tailscale Admin Console](https://login.tailscale.com/admin/machines).
2. Navigate to the **DNS** tab on the left-hand menu.
3. Scroll down to the **MagicDNS** section and ensure it is **Enabled** (it usually is by default).
4. Scroll down further to the **HTTPS Certificates** section and click **Enable HTTPS**.

**Why this is required:**
When you enable this, Tailscale provisions a unique `*.ts.net` domain for your network and sets up the DNS challenge routing with Let's Encrypt. Now, when your Docker stack boots up:
- Caddy wakes up and asks the Tailscale sidecar for a certificate.
- The sidecar reaches out to the Tailscale control plane.
- Because you flipped that switch, the control plane says "Yes," automatically handles the Let's Encrypt DNS verification in the background, and drops a fully trusted, valid TLS certificate right into your sidecar.
- Caddy grabs it, and your browser gives you the green padlock.

Once configured:
1. Determine the MagicDNS address of your node (e.g., `oscar-server.tailxxxxx.ts.net`).
2. Provide this fully-qualified domain name in your `.env` file via the `TAILSCALE_DOMAIN` variable.

### C. State Persistence
The sidecar's machine identity and runtime state are persisted to the local host filesystem at `./tailscale/state` and `./tailscale/sock`. This ensures that if the container restarts, it retains its Tailscale IP and cryptographic identity without needing to re-authenticate with the coordination server.


## 2. Federation Provisioning Requirements

To use the automated provisioning scripts (`provision-node.sh` and `provision-node.bat`), the following Tailscale features must be configured on both the **Central Station** (Source) and the **Federated Node** (Target).

### A. Taildrop (File Sharing)
The scripts use `tailscale file cp` to transfer the API key.
*   **Action**: Ensure Taildrop is enabled in your Tailscale network (Tailnet) settings.
*   **Target Node**: Must be online and capable of receiving files.

### B. Tailscale SSH
The scripts use `tailscale ssh` to move the key into the final configuration directory and set appropriate permissions.
*   **Target Node**: Must have Tailscale SSH enabled.
    *   On Linux: `tailscale up --ssh`
    *   On Windows: Enabled via the Tailscale UI or CLI.
*   **Access Controls (ACLs)**: Your Tailnet ACLs must allow the administrator (Source) to SSH into the Target node.

### C. Tailnet ACL Configuration
You should restrict access so that only authorized administrators can push keys. Example ACL snippet:
```json
{
  "ssh": [
    {
      "action": "accept",
      "src":    ["group:admin"],
      "dst":    ["tag:oscar-node"],
      "users":  ["root", "oscar-user"]
    }
  ]
}
```

## 2. Administrator Responsibilities

### Within Tailscale
1.  **Tagging**: Tag OSCAR nodes (e.g., `tag:oscar-node`) to apply specific security policies.
2.  **Key Expiry**: Disable key expiry for long-lived federated nodes or ensure a process is in place to renew node keys.
3.  **SSH Policies**: Audit who has SSH access to the nodes via Tailscale.

### Within OSCAR
1.  **API Key ownership**: Assign API keys to service accounts with the **minimum necessary permissions** (Least Privilege). Do not use the primary `admin` account for machine-to-machine federation if possible.
2.  **Key Revocation**: If a node is decommissioned or a Tailnet key is compromised, immediately **Revoke** the API key in the OSCAR Admin UI.
3.  **Audit Logs**: Monitor OSCAR logs for unusual API activity associated with specific API keys.

## 3. How the Provisioning Process Works (Technical Flow)

1.  **Local Generation**: The admin generates a random 32-byte API key in the OSCAR Admin UI.
2.  **Hash Storage**: OSCAR stores only the PBKDF2 hash of this key.
3.  **Secure Transfer**:
    *   The `provision-node` script writes the raw key to a temporary local file.
    *   `tailscale file cp` encrypts and transfers the file directly to the target node over the Tailnet (WireGuard).
4.  **Remote Placement**:
    *   `tailscale ssh` executes a command on the target to move the file from the Tailscale "received" folder to `/opt/sensorhub/secrets/api_key` (Linux) or `C:\ProgramData\SensorHub\secrets\api_key` (Windows).
    *   Permissions are set to `600` (read/write by owner only) to prevent local exposure.
5.  **Cleanup**: The temporary local file is deleted immediately.

## 4. Troubleshooting

*   **"Permission Denied" (SSH)**: Check your Tailscale ACLs and ensure the source user has permission to SSH into the target as the specified user.
*   **"File not found"**: Ensure Taildrop is enabled. On some systems, you may need to manually accept the file if Tailscale is not configured to auto-receive.
*   **Connection Timeout**: Verify both nodes are logged into the same Tailnet and are visible to each other (`tailscale status`).
