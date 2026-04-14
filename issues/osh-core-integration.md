# Issue: Integrate osh-core-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate upstream core framework improvements while strictly maintaining our bifurcated authentication model and deterministic initialization lifecycle.

## Actionable Items

### 1. Enhanced JSON Parsing
- **Action**: Apply changes to `include/osh-core/lib-ogc/swe-common-core/src/main/java/org/vast/swe/fast/JsonDataParserGson.java`.
- **Logic**: Ensure `varSizeArray.getElementType().setData(itemData)` is called during array processing to fix variable-size item handling.
- **Recommended Code**:
  ```java
  if (varSizeArray != null && varSizeArray.hasData() && varSizeArray.getData() instanceof DataBlockList) {
      // ... loop items
      varSizeArray.getElementType().setData(itemData);
      globalIdx += eltProcessor.process(itemData, 0);
  }
  ```

### 2. Backend Internationalization
- **Action**: Add new UI keys introduced by upstream to `include/osh-core/sensorhub-core/src/main/resources/messages.properties`.
- **Action**: Use the translation propagation utility (if available) or manually update `messages_*.properties`.
- **Recommended Code**:
  ```properties
  # messages.properties
  dialog.adjudication.success=Adjudication successful
  dialog.adjudication.error=Adjudication failed
  ```

### 3. Security Manager Verification
- **Action**: Audit `SecurityManagerImpl.java` to ensure upstream "Improvements" haven't introduced default credentials or bypassed TOTP checks.
- **Constraint**: `isUninitialized()` must still check for the absence of `twoFactorSecret`.

## Verification
- Run `./gradlew :sensorhub-core:test`.
- Verify OGC SWE JSON parsing with complex variable-length arrays.
