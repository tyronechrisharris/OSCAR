# Database Administration Guide

This document details the database architecture, performance tuning, and administrative procedures for the OSCAR system.

## Database Architecture

OSCAR uses a **PostGIS-enabled PostgreSQL** database for storing spatial-temporal sensor data.

### Core Schema and Extensions

The following PostgreSQL extensions are utilized:
- **postgis**: Provides spatial objects and functions.
- **postgis_topology**: Adds support for topological spatial objects.
- **postgis_tiger_geocoder**: Integration with TIGER/Line data.
- **pg_trgm**: Trigonometric similarity for text search.
- **btree_gist**: GiST index support for scalar data types.
- **btree_gin**: GIN index support for scalar data types.
- **fuzzystrmatch**: String matching and similarity functions.

## Performance Profiles

The `osh-postgis` container supports configurable performance profiles to accommodate different deployment scales.

### Configuration

The performance profile is controlled via the `DB_PERFORMANCE_PROFILE` environment variable in the `.env` file (located in `dist/release/`). These settings are applied at runtime by the launch scripts via command-line flags.

```bash
# Options: edge | hub
DB_PERFORMANCE_PROFILE=edge
```

### Profile Details

| Parameter | Edge (Default) | Hub |
| :--- | :--- | :--- |
| `shared_buffers` | 128MB | 1GB |
| `work_mem` | 4MB | 32MB |
| `maintenance_work_mem` | 64MB | 256MB |
| `wal_buffers` | 4MB | 16MB |
| `checkpoint_timeout` | 5min | 10min |
| `max_wal_size` | 1GB | 2GB |
| `max_connections` | 100 | 1000 |

- **Edge**: Optimized for low-resource deployments or individual sensor nodes.
- **Hub**: Optimized for central hub deployments managing large sensor arrays (e.g., 50+ RPMs) and high-frequency writes.

## Security

### Authentication
- All database connections MUST use **SCRAM-SHA-256** authentication.
- Passwords are managed via **Docker Secrets** (using the `POSTGRES_PASSWORD_FILE` environment variable).

### Encryption
- Connections from the OSH backend are secured over **TLS**.
- The database container generates its own self-signed certificates on startup.

## Maintenance Procedures

### Backup

To perform a manual backup of the database:
- Run `./backup.sh` (Unix/Linux/macOS) or `backup.bat` (Windows) from the repository root.
- The script uses the `DB_HOST` and `POSTGRES_PASSWORD_FILE` environment variables.

### Restore

To restore the database from a dump:
- Run `./restore.sh` (Unix/Linux/macOS) or `restore.bat` (Windows) from the repository root.
- **Warning**: This will overwrite the existing database content.
