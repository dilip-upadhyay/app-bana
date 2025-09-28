# AppBana — Metadata-driven UI → API → Database

Metadata-driven MVP: design forms in a minimal UI builder, persist the schema, auto-create/migrate a backing table, and expose runtime CRUD APIs. Implemented with plain Java SE (no heavy frameworks).

Quick summary
- Frontend: minimal UI builder (vanilla JS) that emits schema JSON.
- Backend: Java (HttpServer) that persists schemas, auto-creates/migrates tables via JDBC, and exposes generic CRUD endpoints at runtime.
- DB: H2 embedded (file) by default; JDBC usage allows swapping to Postgres/MySQL/etc.
- Datasources: built-in UI to add/manage multiple datasources (by name and type) and select the active one at runtime.
- Pooling: HikariCP connection pool with configurable settings per datasource.
- OpenAPI: live spec at /openapi.json and an embedded Swagger UI at /ui/swagger.
- Health: /health (liveness), /ready (readiness with DB check), and per-datasource health at /ui/datasource/health.
- Authentication (optional): token-based auth for /schema, /api/*, /openapi.json, and /ui/datasource/*.

Status of repository
- Fully working MVP backend and minimal frontend builder included.
- Basic builder-v1 UI is present; advanced builder-v2 files were removed.
- Swagger/OpenAPI spec is available at `/openapi.json` and browsable at `/ui/swagger`.
- Datasource management UI available at `/ui/datasource` with list/activate/delete actions, Test Connection, and a live Status badge with “Last tested” info.
- HikariCP pool initialized based on the current active datasource; reconfigured when settings change.
- Built fat JAR available under `target/` after building.
- COPILOT_NOTES.md contains an agent-friendly snapshot of the current state.
- For a step-by-step walkthrough, see `USER_GUIDE.md`.
- UI_Development_Plan.md contains the evolving architecture plan for an upcoming custom in-house no/low‑code “Studio” (prior Angular plan deprecated).

Tech stack
- Java 25 (runs with virtual threads for HTTP request handling)
- H2 (embedded) for development
- Jackson (jackson-databind) for JSON
- SLF4J simple for logging
- HikariCP for JDBC connection pooling
- Maven build with Shade plugin (uber jar)

Build & run
- With system Maven installed:
  - mvn -DskipTests package
  - java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- With the provided wrapper:
  - chmod +x mvnw
  - ./mvnw -DskipTests package
  - java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- From IDE: run org.example.Main

Port configuration
- Default HTTP port: 8080
- Override via system property: -Dappbana.port=9090
- Or via environment variable: APPBANA_PORT=9090

HTTPS (optional)
- The server can also listen on HTTPS when enabled via config or env/system props.
- Config fields in appbana-config.json:
  - httpsEnabled: true|false (default: false)
  - httpsPort: number (default: 8443)
  - keystorePath: path to JKS or PKCS12 keystore (e.g., certs/keystore.p12)
  - keystorePassword: password for the keystore
  - keyPassword: password for the key (defaults to keystorePassword if omitted)
  - redirectHttpToHttps: true|false — if true, the HTTP server responds with 308 redirect to the HTTPS URL
- Environment variables / system properties:
  - APPBANA_HTTPS_ENABLED=true | -Dappbana.https.enabled=true
  - APPBANA_HTTPS_PORT=8443 | -Dappbana.https.port=8443
  - APPBANA_KEYSTORE_PATH=certs/keystore.p12 | -Dappbana.keystore.path=...
  - APPBANA_KEYSTORE_PASSWORD=changeit | -Dappbana.keystore.password=...
  - APPBANA_KEY_PASSWORD=changeit | -Dappbana.key.password=...
  - APPBANA_REDIRECT_HTTP_TO_HTTPS=true | -Dappbana.redirect.http.to.https=true
- Quickstart (self-signed, PKCS12)
  1) Generate a keystore:
     keytool -genkeypair -alias appbana -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore certs/keystore.p12 -storepass changeit -keypass changeit -dname "CN=localhost, OU=Dev, O=AppBana, L=Local, S=Local, C=US"
  2) Run with env vars:
     APPBANA_HTTPS_ENABLED=true \
     APPBANA_KEYSTORE_PATH=certs/keystore.p12 \
     APPBANA_KEYSTORE_PASSWORD=changeit \
     APPBANA_KEY_PASSWORD=changeit \
     APPBANA_HTTPS_PORT=8443 \
     APPBANA_REDIRECT_HTTP_TO_HTTPS=true \
     java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
  3) Open https://localhost:8443/ui/builder (accept your self-signed cert in the browser).

Authentication (optional)
- App supports simple token-based auth. When no tokens are configured, all endpoints are open (dev mode).
- Configure tokens in config file or via env/system properties:
  - Config fields: `adminToken` (read-write) and `readToken` (read-only)
  - Env vars: `APPBANA_ADMIN_TOKEN`, `APPBANA_READ_TOKEN`
  - System props: `-Dappbana.admin.token=...`, `-Dappbana.read.token=...`
- Client headers (either works on the server):
  - `X-AppBana-Token: <token>`
  - `Authorization: Bearer <token>`
- Built-in UIs (builder.html, datasource.html, swagger.html):
  - Use only `X-AppBana-Token` (token value sanitized to avoid browser header restrictions) and store it in localStorage.
  - For API clients and curl examples, `Authorization: Bearer` continues to work.
- Authorization rules when tokens are set:
  - Read-only (readToken or adminToken): GET /schema, GET /schema/{name}, GET /api/*, GET /openapi.json, GET /ui/datasource/list|config|health
  - Admin (adminToken only): POST /schema (apply/preview), POST /api/* (writes), PUT/DELETE /api/*, POST /ui/datasource/save|test|activate|delete
- UIs: builder.html and datasource.html include an “Auth token” box; swagger.html also has one. Saving the token stores it in localStorage and all UI requests send the header automatically.

Default runtime behavior
- On startup the app attempts to ensure two metadata tables (in the active datasource):
  - `appbana_schemas(name PK, json CLOB)` — stores schema JSON
  - `appbana_migrations(id IDENTITY, schema_name, sql CLOB, executed_at TIMESTAMP)` — records DDL executed
- Embedded HTTP server listens on the configured port and uses Java virtual threads for request handling.
- UI builder: http://localhost:8080/ui/builder
- Datasource UI: http://localhost:8080/ui/datasource
- Swagger UI: http://localhost:8080/ui/swagger
- OpenAPI: http://localhost:8080/openapi.json (requires token when auth is enabled)
- Health: http://localhost:8080/health (liveness), http://localhost:8080/ready (readiness), and http://localhost:8080/ui/datasource/health (per-datasource DB ping)

Health & readiness
- GET `/health` → `{ "status": "UP" }` (process liveness)
- GET `/ready` → `{ ok: boolean, activeDatasource?: string, dbProduct?: string, dbVersion?: string, elapsedMs: number, error?: string }`
  - Attempts a DB connection using the active datasource. Returns HTTP 200 when ok=true, or 503 with error details when ok=false.
- GET `/ui/datasource/health?name=<ds>&timeoutSec=<n>` → `{ ok: boolean, name: string, url: string (masked), dbProduct?: string, dbVersion?: string, elapsedMs: number, error?: string, sqlState?: string, errorCode?: number }`
  - Pings the specified datasource (or the active one if name omitted). `timeoutSec` optional (default 3; max 60). URL is masked to avoid leaking passwords.

Datasource management
- UI: `/ui/datasource` supports Add/Update, List, Activate, Delete, and Test Connection (both from the form and via a per-datasource “Test” action in the list). Each row shows a Status badge (Live/Down/Unknown) and a “Last tested” column.
- Each datasource has: name, type (h2/postgres/mysql/mariadb/mssql/oracle/sqlite/custom), jdbcUrl, username, password, driver.
- JDBC URL Builder: optional helper in the form that builds the JDBC URL from fields (type, host, port, database/service, and extra params). Supports H2 (file/mem), Postgres, MySQL, MariaDB, SQL Server, Oracle, and SQLite. Enable Auto-build to keep the URL in sync as you edit fields.
- Server-side URL build (API): if you POST to `/ui/datasource/save` without `url`, the server will construct it from components. Accepted fields:
  - Common: `type`, `host`, `port`, `dbname`, `params`
  - H2-specific: `h2Mode` (file|mem), `h2File`, `h2MemName`
  - SQLite-specific: `sqliteFile`
  Example (Postgres):
  ```json
  {
    "name":"pg","type":"postgres","host":"localhost","port":"5432","dbname":"appbana",
    "username":"sa","password":"Password_123#","driver":"org.postgresql.Driver"
  }
  ```
- Test connection: click “Test Connection” in the form or the per-row “Test” action to attempt a short-lived connection (uses the URL or builds one from components). The API is also available at `POST /ui/datasource/test`.
  - Request body: either {url, username?, password?, driver?, type?} or components {type, host, port, dbname, params?, username?, password?, driver?}; `name` can also be provided to test an existing saved datasource.
  - Optional `timeoutSec` limits the attempt (default 5, max 60).
  - Response: `{ ok: boolean, message?: string, error?: string, url: string (masked), dbProduct?: string, dbVersion?: string, elapsedMs: number, sqlState?: string, errorCode?: number }`
  - When testing a saved datasource by `name`, the last test result is persisted to config and shown in the list.
- Optional pool settings per datasource: maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName.
- Driver inference: if driver is blank, the system infers it from `type` or the JDBC URL.
- Active datasource: the server uses the currently active datasource for all DB operations.
- Pool reconfiguration: any change to the active datasource (including pool fields) rebuilds the Hikari pool lazily on the next getConnection().

Datasource API (JSON)
- GET `/ui/datasource/list` → array of datasources (without passwords), each has {name,type,jdbcUrl,username,driver,active,maxPoolSize,minIdle,connectionTimeoutMs,idleTimeoutMs,maxLifetimeMs,autoCommit,poolName,
  lastTestOk?, lastTestAtEpochMs?, lastTestMessage?, lastTestDbProduct?, lastTestDbVersion?, lastTestElapsedMs?}.
- GET `/ui/datasource/config` → current active datasource details (without password), includes the pool fields.
- POST `/ui/datasource/save` body: {name, type?, url, username?, password?, driver?, maxPoolSize?, minIdle?, connectionTimeoutMs?, idleTimeoutMs?, maxLifetimeMs?, autoCommit?, poolName?}
  - Upserts the datasource by name; if password is empty/missing it isn’t overwritten; activates the saved datasource.
- POST `/ui/datasource/test` body: {url?, type?, host?, port?, dbname?, params?, username?, password?, driver?, name?, timeoutSec?}
  - Attempts a one-off connection; returns `{ok, ...}` with DB product/version on success; masks sensitive parts of the URL.
  - If `name` is provided and matches a saved datasource, the last test result is persisted to config.
- GET `/ui/datasource/health?name=<ds>&timeoutSec=<n>` — ping a datasource (or active if name omitted) and return status.
- POST `/ui/datasource/activate` body: {name}
- POST `/ui/datasource/delete` body: {name}

Pooling defaults (if a field is omitted)
- maxPoolSize: 10
- minIdle: 2
- connectionTimeoutMs: 30000
- idleTimeoutMs: 600000 (10 minutes)
- maxLifetimeMs: 1800000 (30 minutes)
- autoCommit: true
- poolName: `appbana-<datasourceName>`

Configuration
- Config file path: `APPBANA_CONFIG` env var or `-Dappbana.config=...` system property (default: `data/appbana-config.json`).
- Environment overrides (optional):
  - APPBANA_JDBC_URL — override JDBC URL
  - APPBANA_DB_USER — override username
  - APPBANA_DB_PASS — override password
  - APPBANA_DB_DRIVER — override driver class
  - APPBANA_ADMIN_TOKEN — set admin token
  - APPBANA_READ_TOKEN — set read-only token
  - APPBANA_HTTPS_ENABLED, APPBANA_HTTPS_PORT, APPBANA_KEYSTORE_PATH, APPBANA_KEYSTORE_PASSWORD, APPBANA_KEY_PASSWORD, APPBANA_REDIRECT_HTTP_TO_HTTPS
- Config file format (example):
```
{
  "datasources": [
    {
      "name": "primary",
      "type": "h2",
      "jdbcUrl": "jdbc:h2:./data/appbana;AUTO_SERVER=TRUE",
      "username": "sa",
      "password": "secret",
      "driver": "org.h2.Driver",
      "maxPoolSize": 10,
      "minIdle": 2,
      "connectionTimeoutMs": 30000,
      "idleTimeoutMs": 600000,
      "maxLifetimeMs": 1800000,
      "autoCommit": true,
      "poolName": "appbana-primary"
    }
  ],
  "activeDatasource": "primary",
  "adminToken": "change-me-admin",
  "readToken": "change-me-read",
  "httpsEnabled": true,
  "httpsPort": 8443,
  "keystorePath": "certs/keystore.p12",
  "keystorePassword": "changeit",
  "keyPassword": "changeit",
  "redirectHttpToHttps": true
}
```
- Backward compatibility: if only root fields are present (jdbcUrl/username/password/driver/name), the app seeds a default datasource and marks it active.

API endpoints (runtime generic CRUD)
- POST /schema — save schema or preview migration with `?preview=true`
- GET /schema — list schema names (supports `?page=&size=&q=`)
- GET /schema/{name} — return schema JSON
- POST /api/{entity} — insert
- GET /api/{entity} — list
- GET /api/{entity}/{id} — get by id
- PUT /api/{entity}/{id} — update by id
- DELETE /api/{entity}/{id} — delete by id
- GET /openapi.json — OpenAPI 3.0 spec for all generated endpoints
- GET /health — liveness check
- GET /ready — readiness check with DB metadata

Schema JSON (example)
```
{
  "name": "contact",
  "fields": [
    {"name":"id","type":"long","primaryKey":true,"autoIncrement":true},
    {"name":"firstName","type":"string","length":100,"required":true},
    {"name":"age","type":"int","min":0}
  ]
}
```

Where to change common settings
- Port: src/main/java/org/example/Main.java (argument to ApiServer.start)
- Datasource resolution & pooling: src/main/java/org/example/JdbcManager.java
- Config: src/main/java/org/example/ConfigManager.java and src/main/java/org/example/AppConfig.java
- Datasource UI: src/main/resources/ui/datasource.html

Key files
- src/main/java/org/example/ApiServer.java — HTTP handlers (schema, entity CRUD, datasource management, openapi, swagger UI)
- src/main/java/org/example/SchemaManager.java — schema persistence and migration
- src/main/java/org/example/JdbcManager.java — JDBC connection (HikariCP pool; uses active datasource)
- src/main/java/org/example/ConfigManager.java — loads/saves config; normalizes multi-datasource format
- src/main/java/org/example/AppConfig.java, DatasourceConfig.java — config models (DatasourceConfig includes pool and last test fields)
- src/main/java/org/example/model/EntitySchema.java — schema model
- src/main/resources/ui/builder.html — minimal schema builder
- src/main/resources/ui/datasource.html — datasource management UI (pool settings, status badge, last tested)
- src/main/resources/ui/swagger.html — embedded Swagger UI for /openapi.json

Notes
- If DB credentials are wrong at startup, the app still starts so you can fix settings via `/ui/datasource`.
- Identifier quoting uses double-quoted UPPERCASE to avoid reserved word/case issues in H2.
- Backlog: see `TODO.md` for prioritized next steps and enhancements.
- New to the project? Start with `USER_GUIDE.md`.
- UI Styling policy (designer/runtime): see `docs/STYLE_GUIDE.md` (now generalized for custom framework; previous Angular Material specifics removed).

---

(Angular-specific build/run instructions removed; custom Studio implementation will define new scripts when introduced.)
