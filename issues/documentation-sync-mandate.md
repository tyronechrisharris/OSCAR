# Issue: Documentation Sync for v3.3.1 Upgrade

## Overview
Maintain the "Living Wiki" by updating architecture and security documentation to reflect v3.3.1 changes.

## Required Updates

### 1. SYSTEM_ARCHITECTURE.md
- **Component Versions**: Update the OSH Backend version to v3.3.1.
- **PWA & WebID**: Ensure the "Client Features" section accurately describes the integrated spectroscopic QR scanning and offline caching capabilities.
- **Data Flow**: If new ports or sidecars are introduced (e.g., for Sentry or enhanced MediaMTX control), update the "Default Port Configuration" and the Mermaid/SVG diagram.

### 2. SECURITY_ARCHITECTURE.md
- **Authentication**: Explicitly document the bifurcated auth flow if upstream changes modified any endpoint behavior.
- **Persistence**: Document the transition to `PreparedStatement` for PostGIS as a security hardening measure.
- **Sentry Integration**: Document how Sentry is secured (e.g., no sensitive data capture).

### 3. CHANGELOG.md
- Summarize the integration of v3.3.1, highlighting major bug fixes and new features (PWA, RS350 occupancy, PostGIS hardening).

## Compliance
- All documentation updates must be included in the same Pull Request as the code changes, as per `AI_CONTRIBUTING_RULES.md`.
