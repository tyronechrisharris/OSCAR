# Issue: Hardened Persistence and Core Data Integrity

## Description
Refactor the PostGIS persistence layer and core JSON parsing logic to mitigate security risks and ensure data integrity for complex rad-telemetry payloads introduced in OSH v3.3.1.

## Logical Intent
Upstream updates include complex nested data structures for spectroscopic analysis (WebID). These structures require robust persistence to prevent SQL injection and data loss during serialization.

## Implementation Details
1.  **SQL Injection Mitigation**:
    *   Refactor `PostgisObsStoreImpl.java` and `QueryBuilderObsStore.java` to utilize `PreparedStatement` for all telemetry queries.
    *   Ensure that JSONB payloads are handled as parameters rather than string-interpolated literals.
2.  **Datastream Scoping**:
    *   Update `findByUniqueFieldsQuery` to be strictly scoped by `DATASTREAM_ID`. This prevents potential data corruption where lookups might return observations from different sensors with overlapping metadata.
3.  **JSON Parser Fix**:
    *   Patch `JsonDataParserGson.java` in the core library to correctly handle nested variable-sized arrays.
    *   Verify that `updateSize()` calls in `RADHelper.java` (e.g., `createWebIdRecord`) correctly influence the parser to avoid truncating spectroscopic data.
4.  **Security Architecture**:
    *   Maintain the restriction that the PostGIS port (5432) is only accessible within the Docker bridge network.
    *   Verify that database credentials used by the backend are retrieved securely via the `init-secrets` mechanism.

## Acceptance Criteria
- [ ] No string interpolation is used for user-controlled data in SQL queries.
- [ ] Large WebID observations (multiple isotopes, confidence values) are stored and retrieved without data loss.
- [ ] Search queries for specific occupancy IDs are strictly partitioned by datastream.
- [ ] All database-related unit tests in `osh-core` and `osh-oakridge-modules` pass.
