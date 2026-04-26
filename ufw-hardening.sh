#!/bin/bash
# Hardening script for Air-Gapped and Shared Network Topologies using UFW

echo "Configuring UFW for OSCAR node..."

# Reset to default deny (fail-secure)
ufw --force reset
ufw default deny incoming
ufw default deny outgoing

# Allow necessary outbound traffic for Tailscale/DNS/updates (restrict as needed for pure air-gapped)
# For purely air-gapped, these would be disabled, but for shared networks, they may be needed.
# ufw allow out 53/udp
# ufw allow out 123/udp
# ufw allow out 443/tcp

# 1. Tailscale Node-to-Node Traffic (if using Tailscale in a shared network)
ufw allow in on tailscale0
ufw allow out on tailscale0

# 2. Local physical sensors (Rapiscan/Aspect) on specific LAN interface
# Example: Assuming sensors are on eth1 (192.168.10.0/24)
# Adjust interface and subnet based on actual deployment
# ufw allow in on eth1 from 192.168.10.0/24 to any port <SENSOR_PORT> proto tcp

# 3. Allow MediaMTX internal control link (host gateway)
# MediaMTX is on the host network, so allow from the Docker bridge subnet
DOCKER_SUBNET=$(docker network inspect oscar-flat_osh-internal -f '{{(index .IPAM.Config 0).Subnet}}' 2>/dev/null || echo "172.18.0.0/16")
ufw allow in from $DOCKER_SUBNET to any port 9997 proto tcp
ufw allow in from 127.0.0.1 to any port 9997 proto tcp

# 4. HTTPS Ingress (LAN Profile only, for Shared Network topology)
# Only allow from trusted internal subnets (e.g., local management VLANS)
ufw allow in proto tcp from 10.0.0.0/8 to any port 443
ufw allow in proto tcp from 172.16.0.0/12 to any port 443
ufw allow in proto tcp from 192.168.0.0/16 to any port 443

# Enable UFW
ufw --force enable

echo "UFW configuration applied."
ufw status verbose
