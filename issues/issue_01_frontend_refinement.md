# Issue: Frontend i18n and Adjudication UI Refinement

## Description
Integrate upstream logic for WebID QR scanning and adjudication workflows into the `oscar-viewer` frontend while strictly adhering to the project's internationalization (i18n) mandate and security architecture.

## Logical Intent
Upstream OSH v3.3.1 introduces an enhanced adjudication workflow that includes evidence collection from spectroscopic sensors (WebID) and QR code scanning for physical ID verification.

## Implementation Details
1.  **i18n Mapping**:
    *   All new strings introduced by the adjudication form (e.g., "Evidence Collection", "Start QR Scan", "Isotope Analysis") must be added to `web/oscar-viewer/src/locales/en.json`.
    *   Use the `useLanguage` hook and `t()` function to retrieve localized strings.
    *   Avoid hardcoded English strings in components like `AdjudicationDetail.tsx`, `EventPreview.tsx`, and `WebIdAnalysis.tsx`.
2.  **WebID Evidence Workflow**:
    *   Implement the `WebIdAnalysis` component to display real-time and historical spectroscopic results.
    *   Ensure the "Evidence Collection" dropdown is populated from both recent spectroscopic observations and the local `/config/spectroscopy-info.json` proxy to support air-gapped deployments.
3.  **QR Scanning Integration**:
    *   Utilize `qr-scanner` for functional QR detection in `WebIdAnalysis.tsx`.
    *   Handle camera permissions gracefully and provide meaningful error feedback via localized Snackbars.
4.  **Security Boundaries**:
    *   Ensure the Service Worker (`sw.js`) is updated to deny caching for sensitive telemetry routes (`/sensorhub/sos`, `/sensorhub/api/observations`).
    *   Routinely verify that the UI does not attempt to connect to external CDNs for assets; all dependencies must be bundled locally.

## Acceptance Criteria
- [ ] Adjudication form correctly displays localized labels.
- [ ] QR scanner successfully captures data and maps it to the adjudication feedback.
- [ ] WebID evidence can be selected and appended to the adjudication report.
- [ ] No hardcoded strings remain in the adjudication sub-components.
- [ ] Frontend build succeeds and runs within the containerized Caddy reverse proxy.
