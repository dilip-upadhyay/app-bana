# Copilot Notes — project snapshot

This file summarizes the current state so an automated agent can resume work confidently.

## Change Log (recent)
- 2025-09-19: Connection pooling via HikariCP with per-datasource settings:
  - Added pool fields to DatasourceConfig: maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName.
  - JdbcManager now builds a HikariCP pool for the active datasource; pool is rebuilt lazily when config changes.
  - UI (/ui/datasource) extended with a “Connection Pool” section to configure these fields.
  - Endpoints /ui/datasource/list and /ui/datasource/config return pool fields; /ui/datasource/save accepts them.
  - Sensible defaults when fields omitted: maxPoolSize=10, minIdle=2, connectionTimeoutMs=30000, idleTimeoutMs=600000, maxLifetimeMs=1800000, autoCommit=true, poolName="appbana-<name>".
- 2025-09-14: Multi-datasource support added (UI + backend):
  - New UI at /ui/datasource for Add/Update/List/Activate/Delete.
  - New endpoints: GET /ui/datasource/list, GET /ui/datasource/config, POST /ui/datasource/save, POST /ui/datasource/activate, POST /ui/datasource/delete.
  - Config model extended: AppConfig.datasources[] + activeDatasource; DatasourceConfig {name,type,jdbcUrl,username,password,driver}.
  - Driver inference from type/URL when driver blank.
  - Startup resiliency: server still starts if DB init fails to allow fixing datasource via UI.
- 2025-09-14: OpenAPI endpoint added at /openapi.json (generated from saved schemas).
- 2025-09-14: UI kept minimal for schema builder (builder-v1).

## Key components
- Main.java — entrypoint. Starts SchemaManager.init() (best-effort) and ApiServer on port 8080.
- ApiServer.java — HTTP server with handlers:
  - /schema — save/preview/list/load schemas
  - /api/* — runtime CRUD for saved entities
  - /openapi.json — OpenAPI 3.0 for all CRUD endpoints
  - /ui/builder — serves builder.html
  - /ui/datasource — serves datasource.html
  - /ui/datasource/* — JSON endpoints (list/config/save/activate/delete) — now include pool fields
- SchemaManager.java — validates/persists schema JSON; creates/migrates tables; migration preview.
- JdbcManager.java — HikariCP pool for active datasource; infers driver from type/URL; ensures meta tables.
- ConfigManager.java — loads/saves config JSON; normalizes to multi-DS shape; applies env overrides; seeds default.
- AppConfig.java — root config (legacy single-DS fields retained for back-compat) + datasources[] + activeDatasource.
- DatasourceConfig.java — {name,type,jdbcUrl,username,password,driver,maxPoolSize?,minIdle?,connectionTimeoutMs?,idleTimeoutMs?,maxLifetimeMs?,autoCommit?,poolName?}.
- model/EntitySchema.java — schema model.
- OpenApiGenerator.java — builds /openapi.json from saved schemas.

Frontend (resources/ui)
- builder.html — minimal schema builder posting to /schema.
- datasource.html — multi-DS management UI with Type selector, driver/URL hints, and Connection Pool section.

## Behavior and contracts
- Active datasource governs all DB ops. Changing active or pool config rebuilds the pool on next use.
- Passwords are never returned by list/config endpoints. Blank password on save doesn’t overwrite existing.
- Driver inference mapping:
  - h2→org.h2.Driver; postgres→org.postgresql.Driver; mysql→com.mysql.cj.jdbc.Driver; mariadb→org.mariadb.jdbc.Driver; mssql→com.microsoft.sqlserver.jdbc.SQLServerDriver; oracle→oracle.jdbc.OracleDriver; sqlite→org.sqlite.JDBC.
- Pool defaults when unset: maxPoolSize=10, minIdle=2, connectionTimeoutMs=30000, idleTimeoutMs=600000, maxLifetimeMs=1800000, autoCommit=true, poolName="appbana-<name>".

## Config model
- Path: APPBANA_CONFIG env or -Dappbana.config (default data/appbana-config.json).
- Shape:
```
{
  "datasources": [
    {"name":"primary","type":"h2","jdbcUrl":"jdbc:h2:./data/appbana;AUTO_SERVER=TRUE","username":"sa","password":"secret","driver":"org.h2.Driver",
     "maxPoolSize":10,"minIdle":2,"connectionTimeoutMs":30000,"idleTimeoutMs":600000,"maxLifetimeMs":1800000,"autoCommit":true,"poolName":"appbana-primary"}
  ],
  "activeDatasource": "primary"
}
```
- Back-compat: if only root fields exist (jdbcUrl/username/password/driver/name), ConfigManager seeds datasources[0] and sets active.
- Env overrides (root): APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER.

## Useful endpoints (JSON)
- GET /ui/datasource/list → [{name,type,jdbcUrl,username,driver,active,maxPoolSize,minIdle,connectionTimeoutMs,idleTimeoutMs,maxLifetimeMs,autoCommit,poolName}]
- GET /ui/datasource/config → {name,jdbcUrl,username,driver,type,maxPoolSize,minIdle,connectionTimeoutMs,idleTimeoutMs,maxLifetimeMs,autoCommit,poolName}
- POST /ui/datasource/save → {name,type?,url,username?,password?,driver?,maxPoolSize?,minIdle?,connectionTimeoutMs?,idleTimeoutMs?,maxLifetimeMs?,autoCommit?,poolName?}
- POST /ui/datasource/activate → {name}
- POST /ui/datasource/delete → {name}
- POST /schema?preview=true → returns planned DDL (no changes)
- POST /schema → apply schema (create/migrate)
- CRUD: POST/GET /api/{entity}, GET/PUT/DELETE /api/{entity}/{id}
- OpenAPI: GET /openapi.json

## Startup and troubleshooting
- If port 8080 is busy, free it before start (BindException).
- If DB init fails (wrong creds), server still starts; use /ui/datasource to fix and retry operations.

## Quick local smoke (manual)
- Open /ui/datasource, add or load a datasource; optionally set pool fields; Save (auto-activates); then refresh list.
- Open /ui/builder, create a test schema; POST to /schema; verify CRUD at /api/{entity}.
- Fetch /openapi.json to confirm spec includes your entity.

## Next steps (suggested)
- Add auth to /schema, /api/*, and /ui/datasource/*.
- Add per-datasource connectivity test button in datasource UI and a health endpoint.
- Add Swagger UI page to render /openapi.json.
