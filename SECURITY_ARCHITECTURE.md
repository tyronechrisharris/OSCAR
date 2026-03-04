# OSCAR Security Hardening Architecture

**Critical Domain Context:**
This project is an Open Source Central Alarm Station (OSCAR) monitoring radiation portal monitors. The application runs cross-platform on Windows, macOS, and Linux. The primary critical threat is the unauthorized suppression, modification, or spoofing of alarms. Note this specific nomenclature:
* **G Alarm:** Gamma Alarm.
* **N Alarm:** Neutron Alarm.
* **G-N:** Gamma Neutron Alarm.

**OpenSensorHub (OSH) Ecosystem Constraint:**
OSCAR is built on the OpenSensorHub framework. **Under no circumstances may any code modifications break compatibility with the larger OSH ecosystem.** * Standard OGC SWE, SOS, and SPS API endpoints must remain fully compliant.
* Sensor drivers (e.g., video processing, hardware interfaces mapped in `config.csv`) must not be prevented from initializing or communicating.
* Machine-to-machine API routes cannot rely on human-interactive authentication (like 302 redirects to a TOTP login).

**Global Build Constraint:**
Whenever generating or modifying Dockerfiles for this project, you MUST ensure the font package is explicitly set to `fonts-freefont-ttf`. This is strictly required to prevent downstream rendering failures in the application's graphical reporting components.
