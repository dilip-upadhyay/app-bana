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
- HTTPS (optional): when enabled and keystore is configured, an HTTPS listener is started; optionally, HTTP requests are redirected (308) to HTTPS.
- Output: JSON for API responses, HTML for UI pages.
- Errors: JSON {"error":"message"}; appropriate HTTP status (400/404/405/500). Health endpoints return 200 with ok=false payloads for failures except /ready uses 503 when DB is not ready.

Ports and processes
- HTTP: default 8080. Override with -Dappbana.port or APPBANA_PORT.
- HTTPS (optional): default 8443 when httpsEnabled=true; override with httpsPort. When redirectHttpToHttps=true, HTTP responds 308 to the HTTPS URL.
- Single JVM process. Request handling uses virtual threads (Thread.ofVirtual()).

Threading model
- com.sun.net.httpserver.HttpServer with a per-request virtual thread executor: server.setExecutor(r -> Thread.ofVirtual().start(r)).
- When HTTPS is enabled, an HttpsServer is created with SSLContext built from the provided keystore; same executor and contexts are configured on both servers.

Key packages and classes (src/main/java/org/example)
- Main.java — Entrypoint. Initializes SchemaManager.init() and starts ApiServer on configured port.
- ApiServer.java — Binds routes and serves static UIs. Handlers:
  - /schema — SchemaHandler: POST save/preview; GET list; GET by name (auth enforced when enabled)
  - /api — EntityHandler: POST create; GET list; GET/PUT/DELETE by id (auth enforced when enabled)
  - /api/endpoints — returns generated CRUD endpoints (also handled under /api special-case)
  - /openapi.json — builds OpenAPI from stored schemas (auth enforced when enabled)
  - /ui/builder — serves builder.html
  - /ui/datasource — serves datasource.html
  - /ui/swagger — serves swagger.html (embedded Swagger UI loading /openapi.json)
  - /ui/datasource/config|list|save|test|activate|delete — JSON endpoints for multi-DS management (auth enforced when enabled)
  - /ui/datasource/health — GET; ping a datasource by name (or active if omitted)
  - HTTPS: when httpsEnabled=true, starts HttpsServer with keystore-based TLS; optional HTTP→HTTPS redirect.
- SchemaManager.java — Schema persistence/migrations (init/save/generateMigrationPlan/list/load)
- JdbcManager.java — Connection acquisition via HikariCP. Uses DriverUtil to infer driver/type; rebuilds pool lazily on config change
- ConfigManager.java — Load/save config JSON (default path), normalize into multi-DS shape; infer missing datasource types via DriverUtil; apply env overrides; apply token and HTTPS env overrides
- DriverUtil.java — Centralized mapping for DB type and JDBC driver inference.
- AppConfig.java — Root config model + datasources[] + activeDatasource + adminToken + readToken + HTTPS fields (httpsEnabled, httpsPort, keystorePath, keystorePassword, keyPassword, redirectHttpToHttps).
- DatasourceConfig.java — DS model: {name,type,jdbcUrl,username,password,driver,maxPoolSize?,minIdle?,connectionTimeoutMs?,idleTimeoutMs?,maxLifetimeMs?,autoCommit?,poolName?, lastTest*?}.
- OpenApiGenerator.java — Converts stored EntitySchema list into a minimal OpenAPI 3.0 document.
- model/EntitySchema.java — Schema model with Field sub-class (name,type,length,required,primaryKey,autoIncrement,min,max,pattern).

Static resources (src/main/resources/ui)
- builder.html — Minimal schema builder UI (posts JSON to /schema; includes an Auth token box; sends X-AppBana-Token and Authorization headers).
- datasource.html — Multi-datasource management UI with a JDBC URL Builder and a Test Connection button, plus per-row Test and Ping actions. Shows Status chip and Last tested. Fields: name, type, jdbcUrl, username, password, driver, and Pool section (maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName). Actions: Save/Activate/Delete/List/Load/Test/Ping. Includes Auth token box and sends token header.
- swagger.html — Embedded Swagger UI for /openapi.json with an Auth token box; uses requestInterceptor to attach token headers on all requests (including initial spec fetch).

Config resolution
- Path: APPBANA_CONFIG or -Dappbana.config; default data/appbana-config.json.
- Env overrides on root fields: APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER. Token overrides: APPBANA_ADMIN_TOKEN, APPBANA_READ_TOKEN. HTTPS overrides: APPBANA_HTTPS_ENABLED, APPBANA_HTTPS_PORT, APPBANA_KEYSTORE_PATH, APPBANA_KEYSTORE_PASSWORD, APPBANA_KEY_PASSWORD, APPBANA_REDIRECT_HTTP_TO_HTTPS.

Security considerations
- Token-based authentication (optional) is implemented. If adminToken/readToken in AppConfig (or env/system props) are blank, auth is disabled (development mode). If configured:
  - Read operations require readToken or adminToken; write operations require adminToken.
  - Clients may send `X-AppBana-Token` or `Authorization: Bearer`.
  - UIs (builder, datasource, swagger) include a token input that stores the token in localStorage and injects headers into requests.
- HTTPS (optional): use keystore-based TLS; for production, prefer real certificates and enable HTTP→HTTPS redirect.

Developer workflows
- Build: ./mvnw -DskipTests package → target/app-bana-1.0-SNAPSHOT-fat.jar
- Run (HTTP only): java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- Run (HTTPS): set APPBANA_HTTPS_ENABLED=true and keystore env vars, then run the JAR; visit https://localhost:<httpsPort>/ui/builder

Test checklist (manual)
- With tokens set, ensure 401 is returned when missing/invalid token; success when valid.
- With HTTPS enabled, confirm HTTPS listener works and optional HTTP→HTTPS redirect returns 308 with Location.
- GET /ui/datasource/list returns array with lastTest* fields
- POST /ui/datasource/save with new name creates + activates; list shows it
- POST /ui/datasource/test with name persists lastTest*; list shows Status and Last tested updated
- GET /ui/datasource/health?name=<ds> shows live/down with elapsedMs; Ping button updates the row
- POST /schema?preview=true returns DDL plan; POST /schema applies
- CRUD endpoints operate as expected; GET /openapi.json reflects entities; /ui/swagger renders the spec
