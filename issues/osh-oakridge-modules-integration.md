# Issue: Integrate osh-oakridge-modules-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate updates for specialized RPM drivers (Rapiscan, Aspect, Kromek) and the OSCAR service.

## Integration Steps
1. **Comm Provider Hardening**:
   - Ensure all TCP-based sensor drivers (`TCPCommProvider`) utilize `Proxy.NO_PROXY`.
2. **Lane System Logic**:
   - Reconcile `LaneSystem.java` updates with our asynchronous MediaMTX provisioning logic (10-thread pool).
   - Ensure deterministic UIDs are used for MediaMTX paths to prevent race conditions.
3. **i18n**:
   - Localize any new configuration labels or status messages in the OSCAR Service.

## Verification
- Run `./gradlew :sensorhub-service-oscar:test`.
