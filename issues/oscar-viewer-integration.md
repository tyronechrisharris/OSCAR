# Issue: Integrate oscar-viewer-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate upstream frontend changes including Sentry monitoring, PWA support, and enhanced Adjudication/WebID UI. We must preserve our hardened routing and i18n hooks.

## Actionable Items

### 1. Update Dependencies and Build Config
- **Action**: Update `web/oscar-viewer/package.json` to include `jsqr` and `qr-scanner`.
- **Action**: Update `web/oscar-viewer/next.config.js` to wrap the configuration with `withSentryConfig`.
- **Recommended Code**:
  ```javascript
  const { withSentryConfig } = require("@sentry/nextjs");
  const nextConfig = { ... };
  module.exports = withSentryConfig(nextConfig, { ... });
  ```

### 2. Internationalization (i18n) Mapping
- **Action**: Scan `AdjudicationDetail.tsx`, `WebIdAnalysis.tsx`, and `CustomToolbar.tsx` for hardcoded strings.
- **Action**: Add corresponding keys to `web/oscar-viewer/src/locales/en.json`.
- **Recommended Code**:
  - In `en.json`:
    ```json
    {
      "detectorResponseFunction": "Detector Response Function",
      "spectrumType": "Spectrum Type",
      "doneScanning": "Done Scanning",
      "webIdAnalysisLog": "WebID Analysis Results Log"
    }
    ```
  - In TSX components:
    ```tsx
    import { useLanguage } from "@/contexts/LanguageContext";
    const { t } = useLanguage();
    // ...
    <TextField label={t('detectorResponseFunction')} />
    ```

### 3. PWA & Service Worker Hardening
- **Action**: Integrate `public/manifest.json` and `public/sw.js` changes.
- **Action**: Modify `sw.js` to explicitly bypass caching for telemetry and auth routes.
- **Recommended Code** (in `sw.js`):
  ```javascript
  self.addEventListener('fetch', (event) => {
    if (event.request.url.includes('/sensorhub/sos') || event.request.url.includes('/sensorhub/login')) {
      return; // Do not cache
    }
  });
  ```

### 4. Adjudication UI Logic
- **Action**: Integrate the new `Grid`, `Dialog`, and `Select` components into `AdjudicationDetail.tsx`.
- **Action**: Ensure the "QR Scanner" button triggers the `Dialog` correctly.

## Verification
- Run `npm install` and `npm run build` in `web/oscar-viewer`.
- Verify the PWA manifest is detected in Chrome DevTools.
- Toggle languages and ensure all new adjudication labels update correctly.
