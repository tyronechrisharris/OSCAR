# Issue: Integrate oscar-viewer-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate upstream frontend changes including Sentry monitoring, PWA support, and enhanced Adjudication/WebID UI. We must preserve our hardened routing and i18n hooks.

## Integration Details

### 1. Internationalization (i18n) Conversion
The upstream patch introduces many hardcoded strings. These MUST be moved to `src/locales/en.json` and accessed via the `t()` hook.
- **Adjudication UI**: Strings like "Detector Response Function", "Spectrum Type", and "Done Scanning" must be localized.
- **WebID Analysis**: Localize "WebID Analysis Results Log" and log messages.
- **Settings Menu**: The new settings menu in `CustomToolbar.tsx` must be fully localized (e.g., "settings", "Volume", "Audio Alarms").

**Example Mapping:**
- Upstream: `label="Detector Response Function"`
- Oscar-Flat: `label={t('detectorResponseFunction')}`

### 2. Security & Network Flow
- **MQTT Stability**: Maintain the `keepalive: 15` in `mqttOpts` within `Store.tsx` or wherever the MQTT connection is initialized. Upstream might default to 60s, which causes status 1005 errors in our Tailscale/Caddy environment.
- **Sentry Hardening**: Ensure the Sentry configuration in `next.config.js` and `sentry.*.config.ts` does not include sensitive environment variables or leak internal IP addresses in production logs.
- **PWA Routing**: The Service Worker (`sw.js`) must be configured to EXCLUDE `/sensorhub/sos` and `/sensorhub/login` from offline caching to prevent security bypasses and stale telemetry.

### 3. PWA & WebID Consistency
- Upstream adds `jsqr` and `qr-scanner`. Ensure these dependencies are correctly added to `package.json`.
- Preserve our custom logic in `WebIdAnalysis.tsx` that handles the local configuration proxy at `public/config/spectroscopy-info.json` for air-gapped deployments.

## Verification
- `npm run build` and `npm run lint` must pass.
- Verify that switching languages in the Admin UI correctly updates all new upstream UI elements.
- Verify PWA installability on a mobile browser (simulated or real).
