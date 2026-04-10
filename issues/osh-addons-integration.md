# Issue: Integrate osh-addons-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Focus on the PostGIS persistence layer hardening and FFmpeg driver updates, ensuring high-precision timestamp support is preserved.

## Integration Details

### 1. PostGIS Security Hardening
Upstream introduces `insertObsPreparedQuery` using `PreparedStatement` with `?` placeholders.
- **Alignment**: This is a critical security improvement against SQL injection for JSONB payloads.
- **Precision Mandate**: Ensure that the `PHENOMENON_TIME` and `RESULT_TIME` columns continue to support nanosecond resolution. If upstream uses standard `TIMESTAMP`, we must verify it doesn't truncate the data compared to our previous `TIMESTAMPTZ` implementation (see `PostgisUtils.java` in our codebase).
- **Constraint**: Maintain `sslmode=require` in the JDBC connection string managed by `ConnectionManager`.

### 2. FFmpeg & MediaMTX Integration
- **Sidecar Routing**: Upstream FFmpeg driver updates must be compatible with our MediaMTX sidecar running in `network_mode: host`.
- **Proxy Bypass**: Any Java `HttpClient` used for REST control of MediaMTX (port 9997) MUST be configured with `.proxy(ProxySelector.of(null))` to avoid being trapped by the Tailscale SOCKS5 proxy.

### 3. ICommProvider Changes
- Ensure that `TCPCommProvider` and related classes in `osh-addons-comm` respect the `socksNonProxyHosts` whitelist to allow communication with local physical RPM sensors.

## Verification
- Run `./gradlew :sensorhub-datastore-postgis:test`.
- Verify that occupancy data stored in PostGIS retains nanosecond precision in the `PHENOMENON_TIME` column.
