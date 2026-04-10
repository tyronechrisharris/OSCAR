# Issue: Integrate osh-addons-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate upstream addon modules, specifically the PostGIS datastore and FFmpeg processing logic.

## Integration Steps
1. **PostGIS Persistence**:
   - Ensure `PostgisObsStoreImpl.java` maintains high-precision `timestamptz` support.
   - Verify that SQL injection protections (PreparedStatement) are intact for JSONB payloads.
2. **FFmpeg & MediaMTX**:
   - Align FFmpeg driver updates with the MediaMTX sidecar architecture.
   - Ensure RTSP connection strings continue to use authenticated paths.
3. **Proxy Handling**:
   - Verify that all outbound HttpClients are configured with `.proxy(ProxySelector.of(null))` for internal traffic.

## Verification
- Run `./gradlew :sensorhub-datastore-postgis:test`.
