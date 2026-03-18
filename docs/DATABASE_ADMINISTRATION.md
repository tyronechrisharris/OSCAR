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
To support varying deployment sizes without risking resource exhaustion or starvation, we provide predefined configuration profiles. These profiles map directly to variables exposed in our `docker-compose.yml`.

Configurations are stored in `config/profiles.ini` and `config/profiles.toml` for standard configuration management.

### Available Profiles

**1. EdgeNode** (Default)
Optimized for lightweight, single-machine deployments.
- Database Memory Limit: 1G
- Backend Memory Limit: 2G
- Tuned for 50 max connections, 128MB shared buffers.

**2. CentralHub**
Designed for a 16GB server shared between the database and the Java backend.
- Database Memory Limit: 6G
- Backend Memory Limit: 8G
- Tuned for 100 max connections, 4GB shared buffers.

**3. DedicatedDB**
Designed for a 16GB server exclusively running the database (the backend is hosted elsewhere).
- Database Memory Limit: 14G
- Backend Memory Limit: 0G
- Tuned for 200 max connections, 4GB shared buffers, and 12GB effective cache size.

### How to Apply Profiles
To apply these profiles, populate your deployment's environment variables (e.g., via a `.env` file in `dist/release/` or exporting them in your shell prior to deployment).

For example, to apply the **CentralHub** profile, your `.env` should look like:
```env
DB_MAX_CONNECTIONS=100
DB_SHARED_BUFFERS=4GB
DB_EFFECTIVE_CACHE_SIZE=8GB
DB_MAINTENANCE_WORK_MEM=512MB
DB_WAL_BUFFERS=16MB
DB_WORK_MEM=16MB
DB_MAX_WAL_SIZE=4GB
DB_CHECKPOINT_TIMEOUT=15min
DB_MEM_LIMIT=6G
BACKEND_MEM_LIMIT=8G
```
Restart your containers for the new limits and variables to take effect.

## Database Maintenance Operations

### Backing Up the Database
A utility script `backup.sh` (or `backup.bat` on Windows) is located in the repository root. This script safely connects to the container and creates a database dump. It automatically utilizes the secure `POSTGRES_PASSWORD_FILE` (created during initial setup as a Docker Secret) to bypass manual credential entry.

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