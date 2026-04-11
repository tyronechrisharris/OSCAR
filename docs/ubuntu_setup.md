# Ubuntu Server 24.04.4 LTS Setup Guide

This guide describes how to set up a clean Ubuntu Server 24.04.4 LTS installation for the OSCAR stack, including third-party driver support.

## 1. System Requirements
- **OS**: Ubuntu Server 24.04.4 LTS (64-bit)
- **RAM**: 8GB Minimum (16GB recommended for Tactical Hub deployments)
- **Storage**: 50GB SSD Minimum
- **Network**: Internet access for initial setup, then can be air-gapped.

## 2. Docker Installation
Follow the official Docker installation steps:
```bash
# Add Docker's official GPG key:
sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Add the repository to Apt sources:
echo   "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu   $(. /etc/os-release && echo "$VERSION_CODENAME") stable" |   sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

## 3. System Optimizations
The OSCAR stack requires increased network buffer sizes for high-density camera streaming. The 'launch-all.sh' script attempts to set these, but for persistence, add them to `/etc/sysctl.conf`:
```text
net.core.rmem_max=26214400
net.core.rmem_default=26214400
net.core.wmem_max=26214400
net.core.wmem_default=26214400
fs.file-max=1000000
```
Apply with `sudo sysctl -p`.

## 4. Deploying OSCAR
1. Download the latest release artifact (`oscar_DATE_X.zip`) from the GitHub Releases page.
2. Unzip the artifact: `unzip oscar_*.zip -d oscar-deploy`
3. Enter the directory: `cd oscar-deploy`
4. Initialize your environment: `cp .env.example .env`
5. Edit `.env` to set your `TS_AUTHKEY` and select a scaling profile.
6. Launch the stack: `chmod +x launch-all.sh && ./launch-all.sh`

## 5. Third-Party Drivers
If you are using Z-Wave or specialized serial sensors:
- Ensure the user running docker is in the `dialout` group: `sudo usermod -aG dialout $USER`
- Map the devices in your `docker-compose.yml` under the `osh-backend` service if they are not already detected.

## 6. Troubleshooting
- **Firewall**: 'launch-all.sh' automatically whitelists the Docker subnet for the MediaMTX API. If you have manual firewall rules, ensure port 9997 (TCP) and 8554 (RTSP) are accessible.
- **Logs**: Monitor service health with `docker compose logs -f`.
