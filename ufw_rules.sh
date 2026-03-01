#!/bin/bash

# ==============================================================================
# Network Isolation & Hardening using UFW (Uncomplicated Firewall)
# Target Deployment: Air-Gapped Single Node & Shared Physical Security Network
# Objective: Enforce strict ingress/egress controls, limiting communication
#            strictly to necessary ports and defined subnets.
# ==============================================================================

# Ensure script is run as root
if [ "$EUID" -ne 0 ]; then
  echo "Please run as root"
  exit
fi

echo "Disabling UFW during configuration..."
ufw --force disable

echo "Resetting UFW to default state..."
ufw --force reset

# Set default policies (Deny All Inbound, Deny All Outbound)
echo "Applying default deny policies..."
ufw default deny incoming
ufw default deny outgoing
ufw default deny routed

# ==============================================================================
# Rule Configurations
# ==============================================================================

# 1. Allow Loopback (crucial for local service communication, e.g., app to db)
echo "Allowing local loopback traffic..."
ufw allow in on lo
ufw allow out on lo

# 2. Allow specific outbound traffic for updates and essential services
# (Note: In a true Air-Gapped environment, this should be disabled or routed to a local mirror)
# DNS (UDP/TCP 53)
ufw allow out to any port 53 proto udp
ufw allow out to any port 53 proto tcp
# HTTP/HTTPS for updates
ufw allow out to any port 80 proto tcp
ufw allow out to any port 443 proto tcp
# NTP for time synchronization
ufw allow out to any port 123 proto udp

# 3. Secure Admin Access (SSH)
# Restrict SSH access to a specific management subnet (e.g., 192.168.10.0/24)
# Adjust the subnet to match your environment.
MANAGEMENT_SUBNET="192.168.10.0/24"
echo "Allowing SSH from Management Subnet: ${MANAGEMENT_SUBNET}..."
ufw allow in from ${MANAGEMENT_SUBNET} to any port 22 proto tcp

# 4. Application API (Port 8282)
# If using a Reverse Proxy on the same machine, the API port should only be accessible locally.
# If accessed directly, restrict to the specific subnet or IP needing access.
# E.g., Allow API access only from the Operations Subnet.
OPERATIONS_SUBNET="192.168.20.0/24"
echo "Allowing API access on port 8282 from Operations Subnet: ${OPERATIONS_SUBNET}..."
ufw allow in from ${OPERATIONS_SUBNET} to any port 8282 proto tcp

# 5. Database (PostgreSQL/PostGIS - Port 5432)
# The database should NEVER be exposed externally. It should only be accessible via localhost
# (already handled by loopback rule) or from specific application servers if deployed multi-node.
# The following rule is an example of explicitly denying external DB access.
echo "Explicitly denying external PostGIS access..."
ufw deny in to any port 5432

# ==============================================================================
# Rate Limiting & DoS Protection
# ==============================================================================
# Rate limit SSH to prevent brute force
echo "Rate limiting SSH..."
ufw limit 22/tcp

# Rate limit the API port to mitigate basic flood attacks
echo "Rate limiting API port 8282..."
ufw limit 8282/tcp

# ==============================================================================
# Enable and Verify
# ==============================================================================
echo "Enabling UFW..."
ufw --force enable

echo "UFW Status:"
ufw status verbose
