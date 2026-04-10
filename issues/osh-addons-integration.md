# Issue: Integrate osh-addons-upgrade.patch (v3.0.0 -> v3.3.1)

## Overview
Focus on the PostGIS persistence layer hardening and FFmpeg driver updates, ensuring high-precision timestamp support is preserved.

## Actionable Items

### 1. PostGIS PreparedStatement Hardening
- **Action**: Update `QueryBuilderObsStore.java` to include `insertObsPreparedQuery`.
- **Action**: Update `PostgisObsStoreImpl.java` to use `prepareStatement` for observations.
- **Recommended Code** (in `QueryBuilderObsStore.java`):
  ```java
  public String insertObsPreparedQuery() {
      return "INSERT INTO "+this.getStoreTableName()+" " +
             "(id,"+DATASTREAM_ID+", "+FOI_ID+", "+PHENOMENON_TIME+", "+RESULT_TIME+", "+RESULT+") " +
             "VALUES (?::int8,?::int8,?::int8,?::timestamp,?::timestamp,?::jsonb) " +
             "ON CONFLICT ("+DATASTREAM_ID+", COALESCE("+FOI_ID+", -1::bigint), "+PHENOMENON_TIME+", "+RESULT_TIME+") DO NOTHING";
  }
  ```

### 2. Timestamp Precision Preservation
- **Action**: Ensure `PostgisUtils.java` is used for binding parameters in `fillPreparedAddStatement`.
- **Action**: Verify that `?::timestamp` doesn't truncate the `TIMESTAMPTZ` values we use in `oscar-flat`. If necessary, change to `?::timestamptz`.

### 3. String Escaping for Batch Paths
- **Action**: In `PostgisObsStoreImpl.java`, ensure single quotes are escaped when interpolating JSONB for the non-prepared batch path.
- **Recommended Code**:
  ```java
  String escapedBlock = serializedBlock.replace("'", "''");
  values.put("6", "'"+escapedBlock+"'");
  ```

## Verification
- Run `./gradlew :sensorhub-datastore-postgis:test`.
- Connect to the DB and verify that `phenomenon_time` column has nanosecond precision (e.g., `2024-01-01 12:00:00.123456789`).
