# Issue: Integrate oscar-viewer-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate upstream frontend changes while preserving OSCAR's PWA features, hardened routing, and i18n hooks.

## Integration Steps
1. **i18n Conversion**:
   - Scan the patch for any new hardcoded English strings in JSX/TSX.
   - Move these strings to `web/oscar-viewer/src/locales/en.json`.
   - Use the `useLanguage()` hook and `t()` function to render strings.
2. **Security & Routing**:
   - Ensure new API calls are directed through the Reverse Proxy (Caddy).
   - Maintain the 15-second MQTT keepalive in `mqttOpts`.
3. **PWA Consistency**:
   - Ensure changes do not break the Service Worker (`sw.js`) or Manifest.
   - Verify that spectroscopic QR scanning logic in `WebIdAnalysis.tsx` is preserved.

## Verification
- Run `npm run build` and `npm run lint` in `web/oscar-viewer`.
- Verify translations by switching languages in the UI.
