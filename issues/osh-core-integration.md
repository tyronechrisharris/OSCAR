# Issue: Integrate osh-core-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate upstream core framework changes into `include/osh-core`, focusing on security and initialization logic.

## Integration Steps
1. **Security Hardening**:
   - Ensure any changes to `BasicSecurityRealm` or `SecurityManager` do not bypass the TOTP requirement for the `admin` user.
   - Verify that `sslmode=require` is maintained in all JDBC connection logic.
2. **i18n Backend**:
   - Add new message keys to `sensorhub-core/src/main/resources/messages.properties`.
   - Run the translation propagation utility to update all `messages_*.properties` files.
3. **Initialization Lifecycle**:
   - Ensure new modules respect the deterministic startup sequence defined in `oscar_initialization_lifecycle.md`.

## Verification
- Run `./gradlew :sensorhub-core:test`.
- Verify the Setup Wizard flow on a clean volume boot.
