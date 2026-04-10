# Issue: Integrate osh-core-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate upstream core framework improvements while strictly maintaining our bifurcated authentication model and deterministic initialization lifecycle.

## Integration Details

### 1. Internationalization (i18n) Backend
Upstream changes in `sensorhub-webui-core` introduce new UI elements (dialogs, labels).
- **Mandate**: All new strings must be added to `sensorhub-core/src/main/resources/messages.properties`.
- **Propagation**: Use the `messages_*.properties` translation utility to ensure all 23+ supported languages are updated.
- **Vaadin Hooks**: Use `I18N.get("key")` for all component captions and descriptions in Java code.

### 2. Security & Authentication
- **TOTP Persistence**: Ensure improvements to `SecurityManager` or `BasicSecurityRealm` do not break the `twoFactorSecret` persistence in `security.json`.
- **Bifurcated Auth**: Under no circumstances should machine-to-machine (M2M) routes like `/sensorhub/sos` be redirected to the TOTP login page. They must remain secured via API Keys (Bearer tokens).

### 3. JSON Parsing (GSON)
Upstream fixes in `JsonDataParserGson.java` improve handling of variable-sized arrays.
- **Constraint**: Ensure this change does not introduce vulnerabilities when parsing large telemetry payloads from remote federated nodes.

## Verification
- Run `./gradlew :sensorhub-core:test`.
- Perform a "Nuclear Reset" (`docker compose down -v`) and verify the Setup Wizard still correctly enforces Admin password and TOTP initialization.
- Verify that API Key authentication still works for OGC SOS endpoints.
