# PostgreSQL Setup Guide

## Overview
AppBana now uses PostgreSQL 16 in Docker for better transaction support and production readiness.

## Automatic Setup (Recommended)
The `restart-backend.sh` script automatically manages PostgreSQL Docker:
```bash
./restart-backend.sh
```

This will:
1. Check if PostgreSQL container exists
2. Start it if stopped
3. Create it if doesn't exist
4. Wait for PostgreSQL to be ready
5. Build and start backend

## Manual Docker Commands

### Start PostgreSQL Container
```bash
docker run -d \
  --name appbana-postgres \
  -e POSTGRES_DB=appbana \
  -e POSTGRES_USER=appbana \
  -e POSTGRES_PASSWORD=appbana_dev_2026 \
  -p 5432:5432 \
  -v appbana-postgres-data:/var/lib/postgresql/data \
  postgres:16-alpine
```

### Check Container Status
```bash
docker ps | grep appbana-postgres
```

### View Logs
```bash
docker logs appbana-postgres
```

### Stop Container
```bash
docker stop appbana-postgres
```

### Start Existing Container
```bash
docker start appbana-postgres
```

### Remove Container (⚠️ This deletes data!)
```bash
docker stop appbana-postgres
docker rm appbana-postgres
docker volume rm appbana-postgres-data  # Delete persisted data
```

## Connection Details

| Property | Value |
|----------|-------|
| Host | localhost |
| Port | 5432 |
| Database | appbana |
| Username | appbana |
| Password | appbana_dev_2026 |
| JDBC URL | jdbc:postgresql://localhost:5432/appbana |

## Connect with psql

### From Host Machine (if psql installed)
```bash
psql -h localhost -p 5432 -U appbana -d appbana
# Password: appbana_dev_2026
```

### From Docker Container
```bash
docker exec -it appbana-postgres psql -U appbana -d appbana
```

## Useful SQL Commands

### List Tables
```sql
\dt
```

### List App Version Tables
```sql
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' AND table_name LIKE 'app_%';
```

### Check App Versions
```sql
SELECT app_id, environment, version, entity_count, status, deployed_at 
FROM app_versions 
ORDER BY deployed_at DESC;
```

### View Latest Deployment
```sql
SELECT 
  app_id,
  environment,
  version,
  entity_count,
  tables_created,
  deployment_duration_ms,
  status,
  deployed_at
FROM app_versions
ORDER BY deployed_at DESC
LIMIT 1;
```

## Data Persistence

Data is persisted in Docker volume `appbana-postgres-data`:
```bash
# List volumes
docker volume ls | grep appbana

# Inspect volume
docker volume inspect appbana-postgres-data

# Backup data
docker exec appbana-postgres pg_dump -U appbana appbana > backup.sql

# Restore data
docker exec -i appbana-postgres psql -U appbana appbana < backup.sql
```

## Troubleshooting

### Container won't start
```bash
# Check logs
docker logs appbana-postgres

# Check if port is in use
lsof -i :5432

# Remove and recreate
docker stop appbana-postgres
docker rm appbana-postgres
./restart-backend.sh  # Will recreate
```

### Can't connect from backend
```bash
# Verify container is running
docker ps | grep appbana-postgres

# Test connection
docker exec appbana-postgres pg_isready -U appbana

# Check backend logs
tail -f backend.log | grep -i postgres
```

### Data corruption
```bash
# Stop container
docker stop appbana-postgres

# Remove volume and recreate (⚠️ loses all data)
docker volume rm appbana-postgres-data
./restart-backend.sh
```

## Migration from H2

Your data in H2 (./data/appbana.*) is no longer used. To migrate:
1. Export H2 data: `SELECT * FROM table_name`
2. Import to PostgreSQL: `INSERT INTO table_name ...`

Or start fresh - PostgreSQL will run Flyway migrations automatically.

## Production Deployment

For production, use managed PostgreSQL:
- **Neon.tech**: Serverless PostgreSQL (free tier available)
- **AWS RDS**: Managed PostgreSQL
- **Google Cloud SQL**: Managed PostgreSQL

Update `config.json` with production connection details.
