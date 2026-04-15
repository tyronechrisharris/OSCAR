# RS350 Occupancy Process Module

This module provides specialized radiation-aware occupancy processing for the RS350 Radiation Portal Monitor.

## Overview
The RS350 driver publishes raw radiation measurements and alarm states. This process module monitors those outputs and generates `OccupancyExtended` records.

## Features
- **Extended Schema**: Supports `alarmCategoryCode` to preserve the specific type of radiation alarm (e.g., Gamma, Neutron, Isotope).
- **WebID Correlation**: Automatically triggers WebID analysis requests when alarms occur and correlates the results with the specific occupancy event.
- **Bucket Archival**: If enabled, WebID analysis results are archived to the Bucket Service for long-term storage and retrieval.

## Configuration
- **Parent System**: The UID of the system containing the RS350 RPM.
- **WebID Integration**: Enable/Disable automated WebID analysis.
- **Bucket Archival**: Enable/Disable storing results in the bucket service.
- **Timeouts and Delays**: Configurable delays for WebID publishing and message timeouts.

## Dependencies
- `sensorhub-driver-rs350`: Raw data source.
- `sensorhub-utils-rad`: Provides the extended occupancy schema and N42 utilities.
- `sensorhub-service-oscar`: Handles WebID and archival orchestration.

## Fallback Behavior
The module is designed to degrade gracefully:
- If WebID services are unavailable, occupancy records are still produced without analysis results.
- If the Bucket Service is missing, results are published but not archived.
