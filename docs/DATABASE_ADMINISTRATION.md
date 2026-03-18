# OSCAR Database Administration

## Overview
The OSCAR system relies on PostgreSQL with the PostGIS extension to handle high-frequency time-series data from sensor arrays, as well as complex spatial operations.

## Core Schema and Spatial Extensions
The primary database (default name: `gis`) leverages several crucial PostgreSQL extensions:
- **`postgis`**: Provides core spatial types and functions.
- **`postgis_tiger_geocoder`** and **`postgis_topology`**: Support advanced geocoding and topological routing features.
- **`pg_trgm`**: Used for fast text similarity searching.
- **`btree_gist`** and **`btree_gin`**: Provide generalized index structures essential for querying spatial and temporal overlap efficiently.
- **`fuzzystrmatch`**: Helps in approximate string matching and normalization algorithms.

These extensions are automatically initialized when the `osh-postgis` container is created (see `dist/release/postgis/init-extensions.sql`).

## Database Performance Profiles
The default PostgreSQL configuration can be insufficient for larger deployments managing heavy sensor loads (e.g., 50+ Radiation Portal Monitors). To solve this, the `osh-postgis` container supports "Performance Profiles", managed via the `DB_PERFORMANCE_PROFILE` environment variable.

You can set this variable in your deployment `.env` file (located in `dist/release/.env` or populated within your deployment shell script).

**Available Profiles:**

### 1. `edge` Profile (Default)
Optimized for single-machine, edge deployments with limited resources.
- `shared_buffers`: 256MB
- `work_mem`: 4MB
- `maintenance_work_mem`: 64MB
- `wal_buffers`: 4MB
- `checkpoint_timeout`: 5min
- `max_wal_size`: 1GB
- `max_connections`: 100

### 2. `hub` Profile
Optimized for central hub deployments on heavier hardware. Handles high-frequency write spikes and complex concurrent spatial/temporal queries.
- `shared_buffers`: 4GB
- `work_mem`: 64MB
- `maintenance_work_mem`: 512MB
- `wal_buffers`: 16MB
- `checkpoint_timeout`: 15min
- `max_wal_size`: 4GB
- `max_connections`: 1024

**To apply a profile:**
Set the environment variable in your `.env` file:
```env
DB_PERFORMANCE_PROFILE=hub
```
*(Note: Changing the profile requires restarting the `osh-postgis` container. The parameters are passed dynamically via the entrypoint script each time the container starts.)*

## Database Maintenance Operations
Proper administration involves maintaining data integrity and keeping backups up-to-date.

### Backing Up the Database
A utility script `backup.sh` (or `backup.bat` on Windows) is located in the repository root. This script connects to the container using `docker exec` and creates a compressed database dump. It automatically sources the secure `POSTGRES_PASSWORD_FILE` (created during initial setup as a Docker Secret) to bypass manual credential entry.

**Usage:**
```bash
./backup.sh
```

### Restoring the Database
To restore a previously backed-up dump, use the `restore.sh` (or `restore.bat`) utility in the repository root.

**Usage:**
```bash
./restore.sh <path_to_dump_file>
```

Always ensure the `osh-postgis` container is running and healthy prior to running backup or restore scripts.