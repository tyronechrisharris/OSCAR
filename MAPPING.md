# Upstream to OSCAR Mapping

This document defines how upstream modules are mapped into the flattened structure of the OSCAR repository.

| Upstream Path | OSCAR Path (Internal) |
|---------------|----------------------------|
| sensors/      | include/osh-oakridge-modules/sensors/ |
| services/     | include/osh-oakridge-modules/services/ |
| processing/   | include/osh-oakridge-modules/processing/ |
| tools/        | include/osh-oakridge-modules/tools/ |
| core/         | include/osh-core/ |
| addons/        | include/osh-addons/ |
| web/          | web/ |

Note: The integration branch 'integration/oscar-v3.1.0-upgrades-4785495677883353489' already follows this mapping.
