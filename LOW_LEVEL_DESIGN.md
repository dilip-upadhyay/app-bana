# Low Level Design — AppBana

Purpose
- Provide a concrete, code-oriented map of the project so an automated agent can navigate, extend, and debug efficiently.

Runtime contract
- Input: HTTP requests to:
  - /schema (JSON body: EntitySchema) — create/migrate (POST) and list/load (GET)
  - /api/{entity}[/{id}] — CRUD over JDBC using metadata-defined tables
  - /ui/* — static HTML UIs (builder, datasource, swagger)
  - /openapi.json — generated OpenAPI 3.0 spec
  - /health, /ready — liveness/readiness
  - /ui/datasource/health — per-datasource DB ping
- Auth (optional): when tokens configured, clients must send either header `X-AppBana-Token: <token>` or `Authorization: Bearer <token>`.
  - readToken grants read-only access; adminToken grants write and read.
- Output: JSON for API responses, HTML for UI pages.
- Errors: JSON {"error":"message"}; appropriate HTTP status (400/404/405/500). Health endpoints return 200 with ok=false payloads for failures except /ready uses 503 when DB is not ready.

Ports and processes
- Default HTTP port 8080. Override with -Dappbana.port or APPBANA_PORT.
- Single JVM process. Request handling uses virtual threads (Thread.ofVirtual()).

Threading model
- com.sun.net.httpserver.HttpServer with a per-request virtual thread executor: server.setExecutor(r -> Thread.ofVirtual().start(r)).
- All handlers are short-lived, synchronous JDBC calls; blocking is fine with virtual threads.

Key packages and classes (src/main/java/org/example)
- Main.java — Entrypoint. Initializes SchemaManager.init() and starts ApiServer on configured port.
- ApiServer.java — Binds routes and serves static UIs. Handlers:
  - /schema — SchemaHandler: POST save/preview; GET list; GET by name (auth enforced when enabled)
  - /api — EntityHandler: POST create; GET list; GET/PUT/DELETE by id (auth enforced when enabled)
  - /openapi.json — builds OpenAPI from stored schemas (auth enforced when enabled)
  - /ui/builder — serves builder.html
  - /ui/datasource — serves datasource.html
  - /ui/swagger — serves swagger.html (embedded Swagger UI loading /openapi.json)
  - /ui/datasource/config|list|save|test|activate|delete — JSON endpoints for multi-DS management (auth enforced when enabled)
  - /ui/datasource/health — GET; ping a datasource by name (or active if omitted)
- SchemaManager.java — Schema persistence/migrations (init/save/generateMigrationPlan/list/load)
- JdbcManager.java — Connection acquisition via HikariCP. Uses DriverUtil to infer driver/type; rebuilds pool lazily on config change
- ConfigManager.java — Load/save config JSON (default path), normalize into multi-DS shape; infer missing datasource types via DriverUtil; apply env overrides; apply token env overrides
- DriverUtil.java — Centralized mapping for DB type and JDBC driver inference.
- AppConfig.java — Root config model (legacy single-DS fields kept for back-compat) + datasources[] + activeDatasource + adminToken + readToken.
- DatasourceConfig.java — DS model: {name,type,jdbcUrl,username,password,driver,maxPoolSize?,minIdle?,connectionTimeoutMs?,idleTimeoutMs?,maxLifetimeMs?,autoCommit?,poolName?, lastTest*?}.
- OpenApiGenerator.java — Converts stored EntitySchema list into a minimal OpenAPI 3.0 document.
- model/EntitySchema.java — Schema model with Field sub-class (name,type,length,required,primaryKey,autoIncrement,min,max,pattern).

Static resources (src/main/resources/ui)
- builder.html — Minimal schema builder UI (posts JSON to /schema; includes an Auth token box; sends X-AppBana-Token and Authorization headers).
- datasource.html — Multi-datasource management UI with a JDBC URL Builder and a Test Connection button, plus per-row Test and Ping actions. Shows Status chip and Last tested. Fields: name, type, jdbcUrl, username, password, driver, and Pool section (maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName). Actions: Save/Activate/Delete/List/Load/Test/Ping. Includes Auth token box and sends token header.
- swagger.html — Embedded Swagger UI for /openapi.json with an Auth token box; uses requestInterceptor to attach token headers on all requests (including initial spec fetch).

Datasource URL construction (details)
- UI-side builder: builds the JDBC URL in the browser from type + host/port/db/params and writes it to the `url` field.
- Server-side construction: if client omits `url` in POST /ui/datasource/save, ApiServer.buildJdbcUrl() constructs it using submitted components.
  - Common fields: type, host, port, dbname, params
  - H2-only: h2Mode (file|mem), h2File, h2MemName
  - SQLite-only: sqliteFile
  - Driver inference via DriverUtil when driver not provided.

Test Connection and health
- POST /ui/datasource/test: one-off connection attempt. Supports timeoutSec (default 5; max 60). Response includes ok/message|error, masked url, dbProduct/dbVersion on success, elapsedMs, and optional sqlState/errorCode for SQLExceptions. When testing a saved datasource by name, the result is persisted into DatasourceConfig.lastTest* fields. (Auth: admin)
- GET /ui/datasource/health: quick ping for a saved datasource (or active if none specified). Does not persist; returns ok flag and timings. (Auth: read or admin)

Config resolution
- Path: APPBANA_CONFIG or -Dappbana.config; default data/appbana-config.json.
- When no file exists, ConfigManager returns defaults and normalizes to a single H2 datasource named "default".
- Env overrides on root fields only: APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER. Token overrides: APPBANA_ADMIN_TOKEN, APPBANA_READ_TOKEN.

Database mapping rules
- Table name = UPPERCASE(schema.name). Column names = UPPERCASE(field.name). Quoted identifiers with double-quotes.
- Supported field types → SQL types (simplified). Primary key handling and auto-increment as before.

Validation and coercion (EntityHandler)
- Required, number ranges, string length/patterns, timestamp parsing (epoch millis or ISO-8601). Bad input → 400.

Error handling
- send()/sendJson() helpers. 405 for wrong methods; 404 for unknown entity or missing schema; 500 for unexpected errors.

Performance notes
- Virtual threads minimize thread overhead for blocking JDBC per request. HikariCP settings default to conservative values.

Observability
- SLF4J simple logs. Health endpoints for liveness/readiness and per-DS ping.

Security considerations
- Token-based authentication (optional) is implemented. If adminToken/readToken in AppConfig (or env/system props) are blank, auth is disabled (development mode). If configured:
  - Read operations require readToken or adminToken; write operations require adminToken.
  - Clients may send `X-AppBana-Token` or `Authorization: Bearer`.
  - UIs (builder, datasource, swagger) include a token input that stores the token in localStorage and injects headers into requests.

Developer workflows
- Build: ./mvnw -DskipTests package → target/app-bana-1.0-SNAPSHOT-fat.jar
- Run: java -jar target/app-bana-1.0-SNAPSHOT-fat.jar (override port with -Dappbana.port=8081)
- Edit UI: update files in resources/ui; ApiServer serves them at /ui/*

Edge cases to watch
- Port conflicts → BindException; use a different -Dappbana.port
- Wrong DB credentials → startup warns; fix via /ui/datasource and retry
- Missing password on save → does not overwrite existing (by design)
- No schemas stored → /openapi.json returns an empty paths object (valid)

Test checklist (manual)
- With tokens set, ensure 401 is returned when missing/invalid token; success when valid.
- GET /ui/datasource/list returns array with lastTest* fields
- POST /ui/datasource/save with new name creates + activates; list shows it
- POST /ui/datasource/test with name persists lastTest*; list shows Status and Last tested updated
- GET /ui/datasource/health?name=<ds> shows live/down with elapsedMs; Ping button updates the row
- POST /schema?preview=true returns DDL plan; POST /schema applies
- CRUD endpoints operate as expected; GET /openapi.json reflects entities; /ui/swagger renders the spec
