# Issue: Integrate osh-oakridge-modules-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Integrate updates for specialized RPM drivers and the new RS350 occupancy process, ensuring local sensor communication remains unblocked.

## Actionable Items

### 1. New RS350 Occupancy Module
- **Action**: Add the directory `include/osh-oakridge-modules/processing/sensorhub-process-rs350-occupancy`.
- **Action**: Update `settings.gradle` in the root to include `:sensorhub-process-rs350-occupancy`.
- **Action**: Verify the `Activator.java` correctly registers the process provider with the OSH registry.

### 2. Network Isolation (Proxy.NO_PROXY)
- **Action**: Audit all updated sensor drivers (Rapiscan, Aspect, Kromek) in `include/osh-oakridge-modules/sensors/`.
- **Action**: Ensure `TCPCommProvider` or custom socket logic explicitly uses `Proxy.NO_PROXY`.
- **Recommended Code**:
  ```java
  Socket socket = new Socket(Proxy.NO_PROXY);
  socket.connect(new InetSocketAddress(host, port), timeout);
  ```

### 3. LaneSystem & MediaMTX Path Management
- **Action**: Integrate changes to `LaneSystem.java` regarding occupancy event handling.
- **Action**: Ensure `postRemovePath` is called via the MediaMTX REST API when a lane or sensor is deactivated.
- **Recommended Code**:
  ```java
  // In LaneSystem teardown
  String removeUrl = "http://" + mediamtxIp + ":9997/v3/config/paths/remove/" + pathUid;
  httpClient.send(HttpRequest.newBuilder().uri(URI.create(removeUrl)).POST(...).build(), ...);
  ```

## Verification
- Run `./gradlew :sensorhub-service-oscar:test`.
- Deploy in a Tactical Hub scenario and verify that occupancy events from an RS350 sensor trigger the correct state changes in the Lane System.
