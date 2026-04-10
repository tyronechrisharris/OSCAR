# Issue: Integrate osh-oakridge-modules-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate updates for specialized RPM drivers and the new RS350 occupancy process, ensuring local sensor communication remains unblocked.

## Integration Details

### 1. RS350 Occupancy Process
- Upstream adds a new module `sensorhub-process-rs350-occupancy`.
- **Integration**: Ensure the `Activator` and configuration classes align with our `LaneSystem` logic.
- **i18n**: Any user-facing configuration labels or output messages must be localized.

### 2. Driver Hardening (Rapiscan, Aspect, Kromek)
- **Networking**: These drivers connect to physical hardware via TCP. They MUST utilize `Proxy.NO_PROXY` when establishing sockets to prevent the Tailscale mesh from attempting to route local LAN traffic.
- **MediaMTX Paths**: Reconcile changes in `LaneSystem.java` with our asynchronous MediaMTX provisioning. Ensure that MediaMTX paths are removed via REST API (`POST /v3/config/paths/remove/`) during sensor teardown.

### 3. Lane System Logic
- **Race Condition Prevention**: Use deterministic UIDs (URNs based on serial numbers) for internal tracking of MediaMTX paths, as the module's `getUniqueIdentifier()` may not be stable during the early initialization phase.

## Verification
- Run `./gradlew :sensorhub-service-oscar:test`.
- Verify communication with a mock Rapiscan/Aspect sensor in a containerized environment where Tailscale is active.
