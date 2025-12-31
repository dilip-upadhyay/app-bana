# Database Persistence Configuration

## Overview
AppBana now supports **persistent H2 database storage** that retains data across server restarts. This is controlled by the `flywayCleanOnStart` configuration flag.

## Default Behavior
- **Database persists by default** (`flywayCleanOnStart=false`)
- Data is stored in: `./data/appbana.mv.db`
- All data (users, entities, permissions, etc.) survives restarts

## Configuration

### Method 1: Using config.json (Recommended)
Edit `config.json` in the project root:

```json
{
  "jdbcUrl": "jdbc:h2:./data/appbana;AUTO_SERVER=TRUE",
  "flywayCleanOnStart": false
}
```

### Method 2: Environment Variable
```bash
export APPBANA_FLYWAY_CLEAN_ON_START=false
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar
```

### Method 3: JVM System Property
```bash
java -Dappbana.flywayCleanOnStart=false -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar
```

## Usage Scenarios

### ✅ Production Mode (Default)
**Keep your data:**
```json
"flywayCleanOnStart": false
```
- Data persists across restarts
- Migrations only run if schema changes
- Safe for production use

### ⚠️ Development Mode (Clean Database)
**Start fresh every time:**
```json
"flywayCleanOnStart": true
```
- **WARNING:** This deletes ALL data on every restart
- Useful for testing with clean state
- Use only in development

## Verifying Configuration

Check the backend logs on startup:

**Persistence Enabled:**
```
✅ Database persistence enabled (flywayCleanOnStart=false)
```

**Clean Mode (DANGER):**
```
⚠️ CLEANING DATABASE - ALL DATA WILL BE LOST (flywayCleanOnStart=true)
```

## Manual Database Operations

### Clean Database Manually
If you want to clean the database once without changing the config:

```bash
# Stop the backend
kill $(cat backend.pid)

# Delete the database files
rm -rf data/appbana.*

# Restart the backend
./restart-backend.sh
```

### Backup Database
```bash
# Create a backup
cp data/appbana.mv.db data/appbana.mv.db.backup

# Restore from backup
cp data/appbana.mv.db.backup data/appbana.mv.db
```

### View Database Contents
Use the H2 Console (if enabled):
```bash
# Start H2 Console
java -cp app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar org.h2.tools.Console

# Or use the built-in web console at:
# http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:./data/appbana
# Username: sa
# Password: (empty)
```

## Migration Strategy

### Development → Production
1. **During Development:**
   - Use `flywayCleanOnStart=true` to test migrations
   - Test with fresh data each restart

2. **Before Production:**
   - Set `flywayCleanOnStart=false`
   - Backup the database: `cp data/appbana.mv.db production.backup`

3. **In Production:**
   - Keep `flywayCleanOnStart=false` always
   - Regular backups recommended
   - Flyway will handle schema migrations automatically

## Troubleshooting

### Issue: Database gets cleaned on restart
**Solution:** Check `config.json` and ensure `"flywayCleanOnStart": false`

### Issue: Old data appears after setting clean=true
**Solution:** The database file still exists. Delete it manually:
```bash
rm -rf data/appbana.*
```

### Issue: Cannot write to database
**Solution:** Check file permissions:
```bash
chmod -R 755 data/
```

### Issue: Database locked
**Solution:** Another process is using it. Stop all AppBana instances:
```bash
kill $(cat backend.pid)
rm data/appbana.lock.db
```

## Best Practices

1. **Production:** Always use `flywayCleanOnStart=false`
2. **Backups:** Regular database backups before deploying changes
3. **Testing:** Use `flywayCleanOnStart=true` only in isolated dev environments
4. **Version Control:** Never commit `data/` directory to git (already in `.gitignore`)
5. **Monitoring:** Check logs on startup to confirm correct mode

## Related Files

- **Configuration:** `config.json`
- **Code:** `AppConfig.java`, `ServerBootstrap.java`, `ApiServer.java`
- **Database:** `data/appbana.mv.db` (file-based storage)
- **Migrations:** `src/main/resources/db/migration/V*.sql`

---

**Last Updated:** December 31, 2025  
**Default Setting:** `flywayCleanOnStart=false` (Data persists)
