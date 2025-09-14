# Copilot Notes — project snapshot

This file summarizes the current state so an automated agent can resume work confidently.

## Change Log (recent)
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
  - /ui/datasource/* — JSON endpoints (list/config/save/activate/delete)
- SchemaManager.java — validates/persists schema JSON; creates/migrates tables; migration preview.
- JdbcManager.java — uses active datasource to create connections; infers driver from type/URL; ensures meta tables.
- ConfigManager.java — loads/saves config JSON; normalizes to multi-DS shape; applies env overrides; seeds default.
- AppConfig.java — root config (legacy single-DS fields retained for back-compat) + datasources[] + activeDatasource.
- DatasourceConfig.java — {name,type,jdbcUrl,username,password,driver}.
- model/EntitySchema.java — schema model.
- OpenApiGenerator.java — builds /openapi.json from saved schemas.

Frontend (resources/ui)
- builder.html — minimal schema builder posting to /schema.
- datasource.html — multi-DS management UI with Type selector and driver/URL hints.

## Behavior and contracts
- Active datasource governs all DB ops. Changing active affects subsequent connections.
- Passwords are never returned by list/config endpoints. Blank password on save doesn’t overwrite existing.
- Driver inference mapping:
  - h2→org.h2.Driver; postgres→org.postgresql.Driver; mysql→com.mysql.cj.jdbc.Driver; mariadb→org.mariadb.jdbc.Driver; mssql→com.microsoft.sqlserver.jdbc.SQLServerDriver; oracle→oracle.jdbc.OracleDriver; sqlite→org.sqlite.JDBC.

## Config model
- Path: APPBANA_CONFIG env or -Dappbana.config (default data/appbana-config.json).
- Shape:
```
{
  "datasources": [
    {"name":"primary","type":"h2","jdbcUrl":"jdbc:h2:./data/appbana;AUTO_SERVER=TRUE","username":"sa","password":"secret","driver":"org.h2.Driver"}
  ],
  "activeDatasource": "primary"
}
```
- Back-compat: if only root fields exist (jdbcUrl/username/password/driver/name), ConfigManager seeds datasources[0] and sets active.
- Env overrides (root): APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER.

## Useful endpoints (JSON)
- GET /ui/datasource/list → [{name,type,jdbcUrl,username,driver,active}]
- GET /ui/datasource/config → {name,jdbcUrl,username,driver,type}
- POST /ui/datasource/save → {name,type?,url,username?,password?,driver?}
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
- Open /ui/datasource, add a new datasource by name and type; Save and Activate.
- Open /ui/builder, create a test schema; POST to /schema; verify CRUD at /api/{entity}.
- Fetch /openapi.json to confirm spec includes your entity.

## Next steps (suggested)
- Add auth to /schema, /api/*, and /ui/datasource/*.
- Add connection pooling (HikariCP) and connectivity test button in datasource UI.
- Add Swagger UI page to render /openapi.json.
