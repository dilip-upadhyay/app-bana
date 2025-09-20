# Copilot Notes — project snapshot

This file summarizes the current state so an automated agent can resume work confidently.

Backlog: see `TODO.md` for a prioritized to-do list and next actions.

## Contributor checklist (please follow on every change)
- Update all docs to reflect changes (keep these synchronized):
  - README.md (usage, endpoints, how-to)
  - FUNCTIONAL_SPEC.md (functional behavior and contracts)
  - LOW_LEVEL_DESIGN.md (structure, modules, data contracts)
  - COPILOT_NOTES.md (this file — snapshot and workflow notes)
- Build and smoke test locally (override port if busy):
  - ./mvnw -DskipTests package
  - java -Dappbana.port=8081 -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- Verify key routes: /ui/datasource, /ui/builder, /openapi.json, /ui/swagger.
- If you change datasource behavior, test save/list/activate/delete/test paths and pool settings.
- If you change OpenAPI, verify /openapi.json and Swagger UI rendering.

## Change Log (recent)
- 2025-09-20: Per-datasource health + last test persistence; driver util centralization.
  - Backend: GET /ui/datasource/health pings a datasource (by name or active) and returns {ok,name,url(masked),dbProduct?,dbVersion?,elapsedMs,error?}.
  - Backend: POST /ui/datasource/test now accepts timeoutSec, returns sqlState/errorCode for SQLExceptions, masks passwords in URL, and persists lastTest* fields when testing a saved datasource by name.
  - Model: DatasourceConfig gains lastTestOk, lastTestAtEpochMs, lastTestMessage, lastTestDbProduct, lastTestDbVersion, lastTestElapsedMs.
  - UI: datasource.html shows a Status chip (Live/Down/Unknown) and a Last tested column; added a Ping action to call /ui/datasource/health.
  - Refactor: centralized driver/type inference into DriverUtil and updated ApiServer/JdbcManager/ConfigManager to use it.
- 2025-09-19: Added Test Connection feature for datasources.
  - Backend: POST /ui/datasource/test attempts a short-lived JDBC connection from provided url or components (or by name); returns {ok,message|error,url,dbProduct,dbVersion,elapsedMs}.
  - UI: “Test Connection” button on the form; per-row “Test” action in the list.
- 2025-09-19: Upgraded to Java 25; HTTP server now uses virtual threads; added Swagger UI.
  - Maven set to java.version=25; app runs on Java 25.
  - ApiServer uses virtual-thread-per-request executor (Thread.ofVirtual()).
  - Port is configurable via -Dappbana.port or APPBANA_PORT (default 8080).
  - Added /ui/swagger which serves an embedded Swagger UI for /openapi.json.
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
  - Startup resiliency: server still starts if DB init fails to allow fixing datasource via /ui/datasource.
- 2025-09-14: OpenAPI endpoint added at /openapi.json (generated from saved schemas).
- 2025-09-14: UI kept minimal for schema builder (builder-v1).

## Key components
- Main.java — entrypoint. Starts SchemaManager.init() (best-effort) and ApiServer on configured port.
- ApiServer.java — HTTP server with handlers:
  - /schema — save/preview/list/load schemas
  - /api/* — runtime CRUD for saved entities
  - /openapi.json — OpenAPI 3.0 for all CRUD endpoints
  - /ui/builder — serves builder.html
  - /ui/datasource — serves datasource.html
  - /ui/swagger — serves swagger.html (Swagger UI for /openapi.json)
  - /ui/datasource/* — JSON endpoints (list/config/save/test/activate/delete/health) — include pool fields, persist last test metadata, and testing/health endpoints
- SchemaManager.java — validates/persists schema JSON; creates/migrates tables; migration preview.
- JdbcManager.java — HikariCP pool for the active datasource; infers driver from type/URL (via DriverUtil); ensures meta tables.
- ConfigManager.java — loads/saves config JSON; normalizes to multi-DS shape; applies env overrides; seeds default; infers missing types via DriverUtil.
- AppConfig.java — root config + datasources[] + activeDatasource.
- DatasourceConfig.java — {name,type,jdbcUrl,username,password,driver,maxPoolSize?,minIdle?,connectionTimeoutMs?,idleTimeoutMs?,maxLifetimeMs?,autoCommit?,poolName?, lastTest*?}.
- model/EntitySchema.java — schema model.
- OpenApiGenerator.java — builds /openapi.json from saved schemas.

Frontend (resources/ui)
- builder.html — minimal schema builder posting to /schema.
- datasource.html — multi-DS management UI with Type selector, driver/URL hints, JDBC URL Builder, Connection Pool section, Test Connection, per-row Test, Status chip (Live/Down/Unknown), Last tested, and Ping.
- swagger.html — embedded Swagger UI for /openapi.json at /ui/swagger.

## Behavior and contracts
- Active datasource governs all DB ops. Changing active or pool config rebuilds the pool on next use.
- Passwords are never returned by list/config endpoints. Blank password on save doesn’t overwrite existing.
- JDBC URL construction (server-side): if `url` is omitted in POST /ui/datasource/save, the server builds it from components.
- Test Connection: POST /ui/datasource/test returns structured result; masks password in URL; persists last test if testing by name; supports timeoutSec.
- Health: GET /ui/datasource/health tests connectivity for a named or active datasource without persisting.
- Driver inference mapping centralized in DriverUtil.

## Config model
- Path: APPBANA_CONFIG env or -Dappbana.config (default data/appbana-config.json).
- See README for example shape. DatasourceConfig includes pool and last test fields.

## Useful endpoints (JSON)
- GET /ui/datasource/list → [{name,type,jdbcUrl,username,driver,active,maxPoolSize,minIdle,connectionTimeoutMs,idleTimeoutMs,maxLifetimeMs,autoCommit,poolName,lastTestOk,lastTestAtEpochMs,lastTestMessage,lastTestDbProduct,lastTestDbVersion,lastTestElapsedMs}]
- GET /ui/datasource/config → {name,jdbcUrl,username,driver,type,maxPoolSize,minIdle,connectionTimeoutMs,idleTimeoutMs,maxLifetimeMs,autoCommit,poolName}
- POST /ui/datasource/save → accepts a full `url` or components; also pool fields
- POST /ui/datasource/test → one-off connection test; supports timeoutSec; masks password; persists last test for named DS
- GET /ui/datasource/health → ping a datasource (by name or active)
- POST /ui/datasource/activate → {name}
- POST /ui/datasource/delete → {name}
- POST /schema?preview=true → returns planned DDL (no changes)
- POST /schema → apply schema (create/migrate)
- CRUD: POST/GET /api/{entity}, GET/PUT/DELETE /api/{entity}/{id}
- OpenAPI: GET /openapi.json; Swagger UI: GET /ui/swagger

## Startup and troubleshooting
- If default port 8080 is busy, override with -Dappbana.port=8081 or APPBANA_PORT.
- If DB init fails (wrong creds), server still starts; use /ui/datasource to fix and retry operations.

## Quick local smoke (manual)
- Open /ui/datasource, add or load a datasource; optionally set pool fields; Save (auto-activates); then refresh list.
- Use the Test Connection button or per-row Test action to validate connectivity; Ping for a quick status.
- Open /ui/builder, create a test schema; POST to /schema; verify CRUD at /api/{entity}.
- Fetch /openapi.json or visit /ui/swagger to confirm spec includes your entity.

## Next steps (suggested)
- Add auth to /schema, /api/*, and /ui/datasource/*.
- Add metrics and structured request logs; basic rate limiting for test/health endpoints.
- Expand OpenAPI with response schemas and examples.
